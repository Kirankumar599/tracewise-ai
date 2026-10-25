package com.tracwise.api.dto.analysis;

import java.util.List;

public record DependencyGraph(
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        boolean inferredTopology
) {
    public DependencyGraph(List<GraphNode> nodes, List<GraphEdge> edges) {
        this(nodes, edges, false);
    }

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
            boolean isCriticalPath,
            boolean isInferred
    ) {
        public GraphEdge(String from, String to, String status, double errorRate, double latencyMs, boolean isCriticalPath) {
            this(from, to, status, errorRate, latencyMs, isCriticalPath, false);
        }
    }
}
