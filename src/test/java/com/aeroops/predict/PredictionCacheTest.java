package com.aeroops.predict;

import com.aeroops.predict.api.DelayResponse;
import com.aeroops.predict.cache.PredictionCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PredictionCacheTest {
    @SuppressWarnings("unchecked")
    @Test
    void failsOpenWhenRedisIsDown() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        doThrow(new RedisConnectionFailureException("down")).when(ops).set(anyString(), anyString(), any(Duration.class));
        PredictionCache cache = new PredictionCache(redis, new ObjectMapper(), Duration.ofMinutes(1));
        assertThat(cache.get("k")).isEmpty();
        assertThatCode(() -> cache.put("k", new DelayResponse(0.1, false, 0.5, "x", List.of(), false, "v"))).doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    @Test
    void roundTripsJsonWithTtl() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        ObjectMapper m = new ObjectMapper();
        DelayResponse r = new DelayResponse(0.42, true, 0.3, "2024-03-31", List.of("origin"), false, "v1");
        PredictionCache cache = new PredictionCache(redis, m, Duration.ofMinutes(10));
        cache.put("k", r);
        verify(ops).set(eq("k"), anyString(), eq(Duration.ofMinutes(10)));
        when(ops.get("k")).thenReturn(m.writeValueAsString(r));
        assertThat(cache.get("k")).contains(r);
    }
}
