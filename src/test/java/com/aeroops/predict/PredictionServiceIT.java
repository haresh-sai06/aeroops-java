package com.aeroops.predict;

import com.aeroops.predict.persistence.PredictionLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end: HTTP -> Redis cache -> XGBoost -> async Postgres audit log (Flyway schema).
 * Uses Testcontainers when Docker is available; otherwise falls back to already-running
 * Postgres/Redis given by DB_URL / REDIS_HOST (how it was run on the dev box, which has no Docker).
 */
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PredictionServiceIT {
    static final boolean DOCKER = dockerAvailable();
    static PostgreSQLContainer<?> pg;
    static GenericContainer<?> redis;

    static boolean dockerAvailable() {
        try { return DockerClientFactory.instance().isDockerAvailable(); } catch (Throwable t) { return false; }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        if (DOCKER) {
            pg = new PostgreSQLContainer<>("postgres:16-alpine");
            redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
            pg.start();
            redis.start();
            r.add("spring.datasource.url", pg::getJdbcUrl);
            r.add("spring.datasource.username", pg::getUsername);
            r.add("spring.datasource.password", pg::getPassword);
            r.add("spring.data.redis.host", redis::getHost);
            r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        }
        r.add("management.otlp.tracing.endpoint", () -> "http://localhost:1/v1/traces");
    }

    @Autowired TestRestTemplate http;
    @Autowired PredictionLogRepository logs;
    @Autowired StringRedisTemplate redisTemplate;

    @Test
    void cacheMissThenHitAndAuditLogged() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        long before = logs.count();
        Map<String, Object> body = Map.of("carrier", "WN", "origin", "ATL", "dest", "LGA", "flight_date", "2024-04-02",
                "dep_hour", 17, "distance", 762, "prev_leg_arr_delay", 55, "prev_leg_dep_delay", 50, "leg_of_day", 3);
        ResponseEntity<Map> first = http.postForEntity("/api/v1/predict/delay", body, Map.class);
        ResponseEntity<Map> second = http.postForEntity("/api/v1/predict/delay", body, Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("cached")).isEqualTo(false);
        assertThat(second.getBody().get("cached")).isEqualTo(true);
        assertThat(second.getBody().get("delay_probability")).isEqualTo(first.getBody().get("delay_probability"));
        await().until(() -> logs.count() >= before + 2);
        assertThat(logs.countByCacheHitTrue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void actuatorExposesHealthAndCustomMetrics() {
        http.postForEntity("/api/v1/predict/delay", Map.of("carrier", "AA", "origin", "DFW", "dest", "ORD",
                "flight_date", "2024-04-03", "dep_hour", 8, "distance", 802), Map.class);
        assertThat(http.getForObject("/actuator/health", String.class)).contains("UP");
        String prom = http.getForObject("/actuator/prometheus", String.class);
        assertThat(prom).contains("aeroops_inference_seconds").contains("aeroops_cache_total")
                .contains("aeroops_predict_delay_seconds").contains("http_server_requests_seconds_bucket");
    }
}
