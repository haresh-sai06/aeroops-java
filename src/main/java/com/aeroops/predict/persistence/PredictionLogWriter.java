package com.aeroops.predict.persistence;

import com.aeroops.predict.api.DelayResponse;
import com.aeroops.predict.api.FlightRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Writes the audit log off the request thread so Postgres latency never sits on the hot path. */
@Component
public class PredictionLogWriter {
    private static final Logger log = LoggerFactory.getLogger(PredictionLogWriter.class);
    private final PredictionLogRepository repo;

    public PredictionLogWriter(PredictionLogRepository repo) { this.repo = repo; }

    @Async("logExecutor")
    public void write(FlightRequest r, DelayResponse resp, long latencyMicros) {
        try {
            repo.save(new PredictionLog(Instant.now(), r.carrier(), r.origin(), r.dest(), r.flightDate(), r.depHour(),
                    r.prevLegArrDelay(), resp.delayProbability(), resp.predictedDelayed(), resp.cached(),
                    latencyMicros, resp.modelVersion()));
        } catch (Exception e) {
            log.warn("failed to persist prediction log: {}", e.toString());
        }
    }
}
