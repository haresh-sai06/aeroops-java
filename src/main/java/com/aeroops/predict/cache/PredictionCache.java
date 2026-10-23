package com.aeroops.predict.cache;

import com.aeroops.predict.api.DelayResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** Redis cache-aside with TTL. Fails open: if Redis is down we still serve predictions. */
@Component
public class PredictionCache {
    private static final Logger log = LoggerFactory.getLogger(PredictionCache.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public PredictionCache(StringRedisTemplate redis, ObjectMapper mapper,
                           @Value("${aeroops.cache.ttl:PT10M}") Duration ttl) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
    }

    public Optional<DelayResponse> get(String key) {
        try {
            String v = redis.opsForValue().get(key);
            return v == null ? Optional.empty() : Optional.of(mapper.readValue(v, DelayResponse.class));
        } catch (Exception e) {
            log.warn("redis get failed, serving uncached: {}", e.toString());
            return Optional.empty();
        }
    }

    public void put(String key, DelayResponse value) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("redis put failed: {}", e.toString());
        }
    }
}
