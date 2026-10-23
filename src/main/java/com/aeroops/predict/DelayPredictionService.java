package com.aeroops.predict;

import com.aeroops.predict.api.DelayResponse;
import com.aeroops.predict.api.FlightRequest;
import com.aeroops.predict.cache.PredictionCache;
import com.aeroops.predict.model.DelayFeatureBuilder;
import com.aeroops.predict.model.ModelRegistry;
import com.aeroops.predict.persistence.PredictionLogWriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Cache-aside prediction: Redis lookup -> XGBoost inference on miss -> async audit log to Postgres. */
@Service
public class DelayPredictionService {
    private final ModelRegistry registry;
    private final PredictionCache cache;
    private final PredictionLogWriter logWriter;
    private final Timer inferenceTimer;
    private final Counter hits, misses;

    public DelayPredictionService(ModelRegistry registry, PredictionCache cache, PredictionLogWriter logWriter, MeterRegistry meters) {
        this.registry = registry;
        this.cache = cache;
        this.logWriter = logWriter;
        this.inferenceTimer = Timer.builder("aeroops.inference").description("XGBoost tree evaluation time")
                .publishPercentiles(0.5, 0.95, 0.99).register(meters);
        this.hits = Counter.builder("aeroops.cache").tag("result", "hit").register(meters);
        this.misses = Counter.builder("aeroops.cache").tag("result", "miss").register(meters);
    }

    @Observed(name = "aeroops.predict.delay", contextualName = "predict-delay")
    public DelayResponse predict(FlightRequest req) {
        long t0 = System.nanoTime();
        String key = req.cacheKey();
        Optional<DelayResponse> cached = cache.get(key);
        DelayResponse resp;
        if (cached.isPresent()) {
            hits.increment();
            resp = cached.get().withCached(true);
        } else {
            misses.increment();
            resp = infer(req);
            cache.put(key, resp);
        }
        logWriter.write(req, resp, (System.nanoTime() - t0) / 1000);
        return resp;
    }

    public DelayResponse infer(FlightRequest req) {
        DelayFeatureBuilder.Built b = registry.features().build(req);
        double p = inferenceTimer.record(() -> registry.model().predictProba(b.vector()));
        return new DelayResponse(Math.round(p * 1e4) / 1e4, p >= registry.threshold(),
                Math.round(registry.threshold() * 1e3) / 1e3, registry.features().asOf(),
                b.unknownCategories(), false, registry.version());
    }
}
