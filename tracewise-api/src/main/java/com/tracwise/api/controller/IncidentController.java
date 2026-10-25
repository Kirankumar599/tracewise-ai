package com.tracwise.api.controller;

import com.tracwise.api.dto.IncidentRequest;
import com.tracwise.api.dto.IncidentResponse;
import com.tracwise.api.dto.analysis.EvaluationResult;
import com.tracwise.api.dto.analysis.SystemHealthResponse;
import com.tracwise.api.engine.AnomalyDetectionClient;
import com.tracwise.api.service.DemoScenarioService;
import com.tracwise.api.service.DemoScenarioService.ScenarioCatalogItem;
import com.tracwise.api.service.EvaluationService;
import com.tracwise.api.service.IncidentService;
import com.tracwise.api.service.TelemetryParser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class IncidentController {

    private final IncidentService incidentService;
    private final DemoScenarioService demoScenarioService;
    private final EvaluationService evaluationService;
    private final TelemetryParser telemetryParser;
    private final AnomalyDetectionClient anomalyDetectionClient;

    public IncidentController(
            IncidentService incidentService,
            DemoScenarioService demoScenarioService,
            EvaluationService evaluationService,
            TelemetryParser telemetryParser,
            AnomalyDetectionClient anomalyDetectionClient
    ) {
        this.incidentService = incidentService;
        this.demoScenarioService = demoScenarioService;
        this.evaluationService = evaluationService;
        this.telemetryParser = telemetryParser;
        this.anomalyDetectionClient = anomalyDetectionClient;
    }

    /**
     * Existing core endpoint preserved and evolved.
     */
    @PostMapping("/incidents/analyze")
    public ResponseEntity<IncidentResponse> analyze(
            @Valid @RequestBody IncidentRequest request
    ) {
        IncidentResponse response = incidentService.receiveIncident(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<SystemHealthResponse> health() {
        boolean mlOnline = anomalyDetectionClient.isMlServiceAvailable();
        return ResponseEntity.ok(new SystemHealthResponse(
                "UP",
                "1.0.0",
                LocalDateTime.now(),
                mlOnline,
                anomalyDetectionClient.getMlServiceUrl(),
                mlOnline ? "IsolationForest-v1.0" : "Statistical-ZScore-v1.0",
                "TraceWise-MultiEvidence-Ranking-v1.2",
                incidentService.getInvestigationCount()
        ));
    }

    /**
     * Catalog of demo scenarios.
     */
    @GetMapping("/incidents/demo/catalog")
    public ResponseEntity<List<ScenarioCatalogItem>> getScenarioCatalog() {
        return ResponseEntity.ok(demoScenarioService.listScenarios());
    }

    /**
     * Fetch deterministic demo incident payload.
     */
    @GetMapping("/incidents/demo/{scenarioId}")
    public ResponseEntity<IncidentRequest> getDemoIncident(
            @PathVariable String scenarioId
    ) {
        return ResponseEntity.ok(demoScenarioService.getScenario(scenarioId));
    }

    /**
     * Execute demo incident directly.
     */
    @PostMapping("/incidents/demo/{scenarioId}/run")
    public ResponseEntity<IncidentResponse> runDemoIncident(
            @PathVariable String scenarioId
    ) {
        IncidentRequest req = demoScenarioService.getScenario(scenarioId);
        IncidentResponse response = incidentService.receiveIncident(req);
        return ResponseEntity.ok(response);
    }

    /**
     * Upload CSV or JSON telemetry file for investigation.
     */
    @PostMapping(value = "/incidents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IncidentResponse> uploadTelemetry(
            @RequestParam("file") MultipartFile file
    ) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        IncidentRequest parsedRequest;

        if (filename.endsWith(".csv")) {
            parsedRequest = telemetryParser.parseCsv(file.getInputStream());
        } else if (filename.endsWith(".json")) {
            String content = new String(file.getBytes());
            parsedRequest = telemetryParser.parseJson(content);
        } else {
            throw new IllegalArgumentException("Unsupported file type: only .csv and .json files are accepted.");
        }

        IncidentResponse response = incidentService.receiveIncident(parsedRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * List recent investigations.
     */
    @GetMapping("/incidents")
    public ResponseEntity<List<IncidentResponse>> listIncidents() {
        return ResponseEntity.ok(incidentService.getRecentIncidents());
    }

    /**
     * Get single incident by ID.
     */
    @GetMapping("/incidents/{incidentId}")
    public ResponseEntity<IncidentResponse> getIncident(
            @PathVariable String incidentId
    ) {
        return incidentService.getIncidentById(incidentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Export Markdown investigation post-mortem report.
     */
    @GetMapping(value = "/incidents/{incidentId}/report", produces = "text/markdown")
    public ResponseEntity<String> getIncidentReport(
            @PathVariable String incidentId
    ) {
        String report = incidentService.generateMarkdownReport(incidentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", "incident-" + incidentId + "-postmortem.md");
        return new ResponseEntity<>(report, headers, HttpStatus.OK);
    }

    /**
     * Run evaluation against held-out benchmark incidents.
     */
    @GetMapping("/evaluation")
    public ResponseEntity<EvaluationResult> runEvaluation() {
        return ResponseEntity.ok(evaluationService.runEvaluation());
    }
}
