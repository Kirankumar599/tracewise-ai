package com.tracwise.api.service;

import com.tracwise.api.dto.IncidentRequest;
import com.tracwise.api.dto.IncidentRequest.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class DemoScenarioService {

    public IncidentRequest getScenario(String scenarioId) {
        if (scenarioId == null || scenarioId.isBlank()) {
            return buildDbPoolExhaustionScenario();
        }

        return switch (scenarioId.toLowerCase().trim()) {
            case "db-pool-exhaustion", "db-pool", "db", "1", "inc-2026-db-pool" -> buildDbPoolExhaustionScenario();
            case "cpu-saturation", "cpu", "2", "inc-2026-cpu-auth" -> buildCpuSaturationScenario();
            case "latency-error-spike", "latency", "shipping", "3", "inc-2026-ext-shipping" -> buildLatencySpikeScenario();
            case "bench-healthy-01", "healthy-baseline", "healthy", "4" -> buildHealthyBaselineScenario();
            case "bench-mem-02", "memory-pressure", "memory-leak", "mem", "5" -> buildMemoryPressureScenario();
            default -> throw new NoSuchElementException("Unknown scenario ID: " + scenarioId);
        };
    }

    public List<ScenarioCatalogItem> listScenarios() {
        return List.of(
                new ScenarioCatalogItem(
                        "db-pool-exhaustion",
                        "Database Connection Pool Exhaustion",
                        "Postgres pool reaches 100/100 connections due to an unindexed transaction lock, causing cascading timeouts in Order and Payment services.",
                        "CRITICAL",
                        "4 Services (postgres-db, order-service, payment-service, api-gateway)",
                        "DATABASE_EXHAUSTION",
                        "postgres-db"
                ),
                new ScenarioCatalogItem(
                        "cpu-saturation",
                        "CPU Saturation via Regressive Deployment",
                        "Auth Service CPU spikes to 98% following a flawed regex tokenizer deployment, choking authentication threads across the web portal.",
                        "HIGH",
                        "4 Services (auth-service, web-portal, user-service, redis-session-store)",
                        "RESOURCE_SATURATION",
                        "auth-service"
                ),
                new ScenarioCatalogItem(
                        "latency-error-spike",
                        "Third-Party Upstream Timeout Cascade",
                        "External shipping carrier API degrades to 8,000ms latency and 45% 504 timeouts, blocking worker queues in Checkout service.",
                        "HIGH",
                        "4 Services (third-party-shipping-api, checkout-service, inventory-service, ingress-router)",
                        "UPSTREAM_TIMEOUT",
                        "third-party-shipping-api"
                ),
                new ScenarioCatalogItem(
                        "bench-healthy-01",
                        "Healthy Baseline System",
                        "Nominal multi-service baseline operating under regular workload with zero anomalies and sub-second latencies.",
                        "LOW",
                        "3 Services (gateway, user-service, user-db)",
                        "NONE",
                        "NONE"
                ),
                new ScenarioCatalogItem(
                        "bench-mem-02",
                        "Search Indexer Memory Leak (Heap Exhaustion)",
                        "Search indexer triggers OutOfMemoryError and heap space exhaustion during batch merge, causing cascading delays in web frontend.",
                        "CRITICAL",
                        "2 Services (web-frontend, search-indexer)",
                        "RESOURCE_SATURATION",
                        "search-indexer"
                )
        );
    }

    public record ScenarioCatalogItem(
            String id,
            String title,
            String description,
            String severity,
            String affectedTopology,
            String groundTruthCategory,
            String groundTruthService
    ) {}

    private IncidentRequest buildDbPoolExhaustionScenario() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 24, 10, 0, 0);

        List<ServiceMetric> metrics = new ArrayList<>();
        List<LogEvent> logs = new ArrayList<>();
        List<Dependency> dependencies = List.of(
                new Dependency("api-gateway", "order-service"),
                new Dependency("api-gateway", "payment-service"),
                new Dependency("order-service", "postgres-db"),
                new Dependency("payment-service", "postgres-db")
        );

        List<ChangeEvent> changes = List.of(
                new ChangeEvent(base.minusMinutes(12), "postgres-db", "Automated migration: Added index to billing_invoices table")
        );

        // Timesteps: t0 (normal), t1 (normal), t2 (db pool climbing), t3 (db pool hits limit), t4 (cascade to orders), t5 (cascade to gateway)
        for (int i = 0; i < 8; i++) {
            LocalDateTime t = base.plusSeconds(i * 30L);

            if (i < 2) {
                // Normal
                metrics.add(new ServiceMetric("postgres-db", t, 18.0, 12.0, 0.001, 24, 100));
                metrics.add(new ServiceMetric("order-service", t, 22.0, 45.0, 0.002, 10, 50));
                metrics.add(new ServiceMetric("payment-service", t, 19.0, 38.0, 0.001, 8, 50));
                metrics.add(new ServiceMetric("api-gateway", t, 15.0, 60.0, 0.001, 30, 200));
            } else if (i == 2) {
                // Onset at postgres-db
                metrics.add(new ServiceMetric("postgres-db", t, 42.0, 85.0, 0.02, 88, 100));
                metrics.add(new ServiceMetric("order-service", t, 25.0, 65.0, 0.005, 12, 50));
                metrics.add(new ServiceMetric("payment-service", t, 21.0, 50.0, 0.002, 9, 50));
                metrics.add(new ServiceMetric("api-gateway", t, 16.0, 75.0, 0.002, 35, 200));

                logs.add(new LogEvent(t.plusSeconds(5), "postgres-db", "WARN", "HikariPool-1 - Connection pool usage at 88/100. Connection acquisition latency rising."));
            } else if (i == 3) {
                // Full saturation at postgres-db
                metrics.add(new ServiceMetric("postgres-db", t, 68.0, 350.0, 0.15, 100, 100));
                metrics.add(new ServiceMetric("order-service", t, 45.0, 1200.0, 0.12, 48, 50));
                metrics.add(new ServiceMetric("payment-service", t, 32.0, 650.0, 0.08, 35, 50));
                metrics.add(new ServiceMetric("api-gateway", t, 25.0, 300.0, 0.04, 70, 200));

                logs.add(new LogEvent(t, "postgres-db", "ERROR", "HikariPool-1 - Connection pool is exhausted! 100 of 100 active connections in use."));
                logs.add(new LogEvent(t.plusSeconds(10), "order-service", "ERROR", "Connection acquisition timeout (30000ms) waiting for postgres-db"));
            } else {
                // Full cascade into callers
                metrics.add(new ServiceMetric("postgres-db", t, 75.0, 520.0, 0.28, 100, 100));
                metrics.add(new ServiceMetric("order-service", t, 62.0, 3400.0, 0.38, 50, 50));
                metrics.add(new ServiceMetric("payment-service", t, 55.0, 2800.0, 0.31, 50, 50));
                metrics.add(new ServiceMetric("api-gateway", t, 48.0, 1950.0, 0.24, 180, 200));

                logs.add(new LogEvent(t, "order-service", "ERROR", "org.postgresql.util.PSQLException: FATAL: remaining connection slots are reserved"));
                logs.add(new LogEvent(t.plusSeconds(5), "api-gateway", "ERROR", "HTTP 504 Gateway Timeout downstream for /api/v1/orders/checkout"));
            }
        }

        return new IncidentRequest(
                "INC-2026-DB-POOL",
                metrics,
                logs,
                dependencies,
                changes,
                new EvaluationMetadata("postgres-db", "DATABASE_EXHAUSTION", "Deterministic benchmark ground truth: postgres connection pool saturation")
        );
    }

    private IncidentRequest buildCpuSaturationScenario() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 24, 14, 0, 0);

        List<ServiceMetric> metrics = new ArrayList<>();
        List<LogEvent> logs = new ArrayList<>();
        List<Dependency> dependencies = List.of(
                new Dependency("web-portal", "auth-service"),
                new Dependency("web-portal", "user-service"),
                new Dependency("auth-service", "redis-session-store")
        );

        List<ChangeEvent> changes = List.of(
                new ChangeEvent(base.minusMinutes(4), "auth-service", "Deploy release v2.4.1: Enhanced Regex JWT Signature Verification")
        );

        for (int i = 0; i < 8; i++) {
            LocalDateTime t = base.plusSeconds(i * 30L);

            if (i < 2) {
                metrics.add(new ServiceMetric("auth-service", t, 25.0, 30.0, 0.001, 15, 80));
                metrics.add(new ServiceMetric("web-portal", t, 18.0, 45.0, 0.002, 20, 100));
                metrics.add(new ServiceMetric("user-service", t, 15.0, 20.0, 0.001, 10, 50));
                metrics.add(new ServiceMetric("redis-session-store", t, 8.0, 2.0, 0.000, 30, 500));
            } else if (i == 2) {
                // CPU spike starts on auth-service
                metrics.add(new ServiceMetric("auth-service", t, 84.0, 180.0, 0.03, 40, 80));
                metrics.add(new ServiceMetric("web-portal", t, 22.0, 90.0, 0.005, 25, 100));
                metrics.add(new ServiceMetric("user-service", t, 16.0, 22.0, 0.001, 11, 50));
                metrics.add(new ServiceMetric("redis-session-store", t, 9.0, 3.0, 0.000, 35, 500));

                logs.add(new LogEvent(t, "auth-service", "WARN", "High CPU load detected on container worker threads (84%). Inefficient regex evaluation."));
            } else if (i == 3) {
                // Extreme saturation
                metrics.add(new ServiceMetric("auth-service", t, 98.5, 2600.0, 0.22, 78, 80));
                metrics.add(new ServiceMetric("web-portal", t, 35.0, 850.0, 0.09, 45, 100));
                metrics.add(new ServiceMetric("user-service", t, 16.0, 25.0, 0.001, 12, 50));
                metrics.add(new ServiceMetric("redis-session-store", t, 10.0, 3.0, 0.000, 38, 500));

                logs.add(new LogEvent(t, "auth-service", "ERROR", "Thread pool exhaustion: 128/128 worker threads in RUNNABLE state executing TokenValidator.regexMatch"));
                logs.add(new LogEvent(t.plusSeconds(8), "web-portal", "ERROR", "Upstream call to /oauth/token timed out after 5000ms"));
            } else {
                metrics.add(new ServiceMetric("auth-service", t, 99.1, 4200.0, 0.35, 80, 80));
                metrics.add(new ServiceMetric("web-portal", t, 52.0, 1900.0, 0.28, 85, 100));
                metrics.add(new ServiceMetric("user-service", t, 17.0, 30.0, 0.002, 12, 50));
                metrics.add(new ServiceMetric("redis-session-store", t, 10.0, 4.0, 0.000, 40, 500));

                logs.add(new LogEvent(t, "web-portal", "ERROR", "HTTP 502 Bad Gateway: auth-service unresponsive"));
            }
        }

        return new IncidentRequest(
                "INC-2026-CPU-AUTH",
                metrics,
                logs,
                dependencies,
                changes,
                new EvaluationMetadata("auth-service", "RESOURCE_SATURATION", "Deterministic benchmark ground truth: CPU saturation following regex release")
        );
    }

    private IncidentRequest buildLatencySpikeScenario() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 24, 18, 0, 0);

        List<ServiceMetric> metrics = new ArrayList<>();
        List<LogEvent> logs = new ArrayList<>();
        List<Dependency> dependencies = List.of(
                new Dependency("ingress-router", "checkout-service"),
                new Dependency("checkout-service", "inventory-service"),
                new Dependency("checkout-service", "third-party-shipping-api")
        );

        List<ChangeEvent> changes = List.of(
                new ChangeEvent(base.minusMinutes(8), "third-party-shipping-api", "External provider notified: Major cloud provider network maintenance")
        );

        for (int i = 0; i < 8; i++) {
            LocalDateTime t = base.plusSeconds(i * 30L);

            if (i < 2) {
                metrics.add(new ServiceMetric("third-party-shipping-api", t, 10.0, 75.0, 0.001, 5, 50));
                metrics.add(new ServiceMetric("checkout-service", t, 20.0, 95.0, 0.001, 15, 100));
                metrics.add(new ServiceMetric("inventory-service", t, 14.0, 35.0, 0.001, 8, 50));
                metrics.add(new ServiceMetric("ingress-router", t, 12.0, 110.0, 0.001, 25, 200));
            } else if (i == 2) {
                // Latency degradation starts on external shipping API
                metrics.add(new ServiceMetric("third-party-shipping-api", t, 12.0, 1850.0, 0.12, 22, 50));
                metrics.add(new ServiceMetric("checkout-service", t, 28.0, 450.0, 0.02, 25, 100));
                metrics.add(new ServiceMetric("inventory-service", t, 15.0, 36.0, 0.001, 9, 50));
                metrics.add(new ServiceMetric("ingress-router", t, 15.0, 220.0, 0.005, 35, 200));

                logs.add(new LogEvent(t, "third-party-shipping-api", "WARN", "Degraded network transport: Packet loss 8.2% to shipping carrier endpoint"));
            } else if (i == 3) {
                // Severe degradation
                metrics.add(new ServiceMetric("third-party-shipping-api", t, 14.0, 7800.0, 0.44, 48, 50));
                metrics.add(new ServiceMetric("checkout-service", t, 48.0, 3200.0, 0.28, 88, 100));
                metrics.add(new ServiceMetric("inventory-service", t, 16.0, 40.0, 0.001, 10, 50));
                metrics.add(new ServiceMetric("ingress-router", t, 35.0, 1600.0, 0.18, 110, 200));

                logs.add(new LogEvent(t, "checkout-service", "ERROR", "Read timed out after 5000ms calling external shipping rate provider"));
                logs.add(new LogEvent(t.plusSeconds(6), "ingress-router", "ERROR", "Client connection timeout on POST /api/checkout/quote"));
            } else {
                metrics.add(new ServiceMetric("third-party-shipping-api", t, 15.0, 8900.0, 0.48, 50, 50));
                metrics.add(new ServiceMetric("checkout-service", t, 56.0, 4800.0, 0.36, 95, 100));
                metrics.add(new ServiceMetric("inventory-service", t, 16.0, 42.0, 0.002, 10, 50));
                metrics.add(new ServiceMetric("ingress-router", t, 42.0, 2800.0, 0.29, 140, 200));

                logs.add(new LogEvent(t, "checkout-service", "ERROR", "Circuit breaker OPEN for ThirdPartyShippingGateway - fallback triggered"));
            }
        }

        return new IncidentRequest(
                "INC-2026-EXT-SHIPPING",
                metrics,
                logs,
                dependencies,
                changes,
                new EvaluationMetadata("third-party-shipping-api", "UPSTREAM_TIMEOUT", "Deterministic benchmark ground truth: external carrier API latency explosion")
        );
    }

    public IncidentRequest buildHealthyBaselineScenario() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 24, 8, 0, 0);
        List<ServiceMetric> metrics = new ArrayList<>();
        List<Dependency> dependencies = List.of(
                new Dependency("gateway", "user-service"),
                new Dependency("user-service", "user-db")
        );

        for (int i = 0; i < 4; i++) {
            LocalDateTime t = base.plusSeconds(i * 30L);
            metrics.add(new ServiceMetric("gateway", t, 12.0, 45.0, 0.000, 15, 100));
            metrics.add(new ServiceMetric("user-service", t, 18.0, 25.0, 0.000, 8, 50));
            metrics.add(new ServiceMetric("user-db", t, 14.0, 8.0, 0.000, 12, 100));
        }

        return new IncidentRequest(
                "BENCH-HEALTHY-01",
                metrics,
                Collections.emptyList(),
                dependencies,
                Collections.emptyList(),
                new EvaluationMetadata("", "", "Nominal healthy baseline with zero simulated failure")
        );
    }

    public IncidentRequest buildMemoryPressureScenario() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 24, 16, 0, 0);
        List<ServiceMetric> metrics = new ArrayList<>();
        List<Dependency> dependencies = List.of(
                new Dependency("web-frontend", "search-indexer")
        );
        List<LogEvent> logs = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            LocalDateTime t = base.plusSeconds(i * 30L);
            if (i < 2) {
                metrics.add(new ServiceMetric("web-frontend", t, 15.0, 50.0, 0.001, 10, 100));
                metrics.add(new ServiceMetric("search-indexer", t, 35.0, 120.0, 0.001, 15, 50));
            } else {
                metrics.add(new ServiceMetric("web-frontend", t, 28.0, 850.0, 0.08, 30, 100));
                metrics.add(new ServiceMetric("search-indexer", t, 88.0, 3100.0, 0.25, 48, 50));
                logs.add(new LogEvent(t, "search-indexer", "ERROR", "java.lang.OutOfMemoryError: Java heap space during batch index merge"));
            }
        }

        return new IncidentRequest(
                "BENCH-MEM-02",
                metrics,
                logs,
                dependencies,
                Collections.emptyList(),
                new EvaluationMetadata("search-indexer", "RESOURCE_SATURATION", "Synthetic memory leak and heap exhaustion on search-indexer")
        );
    }
}
