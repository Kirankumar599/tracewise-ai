"""
TraceWise AI: End-to-End Live Integration Verification Script
Verifies frontend-backend-ML contracts, all 5 benchmark scenarios,
healthy baseline nominal output, regression tests, and error handling.
"""

import sys
import os
import json
import urllib.request
import urllib.error

API_BASE = os.environ.get("API_BASE", "http://localhost:8081").rstrip("/")
ML_BASE = os.environ.get("ML_BASE", "http://localhost:8000").rstrip("/")

def request(method, url, data=None):
    req = urllib.request.Request(url, method=method)
    req.add_header("Content-Type", "application/json")
    body = json.dumps(data).encode("utf-8") if data else None
    try:
        with urllib.request.urlopen(req, data=body, timeout=30) as resp:
            content = resp.read().decode("utf-8")
            return resp.status, json.loads(content) if content else {}
    except urllib.error.HTTPError as e:
        content = e.read().decode("utf-8")
        try:
            return e.code, json.loads(content)
        except Exception:
            return e.code, {"raw": content}
    except Exception as e:
        return 0, {"error": str(e)}

results = []

def record(name, passed, details=""):
    status = "PASS" if passed else "FAIL"
    results.append({"name": name, "status": status, "details": details})
    print(f"[{status}] {name} {('- ' + details) if details else ''}")

print("=== Starting TraceWise AI Live E2E Integration Test Suite ===")

# 1. Health Checks
status, data = request("GET", f"{ML_BASE}/health")
record("ML Service Health Check", status == 200 and data.get("model_loaded") is True, f"Model loaded: {data.get('model_version')}")

status, data = request("GET", f"{API_BASE}/api/v1/health")
record("Spring Boot API Health Check", status == 200 and data.get("status") == "UP" and data.get("mlServiceConnected") is True, f"ML connected: {data.get('mlServiceConnected')}")

# 2. Demo Catalog
status, data = request("GET", f"{API_BASE}/api/v1/incidents/demo/catalog")
record("Demo Catalog Loaded", status == 200 and len(data) == 5, f"Catalog items: {len(data)}")

# 3. Scenario 1: DB Pool Exhaustion
status, data = request("POST", f"{API_BASE}/api/v1/incidents/demo/db-pool-exhaustion/run")
top_hyp = data.get("hypotheses", [{}])[0] if data.get("hypotheses") else {}
has_carrier_leak = any("carrier" in str(h).lower() for h in data.get("hypotheses", []))
record("Scenario 1: DB Pool Exhaustion Top-1",
       status == 200 and top_hyp.get("suspectedService") == "postgres-db" and top_hyp.get("rootCauseCategory") == "DATABASE_EXHAUSTION",
       f"Top suspect: {top_hyp.get('suspectedService')}, Score: {top_hyp.get('evidenceScore')}%")
record("Scenario 1: Regression Test (No Carrier Leak)",
       not has_carrier_leak,
       "No false upstream carrier hypotheses generated for database pool failure")

# 4. Scenario 2: CPU Saturation
status, data = request("POST", f"{API_BASE}/api/v1/incidents/demo/cpu-saturation/run")
top_hyp = data.get("hypotheses", [{}])[0] if data.get("hypotheses") else {}
record("Scenario 2: CPU Saturation Top-1",
       status == 200 and top_hyp.get("suspectedService") == "auth-service" and top_hyp.get("rootCauseCategory") == "RESOURCE_SATURATION",
       f"Top suspect: {top_hyp.get('suspectedService')}, Score: {top_hyp.get('evidenceScore')}%")

# 5. Scenario 3: Third-Party Shipping Latency
status, data = request("POST", f"{API_BASE}/api/v1/incidents/demo/latency-error-spike/run")
top_hyp = data.get("hypotheses", [{}])[0] if data.get("hypotheses") else {}
record("Scenario 3: External Shipping Timeout Top-1",
       status == 200 and top_hyp.get("suspectedService") == "third-party-shipping-api" and top_hyp.get("rootCauseCategory") == "UPSTREAM_TIMEOUT",
       f"Top suspect: {top_hyp.get('suspectedService')}, Score: {top_hyp.get('evidenceScore')}%")

