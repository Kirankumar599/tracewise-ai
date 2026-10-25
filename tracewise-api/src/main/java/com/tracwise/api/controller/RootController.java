package com.tracwise.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
                "service", "TraceWise AI - Root Cause Analysis API",
                "status", "UP",
                "version", "1.0.0",
                "endpoints", Map.of(
                        "health", "/api/v1/health",
                        "demoCatalog", "/api/v1/incidents/demo/catalog",
                        "analyze", "/api/v1/incidents/analyze",
                        "evaluation", "/api/v1/evaluation"
                ),
                "timestamp", LocalDateTime.now()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthAlias() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "tracewise-api",
                "message", "Service is running. Detailed status available at /api/v1/health"
        ));
    }
}
