package com.tracwise.api.engine;

import com.tracwise.api.dto.IncidentRequest.ServiceMetric;
import com.tracwise.api.dto.analysis.AnomalyRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class StatisticalAnomalyDetector {

    public List<AnomalyRecord> detectAnomalies(List<ServiceMetric> metrics, String detectionMethod) {
        if (metrics == null || metrics.isEmpty()) {
            return Collections.emptyList();
        }

        List<AnomalyRecord> anomalies = new ArrayList<>();
        Map<String, List<ServiceMetric>> byService = metrics.stream()
                .collect(Collectors.groupingBy(ServiceMetric::name));

        int anomalyCounter = 1;

        for (Map.Entry<String, List<ServiceMetric>> entry : byService.entrySet()) {
            String service = entry.getKey();
            List<ServiceMetric> serviceMetrics = entry.getValue().stream()
                    .sorted(Comparator.comparing(ServiceMetric::timestamp))
                    .toList();

            // Establish baseline from first 30% of records or minimum values
            int baselineSize = Math.max(1, serviceMetrics.size() / 3);
            double avgCpu = serviceMetrics.stream().limit(baselineSize).mapToDouble(ServiceMetric::cpuPercent).average().orElse(20.0);
            double avgLatency = serviceMetrics.stream().limit(baselineSize).mapToDouble(ServiceMetric::latencyMs).average().orElse(50.0);
            double avgError = serviceMetrics.stream().limit(baselineSize).mapToDouble(ServiceMetric::errorRate).average().orElse(0.01);

            for (ServiceMetric m : serviceMetrics) {
                // 1. Connection pool exhaustion check
                if (m.connectionPoolLimit() > 0) {
                    double poolUtilization = (double) m.activeConnections() / m.connectionPoolLimit();
                    if (poolUtilization >= 0.85) {
                        double score = Math.min(1.0, 0.70 + (poolUtilization - 0.85) * 2.0);
                        String severity = poolUtilization >= 0.95 ? "CRITICAL" : "WARNING";
                        Map<String, Double> contrib = new LinkedHashMap<>();
                        contrib.put("pool_utilization", poolUtilization);
                        contrib.put("active_connections", (double) m.activeConnections());
                        contrib.put("pool_limit", (double) m.connectionPoolLimit());

                        anomalies.add(new AnomalyRecord(
                                "ANOM-" + (anomalyCounter++),
                                service,
                                m.timestamp(),
                                "CONNECTION_POOL_EXHAUSTION",
                                Math.round(score * 100.0) / 100.0,
                                severity,
                                m.activeConnections(),
                                m.connectionPoolLimit() * 0.40,
                                "connections",
                                String.format("Connection pool saturation at %.1f%% (%d/%d active connections)",
                                        poolUtilization * 100.0, m.activeConnections(), m.connectionPoolLimit()),
                                contrib,
                                detectionMethod
                        ));
                    }
                }

                // 2. CPU Saturation check
                if (m.cpuPercent() >= 80.0 || (m.cpuPercent() - avgCpu >= 45.0 && m.cpuPercent() >= 75.0)) {
                    double score = Math.min(1.0, 0.65 + ((m.cpuPercent() - 75.0) / 25.0) * 0.35);
                    String severity = m.cpuPercent() >= 90.0 ? "CRITICAL" : "WARNING";
                    Map<String, Double> contrib = new LinkedHashMap<>();
                    contrib.put("cpu_percent", m.cpuPercent());
                    contrib.put("baseline_cpu", avgCpu);

                    anomalies.add(new AnomalyRecord(
                            "ANOM-" + (anomalyCounter++),
                            service,
                            m.timestamp(),
                            "CPU_SATURATION",
                            Math.round(score * 100.0) / 100.0,
                            severity,
                            m.cpuPercent(),
                            avgCpu,
                            "%",
                            String.format("Sustained elevated CPU utilization at %.1f%% (baseline: %.1f%%)",
                                    m.cpuPercent(), avgCpu),
                            contrib,
                            detectionMethod
                    ));
                }

                // 3. Latency spike check
                if (m.latencyMs() >= Math.max(300.0, avgLatency * 3.0)) {
                    double score = Math.min(1.0, 0.60 + Math.min(0.40, (m.latencyMs() - avgLatency) / 2000.0));
                    String severity = m.latencyMs() >= 2000.0 ? "CRITICAL" : "WARNING";
                    Map<String, Double> contrib = new LinkedHashMap<>();
                    contrib.put("latency_ms", m.latencyMs());
                    contrib.put("baseline_latency", avgLatency);

                    anomalies.add(new AnomalyRecord(
                            "ANOM-" + (anomalyCounter++),
                            service,
                            m.timestamp(),
                            "LATENCY_SPIKE",
                            Math.round(score * 100.0) / 100.0,
                            severity,
                            m.latencyMs(),
                            avgLatency,
                            "ms",
                            String.format("Significant response time degradation to %.0f ms (baseline: %.0f ms)",
                                    m.latencyMs(), avgLatency),
                            contrib,
                            detectionMethod
                    ));
                }

                // 4. Error rate surge check
                if (m.errorRate() >= 0.05 || (m.errorRate() - avgError >= 0.04)) {
                    double score = Math.min(1.0, 0.65 + Math.min(0.35, m.errorRate() * 1.5));
                    String severity = m.errorRate() >= 0.20 ? "CRITICAL" : "WARNING";
                    Map<String, Double> contrib = new LinkedHashMap<>();
                    contrib.put("error_rate", m.errorRate());
                    contrib.put("baseline_error", avgError);

                    anomalies.add(new AnomalyRecord(
                            "ANOM-" + (anomalyCounter++),
                            service,
                            m.timestamp(),
                            "ERROR_RATE_SURGE",
                            Math.round(score * 100.0) / 100.0,
                            severity,
                            m.errorRate() * 100.0,
                            avgError * 100.0,
                            "%",
                            String.format("Error rate spike to %.1f%% (baseline: %.1f%%)",
                                    m.errorRate() * 100.0, avgError * 100.0),
                            contrib,
                            detectionMethod
                    ));
                }
            }
        }

        return anomalies;
    }
}
