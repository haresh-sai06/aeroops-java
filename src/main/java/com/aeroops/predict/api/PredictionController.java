package com.aeroops.predict.api;

import com.aeroops.predict.persistence.PredictionLog;
import com.aeroops.predict.persistence.PredictionLogRepository;
import com.aeroops.predict.DelayPredictionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PredictionController {
    private final DelayPredictionService service;
    private final PredictionLogRepository logs;

    public PredictionController(DelayPredictionService service, PredictionLogRepository logs) {
        this.service = service;
        this.logs = logs;
    }

    @PostMapping("/predict/delay")
    public DelayResponse predict(@Valid @RequestBody FlightRequest req) {
        return service.predict(req);
    }

    @GetMapping("/predictions/recent")
    public List<PredictionLog> recent(@RequestParam(defaultValue = "20") int limit) {
        return logs.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 200)));
    }

    @GetMapping("/predictions/stats")
    public Map<String, Object> stats() {
        return Map.of("total", logs.count(), "cache_hits", logs.countByCacheHitTrue());
    }
}
