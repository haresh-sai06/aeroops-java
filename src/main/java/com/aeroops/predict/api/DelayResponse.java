package com.aeroops.predict.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DelayResponse(double delayProbability, boolean predictedDelayed, double threshold,
                            String historyAsOf, List<String> unknownCategories, boolean cached, String modelVersion) {
    public DelayResponse withCached(boolean c) {
        return new DelayResponse(delayProbability, predictedDelayed, threshold, historyAsOf, unknownCategories, c, modelVersion);
    }
}
