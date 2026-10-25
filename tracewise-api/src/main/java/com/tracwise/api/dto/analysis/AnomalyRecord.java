package com.tracwise.api.dto.analysis;

import java.time.LocalDateTime;
import java.util.Map;

public record AnomalyRecord(
        String id,
        String service,
        LocalDateTime timestamp,
        String metricName,
        double anomalyScore,
        String severity,
        double observedValue,
        double expectedBaseline,
        String unit,
        String description,
        Map<String, Double> featureContributions,
        String detectionMethod
) {}
