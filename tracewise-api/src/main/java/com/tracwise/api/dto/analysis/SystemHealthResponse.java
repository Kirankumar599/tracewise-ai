package com.tracwise.api.dto.analysis;

import java.time.LocalDateTime;

public record SystemHealthResponse(
        String status,
        String apiVersion,
        LocalDateTime timestamp,
        boolean mlServiceConnected,
        String mlServiceUrl,
        String mlModelVersion,
        String rootCauseEngineVersion,
        int totalInvestigationsCompleted
) {}
