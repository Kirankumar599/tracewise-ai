package com.tracwise.api.service;

import com.tracwise.api.dto.IncidentRequest;
import com.tracwise.api.dto.IncidentRequest.*;
import com.tracwise.api.dto.analysis.*;
import com.tracwise.api.dto.analysis.EvaluationResult.ScenarioEvaluationDetail;
import com.tracwise.api.engine.AnomalyDetectionClient;
import com.tracwise.api.engine.RootCauseEngine;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class EvaluationService {

    private final DemoScenarioService demoScenarioService;
    private final AnomalyDetectionClient anomalyDetectionClient;
    private final RootCauseEngine rootCauseEngine;

    public EvaluationService(
            DemoScenarioService demoScenarioService,
            AnomalyDetectionClient anomalyDetectionClient,
            RootCauseEngine rootCauseEngine
    ) {
        this.demoScenarioService = demoScenarioService;
        this.anomalyDetectionClient = anomalyDetectionClient;
        this.rootCauseEngine = rootCauseEngine;
    }

    public EvaluationResult runEvaluation() {
        List<IncidentRequest> testSuite = new ArrayList<>();
        testSuite.add(demoScenarioService.getScenario("db-pool-exhaustion"));
        testSuite.add(demoScenarioService.getScenario("cpu-saturation"));
        testSuite.add(demoScenarioService.getScenario("latency-error-spike"));
        testSuite.add(demoScenarioService.getScenario("bench-healthy-01"));
        testSuite.add(demoScenarioService.getScenario("bench-mem-02"));

        int tp = 0;
        int fp = 0;
        int fn = 0;
        int tn = 0;

        int top1Correct = 0;
        int top3Correct = 0;
        int totalKnownCauseScenarios = 0;

        List<ScenarioEvaluationDetail> details = new ArrayList<>();

        for (IncidentRequest req : testSuite) {
            var detection = anomalyDetectionClient.detect(req.services());
            List<AnomalyRecord> anomalies = detection.anomalies();

            var engineResult = rootCauseEngine.analyze(
                    req.incidentId(),
                    req.services(),
                    req.logs(),
                    req.dependencies(),
                    req.recentChanges(),
                    anomalies
            );

            boolean hasExpectedGroundTruth = req.evaluationMetadata() != null
                    && req.evaluationMetadata().expectedRootCauseService() != null
                    && !req.evaluationMetadata().expectedRootCauseService().isBlank();

            String expectedService = hasExpectedGroundTruth ? req.evaluationMetadata().expectedRootCauseService() : "NONE";
            String top1Service = engineResult.hypotheses().isEmpty() ? "NONE" : engineResult.hypotheses().get(0).suspectedService();
            double top1Score = engineResult.hypotheses().isEmpty() ? 0.0 : engineResult.hypotheses().get(0).evidenceScore();
boolean isTop1 = top1Service.equalsIgnoreCase(expectedService);

boolean isTop3 = "NONE".equalsIgnoreCase(expectedService)
        ? engineResult.hypotheses().isEmpty()
        : engineResult.hypotheses().stream()
                .limit(3)
                .anyMatch(h ->
                        h.suspectedService().equalsIgnoreCase(expectedService));

            if (isTop1) top1Correct++;
            if (isTop3) top3Correct++;

            if (hasExpectedGroundTruth) {
            totalKnownCauseScenarios++;

                // Anomaly detection metrics for incident scenarios
                if (!anomalies.isEmpty()) {
                    tp += anomalies.size();
                } else {
                    fn++;
                }
            } else {
                // Baseline scenario: expect 0 anomalies
                if (anomalies.isEmpty()) {
                    tn += req.services().size();
                } else {
                    fp += anomalies.size();
                }
            }

            details.add(new ScenarioEvaluationDetail(
                    req.incidentId(),
                    req.evaluationMetadata() != null && req.evaluationMetadata().notes() != null
                            ? req.evaluationMetadata().notes() : req.incidentId(),
                    expectedService,
                    top1Service,
                    top1Score,
                    isTop1,
                    isTop3,
                    anomalies.size(),
                    String.format("Detected %d anomalies. Top-1 hypothesis: %s (%.1f%% score)",
                            anomalies.size(), top1Service, top1Score)
            ));
        }

        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 1.0;
        double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 1.0;

        double f1 = (precision + recall) > 0 ? 2.0 * (precision * recall) / (precision + recall) : 0.0;
        
        double top1Acc = testSuite.isEmpty()
        ? 0.0
        : (double) top1Correct / testSuite.size();

        double top3Acc = testSuite.isEmpty()
        ? 0.0
        : (double) top3Correct / testSuite.size();

        Map<String, String> limitations = new LinkedHashMap<>();
        limitations.put("dataset_origin", "Evaluated on 5 deterministic synthetic distributed architecture scenarios with known ground truth.");
        limitations.put("ground_truth_isolation", "Expected root-cause service metadata is held out and never passed into anomaly detection or ranking engine.");
        limitations.put("accuracy_distinction", "Root-cause ranking accuracy (Top-1/Top-3 on synthetic benchmarks) is distinct from real-world telemetry anomaly detection precision and recall.");
        limitations.put("stochastic_noise", "Real-world microservices may experience unobserved network jitter, clock skew, or log dropping not present in synthetic fixtures.");
        limitations.put("recommendation", "Periodically benchmark against historical post-mortems and replay anonymized production traces.");

        return new EvaluationResult(
                "TraceWise-Evaluation-Suite-v1.2",
                LocalDateTime.now(),
                testSuite.size(),
                Math.round(precision * 1000.0) / 1000.0,
                Math.round(recall * 1000.0) / 1000.0,
                Math.round(f1 * 1000.0) / 1000.0,
                tp,
                fp,
                fn,
                tn,
                Math.round(top1Acc * 1000.0) / 1000.0,
                Math.round(top3Acc * 1000.0) / 1000.0,
                details,
                limitations
        );
    }
}
