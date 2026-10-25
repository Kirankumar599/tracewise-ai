package com.tracwise.api.dto.analysis;

public record ServiceHealthSummary(
        String serviceName,
        String status,
        double peakCpuPercent,
        double peakLatencyMs,
        double peakErrorRate,
        int maxActiveConnections,
        int connectionPoolLimit,
        int errorCount,
        int anomalyCount
) {}
