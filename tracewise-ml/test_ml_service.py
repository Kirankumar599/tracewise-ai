"""
Unit test for TraceWise AI FastAPI ML service
"""

from fastapi.testclient import TestClient
from main import app

def test_all():
    with TestClient(app) as client:
        # 1. Health
        resp = client.get("/health")
        assert resp.status_code == 200, resp.text
        data = resp.json()
        assert data["status"] == "healthy"
        assert data["model_loaded"] is True
        assert "isolation-forest" in data["model_version"]
        print("[PASS] /health passed")

        # 2. Detect Anomalies
        payload = {
            "metrics": [
                {
                    "name": "postgres-db",
                    "timestamp": "2026-10-24T10:00:00",
                    "cpuPercent": 20.0,
                    "latencyMs": 15.0,
                    "errorRate": 0.001,
                    "activeConnections": 20,
                    "connectionPoolLimit": 100
                },
                {
                    "name": "postgres-db",
                    "timestamp": "2026-10-24T10:02:00",
                    "cpuPercent": 75.0,
                    "latencyMs": 850.0,
                    "errorRate": 0.18,
                    "activeConnections": 98,
                    "connectionPoolLimit": 100
                }
            ]
        }
        resp = client.post("/api/v1/detect-anomalies", json=payload)
        assert resp.status_code == 200, resp.text
        data = resp.json()
        assert data["status"] == "SUCCESS"
        assert data["total_metrics_evaluated"] == 2
        assert len(data["anomalies"]) >= 1

        anom = data["anomalies"][0]
        assert anom["service"] == "postgres-db"
        assert anom["metric_name"] == "CONNECTION_POOL_EXHAUSTION"
        assert anom["severity"] == "CRITICAL"
        assert "feature_contributions" in anom
        print("[PASS] /api/v1/detect-anomalies passed")

        # 3. Evaluate
        resp = client.get("/api/v1/evaluate")
        assert resp.status_code == 200, resp.text
        data = resp.json()
        assert "validationMetrics" in data
        assert data["validationMetrics"]["precision"] > 0.90
        assert data["validationMetrics"]["recall"] > 0.95
        print("[PASS] /api/v1/evaluate passed")

    print("\nAll ML service tests passed successfully!")

if __name__ == "__main__":
    test_all()
