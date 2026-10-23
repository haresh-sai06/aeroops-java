package com.aeroops.predict.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FlightRequest(
        @NotBlank @Size(max = 3) String carrier,
        @NotBlank @Size(max = 4) String origin,
        @NotBlank @Size(max = 4) String dest,
        @NotNull LocalDate flightDate,
        @NotNull @Min(0) @Max(23) Integer depHour,
        @NotNull @Positive Double distance,
        Double prevLegArrDelay,
        Double prevLegDepDelay,
        @Min(1) Integer legOfDay) {

    /** Canonical cache key: every field that affects the prediction. */
    public String cacheKey() {
        return String.join(":", "delay", carrier, origin, dest, flightDate.toString(), depHour.toString(),
                distance.toString(), String.valueOf(prevLegArrDelay), String.valueOf(prevLegDepDelay), String.valueOf(legOfDay));
    }
}
