package com.aeroops.predict;

import com.aeroops.predict.api.FlightRequest;
import com.aeroops.predict.model.DelayFeatureBuilder;
import com.aeroops.predict.model.ModelRegistry;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Golden test: Java tree evaluator must reproduce the Python/XGBoost predictions (scripts/gen_parity_fixtures.py). */
class XgbParityTest {
    static ModelRegistry registry;
    static ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @BeforeAll
    static void load() throws Exception {
        registry = new ModelRegistry(new ClassPathResource("models/"), mapper);
    }

    @Test
    void matchesPythonXgboostOn500Requests() throws Exception {
        JsonNode fixtures = mapper.readTree(new ClassPathResource("parity_fixtures.json").getInputStream());
        double maxErr = 0;
        int n = 0;
        for (JsonNode f : fixtures) {
            FlightRequest req = mapper.treeToValue(f.get("request"), FlightRequest.class);
            DelayFeatureBuilder.Built b = registry.features().build(req);
            double p = registry.model().predictProba(b.vector());
            maxErr = Math.max(maxErr, Math.abs(p - f.get("probability").asDouble()));
            List<String> expectedUnknown = new ArrayList<>();
            f.get("unknown").forEach(u -> expectedUnknown.add(u.asText()));
            assertThat(b.unknownCategories()).isEqualTo(expectedUnknown);
            n++;
        }
        System.out.printf("parity: n=%d max|p_java - p_python|=%.3e%n", n, maxErr);
        assertThat(n).isEqualTo(500);
        assertThat(maxErr).isLessThan(1e-5);
    }

    @Test
    void modelShape() {
        assertThat(registry.model().numTrees()).isEqualTo(300);
        assertThat(registry.model().numFeatures()).isEqualTo(20);
    }
}