# 6. Scenario 4: Healthy Baseline
status, data = request("POST", f"{API_BASE}/api/v1/incidents/demo/bench-healthy-01/run")
summary = data.get("summary", {})
anoms = data.get("anomalies", [])
record("Scenario 4: Healthy Baseline produces NONE",
       status == 200 and summary.get("primaryRootCause") == "NONE" and summary.get("status") == "NO_INCIDENT_DETECTED" and len(anoms) == 0,
       f"Status: {summary.get('status')}, Anomalies: {len(anoms)}, Hypotheses: {len(data.get('hypotheses', []))}")

# 7. Scenario 5: Search Indexer Memory Leak
status, data = request("POST", f"{API_BASE}/api/v1/incidents/demo/bench-mem-02/run")
top_hyp = data.get("hypotheses", [{}])[0] if data.get("hypotheses") else {}
has_heap_rec = any("heap" in str(a).lower() or "memory" in str(a).lower() for a in data.get("recommendedActions", []))
record("Scenario 5: Search Indexer Memory Leak Top-1",
       status == 200 and top_hyp.get("suspectedService") == "search-indexer" and top_hyp.get("rootCauseCategory") == "RESOURCE_SATURATION",
       f"Top suspect: {top_hyp.get('suspectedService')}, Heap actions: {has_heap_rec}")

# 8. Evaluation Benchmark Endpoint
status, data = request("GET", f"{API_BASE}/api/v1/evaluation")
record("Evaluation Benchmark Results",
       status == 200 and data.get("totalTestScenarios") == 5 and data.get("top1RootCauseAccuracy") == 1.0,
       f"Scenarios: {data.get('totalTestScenarios')}, Top-1 Accuracy: {data.get('top1RootCauseAccuracy')*100:.0f}%")

# 9. Custom Analyze Endpoint
custom_req = {
    "incidentId": "E2E-CUSTOM-001",
    "services": [
        {"name": "orders-db", "timestamp": "2026-10-24T10:00:00", "cpuPercent": 45.0, "latencyMs": 120.0, "errorRate": 0.02, "activeConnections": 96, "connectionPoolLimit": 100},
        {"name": "web-api", "timestamp": "2026-10-24T10:00:00", "cpuPercent": 20.0, "latencyMs": 500.0, "errorRate": 0.10, "activeConnections": 15, "connectionPoolLimit": 50}
    ],
    "logs": [
        {"timestamp": "2026-10-24T10:00:05", "service": "orders-db", "level": "ERROR", "message": "HikariPool-1 - Connection pool exhausted (96/100)"}
    ],
    "dependencies": [
        {"from": "web-api", "to": "orders-db"}
    ],
    "recentChanges": []
}
status, data = request("POST", f"{API_BASE}/api/v1/incidents/analyze", custom_req)
record("Custom Incident Analysis",
       status == 200 and data.get("status") == "ANALYZED" and data.get("summary", {}).get("primaryAffectedService") == "orders-db",
       f"Identified primary service: {data.get('summary', {}).get('primaryAffectedService')}")

# 10. Failure & Error Handling Tests
status, data = request("POST", f"{API_BASE}/api/v1/incidents/demo/nonexistent-id-999/run")
record("Error Handling: Unknown Demo Scenario Returns 404",
       status == 404 and data.get("errorCode") == "RESOURCE_NOT_FOUND",
       f"HTTP Status: {status}, ErrorCode: {data.get('errorCode')}")

status, data = request("POST", f"{API_BASE}/api/v1/incidents/analyze", {"incidentId": "INVALID", "services": []})
record("Error Handling: Validation Failure on Empty Services Returns 400",
       status == 400 and data.get("errorCode") == "VALIDATION_ERROR",
       f"HTTP Status: {status}, ErrorCode: {data.get('errorCode')}")

print("\n=== E2E Integration Summary ===")
total = len(results)
passed = sum(1 for r in results if r["status"] == "PASS")
failed = total - passed
print(f"Total Tests: {total} | Passed: {passed} | Failed: {failed}")

if failed > 0:
    sys.exit(1)
sys.exit(0)
