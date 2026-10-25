package com.tracwise.api.dto.analysis;

import java.util.List;

public record DependencyGraph(
        List<GraphNode> nodes,
        List<GraphEdge> edges
) {
    public record GraphNode(
            String id,
            String name,
            String tier,
            String status,
            double anomalyScore,
            boolean isRootCause,
            int incomingErrors
    ) {}

    public record GraphEdge(
            String from,
            String to,
            String status,
            double errorRate,
            double latencyMs,
            boolean isCriticalPath
    ) {}
}
