package com.tracwise.api.dto.analysis;

import java.time.LocalDateTime;

public record TimelineEvent(
        String id,
        LocalDateTime timestamp,
        String eventType,
        String service,
        String summary,
        String details,
        String severity,
        boolean isInitiatingEvent
) {}
