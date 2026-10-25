package com.tracwise.api;

import com.tracwise.api.dto.IncidentRequest;
import com.tracwise.api.dto.analysis.AnomalyRecord;
import com.tracwise.api.engine.RootCauseEngine;
import com.tracwise.api.engine.StatisticalAnomalyDetector;
import com.tracwise.api.service.DemoScenarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RootCauseEngineTest {

    private RootCauseEngine engine;
    private StatisticalAnomalyDetector detector;
    private DemoScenarioService demoService;

    @BeforeEach
    void setUp() {
        engine = new RootCauseEngine();
        detector = new StatisticalAnomalyDetector();
        demoService = new DemoScenarioService();
    }

    @Test
    void testDbPoolExhaustionIdentifiesPostgresDbAsTopRootCause() {
        IncidentRequest req = demoService.getScenario("db-pool-exhaustion");
        List<AnomalyRecord> anomalies = detector.detectAnomalies(req.services(), "StatisticalTest");

        var result = engine.analyze(
                req.incidentId(),
                req.services(),
                req.logs(),
                req.dependencies(),
                req.recentChanges(),
                anomalies
        );

        assertNotNull(result);
        assertFalse(result.hypotheses().isEmpty(), "Should produce ranked hypotheses");

        var topHyp = result.hypotheses().get(0);
        assertEquals("postgres-db", topHyp.suspectedService(), "Top root cause must be postgres-db");
        assertEquals("DATABASE_EXHAUSTION", topHyp.rootCauseCategory());
        assertTrue(topHyp.evidenceScore() >= 80.0, "Evidence score should reflect high confidence");
        assertFalse(topHyp.supportingEvidence().isEmpty());
    }

    @Test
    void testCpuSaturationIdentifiesAuthServiceAsTopRootCause() {
        IncidentRequest req = demoService.getScenario("cpu-saturation");
        List<AnomalyRecord> anomalies = detector.detectAnomalies(req.services(), "StatisticalTest");

        var result = engine.analyze(
                req.incidentId(),
                req.services(),
                req.logs(),
                req.dependencies(),
                req.recentChanges(),
                anomalies
        );

        assertNotNull(result);
        assertFalse(result.hypotheses().isEmpty());

        var topHyp = result.hypotheses().get(0);
        assertEquals("auth-service", topHyp.suspectedService(), "Top root cause must be auth-service");
        assertEquals("RESOURCE_SATURATION", topHyp.rootCauseCategory());
        assertTrue(topHyp.evidenceScore() >= 75.0);
    }

    @Test
    void testLatencySpikeIdentifiesShippingApiAsTopRootCause() {
        IncidentRequest req = demoService.getScenario("latency-error-spike");
        List<AnomalyRecord> anomalies = detector.detectAnomalies(req.services(), "StatisticalTest");

        var result = engine.analyze(
                req.incidentId(),
                req.services(),
                req.logs(),
                req.dependencies(),
                req.recentChanges(),
                anomalies
        );

        assertNotNull(result);
        assertFalse(result.hypotheses().isEmpty());

        var topHyp = result.hypotheses().get(0);
        assertEquals("third-party-shipping-api", topHyp.suspectedService());
        assertEquals("UPSTREAM_TIMEOUT", topHyp.rootCauseCategory());
        assertTrue(topHyp.evidenceScore() >= 70.0);
    }

    @Test
    void testHealthyBaselineProducesZeroAnomaliesAndNoneRootCause() {
        IncidentRequest req = demoService.getScenario("bench-healthy-01");
        List<AnomalyRecord> anomalies = detector.detectAnomalies(req.services(), "StatisticalTest");

        assertEquals(0, anomalies.size(), "Healthy baseline must produce zero anomalies");

        var result = engine.analyze(
                req.incidentId(),
                req.services(),
                req.logs(),
                req.dependencies(),
                req.recentChanges(),
                anomalies
        );

        assertNotNull(result);
        assertTrue(result.hypotheses().isEmpty(), "Healthy baseline must not produce any incident hypotheses");
        assertEquals("NONE", result.summary().primaryRootCause());
        assertEquals("NONE", result.summary().primaryAffectedService());
        assertEquals("NO_INCIDENT_DETECTED", result.summary().status());
        assertEquals(0, result.summary().blastRadiusPercent());
        assertTrue(result.recommendedActions().isEmpty(), "Healthy baseline should have no remediation actions");
    }

    @Test
    void testMemoryLeakIdentifiesSearchIndexerAndResourceSaturation() {
        IncidentRequest req = demoService.getScenario("bench-mem-02");
        List<AnomalyRecord> anomalies = detector.detectAnomalies(req.services(), "StatisticalTest");

        var result = engine.analyze(
                req.incidentId(),
                req.services(),
                req.logs(),
                req.dependencies(),
                req.recentChanges(),
                anomalies
        );

        assertNotNull(result);
        assertFalse(result.hypotheses().isEmpty(), "Memory leak should generate hypotheses");

        var topHyp = result.hypotheses().get(0);
        assertEquals("search-indexer", topHyp.suspectedService(), "search-indexer should be top suspect");
        assertEquals("RESOURCE_SATURATION", topHyp.rootCauseCategory());
        assertTrue(topHyp.title().toLowerCase().contains("memory"), "Title should indicate memory exhaustion or leak");
        assertTrue(result.recommendedActions().stream().anyMatch(a -> a.commandOrCheck().toLowerCase().contains("heap") || a.commandOrCheck().toLowerCase().contains("memory")),
                "Should recommend heap dump / memory profiling");
    }

    @Test
    void testDbPoolRegressionNoCarrierApiRemediationForDatabase() {
        IncidentRequest req = demoService.getScenario("db-pool-exhaustion");
        List<AnomalyRecord> anomalies = detector.detectAnomalies(req.services(), "StatisticalTest");

        var result = engine.analyze(
                req.incidentId(),
                req.services(),
                req.logs(),
                req.dependencies(),
                req.recentChanges(),
                anomalies
        );

        // Ensure NO hypothesis falsely classifies postgres-db as an external carrier API
        boolean hasFalseCarrierHypothesis = result.hypotheses().stream()
                .anyMatch(h -> h.title().toLowerCase().contains("carrier") || h.rootCauseCategory().equals("UPSTREAM_TIMEOUT"));
        assertFalse(hasFalseCarrierHypothesis, "Database pool exhaustion must NOT produce upstream carrier API timeout hypotheses");

        // Ensure actions do not recommend checking carrier API status for database
        boolean hasCarrierAction = result.recommendedActions().stream()
                .anyMatch(a -> a.commandOrCheck().toLowerCase().contains("carrier"));
        assertFalse(hasCarrierAction, "Database pool exhaustion must NOT recommend carrier API remediation actions");
    }

    @Test
    void testExternalPaymentGatewayRecognizedAsExternalProviderNotIngressGateway() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 25, 12, 0, 0);
        List<IncidentRequest.ServiceMetric> metrics = List.of(
                new IncidentRequest.ServiceMetric("external-payment-gateway", base, 35.0, 3100.0, 0.15, 14, 50),
                new IncidentRequest.ServiceMetric("payment-service", base, 42.0, 620.0, 0.03, 18, 50),
                new IncidentRequest.ServiceMetric("api-gateway", base.plusMinutes(2), 31.0, 1600.0, 0.11, 25, 100)
        );

        var result = engine.analyze(
                "TEST-EXT-GATEWAY",
                metrics,
                java.util.Collections.emptyList(),
                java.util.Collections.emptyList(),
                java.util.Collections.emptyList(),
                java.util.Collections.emptyList()
        );

        assertNotNull(result);
        assertFalse(result.hypotheses().isEmpty(), "Must produce candidate hypothesis for external-payment-gateway");

        var topHyp = result.hypotheses().get(0);
        assertEquals("external-payment-gateway", topHyp.suspectedService(), "external-payment-gateway must be leading hypothesis");
        assertEquals("UPSTREAM_TIMEOUT", topHyp.rootCauseCategory(), "Category must be UPSTREAM_TIMEOUT, not TRANSIENT_DEGRADATION");
        assertTrue(topHyp.evidenceScore() >= 80.0, "Evidence score must be high confidence");

        // Verify tier classification in dependency graph
        var extNode = result.dependencyGraph().nodes().stream()
                .filter(n -> n.id().equals("external-payment-gateway"))
                .findFirst()
                .orElseThrow();
        assertEquals("EXTERNAL", extNode.tier(), "external-payment-gateway must be classified as EXTERNAL tier, not GATEWAY");

        var gwNode = result.dependencyGraph().nodes().stream()
                .filter(n -> n.id().equals("api-gateway"))
                .findFirst()
                .orElseThrow();
        assertEquals("GATEWAY", gwNode.tier(), "api-gateway must be classified as GATEWAY tier");

        // Inferred topology must place external-payment-gateway downstream (i.e. target of edges, not source)
        boolean hasOutgoingFromExternal = result.dependencyGraph().edges().stream()
                .anyMatch(e -> e.from().equals("external-payment-gateway"));
        assertFalse(hasOutgoingFromExternal, "External provider must not have outgoing edges to app services in inferred topology");

        boolean hasIncomingToExternal = result.dependencyGraph().edges().stream()
                .anyMatch(e -> e.to().equals("external-payment-gateway"));
        assertTrue(hasIncomingToExternal, "Application services must call external provider in inferred topology");
    }

    @Test
    void testTemporalPrecedenceOnlyAwardedWhenDegradationStrictlyPrecededDependents() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 25, 12, 0, 0);

        // Case A: external service degraded BEFORE dependent caller
        List<IncidentRequest.ServiceMetric> metricsPreceding = List.of(
                new IncidentRequest.ServiceMetric("external-payment-gateway", base, 20.0, 3200.0, 0.15, 14, 50),
                new IncidentRequest.ServiceMetric("payment-service", base.plusMinutes(2), 25.0, 1200.0, 0.08, 18, 50)
        );
        List<IncidentRequest.Dependency> deps = List.of(
                new IncidentRequest.Dependency("payment-service", "external-payment-gateway")
        );

        var resultPreceding = engine.analyze(
                "TEST-TEMPORAL-PRECEDING",
                metricsPreceding,
                java.util.Collections.emptyList(),
                deps,
                java.util.Collections.emptyList(),
                java.util.Collections.emptyList()
        );

        var hypPreceding = resultPreceding.hypotheses().get(0);
        boolean hasTemporalEvidence = hypPreceding.supportingEvidence().stream()
                .anyMatch(e -> "TEMPORAL_ORDER".equals(e.type()));
        assertTrue(hasTemporalEvidence, "Temporal precedence evidence must be awarded when external degradation strictly preceded caller");

        // Case B: simultaneous degradation (no precedence over dependent)
        List<IncidentRequest.ServiceMetric> metricsSimultaneous = List.of(
                new IncidentRequest.ServiceMetric("external-payment-gateway", base, 20.0, 3200.0, 0.15, 14, 50),
                new IncidentRequest.ServiceMetric("payment-service", base, 25.0, 1200.0, 0.08, 18, 50)
        );

        var resultSimultaneous = engine.analyze(
                "TEST-TEMPORAL-SIMULTANEOUS",
                metricsSimultaneous,
                java.util.Collections.emptyList(),
                deps,
                java.util.Collections.emptyList(),
                java.util.Collections.emptyList()
        );

        var hypSimultaneous = resultSimultaneous.hypotheses().get(0);
        boolean hasSimultaneousTemporalEvidence = hypSimultaneous.supportingEvidence().stream()
                .anyMatch(e -> "TEMPORAL_ORDER".equals(e.type()));
        assertFalse(hasSimultaneousTemporalEvidence, "Temporal precedence must NOT be awarded when external provider did not degrade before dependent");
    }
}

