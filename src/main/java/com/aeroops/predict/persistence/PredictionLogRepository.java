package com.aeroops.predict.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PredictionLogRepository extends JpaRepository<PredictionLog, Long> {
    List<PredictionLog> findAllByOrderByCreatedAtDesc(Pageable page);
    long countByCacheHitTrue();
}
