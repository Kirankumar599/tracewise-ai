package com.tracwise.api.dto;

import com.tracwise.api.dto.analysis.*;

import java.util.Collections;
import java.util.List;

public record IncidentResponse(
        String incidentId,
        String status,
        String message,
        int serviceCount,
        int logCount,
        int dependencyCount,
        int changeCount,
        IncidentSummary summary,
        List<AnomalyRecord> anomalies,
        List<TimelineEvent> timeline,
        DependencyGraph dependencyGraph,
        List<RootCauseHypothesis> hypotheses,
        List<ServiceHealthSummary> affectedServices,
        List<DiagnosticAction> recommendedActions,
        String engineMode
) {
    // Backward-compatible 7-argument constructor
    public IncidentResponse(
            String incidentId,
            String status,
            String message,
            int serviceCount,
            int logCount,
            int dependencyCount,
            int changeCount
    ) {
        this(
                incidentId,
                status,
                message,
                serviceCount,
                logCount,
                dependencyCount,
                changeCount,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "STANDARD_RECEIVED"
        );
    }
}
