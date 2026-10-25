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
                colIndex.put(rawHeaders[i].trim().toLowerCase(), i);
            }

            if (!colIndex.containsKey("service") && !colIndex.containsKey("name")) {
                throw new IllegalArgumentException("CSV header must contain a 'service' or 'name' column.");
            }
            if (!colIndex.containsKey("timestamp")) {
                throw new IllegalArgumentException("CSV header must contain a 'timestamp' column.");
            }

            int serviceIdx = colIndex.getOrDefault("service", colIndex.get("name"));
            int timeIdx = colIndex.get("timestamp");
            int cpuIdx = colIndex.getOrDefault("cpupercent", colIndex.getOrDefault("cpu", -1));
            int latencyIdx = colIndex.getOrDefault("latencyms", colIndex.getOrDefault("latency", -1));
            int errorIdx = colIndex.getOrDefault("errorrate", colIndex.getOrDefault("errors", -1));
            int connIdx = colIndex.getOrDefault("activeconnections", colIndex.getOrDefault("connections", -1));
            int limitIdx = colIndex.getOrDefault("connectionpoollimit", colIndex.getOrDefault("poollimit", -1));

            List<ServiceMetric> metrics = new ArrayList<>();
            String line;
            int lineNum = 1;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split(",");
                if (parts.length < 2) continue;

                String sName = parts[serviceIdx].trim();
                LocalDateTime timestamp = parseFlexibleTimestamp(parts[timeIdx].trim(), lineNum);

                double cpu = cpuIdx >= 0 && cpuIdx < parts.length ? parseDoubleSafe(parts[cpuIdx], 0.0) : 10.0;
                double latency = latencyIdx >= 0 && latencyIdx < parts.length ? parseDoubleSafe(parts[latencyIdx], 20.0) : 20.0;
                double error = errorIdx >= 0 && errorIdx < parts.length ? parseDoubleSafe(parts[errorIdx], 0.0) : 0.0;
                int conns = connIdx >= 0 && connIdx < parts.length ? parseIntSafe(parts[connIdx], 5) : 5;
                int limit = limitIdx >= 0 && limitIdx < parts.length ? parseIntSafe(parts[limitIdx], 100) : 100;

                metrics.add(new ServiceMetric(sName, timestamp, cpu, latency, error, conns, limit));
            }

            if (metrics.isEmpty()) {
                throw new IllegalArgumentException("No valid telemetry rows found in CSV file.");
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
        double cpu = node.has("cpuPercent") ? node.get("cpuPercent").asDouble() : node.path("cpu").asDouble(10.0);
        double latency = node.has("latencyMs") ? node.get("latencyMs").asDouble() : node.path("latency").asDouble(20.0);
        double error = node.has("errorRate") ? node.get("errorRate").asDouble() : node.path("errors").asDouble(0.0);
        int conns = node.has("activeConnections") ? node.get("activeConnections").asInt() : node.path("connections").asInt(5);
        int limit = node.has("connectionPoolLimit") ? node.get("connectionPoolLimit").asInt() : node.path("poolLimit").asInt(100);

        return new ServiceMetric(name, time, cpu, latency, error, conns, limit);
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
