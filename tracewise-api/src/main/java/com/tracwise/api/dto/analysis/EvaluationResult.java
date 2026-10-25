package com.tracwise.api.dto.analysis;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record EvaluationResult(
        String evaluationSuiteVersion,
        LocalDateTime evaluatedAt,
        int totalTestScenarios,
        double detectionPrecision,
        double detectionRecall,
        double detectionF1Score,
        int truePositives,
        int falsePositives,
        int falseNegatives,
        int trueNegatives,
        double top1RootCauseAccuracy,
        double top3RootCauseAccuracy,
        List<ScenarioEvaluationDetail> scenarioDetails,
        Map<String, String> evaluationLimitations
) {
    public record ScenarioEvaluationDetail(
            String scenarioId,
            String scenarioName,
            String groundTruthRootCause,
            String predictedTop1RootCause,
            double top1EvidenceScore,
            boolean isTop1Correct,
            boolean isTop3Correct,
            int anomalyCountDetected,
            String notes
    ) {}
}
