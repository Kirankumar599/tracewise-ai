package com.tracwise.api.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tracwise.api.dto.IncidentRequest.ServiceMetric;
import com.tracwise.api.dto.analysis.AnomalyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class AnomalyDetectionClient {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionClient.class);

    private final RestClient restClient;
    private final StatisticalAnomalyDetector statisticalDetector;
    private final String mlServiceUrl;

    public AnomalyDetectionClient(
            @Value("${tracewise.ml.service.url:http://localhost:8000}") String mlServiceUrl,
            @Value("${tracewise.ml.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${tracewise.ml.read-timeout-ms:3000}") int readTimeoutMs,
            StatisticalAnomalyDetector statisticalDetector
    ) {
        this.mlServiceUrl = mlServiceUrl;
        this.statisticalDetector = statisticalDetector;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(mlServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public record AnomalyDetectionResult(
            List<AnomalyRecord> anomalies,
            String detectionEngine,
            boolean mlServiceOnline
    ) {}

    public AnomalyDetectionResult detect(List<ServiceMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return new AnomalyDetectionResult(Collections.emptyList(), "NONE", false);
        }

        try {
            log.info("Querying ML Anomaly Service at {} for {} metric points...", mlServiceUrl, metrics.size());
            MlAnomalyResponse response = restClient.post()
                    .uri("/api/v1/detect-anomalies")
                    .body(new MlAnomalyRequest(metrics))
                    .retrieve()
                    .body(MlAnomalyResponse.class);

            if (response != null && response.anomalies() != null) {
                log.info("ML Anomaly Service responded successfully with {} anomalies. Model: {}",
                        response.anomalies().size(), response.modelVersion());
                List<AnomalyRecord> converted = response.anomalies().stream()
                        .map(item -> new AnomalyRecord(
                                item.id(),
                                item.service(),
                                item.timestamp(),
                                item.metricName(),
                                item.anomalyScore(),
                                item.severity(),
                                item.observedValue(),
                                item.expectedBaseline(),
                                item.unit(),
                                item.description(),
                                item.featureContributions(),
                                "IsolationForest (" + response.modelVersion() + ")"
                        ))
                        .toList();
                return new AnomalyDetectionResult(converted, "ISOLATION_FOREST_ML", true);
            }
        } catch (Exception e) {
            log.warn("Python ML service at {} unavailable ({}). Engaging documented statistical fallback.",
                    mlServiceUrl, e.getMessage());
        }

        // Documented transparent fallback
        List<AnomalyRecord> fallbackAnomalies = statisticalDetector.detectAnomalies(
                metrics,
                "StatisticalBaselineFallback (ML Service Offline)"
        );
        return new AnomalyDetectionResult(fallbackAnomalies, "STATISTICAL_FALLBACK", false);
    }

    public boolean isMlServiceAvailable() {
        try {
            Map<?, ?> health = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(Map.class);
            return health != null && "healthy".equalsIgnoreCase(String.valueOf(health.get("status")));
        } catch (Exception e) {
            return false;
        }
    }

    public String getMlServiceUrl() {
        return mlServiceUrl;
    }

    public record MlAnomalyRequest(List<ServiceMetric> metrics) {}

    public record MlAnomalyResponse(
            String status,
            String modelVersion,
            List<MlAnomalyItem> anomalies
    ) {}

    public record MlAnomalyItem(
            String id,
            String service,
            LocalDateTime timestamp,
            @JsonProperty("metric_name") String metricName,
            @JsonProperty("anomaly_score") double anomalyScore,
            String severity,
            @JsonProperty("observed_value") double observedValue,
            @JsonProperty("expected_baseline") double expectedBaseline,
            String unit,
            String description,
            @JsonProperty("feature_contributions") Map<String, Double> featureContributions
    ) {}
}
