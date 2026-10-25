package com.tracwise.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tracwise.api.dto.IncidentRequest;
import com.tracwise.api.dto.IncidentRequest.ServiceMetric;
import com.tracwise.api.engine.RootCauseEngine;
import com.tracwise.api.engine.RootCauseEngine.EngineAnalysisResult;
import com.tracwise.api.service.TelemetryParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TelemetryParserTest {

    private TelemetryParser telemetryParser;
    private RootCauseEngine rootCauseEngine;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        telemetryParser = new TelemetryParser(objectMapper);
        rootCauseEngine = new RootCauseEngine();
    }

    @Test
    void testParseSnakeCaseSyntheticIncidentTelemetryCsv() throws Exception {
        File csvFile = new File("../sample-data/tracewise-synthetic-incident-telemetry.csv");
        if (!csvFile.exists()) {
            csvFile = new File("sample-data/tracewise-synthetic-incident-telemetry.csv");
        }
        assertTrue(csvFile.exists(), "Sample telemetry CSV must exist");

        IncidentRequest request;
        try (InputStream in = new FileInputStream(csvFile)) {
            request = telemetryParser.parseCsv(in);
        }

        assertNotNull(request);
        List<ServiceMetric> metrics = request.services();
        assertEquals(7, metrics.size(), "All 7 rows in synthetic telemetry CSV must be parsed");

        // Verify values are NOT the fallback defaults (10% CPU, 20ms latency, 0% error, 5/100 conns)
        List<ServiceMetric> pgMetrics = metrics.stream()
                .filter(m -> m.name().equals("postgres-db"))
                .toList();
        assertEquals(4, pgMetrics.size());

        // Check postgres-db pool utilization and limit calculation (100)
        for (ServiceMetric m : pgMetrics) {
            assertEquals(100, m.connectionPoolLimit(), "postgres-db connection pool limit must resolve to 100");
        }
        assertEquals(35, pgMetrics.get(0).activeConnections());
        assertEquals(78, pgMetrics.get(1).activeConnections());
        assertEquals(100, pgMetrics.get(2).activeConnections());
        assertEquals(100, pgMetrics.get(3).activeConnections());

        // Latency progression for postgres-db: 18 -> 65 -> 240 -> 320 ms
        assertEquals(18.0, pgMetrics.get(0).latencyMs());
        assertEquals(65.0, pgMetrics.get(1).latencyMs());
        assertEquals(240.0, pgMetrics.get(2).latencyMs());
        assertEquals(320.0, pgMetrics.get(3).latencyMs());

        // Verify order-service
        ServiceMetric orderMetric = metrics.stream()
                .filter(m -> m.name().equals("order-service"))
                .findFirst()
                .orElseThrow();
        assertEquals(1800.0, orderMetric.latencyMs());
        assertEquals(0.18, orderMetric.errorRate(), 0.001, "18% error rate must normalize to 0.18 fraction");
        assertEquals(48, orderMetric.activeConnections());
        assertEquals(50, orderMetric.connectionPoolLimit(), "order-service limit must resolve to 50 from 48 conns / 96% util");

        // Verify payment-service
        ServiceMetric payMetric = metrics.stream()
                .filter(m -> m.name().equals("payment-service"))
                .findFirst()
                .orElseThrow();
        assertEquals(1450.0, payMetric.latencyMs());
        assertEquals(0.09, payMetric.errorRate(), 0.001, "9% error rate must normalize to 0.09 fraction");
        assertEquals(44, payMetric.activeConnections());
        assertEquals(50, payMetric.connectionPoolLimit(), "payment-service limit must resolve to 50 from 44 conns / 88% util");

        // Verify api-gateway
        ServiceMetric gwMetric = metrics.stream()
                .filter(m -> m.name().equals("api-gateway"))
                .findFirst()
                .orElseThrow();
        assertEquals(2100.0, gwMetric.latencyMs());
        assertEquals(0.24, gwMetric.errorRate(), 0.001, "24% error rate must normalize to 0.24 fraction");
        assertEquals(160, gwMetric.activeConnections());
        assertEquals(200, gwMetric.connectionPoolLimit(), "api-gateway limit must resolve to 200 from 160 conns / 80% util");
    }

    @Test
    void testRootCauseEngineCorrectlyIdentifiesPostgresDbForSyntheticIncident() throws Exception {
        File csvFile = new File("../sample-data/tracewise-synthetic-incident-telemetry.csv");
        if (!csvFile.exists()) {
            csvFile = new File("sample-data/tracewise-synthetic-incident-telemetry.csv");
        }
        IncidentRequest request;
        try (InputStream in = new FileInputStream(csvFile)) {
            request = telemetryParser.parseCsv(in);
        }

        EngineAnalysisResult result = rootCauseEngine.analyze(
                request.incidentId(),
                request.services(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertNotNull(result);
        assertFalse(result.hypotheses().isEmpty(), "Must produce candidate hypotheses");

        // Primary root cause must be postgres-db, NOT payment-service
        assertEquals("postgres-db", result.summary().primaryAffectedService());
        assertEquals("postgres-db", result.hypotheses().get(0).suspectedService());
        assertEquals("DATABASE_EXHAUSTION", result.hypotheses().get(0).rootCauseCategory());
        assertTrue(result.hypotheses().get(0).evidenceScore() >= 85.0, "postgres-db score must be high confidence (>= 85%)");

        // Inferred topology flag must be set
        assertTrue(result.dependencyGraph().inferredTopology(), "Topology must be flagged as inferred when no traces exist");
        assertTrue(result.dependencyGraph().edges().stream().allMatch(e -> e.isInferred()), "Synthesized edges must be marked inferred");
    }

    @Test
    void testExistingCamelCaseDatabasePoolExhaustionCsvCompatibility() throws Exception {
        File csvFile = new File("../sample-data/sample_database_pool_exhaustion.csv");
        if (!csvFile.exists()) {
            csvFile = new File("sample-data/sample_database_pool_exhaustion.csv");
        }
        assertTrue(csvFile.exists());

        IncidentRequest request;
        try (InputStream in = new FileInputStream(csvFile)) {
            request = telemetryParser.parseCsv(in);
        }

        assertNotNull(request);
        assertEquals(16, request.services().size());

        // Fractional error rates should be preserved
        ServiceMetric m = request.services().get(0);
        assertEquals(0.001, m.errorRate(), 0.0001);
        assertEquals(100, m.connectionPoolLimit());
    }
}
