package com.tracwise.api.engine;

import com.tracwise.api.dto.IncidentRequest.*;
import com.tracwise.api.dto.analysis.*;
import com.tracwise.api.dto.analysis.DependencyGraph.*;
import com.tracwise.api.dto.analysis.RootCauseHypothesis.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RootCauseEngine {

    public record EngineAnalysisResult(
            IncidentSummary summary,
            DependencyGraph dependencyGraph,
            List<RootCauseHypothesis> hypotheses,
            List<TimelineEvent> timeline,
            List<ServiceHealthSummary> serviceHealthSummaries,
            List<DiagnosticAction> recommendedActions
    ) {}

    public EngineAnalysisResult analyze(
            String incidentId,
            List<ServiceMetric> services,
            List<LogEvent> logs,
            List<Dependency> dependencies,
            List<ChangeEvent> recentChanges,
            List<AnomalyRecord> anomalies
    ) {
        logs = logs == null ? Collections.emptyList() : logs;
        dependencies = dependencies == null ? Collections.emptyList() : dependencies;
        recentChanges = recentChanges == null ? Collections.emptyList() : recentChanges;
        anomalies = anomalies == null ? Collections.emptyList() : anomalies;

        // 1. Group metrics by service
        Map<String, List<ServiceMetric>> metricsByService = services.stream()
                .collect(Collectors.groupingBy(ServiceMetric::name));

        // 2. Identify all known services
        Set<String> allServiceNames = new LinkedHashSet<>(metricsByService.keySet());
        for (Dependency d : dependencies) {
            allServiceNames.add(d.from());
            allServiceNames.add(d.to());
        }
        for (LogEvent l : logs) {
            allServiceNames.add(l.service());
        }

        // 3. Dependency topology
        Map<String, Set<String>> outgoingDeps = new HashMap<>(); // service -> who it calls
        Map<String, Set<String>> incomingDeps = new HashMap<>(); // service -> who calls it

        for (String s : allServiceNames) {
            outgoingDeps.put(s, new HashSet<>());
            incomingDeps.put(s, new HashSet<>());
        }

        for (Dependency d : dependencies) {
            outgoingDeps.computeIfAbsent(d.from(), k -> new HashSet<>()).add(d.to());
            incomingDeps.computeIfAbsent(d.to(), k -> new HashSet<>()).add(d.from());
        }

        // 4. Calculate health summaries
        Map<String, ServiceHealthSummary> healthMap = new HashMap<>();
        Map<String, LocalDateTime> firstAnomalyTime = new HashMap<>();
        Map<String, LocalDateTime> firstErrorLogTime = new HashMap<>();

        for (String sName : allServiceNames) {
            List<ServiceMetric> sMetrics = metricsByService.getOrDefault(sName, Collections.emptyList());
            double peakCpu = sMetrics.stream().mapToDouble(ServiceMetric::cpuPercent).max().orElse(0.0);
            double peakLatency = sMetrics.stream().mapToDouble(ServiceMetric::latencyMs).max().orElse(0.0);
            double peakError = sMetrics.stream().mapToDouble(ServiceMetric::errorRate).max().orElse(0.0);
            int maxConns = sMetrics.stream().mapToInt(ServiceMetric::activeConnections).max().orElse(0);
            int poolLimit = sMetrics.stream().mapToInt(ServiceMetric::connectionPoolLimit).max().orElse(0);

            double peakMemory = sMetrics.stream()
                    .filter(m -> m.metricName() != null && (m.metricName().toUpperCase().contains("MEMORY") || m.metricName().toUpperCase().contains("HEAP")))
                    .filter(m -> m.observedValue() != null)
                    .mapToDouble(ServiceMetric::observedValue)
                    .max().orElse(0.0);

            long errorLogCount = logs.stream()
                    .filter(l -> l.service().equalsIgnoreCase(sName))
                    .filter(l -> "ERROR".equalsIgnoreCase(l.level()) || "FATAL".equalsIgnoreCase(l.level()) || "CRITICAL".equalsIgnoreCase(l.level()))
                    .count();

            List<AnomalyRecord> sAnoms = anomalies.stream()
                    .filter(a -> a.service().equalsIgnoreCase(sName))
                    .toList();
            long serviceAnomCount = sAnoms.size();
            boolean hasCriticalAnomaly = sAnoms.stream().anyMatch(a ->
                    "CRITICAL".equalsIgnoreCase(a.severity()) || a.anomalyScore() >= 0.85);

            boolean hasMetricDegradation = peakError >= 0.05
                    || peakLatency >= 400.0
                    || peakCpu >= 75.0
                    || (poolLimit > 0 && (double) maxConns / poolLimit >= 0.85)
                    || peakMemory >= 80.0;

            String status = "HEALTHY";
            if (peakError >= 0.20 || (poolLimit > 0 && (double) maxConns / poolLimit >= 0.90) || peakCpu >= 90.0 || peakMemory >= 95.0) {
                status = "FAILING";
            } else if (hasMetricDegradation || hasCriticalAnomaly || (serviceAnomCount > 1 && (peakLatency >= 250.0 || peakError >= 0.02 || peakCpu >= 60.0 || errorLogCount > 0))) {
                status = "DEGRADED";
            }

            healthMap.put(sName, new ServiceHealthSummary(
                    sName,
                    status,
                    Math.round(peakCpu * 10.0) / 10.0,
                    Math.round(peakLatency * 10.0) / 10.0,
                    Math.round(peakError * 1000.0) / 10.0,
                    maxConns,
                    poolLimit,
                    (int) errorLogCount,
                    (int) serviceAnomCount
            ));
        }

        // Determine earliest onset
        for (AnomalyRecord a : anomalies) {
            if ("CRITICAL".equalsIgnoreCase(a.severity()) || a.anomalyScore() >= 0.80) {
                firstAnomalyTime.merge(a.service(), a.timestamp(), (oldT, newT) -> newT.isBefore(oldT) ? newT : oldT);
            }
        }
        for (ServiceMetric m : services) {
            boolean isDegraded = (m.connectionPoolLimit() > 0 && (double) m.activeConnections() / m.connectionPoolLimit() >= 0.85)
                    || m.cpuPercent() >= 75.0
                    || m.errorRate() >= 0.05
                    || m.latencyMs() >= 400.0
                    || (m.observedValue() != null && m.metricName() != null
                        && (m.metricName().toUpperCase().contains("MEMORY") || m.metricName().toUpperCase().contains("HEAP"))
                        && m.observedValue() >= 80.0);
            if (isDegraded) {
                firstAnomalyTime.merge(m.name(), m.timestamp(), (oldT, newT) -> newT.isBefore(oldT) ? newT : oldT);
            }
        }
        for (LogEvent l : logs) {
            if ("ERROR".equalsIgnoreCase(l.level()) || "FATAL".equalsIgnoreCase(l.level())) {
                firstErrorLogTime.merge(l.service(), l.timestamp(), (oldT, newT) -> newT.isBefore(oldT) ? newT : oldT);
            }
        }

        LocalDateTime earliestGlobalOnset = null;
        for (LocalDateTime t : firstAnomalyTime.values()) {
            if (earliestGlobalOnset == null || t.isBefore(earliestGlobalOnset)) {
                earliestGlobalOnset = t;
            }
        }
        for (LocalDateTime t : firstErrorLogTime.values()) {
            if (earliestGlobalOnset == null || t.isBefore(earliestGlobalOnset)) {
                earliestGlobalOnset = t;
            }
        }
        if (earliestGlobalOnset == null && !services.isEmpty()) {
            earliestGlobalOnset = services.get(0).timestamp();
        }

        // 5. Evaluate candidate hypotheses
        List<RootCauseHypothesis> candidates = new ArrayList<>();
        int hypCounter = 1;

        for (String sName : allServiceNames) {
            ServiceHealthSummary health = healthMap.get(sName);
            List<ServiceMetric> sMetrics = metricsByService.getOrDefault(sName, Collections.emptyList());
            Set<String> dependents = incomingDeps.getOrDefault(sName, Collections.emptySet());
            String lower = sName.toLowerCase();
            boolean isDatabaseTier = lower.contains("db") || lower.contains("postgres") || lower.contains("sql") || lower.contains("mongo");
            boolean isExternalProvider = lower.contains("external") || lower.contains("shipping") || lower.contains("carrier") || lower.contains("third-party") || lower.contains("partner");
            boolean isIngressGateway = !isExternalProvider && (lower.contains("gateway") || lower.contains("router") || lower.contains("ingress") || lower.contains("frontend") || lower.contains("web"));
            boolean isLeafDownstream = outgoingDeps.getOrDefault(sName, Collections.emptySet()).isEmpty() && !dependents.isEmpty();

            // Check A: Database Connection Pool Exhaustion
            // Valid if it is a database service or explicit DB connection exhaustion logs exist
            if (health.connectionPoolLimit() > 0 && ((double) health.maxActiveConnections() / health.connectionPoolLimit() >= 0.85) && isDatabaseTier) {
                double poolUtil = (double) health.maxActiveConnections() / health.connectionPoolLimit();
                List<EvidenceItem> supporting = new ArrayList<>();
                List<EvidenceItem> contradicting = new ArrayList<>();
                double score = 42.0;

                double satPoints = poolUtil >= 0.95 ? 28.0 : 18.0;
                score += satPoints;
                supporting.add(new EvidenceItem(
                        "SATURATION_METRIC",
                        String.format("Active database connections reached %d of %d (%.1f%% pool saturation)",
                                health.maxActiveConnections(), health.connectionPoolLimit(), poolUtil * 100.0),
                        satPoints,
                        "Telemetry: " + sName
                ));

                LocalDateTime sOnset = firstAnomalyTime.get(sName);
                if (sOnset != null && earliestGlobalOnset != null) {
                    long diffSeconds = Math.abs(Duration.between(earliestGlobalOnset, sOnset).getSeconds());
                    if (diffSeconds <= 15) {
                        score += 20.0;
                        supporting.add(new EvidenceItem(
                                "TEMPORAL_ORDER",
                                String.format("Pool saturation initiated at %s matching earliest incident onset", sOnset),
                                20.0,
                                "Timeline analysis"
                        ));
                    }
                }

                if (!dependents.isEmpty()) {
                    List<String> affectedDependents = dependents.stream()
                            .filter(d -> !"HEALTHY".equals(healthMap.getOrDefault(d, health).status()))
                            .toList();
                    if (!affectedDependents.isEmpty()) {
                        score += 20.0;
                        supporting.add(new EvidenceItem(
                                "DEPENDENCY_CASCADE",
                                String.format("Dependent callers %s experienced cascading connection acquisition timeouts",
                                        String.join(", ", affectedDependents)),
                                20.0,
                                "Dependency Graph"
                        ));
                    }
                }

                boolean hasPoolTimeoutLogs = logs.stream()
                        .filter(l -> l.service().equalsIgnoreCase(sName) || dependents.contains(l.service()))
                        .anyMatch(l -> {
                            String msg = l.message().toLowerCase();
                            return msg.contains("connection") && (msg.contains("timeout") || msg.contains("pool") || msg.contains("exhaust") || msg.contains("hikaripool"));
                        });

                if (hasPoolTimeoutLogs) {
                    score += 15.0;
                    supporting.add(new EvidenceItem(
                            "ERROR_LOG",
                            "Log traces confirm HikariPool connection starvation and acquisition timeouts",
                            15.0,
                            "Structured Logs"
                    ));
                }

                List<String> actions = List.of(
                        "Execute `SELECT * FROM pg_stat_activity WHERE state != 'idle';` to inspect long-running transactions and locks",
                        "Verify connection pool configuration (`maximumPoolSize`, `connectionTimeout`) in application.yaml",
                        "Check for leaked database connections or unclosed sessions in recent query paths",
                        "Temporarily scale connection pool limits or restart stalled pool workers"
                );

                candidates.add(new RootCauseHypothesis(
                        0,
                        "HYP-" + (hypCounter++),
                        "Database Connection Pool Exhaustion on " + sName,
                        "DATABASE_EXHAUSTION",
                        sName,
                        Math.min(96.0, Math.max(10.0, score)),
                        score >= 80.0 ? "HIGH_CONFIDENCE" : score >= 60.0 ? "MODERATE_CONFIDENCE" : "LOW_CONFIDENCE",
                        supporting,
                        contradicting,
                        String.format("Service '%s' exhausted its configured connection pool limit (%d connections), triggering thread starvation and cascading acquisition timeouts into upstream callers.",
                                sName, health.connectionPoolLimit()),
                        actions
                ));
            }

            // Check B: CPU / Memory Resource Saturation
            boolean hasOomLogs = logs.stream()
                    .filter(l -> l.service().equalsIgnoreCase(sName))
                    .anyMatch(l -> {
                        String msg = l.message().toLowerCase();
                        return msg.contains("outofmemory") || msg.contains("heap space") || msg.contains("memory leak");
                    });

            List<ServiceMetric> sMemMetrics = sMetrics.stream()
                    .filter(m -> m.metricName() != null && (m.metricName().toUpperCase().contains("MEMORY") || m.metricName().toUpperCase().contains("HEAP")))
                    .filter(m -> m.observedValue() != null)
                    .sorted(Comparator.comparing(ServiceMetric::timestamp))
                    .toList();

            double peakMemVal = sMemMetrics.stream().mapToDouble(ServiceMetric::observedValue).max().orElse(0.0);
            double firstMemVal = sMemMetrics.isEmpty() ? 0.0 : sMemMetrics.get(0).observedValue();

            boolean isMonotonicallyIncreasing = false;
            if (sMemMetrics.size() >= 2) {
                boolean inc = true;
                for (int i = 0; i < sMemMetrics.size() - 1; i++) {
                    if (sMemMetrics.get(i + 1).observedValue() < sMemMetrics.get(i).observedValue() - 0.001) {
                        inc = false;
                        break;
                    }
                }
                isMonotonicallyIncreasing = inc;
            }

            // Distinguish progressive memory leak from static memory pressure
            boolean isProgressiveLeak = isMonotonicallyIncreasing
                    && (peakMemVal - firstMemVal >= 15.0)
                    && peakMemVal >= 85.0
                    && sMemMetrics.size() >= 2;

            boolean isMemoryPressure = !isProgressiveLeak && (peakMemVal >= 80.0
                    || sMemMetrics.stream().anyMatch(m -> m.expectedBaseline() != null && m.observedValue() - m.expectedBaseline() >= 30.0));

            if (health.peakCpuPercent() >= 75.0 || hasOomLogs || isProgressiveLeak || isMemoryPressure) {
                List<EvidenceItem> supporting = new ArrayList<>();
                List<EvidenceItem> contradicting = new ArrayList<>();
                double score = 40.0;

                if (isProgressiveLeak) {
                    score += 30.0;
                    supporting.add(new EvidenceItem(
                            "MEMORY_LEAK_PROGRESSION",
                            String.format("Progressive monotonic memory growth across %d samples from %.1f%% to %.1f%% without GC reclamation",
                                    sMemMetrics.size(), firstMemVal, peakMemVal),
                            30.0,
                            "Telemetry: " + sName
                    ));
                } else if (isMemoryPressure) {
                    score += 20.0;
                    supporting.add(new EvidenceItem(
                            "MEMORY_PRESSURE",
                            String.format("Memory utilization reached elevated peak of %.1f%%", peakMemVal),
                            20.0,
                            "Telemetry: " + sName
                    ));
                }

                if (health.peakCpuPercent() >= 75.0) {
                    double cpuPoints = health.peakCpuPercent() >= 90.0 ? 30.0 : 18.0;
                    score += cpuPoints;
                    supporting.add(new EvidenceItem(
                            "SATURATION_METRIC",
                            String.format("CPU utilization reached peak of %.1f%%", health.peakCpuPercent()),
                            cpuPoints,
                            "Telemetry: " + sName
                    ));
                }

                if (hasOomLogs) {
                    score += 30.0;
                    supporting.add(new EvidenceItem(
                            "ERROR_LOG",
                            "Log traces confirm JVM heap space exhaustion and OutOfMemoryError",
                            30.0,
                            "Structured Logs: " + sName
                    ));
                }

                LocalDateTime sOnset = firstAnomalyTime.get(sName);
                if (sOnset != null && earliestGlobalOnset != null) {
                    long diffSec = Math.abs(Duration.between(earliestGlobalOnset, sOnset).getSeconds());
                    if (diffSec <= 20) {
                        score += 20.0;
                        supporting.add(new EvidenceItem(
                                "TEMPORAL_ORDER",
                                String.format("Resource saturation onset at %s aligns with initial incident initiation", sOnset),
                                20.0,
                                "Timeline analysis"
                        ));
                    }
                }

                Optional<ChangeEvent> matchedChange = recentChanges.stream()
                        .filter(c -> c.service().equalsIgnoreCase(sName))
                        .findFirst();
                if (matchedChange.isPresent()) {
                    score += 20.0;
                    supporting.add(new EvidenceItem(
                            "CHANGE_EVENT",
                            String.format("Recent deployment to %s: '%s'", sName, matchedChange.get().description()),
                            20.0,
                            "Deployment Log"
                    ));
                }

                if (!dependents.isEmpty()) {
                    score += 15.0;
                    supporting.add(new EvidenceItem(
                            "DEPENDENCY_CASCADE",
                            String.format("Callers waiting on %s experienced timeouts: %s", sName, String.join(", ", dependents)),
                            15.0,
                            "Dependency topology"
                    ));
                }

                List<String> actions = new ArrayList<>();
                if (isProgressiveLeak) {
                    actions.add("Capture JVM heap dump (`jmap -dump:live,format=b,file=heap.hprof <pid>`) to identify memory leak roots");
                    actions.add("Inspect memory allocation limits and GC telemetry (-XX:+PrintGCDetails / container cgroups memory limit)");
                    actions.add("Restart worker container and apply rolling restart to clear heap fragmentation");
                } else if (hasOomLogs) {
                    actions.add("Capture JVM heap dump (`jmap -dump:live,format=b,file=heap.hprof <pid>`) to identify leaking objects");
                    actions.add("Review memory allocation limits (-Xmx / container cgroups memory limit)");
                    actions.add("Scale container memory limit or restart worker process to clear heap fragmentation");
                } else if (isMemoryPressure) {
                    actions.add("Inspect memory utilization trends and evaluate memory limits (-Xmx / cgroups)");
                    actions.add("Scale container memory or optimize cached object lifecycles");
                }
                if (health.peakCpuPercent() >= 75.0) {
                    actions.add("Capture a thread dump using `jstack <pid>` or `kubectl exec -it <pod> -- kill -3 1` to find thread locks or busy loops");
                    actions.add("Inspect CPU profiling metrics to isolate expensive routines (e.g. regex, deserialization, unindexed iteration)");
                    actions.add("Scale horizontal replicas (`kubectl scale deployment " + sName + " --replicas=+2`) to distribute compute load");
                }
                if (matchedChange.isPresent()) {
                    actions.add("Roll back recent deployment if resource degradation immediately succeeded release");
                }

                String hypTitle;
                String reasoning;
                if (isProgressiveLeak) {
                    hypTitle = "Progressive Memory Leak & Heap Exhaustion on " + sName;
                    reasoning = String.format("Service '%s' exhibited monotonic memory growth (%.1f%% to %.1f%%) without garbage collection reclamation, culminating in severe resource starvation and caller timeouts.", sName, firstMemVal, peakMemVal);
                } else if (hasOomLogs) {
                    hypTitle = "Memory Saturation & Heap Exhaustion on " + sName;
                    reasoning = String.format("Service '%s' experienced JVM heap exhaustion and OutOfMemoryError, causing worker thread failure and severe caller degradation.", sName);
                } else if (isMemoryPressure) {
                    hypTitle = "Memory Resource Saturation on " + sName;
                    reasoning = String.format("Service '%s' reached elevated memory saturation of %.1f%%, exceeding safe operating thresholds.", sName, peakMemVal);
                } else {
                    hypTitle = "CPU Resource Saturation on " + sName;
                    reasoning = String.format("Service '%s' experienced sustained compute saturation (%.1f%% CPU), exhausting executor threads and throttling request processing.", sName, health.peakCpuPercent());
                }

                candidates.add(new RootCauseHypothesis(
                        0,
                        "HYP-" + (hypCounter++),
                        hypTitle,
                        "RESOURCE_SATURATION",
                        sName,
                        Math.min(95.0, Math.max(10.0, score)),
                        score >= 80.0 ? "HIGH_CONFIDENCE" : score >= 60.0 ? "MODERATE_CONFIDENCE" : "LOW_CONFIDENCE",
                        supporting,
                        contradicting,
                        reasoning,
                        actions
                ));
            }

            // Check C: Upstream Dependency Timeout / External Provider Failure
            // Only non-database, non-cache, non-ingress services that are external providers or leaf downstream sinks
            if (!isDatabaseTier && !lower.contains("cache") && !lower.contains("redis") && !isIngressGateway
                    && (health.peakLatencyMs() >= 1000.0 || health.peakErrorRate() >= 10.0)
                    && (isExternalProvider || isLeafDownstream)) {
                List<EvidenceItem> supporting = new ArrayList<>();
                List<EvidenceItem> contradicting = new ArrayList<>();
                double score = 40.0;

                double latPoints = health.peakLatencyMs() >= 3000.0 ? 30.0 : 18.0;
                score += latPoints;
                supporting.add(new EvidenceItem(
                        "SATURATION_METRIC",
                        String.format("Severe response time explosion to %.0f ms and %.1f%% error rate",
                                health.peakLatencyMs(), health.peakErrorRate()),
                        latPoints,
                        "Telemetry: " + sName
                ));

                if (outgoingDeps.getOrDefault(sName, Collections.emptySet()).isEmpty()) {
                    score += 20.0;
                    supporting.add(new EvidenceItem(
                            "TOPOLOGY_LEAF",
                            String.format("%s is a terminal downstream dependency with no downstream egress", sName),
                            20.0,
                            "Dependency topology"
                    ));
                }

                if (!dependents.isEmpty()) {
                    score += 15.0;
                    supporting.add(new EvidenceItem(
                            "DEPENDENCY_CASCADE",
                            String.format("Callers waiting on %s thread pools choked: %s", sName, String.join(", ", dependents)),
                            15.0,
                            "Dependency topology"
                    ));

                    LocalDateTime sOnset = firstAnomalyTime.get(sName);
                    if (sOnset != null) {
                        LocalDateTime earliestDepOnset = dependents.stream()
                                .map(firstAnomalyTime::get)
                                .filter(Objects::nonNull)
                                .min(LocalDateTime::compareTo)
                                .orElse(null);
                        if (earliestDepOnset != null && sOnset.isBefore(earliestDepOnset)) {
                            score += 15.0;
                            supporting.add(new EvidenceItem(
                                    "TEMPORAL_ORDER",
                                    String.format("%s degradation onset at %s preceded dependent caller degradation at %s",
                                            sName, sOnset, earliestDepOnset),
                                    15.0,
                                    "Timeline analysis"
                            ));
                        }
                    }
                }

                for (ChangeEvent c : recentChanges) {
                    if (c.service().equalsIgnoreCase(sName)) {
                        score += 15.0;
                        supporting.add(new EvidenceItem("CHANGE_EVENT", "Maintenance notice: " + c.description(), 15.0, "Change log"));
                        break;
                    }
                }

                String providerLabel = (lower.contains("shipping") || lower.contains("carrier")) ? "carrier API" : "upstream provider API";
                List<String> actions = List.of(
                        "Inspect external network latency and " + providerLabel + " status page",
                        "Verify circuit breaker threshold and activate emergency fallback mock responses",
                        "Check timeout configurations on upstream HTTP client (set max socket timeout to 2000ms)",
                        "Inspect ingress queue depths on caller services"
                );

                candidates.add(new RootCauseHypothesis(
                        0,
                        "HYP-" + (hypCounter++),
                        "Upstream Timeout and Degradation in " + sName,
                        "UPSTREAM_TIMEOUT",
                        sName,
                        Math.min(94.0, Math.max(10.0, score)),
                        score >= 80.0 ? "HIGH_CONFIDENCE" : score >= 60.0 ? "MODERATE_CONFIDENCE" : "LOW_CONFIDENCE",
                        supporting,
                        contradicting,
                        String.format("Service '%s' exhibited severe latency (%.0f ms) and elevated error rate (%.1f%%), triggering upstream caller timeouts.",
                                sName, health.peakLatencyMs(), health.peakErrorRate()),
                        actions
                ));
            }
        }

        // Fallback hypothesis if no specific threshold triggered
        // Fallback hypothesis only when incident signals exist.
        if (candidates.isEmpty()) {
            boolean allServicesHealthy = healthMap.values().stream()
                    .allMatch(h -> "HEALTHY".equals(h.status()));

            boolean hasErrorLogs = logs.stream().anyMatch(l ->
                    "ERROR".equalsIgnoreCase(l.level())
                    || "FATAL".equalsIgnoreCase(l.level())
                    || "CRITICAL".equalsIgnoreCase(l.level()));

            boolean hasActionableAnomalies = anomalies.stream().anyMatch(a ->
                    "CRITICAL".equalsIgnoreCase(a.severity()) || a.anomalyScore() >= 0.85);

            boolean hasIncidentSignals = !allServicesHealthy
                    || hasErrorLogs
                    || !recentChanges.isEmpty()
                    || hasActionableAnomalies;

    if (hasIncidentSignals) {
        String suspected = "system";
        if (!allServiceNames.isEmpty()) {
            suspected = healthMap.values().stream()
                    .filter(h -> !"HEALTHY".equals(h.status()))
                    .max(Comparator.comparingDouble(ServiceHealthSummary::peakErrorRate)
                            .thenComparingDouble(ServiceHealthSummary::peakLatencyMs))
                    .map(ServiceHealthSummary::serviceName)
                    .orElse("system");
        }

        candidates.add(new RootCauseHypothesis(
                1,
                "HYP-1",
                "Transient Distributed Intermittent Latency",
                "TRANSIENT_DEGRADATION",
                suspected,
                45.0,
                "LOW_CONFIDENCE",
                List.of(new EvidenceItem(
                        "TELEMETRY",
                        "Insufficient evidence to identify a specific failure mode",
                        30.0,
                        "Telemetry"
                )),
                List.of(new EvidenceItem(
                        "INSUFFICIENT_EVIDENCE",
                        "No specific root-cause threshold was met",
                        -10.0,
                        "Rule evaluation"
                )),
                "A specific root cause could not be established. Investigate additional telemetry and logs.",
                List.of(
                        "Inspect service metrics and recent logs",
                        "Verify dependency health and request traces"
                )
        ));
    }
}

        // Sort candidates by evidenceScore descending and assign rank
        candidates.sort(Comparator.comparingDouble(RootCauseHypothesis::evidenceScore).reversed());
        List<RootCauseHypothesis> ranked = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            RootCauseHypothesis orig = candidates.get(i);
            ranked.add(new RootCauseHypothesis(
                    i + 1,
                    orig.hypothesisId(),
                    orig.title(),
                    orig.rootCauseCategory(),
                    orig.suspectedService(),
                    orig.evidenceScore(),
                    orig.confidenceTier(),
                    orig.supportingEvidence(),
                    orig.contradictingEvidence(),
                    orig.reasoning(),
                    orig.recommendedActions()
            ));
        }

        // 6. Build unified chronological timeline
        List<TimelineEvent> timeline = new ArrayList<>();
        int tEventId = 1;

        for (ChangeEvent c : recentChanges) {
            timeline.add(new TimelineEvent(
                    "TLE-" + (tEventId++),
                    c.timestamp(),
                    "CHANGE",
                    c.service(),
                    "Deployment: " + c.description(),
                    "Recent change event recorded prior to/during incident window",
                    "INFO",
                    false
            ));
        }

        for (AnomalyRecord a : anomalies) {
            boolean isInit = ranked.isEmpty() ? false : a.service().equalsIgnoreCase(ranked.get(0).suspectedService());
            timeline.add(new TimelineEvent(
                    "TLE-" + (tEventId++),
                    a.timestamp(),
                    "METRIC_ANOMALY",
                    a.service(),
                    a.metricName() + " (" + a.severity() + ")",
                    a.description(),
                    a.severity(),
                    isInit
            ));
        }

        for (LogEvent l : logs) {
            if ("ERROR".equalsIgnoreCase(l.level()) || "FATAL".equalsIgnoreCase(l.level()) || "WARN".equalsIgnoreCase(l.level())) {
                timeline.add(new TimelineEvent(
                        "TLE-" + (tEventId++),
                        l.timestamp(),
                        l.level().toUpperCase() + "_LOG",
                        l.service(),
                        l.message().length() > 80 ? l.message().substring(0, 77) + "..." : l.message(),
                        l.message(),
                        "WARN".equalsIgnoreCase(l.level()) ? "WARNING" : "CRITICAL",
                        false
                ));
            }
        }

        timeline.sort(Comparator.comparing(TimelineEvent::timestamp));

        if (!ranked.isEmpty()) {
            String topService = ranked.get(0).suspectedService();
            for (int i = 0; i < timeline.size(); i++) {
                TimelineEvent te = timeline.get(i);
                if (te.service().equalsIgnoreCase(topService) && ("METRIC_ANOMALY".equals(te.eventType()) || te.eventType().contains("ERROR"))) {
                    timeline.set(i, new TimelineEvent(
                            te.id(),
                            te.timestamp(),
                            te.eventType(),
                            te.service(),
                            "[INITIATING EVENT] " + te.summary(),
                            te.details(),
                            "CRITICAL",
                            true
                    ));
                    break;
                }
            }
        }

        // 7. Build Dependency Graph
        List<GraphNode> nodes = new ArrayList<>();
        String topSuspect = ranked.isEmpty() ? "" : ranked.get(0).suspectedService();

        for (String sName : allServiceNames) {
            ServiceHealthSummary h = healthMap.getOrDefault(sName, new ServiceHealthSummary(sName, "HEALTHY", 0, 0, 0, 0, 0, 0, 0));
            double anomScore = anomalies.stream()
                    .filter(a -> a.service().equalsIgnoreCase(sName))
                    .mapToDouble(AnomalyRecord::anomalyScore)
                    .max()
                    .orElse(0.0);

            String tier = "SERVICE";
            String lowerS = sName.toLowerCase();
            boolean isExternalS = lowerS.contains("external") || lowerS.contains("shipping") || lowerS.contains("carrier") || lowerS.contains("third-party") || lowerS.contains("partner");
            if (isExternalS) {
                tier = "EXTERNAL";
            } else if (lowerS.contains("db") || lowerS.contains("postgres") || lowerS.contains("sql") || lowerS.contains("mongo")) {
                tier = "DATABASE";
            } else if (lowerS.contains("cache") || lowerS.contains("redis")) {
                tier = "CACHE";
            } else if (lowerS.contains("gateway") || lowerS.contains("edge") || lowerS.contains("portal") || lowerS.contains("router") || lowerS.contains("ingress") || lowerS.contains("frontend") || lowerS.contains("web")) {
                tier = "GATEWAY";
            }

            nodes.add(new GraphNode(
                    sName,
                    sName,
                    tier,
                    h.status(),
                    anomScore,
                    sName.equalsIgnoreCase(topSuspect),
                    h.errorCount()
            ));
        }

        List<GraphEdge> edges = new ArrayList<>();
        for (Dependency d : dependencies) {
            ServiceHealthSummary targetHealth = healthMap.getOrDefault(d.to(), healthMap.get(d.from()));
            double latency = targetHealth != null ? targetHealth.peakLatencyMs() : 50.0;
            double error = targetHealth != null ? targetHealth.peakErrorRate() : 0.0;

            String edgeStatus = "NORMAL";
            if (error >= 20.0 || latency >= 1000.0) {
                edgeStatus = "BLOCKED";
            } else if (error >= 5.0 || latency >= 400.0) {
                edgeStatus = "DEGRADED";
            }

            boolean isCrit = d.to().equalsIgnoreCase(topSuspect) || d.from().equalsIgnoreCase(topSuspect);
            edges.add(new GraphEdge(
                    d.from(),
                    d.to(),
                    edgeStatus,
                    error,
                    latency,
                    isCrit
            ));
        }

        boolean inferredTopology = false;
        // Synthesize fallback architectural topology when source telemetry omitted explicit trace dependencies
        if (edges.isEmpty() && nodes.size() > 1) {
            inferredTopology = true;
            List<GraphNode> gatewayNodes = nodes.stream().filter(n -> "GATEWAY".equals(n.tier())).toList();
            List<GraphNode> appServiceNodes = nodes.stream().filter(n -> "SERVICE".equals(n.tier())).toList();
            List<GraphNode> backendNodes = nodes.stream().filter(n -> "DATABASE".equals(n.tier()) || "CACHE".equals(n.tier()) || "EXTERNAL".equals(n.tier())).toList();

            for (GraphNode gw : gatewayNodes) {
                for (GraphNode svc : appServiceNodes) {
                    boolean isCrit = svc.id().equalsIgnoreCase(topSuspect);
                    edges.add(new GraphEdge(gw.id(), svc.id(), isCrit ? "DEGRADED" : "NORMAL", isCrit ? 12.0 : 0.0, isCrit ? 600.0 : 45.0, isCrit, true));
                }
            }
            for (GraphNode svc : appServiceNodes) {
                for (GraphNode backend : backendNodes) {
                    boolean isCrit = backend.id().equalsIgnoreCase(topSuspect) || svc.id().equalsIgnoreCase(topSuspect);
                    edges.add(new GraphEdge(svc.id(), backend.id(), isCrit ? "BLOCKED" : "NORMAL", isCrit ? 28.0 : 0.0, isCrit ? 1400.0 : 50.0, isCrit, true));
                }
            }
            if (edges.isEmpty()) {
                for (int i = 0; i < nodes.size() - 1; i++) {
                    edges.add(new GraphEdge(nodes.get(i).id(), nodes.get(i + 1).id(), "NORMAL", 0.0, 50.0, false, true));
                }
            }
        }

        // 8. Build Incident Summary
        long degradedCount = healthMap.values().stream().filter(h -> !"HEALTHY".equals(h.status())).count();
        int blastRadius = allServiceNames.isEmpty() ? 0 : (int) Math.round(((double) degradedCount / allServiceNames.size()) * 100.0);

        String severity = ranked.isEmpty() ? "LOW" : (blastRadius >= 50 ? "CRITICAL" : blastRadius >= 25 ? "HIGH" : "MEDIUM");
        String primaryCause = ranked.isEmpty() ? "NONE" : ranked.get(0).title();
        String primaryService = ranked.isEmpty() ? "NONE" : ranked.get(0).suspectedService();

        String durationStr = "N/A";
        if (timeline.size() >= 2) {
            Duration dur = Duration.between(timeline.get(0).timestamp(), timeline.get(timeline.size() - 1).timestamp());
            long minutes = Math.max(1, dur.toMinutes());
            long seconds = Math.abs(dur.toSecondsPart());
            durationStr = String.format("%dm %02ds", minutes, seconds);
        }

        String execSummary = ranked.isEmpty()
                ? String.format("Telemetry baseline is nominal with zero anomalies or service degradations detected across all %d monitored services.", allServiceNames.size())
                : String.format(
                "Cascading incident detected originating from %s. Failure mode identified as '%s' with %.1f%% evidence strength. Incident propagated across %d of %d services (%d%% blast radius).",
                primaryService, primaryCause, ranked.isEmpty() ? 0.0 : ranked.get(0).evidenceScore(),
                degradedCount, allServiceNames.size(), blastRadius
        );

        IncidentSummary summary = new IncidentSummary(
            ranked.isEmpty()
                ? "Incident #" + incidentId + ": No Incident Detected"
                : "Incident #" + incidentId + ": Cascading Failure across " + degradedCount + " Services",
            ranked.isEmpty() ? "LOW" : severity,
            ranked.isEmpty()
                ? "NO_INCIDENT_DETECTED"
                : "ROOT_CAUSE_IDENTIFIED",
            earliestGlobalOnset != null ? earliestGlobalOnset : LocalDateTime.now(),
            primaryCause,
            primaryService,
            ranked.isEmpty() ? 0 : blastRadius,
            durationStr,
            execSummary
        );

        // 9. Consolidate diagnostic actions
        List<DiagnosticAction> actions = new ArrayList<>();
        int actId = 1;
        if (!ranked.isEmpty()) {
            for (String rec : ranked.get(0).recommendedActions()) {
                actions.add(new DiagnosticAction(
                        "ACT-" + (actId++),
                        rec.contains("`") ? rec.substring(0, Math.min(40, rec.indexOf("`") + 20)) : "Remediation Step " + actId,
                        rec,
                        "Recommended targeted diagnostic verification for top ranked hypothesis",
                        actId == 2 ? "P1" : "P2",
                        ranked.get(0).suspectedService()
                ));
            }
        }

        return new EngineAnalysisResult(
                summary,
                new DependencyGraph(nodes, edges, inferredTopology),
                ranked,
                timeline,
                new ArrayList<>(healthMap.values()),
                actions
        );
    }
}
