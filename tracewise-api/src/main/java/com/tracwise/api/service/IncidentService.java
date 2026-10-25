package com.tracwise.api.service;

import com.tracwise.api.dto.IncidentRequest;
import com.tracwise.api.dto.IncidentResponse;
import com.tracwise.api.dto.analysis.AnomalyRecord;
import com.tracwise.api.dto.analysis.RootCauseHypothesis;
import com.tracwise.api.engine.AnomalyDetectionClient;
import com.tracwise.api.engine.AnomalyDetectionClient.AnomalyDetectionResult;
import com.tracwise.api.engine.RootCauseEngine;
import com.tracwise.api.engine.RootCauseEngine.EngineAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final AnomalyDetectionClient anomalyDetectionClient;
    private final RootCauseEngine rootCauseEngine;
    private final Map<String, IncidentResponse> investigationHistory = new ConcurrentHashMap<>();

    public IncidentService(
            AnomalyDetectionClient anomalyDetectionClient,
            RootCauseEngine rootCauseEngine
    ) {
        this.anomalyDetectionClient = anomalyDetectionClient;
        this.rootCauseEngine = rootCauseEngine;
    }

    public IncidentResponse receiveIncident(IncidentRequest request) {
        log.info("Starting investigation for incident: {} with {} services",
                request.incidentId(), request.services().size());

        int logCount = request.logs() == null ? 0 : request.logs().size();
        int dependencyCount = request.dependencies() == null ? 0 : request.dependencies().size();
        int changeCount = request.recentChanges() == null ? 0 : request.recentChanges().size();

        // 1. Telemetry anomaly detection (Isolation Forest ML or Statistical Fallback)
        AnomalyDetectionResult detectionResult = anomalyDetectionClient.detect(request.services());
        List<AnomalyRecord> anomalies = detectionResult.anomalies();

        // 2. Root-Cause Ranking Engine
        EngineAnalysisResult analysis = rootCauseEngine.analyze(
                request.incidentId(),
                request.services(),
                request.logs(),
                request.dependencies(),
                request.recentChanges(),
                anomalies
        );

        IncidentResponse response = new IncidentResponse(
                request.incidentId(),
                "ANALYZED",
                "Incident investigation completed. Cascading failure analyzed with transparent evidence ranking.",
                request.services().size(),
                logCount,
                dependencyCount,
                changeCount,
                analysis.summary(),
                anomalies,
                analysis.timeline(),
                analysis.dependencyGraph(),
                analysis.hypotheses(),
                analysis.serviceHealthSummaries(),
                analysis.recommendedActions(),
                detectionResult.detectionEngine()
        );

        // Store in investigation repository
        investigationHistory.put(request.incidentId(), response);
        log.info("Incident {} investigation completed. Identified top root-cause: {}",
                request.incidentId(), analysis.summary().primaryRootCause());

        return response;
    }

    public List<IncidentResponse> getRecentIncidents() {
        List<IncidentResponse> list = new ArrayList<>(investigationHistory.values());
        list.sort(Comparator.comparing((IncidentResponse r) ->
                r.summary() != null && r.summary().detectedAt() != null
                        ? r.summary().detectedAt()
                        : java.time.LocalDateTime.MIN
        ).reversed());
        return list;
    }

    public Optional<IncidentResponse> getIncidentById(String incidentId) {
        return Optional.ofNullable(investigationHistory.get(incidentId));
    }

    public int getInvestigationCount() {
        return investigationHistory.size();
    }

    public String generateMarkdownReport(String incidentId) {
        IncidentResponse resp = investigationHistory.get(incidentId);
        if (resp == null) {
            throw new NoSuchElementException("Incident not found: " + incidentId);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        StringBuilder sb = new StringBuilder();

        sb.append("# TraceWise AI Incident Investigation Report\n\n");
        sb.append("**Incident ID:** `").append(resp.incidentId()).append("`  \n");
        sb.append("**Status:** ").append(resp.status()).append("  \n");
        sb.append("**Severity:** ").append(resp.summary() != null ? resp.summary().severity() : "UNKNOWN").append("  \n");
        sb.append("**Primary Root Cause:** ").append(resp.summary() != null ? resp.summary().primaryRootCause() : "Unresolved").append("  \n");
        sb.append("**Initiating Service:** `").append(resp.summary() != null ? resp.summary().primaryAffectedService() : "N/A").append("`  \n");
        sb.append("**Blast Radius:** ").append(resp.summary() != null ? resp.summary().blastRadiusPercent() : 0).append("% of services degraded  \n");
        sb.append("**Detection Engine:** `").append(resp.engineMode()).append("`  \n\n");

        sb.append("---\n\n");
        sb.append("## Executive Summary\n\n");
        sb.append(resp.summary() != null ? resp.summary().executiveSummary() : "No summary available.").append("\n\n");

        sb.append("## Ranked Root-Cause Hypotheses\n\n");
        if (resp.hypotheses() != null && !resp.hypotheses().isEmpty()) {
            for (RootCauseHypothesis hyp : resp.hypotheses()) {
                sb.append(String.format("### Rank #%d: %s (%s)\n\n", hyp.rank(), hyp.title(), hyp.confidenceTier()));
                sb.append(String.format("- **Candidate Service:** `%s`\n", hyp.suspectedService()));
                sb.append(String.format("- **Evidence Strength Score:** `%.1f%%` (Explainable multi-factor scoring)\n", hyp.evidenceScore()));
                sb.append(String.format("- **Reasoning:** %s\n\n", hyp.reasoning()));

                sb.append("#### Supporting Evidence:\n");
                for (var sup : hyp.supportingEvidence()) {
                    sb.append(String.format("- **[%s]** (+%.0f pts) %s *(Source: %s)*\n",
                            sup.type(), sup.scoreContribution(), sup.summary(), sup.sourceReference()));
                }

                if (!hyp.contradictingEvidence().isEmpty()) {
                    sb.append("\n#### Contradicting or Missing Evidence:\n");
                    for (var con : hyp.contradictingEvidence()) {
                        sb.append(String.format("- **[%s]** (%.0f pts) %s *(Source: %s)*\n",
                                con.type(), con.scoreContribution(), con.summary(), con.sourceReference()));
                    }
                }
                sb.append("\n");
            }
        }

        sb.append("## Recommended Diagnostic Checks\n\n");
        if (resp.recommendedActions() != null && !resp.recommendedActions().isEmpty()) {
            for (var act : resp.recommendedActions()) {
                sb.append(String.format("- **[%s - %s]**: %s\n", act.priority(), act.targetedService(), act.commandOrCheck()));
            }
            sb.append("\n");
        }

        sb.append("## Anomaly Detections Log\n\n");
        sb.append("| ID | Service | Metric | Severity | Score | Observed | Baseline | Description |\n");
        sb.append("|---|---|---|---|---|---|---|---|\n");
        if (resp.anomalies() != null) {
            for (var a : resp.anomalies()) {
                sb.append(String.format("| %s | %s | %s | %s | %.2f | %.1f %s | %.1f %s | %s |\n",
                        a.id(), a.service(), a.metricName(), a.severity(), a.anomalyScore(),
                        a.observedValue(), a.unit(), a.expectedBaseline(), a.unit(), a.description()));
            }
        }

        sb.append("\n\n---\n*Generated by TraceWise AI — From Scattered Alerts to Clear Answers.*");
        return sb.toString();
    }
}
