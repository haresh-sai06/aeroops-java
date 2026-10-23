package com.aeroops.predict.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "prediction_log")
public class PredictionLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Instant createdAt;
    private String carrier;
    private String origin;
    private String dest;
    private LocalDate flightDate;
    private Integer depHour;
    private Double prevLegArrDelay;
    private Double probability;
    private Boolean predictedDelayed;
    private Boolean cacheHit;
    private Long latencyMicros;
    private String modelVersion;

    protected PredictionLog() {}

    public PredictionLog(Instant createdAt, String carrier, String origin, String dest, LocalDate flightDate, Integer depHour,
                         Double prevLegArrDelay, Double probability, Boolean predictedDelayed, Boolean cacheHit,
                         Long latencyMicros, String modelVersion) {
        this.createdAt = createdAt; this.carrier = carrier; this.origin = origin; this.dest = dest;
        this.flightDate = flightDate; this.depHour = depHour; this.prevLegArrDelay = prevLegArrDelay;
        this.probability = probability; this.predictedDelayed = predictedDelayed; this.cacheHit = cacheHit;
        this.latencyMicros = latencyMicros; this.modelVersion = modelVersion;
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCarrier() { return carrier; }
    public String getOrigin() { return origin; }
    public String getDest() { return dest; }
    public LocalDate getFlightDate() { return flightDate; }
    public Integer getDepHour() { return depHour; }
    public Double getPrevLegArrDelay() { return prevLegArrDelay; }
    public Double getProbability() { return probability; }
    public Boolean getPredictedDelayed() { return predictedDelayed; }
    public Boolean getCacheHit() { return cacheHit; }
    public Long getLatencyMicros() { return latencyMicros; }
    public String getModelVersion() { return modelVersion; }
}
