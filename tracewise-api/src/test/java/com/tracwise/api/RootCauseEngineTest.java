package com.tracwise.api;

import com.tracwise.api.dto.IncidentRequest;
import com.tracwise.api.dto.analysis.AnomalyRecord;
import com.tracwise.api.engine.RootCauseEngine;
import com.tracwise.api.engine.StatisticalAnomalyDetector;
import com.tracwise.api.service.DemoScenarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
