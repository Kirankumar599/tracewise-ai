package com.tracwise.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;
import java.util.List;

public record IncidentRequest(
        @NotBlank String incidentId,

        @NotEmpty
        List<@Valid ServiceMetric> services,

        List<@Valid LogEvent> logs,

        List<@Valid Dependency> dependencies,

        List<@Valid ChangeEvent> recentChanges,

        EvaluationMetadata evaluationMetadata
) {
    // 5-argument constructor preserving existing API calls
    public IncidentRequest(
            String incidentId,
            List<ServiceMetric> services,
            List<LogEvent> logs,
            List<Dependency> dependencies,
            List<ChangeEvent> recentChanges
    ) {
        this(incidentId, services, logs, dependencies, recentChanges, null);
    }

    public record ServiceMetric(
            @NotBlank String name,
            @NotNull LocalDateTime timestamp,
            @PositiveOrZero double cpuPercent,
            @PositiveOrZero double latencyMs,
            @PositiveOrZero double errorRate,
            @PositiveOrZero int activeConnections,
            @PositiveOrZero int connectionPoolLimit
    ) {}

    public record LogEvent(
            @NotNull LocalDateTime timestamp,
            @NotBlank String service,
            @NotBlank String level,
            @NotBlank String message
    ) {}

    public record Dependency(
            @NotBlank String from,
            @NotBlank String to
    ) {}

    public record ChangeEvent(
            @NotNull LocalDateTime timestamp,
            @NotBlank String service,
            @NotBlank String description
    ) {}

    public record EvaluationMetadata(
            String expectedRootCauseService,
            String expectedRootCauseCategory,
            String notes
    ) {}
}
