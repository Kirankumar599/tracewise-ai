package com.tracwise.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracwise.api.dto.IncidentRequest;
import com.tracwise.api.dto.IncidentRequest.*;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Component
public class TelemetryParser {

    private final ObjectMapper objectMapper;

    public TelemetryParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public IncidentRequest parseJson(String jsonContent) throws Exception {
        JsonNode root = objectMapper.readTree(jsonContent);

        // Check if it's already an IncidentRequest schema
        if (root.has("incidentId") && root.has("services")) {
            return objectMapper.treeToValue(root, IncidentRequest.class);
        }

        // Check if it's an array of service metrics
        if (root.isArray()) {
            List<ServiceMetric> metrics = new ArrayList<>();
            for (JsonNode item : root) {
                metrics.add(parseMetricNode(item));
            }
            return new IncidentRequest(
                    "UPLOAD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    metrics,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        throw new IllegalArgumentException("JSON payload must contain an incident object or an array of service metrics.");
    }

    public IncidentRequest parseCsv(InputStream inputStream) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty.");
            }

            String[] rawHeaders = headerLine.split(",");
            Map<String, Integer> colIndex = new HashMap<>();
            for (int i = 0; i < rawHeaders.length; i++) {
                colIndex.put(normalizeHeader(rawHeaders[i]), i);
            }

            int serviceIdx = findColumnIndex(colIndex, "service", "servicename", "name", "app", "application");
            int timeIdx = findColumnIndex(colIndex, "timestamp", "time", "date", "datetime");

            if (serviceIdx < 0) {
                throw new IllegalArgumentException("CSV header must contain a 'service' or 'name' column.");
            }
            if (timeIdx < 0) {
                throw new IllegalArgumentException("CSV header must contain a 'timestamp' column.");
            }

            int cpuIdx = findColumnIndex(colIndex, "cpupercent", "cpu_percent", "cpu", "cpuutilization", "cpu_utilization");
            int latencyIdx = findColumnIndex(colIndex, "latencyms", "latency_ms", "latency", "responsetime", "response_time_ms");
            int errorIdx = findColumnIndex(colIndex, "errorrate", "error_rate", "errors", "errorpercent", "error_percent");
            int connIdx = findColumnIndex(colIndex, "activeconnections", "active_connections", "connections", "conns");
            int limitIdx = findColumnIndex(colIndex, "connectionpoollimit", "connection_pool_limit", "poollimit", "pool_limit", "maxconnections", "max_connections");
            int poolUtilIdx = findColumnIndex(colIndex, "poolutilization", "pool_utilization", "poolutil", "pool_util");
            int unitIdx = findColumnIndex(colIndex, "unit", "units");

            List<String[]> rawRows = new ArrayList<>();
            List<Integer> lineNumbers = new ArrayList<>();
            String line;
            int lineNum = 1;

            boolean hasErrorGreaterThanOne = false;
            boolean hasPercentUnit = false;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split(",");
                if (parts.length < 2) continue;

                rawRows.add(parts);
                lineNumbers.add(lineNum);

                if (errorIdx >= 0 && errorIdx < parts.length) {
                    double errVal = parseDoubleSafe(parts[errorIdx], 0.0);
                    if (errVal > 1.0) {
                        hasErrorGreaterThanOne = true;
                    }
                }
                if (unitIdx >= 0 && unitIdx < parts.length) {
                    String u = parts[unitIdx].trim().toLowerCase();
                    if (u.contains("percent") || u.contains("%")) {
                        hasPercentUnit = true;
                    }
                }
            }

            if (rawRows.isEmpty()) {
                throw new IllegalArgumentException("No valid telemetry rows found in CSV file.");
            }

            boolean isErrorPercentScale = hasErrorGreaterThanOne || hasPercentUnit;
            List<ServiceMetric> metrics = new ArrayList<>();

