package com.tracwise.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tracwise.api.controller.GlobalExceptionHandler;
import com.tracwise.api.controller.IncidentController;
import com.tracwise.api.dto.IncidentRequest;
import com.tracwise.api.dto.IncidentRequest.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class IncidentControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private IncidentController incidentController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(incidentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void testAnalyzeEndpointWithValidPayload() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        IncidentRequest request = new IncidentRequest(
                "TEST-INC-001",
                List.of(
                        new ServiceMetric("db-orders", now, 45.0, 150.0, 0.05, 95, 100),
                        new ServiceMetric("orders-api", now, 30.0, 500.0, 0.10, 20, 50)
                ),
                List.of(
                        new LogEvent(now, "db-orders", "ERROR", "Connection pool exhausted (95/100 connections)")
                ),
                List.of(
                        new Dependency("orders-api", "db-orders")
                ),
                List.of(
                        new ChangeEvent(now.minusMinutes(5), "db-orders", "Index recreation on orders table")
                )
        );

        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId", is("TEST-INC-001")))
                .andExpect(jsonPath("$.status", is("ANALYZED")))
                .andExpect(jsonPath("$.summary.primaryRootCause", notNullValue()))
                .andExpect(jsonPath("$.hypotheses", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.hypotheses[0].suspectedService", is("db-orders")))
                .andExpect(jsonPath("$.dependencyGraph.nodes", hasSize(2)))
                .andExpect(jsonPath("$.timeline", hasSize(greaterThan(0))));
    }

    @Test
    void testAnalyzeValidationFailureOnEmptyServices() throws Exception {
        String invalidJson = """
                {
                    "incidentId": "INVALID-001",
                    "services": []
                }
                """;

        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));
    }

    @Test
    void testGetDemoScenariosAndRun() throws Exception {
        // Fetch demo scenario
        mockMvc.perform(get("/api/v1/incidents/demo/db-pool-exhaustion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId", is("INC-2026-DB-POOL")))
                .andExpect(jsonPath("$.services", hasSize(greaterThan(0))));

        // Run demo scenario directly
        mockMvc.perform(post("/api/v1/incidents/demo/db-pool-exhaustion/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ANALYZED")))
                .andExpect(jsonPath("$.hypotheses[0].suspectedService", is("postgres-db")));
    }

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.apiVersion", is("1.0.0")));
    }

    @Test
    void testEvaluationEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/evaluation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTestScenarios", is(5)))
                .andExpect(jsonPath("$.top1RootCauseAccuracy", is(1.0)))
                .andExpect(jsonPath("$.detectionPrecision", greaterThan(0.0)));
    }

    @Test
    void testGetDemoCatalogReturnsAllFiveScenarios() throws Exception {
        mockMvc.perform(get("/api/v1/incidents/demo/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[?(@.id == 'INC-2026-DB-POOL')]", notNullValue()))
                .andExpect(jsonPath("$[?(@.id == 'INC-2026-CPU-AUTH')]", notNullValue()))
                .andExpect(jsonPath("$[?(@.id == 'INC-2026-EXT-SHIPPING')]", notNullValue()))
                .andExpect(jsonPath("$[?(@.id == 'BENCH-HEALTHY-01')]", notNullValue()))
                .andExpect(jsonPath("$[?(@.id == 'BENCH-MEM-02')]", notNullValue()));
    }

    @Test
    void testRunHealthyBaselineScenarioReturnsNominal() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/demo/bench-healthy-01/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ANALYZED")))
                .andExpect(jsonPath("$.summary.primaryRootCause", is("NONE")))
                .andExpect(jsonPath("$.summary.status", is("NO_INCIDENT_DETECTED")))
                .andExpect(jsonPath("$.hypotheses", hasSize(0)));
    }

    @Test
    void testGetUnknownDemoScenarioReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/incidents/demo/nonexistent-scenario-xyz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    void testRunUnknownDemoScenarioReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/demo/nonexistent-scenario-xyz/run"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    void testUploadSyntheticIncidentTelemetryCsv() throws Exception {
        java.io.File file = new java.io.File("../sample-data/tracewise-synthetic-incident-telemetry.csv");
        if (!file.exists()) {
            file = new java.io.File("sample-data/tracewise-synthetic-incident-telemetry.csv");
        }
        byte[] csvBytes = java.nio.file.Files.readAllBytes(file.toPath());
        org.springframework.mock.web.MockMultipartFile multipartFile = new org.springframework.mock.web.MockMultipartFile(
                "file",
                "tracewise-synthetic-incident-telemetry.csv",
                "text/csv",
                csvBytes
        );

        mockMvc.perform(multipart("/api/v1/incidents/upload").file(multipartFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ANALYZED")))
                .andExpect(jsonPath("$.summary.primaryAffectedService", is("postgres-db")))
                .andExpect(jsonPath("$.hypotheses[0].suspectedService", is("postgres-db")))
                .andExpect(jsonPath("$.hypotheses[0].rootCauseCategory", is("DATABASE_EXHAUSTION")))
                .andExpect(jsonPath("$.dependencyGraph.inferredTopology", is(true)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'postgres-db')].peakCpuPercent", contains(76.0)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'postgres-db')].peakLatencyMs", contains(320.0)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'postgres-db')].peakErrorRate", contains(15.0)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'postgres-db')].maxActiveConnections", contains(100)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'postgres-db')].connectionPoolLimit", contains(100)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'order-service')].peakLatencyMs", contains(1800.0)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'order-service')].peakErrorRate", contains(18.0)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'order-service')].maxActiveConnections", contains(48)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'order-service')].connectionPoolLimit", contains(50)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'payment-service')].peakLatencyMs", contains(1450.0)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'payment-service')].peakErrorRate", contains(9.0)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'payment-service')].maxActiveConnections", contains(44)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'payment-service')].connectionPoolLimit", contains(50)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'api-gateway')].peakLatencyMs", contains(2100.0)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'api-gateway')].peakErrorRate", contains(24.0)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'api-gateway')].maxActiveConnections", contains(160)))
                .andExpect(jsonPath("$.affectedServices[?(@.serviceName == 'api-gateway')].connectionPoolLimit", contains(200)));
    }
}
