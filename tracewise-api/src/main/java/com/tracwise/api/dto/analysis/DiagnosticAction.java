package com.tracwise.api.dto.analysis;

public record DiagnosticAction(
        String id,
        String title,
        String commandOrCheck,
        String rationale,
        String priority,
        String targetedService
) {}