            for (int r = 0; r < rawRows.size(); r++) {
                String[] parts = rawRows.get(r);
                int currentLine = lineNumbers.get(r);

                String sName = parts[serviceIdx].trim();
                LocalDateTime timestamp = parseFlexibleTimestamp(parts[timeIdx].trim(), currentLine);

                double cpu = cpuIdx >= 0 && cpuIdx < parts.length ? parseDoubleSafe(parts[cpuIdx], 10.0) : 10.0;
                double latency = latencyIdx >= 0 && latencyIdx < parts.length ? parseDoubleSafe(parts[latencyIdx], 20.0) : 20.0;
                double error = errorIdx >= 0 && errorIdx < parts.length ? parseDoubleSafe(parts[errorIdx], 0.0) : 0.0;
                if (isErrorPercentScale && error > 0.0) {
                    error = error / 100.0;
                }
                int conns = connIdx >= 0 && connIdx < parts.length ? parseIntSafe(parts[connIdx], 5) : 5;
                int limit = limitIdx >= 0 && limitIdx < parts.length ? parseIntSafe(parts[limitIdx], -1) : -1;

                if (limit <= 0 && poolUtilIdx >= 0 && poolUtilIdx < parts.length) {
                    double poolUtil = parseDoubleSafe(parts[poolUtilIdx], 0.0);
                    if (poolUtil > 0.0 && conns > 0) {
                        double utilFraction = poolUtil > 1.0 ? poolUtil / 100.0 : poolUtil;
                        limit = (int) Math.round(conns / utilFraction);
                    }
                }

                if (limit <= 0) {
                    String lowerName = sName.toLowerCase();
                    if (lowerName.contains("gateway") || lowerName.contains("router")) {
                        limit = 200;
                    } else if (lowerName.contains("db") || lowerName.contains("postgres") || lowerName.contains("sql")) {
                        limit = 100;
                    } else {
                        limit = 50;
                    }
                }
                if (limit < conns) {
                    limit = conns;
                }

                metrics.add(new ServiceMetric(sName, timestamp, cpu, latency, error, conns, limit));
            }

            return new IncidentRequest(
                    "CSV-UPLOAD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    metrics,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }
    }

    private ServiceMetric parseMetricNode(JsonNode node) {
        String name = node.has("name") ? node.get("name").asText() : node.get("service").asText();
        LocalDateTime time = parseFlexibleTimestamp(node.get("timestamp").asText(), 0);
        double cpu = node.has("cpuPercent") ? node.get("cpuPercent").asDouble() :
                     node.has("cpu_percent") ? node.get("cpu_percent").asDouble() :
                     node.path("cpu").asDouble(10.0);
        double latency = node.has("latencyMs") ? node.get("latencyMs").asDouble() :
                         node.has("latency_ms") ? node.get("latency_ms").asDouble() :
                         node.path("latency").asDouble(20.0);
        double error = node.has("errorRate") ? node.get("errorRate").asDouble() :
                       node.has("error_rate") ? node.get("error_rate").asDouble() :
                       node.path("errors").asDouble(0.0);
        if (error > 1.0) {
            error = error / 100.0;
        }
        int conns = node.has("activeConnections") ? node.get("activeConnections").asInt() :
                    node.has("active_connections") ? node.get("active_connections").asInt() :
                    node.path("connections").asInt(5);
        int limit = node.has("connectionPoolLimit") ? node.get("connectionPoolLimit").asInt() :
                    node.has("connection_pool_limit") ? node.get("connection_pool_limit").asInt() :
                    node.path("poolLimit").asInt(100);

        return new ServiceMetric(name, time, cpu, latency, error, conns, limit);
    }

    private String normalizeHeader(String header) {
        if (header == null) return "";
        return header.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private int findColumnIndex(Map<String, Integer> colIndex, String... aliases) {
        for (String alias : aliases) {
            String norm = normalizeHeader(alias);
            if (colIndex.containsKey(norm)) {
                return colIndex.get(norm);
            }
        }
        return -1;
    }

    private LocalDateTime parseFlexibleTimestamp(String text, int lineNum) {
        text = text.replace("Z", "");
        if (text.contains("+")) {
            text = text.substring(0, text.indexOf("+"));
        }

        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e1) {
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException e2) {
                try {
                    return LocalDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME);
                } catch (Exception e3) {
                    throw new IllegalArgumentException(String.format("Invalid timestamp format '%s' at line %d. Expected ISO-8601 (e.g. 2026-10-24T10:00:00).", text, lineNum));
                }
            }
        }
    }

    private double parseDoubleSafe(String val, double def) {
        try {
            return Double.parseDouble(val.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private int parseIntSafe(String val, int def) {
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
