package com.tracwise.api.dto.analysis;

import java.util.List;

public record RootCauseHypothesis(
        int rank,
        String hypothesisId,
        String title,
        String rootCauseCategory,
        String suspectedService,
        double evidenceScore,
        String confidenceTier,
        List<EvidenceItem> supportingEvidence,
        List<EvidenceItem> contradictingEvidence,
        String reasoning,
        List<String> recommendedActions
) {
    public record EvidenceItem(
            String type,
            String summary,
            double scoreContribution,
            String sourceReference
    ) {}
}
