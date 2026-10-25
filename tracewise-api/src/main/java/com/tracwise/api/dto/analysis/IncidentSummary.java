package com.tracwise.api.dto.analysis;

import java.time.LocalDateTime;

public record IncidentSummary(
        String title,
        String severity,
        String status,
        LocalDateTime detectedAt,
        String primaryRootCause,
        String primaryAffectedService,
        int blastRadiusPercent,
        String cascadeDuration,
        String executiveSummary
) {}
