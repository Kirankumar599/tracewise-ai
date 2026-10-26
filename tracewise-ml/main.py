"""
TraceWise AI: Python Machine Learning Anomaly Detection Microservice
Exposes:
  - GET  /health
  - POST /api/v1/detect-anomalies
  - GET  /api/v1/evaluate
"""

import json
import os
from contextlib import asynccontextmanager
from typing import Dict, List, Optional
import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

ARTIFACT_DIR = os.path.join(os.path.dirname(__file__), "model_artifacts")
MODEL_PATH = os.path.join(ARTIFACT_DIR, "isolation_forest.joblib")
META_PATH = os.path.join(ARTIFACT_DIR, "metadata.json")

model = None
metadata = {}

@asynccontextmanager
async def lifespan(app: FastAPI):
    global model, metadata
    if os.path.exists(MODEL_PATH) and os.path.exists(META_PATH):
        model = joblib.load(MODEL_PATH)
        with open(META_PATH, "r", encoding="utf-8") as f:
            metadata = json.load(f)
        print(f"Loaded Isolation Forest model: {metadata.get('model_version')}")
    else:
        print("Model artifact not found; training inline...")
        from train_model import train_and_evaluate
        metadata = train_and_evaluate()
        model = joblib.load(MODEL_PATH)
    yield

app = FastAPI(
    title="TraceWise AI - ML Anomaly Detection Service",
    description="Explainable Isolation Forest anomaly detection for cascading software failures.",
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class ServiceMetricItem(BaseModel):
    name: str
    timestamp: str
    cpuPercent: float = Field(default=10.0, ge=0.0)
    latencyMs: float = Field(default=20.0, ge=0.0)
    errorRate: float = Field(default=0.0, ge=0.0)
    activeConnections: int = Field(default=5, ge=0)
    connectionPoolLimit: int = Field(default=100, ge=0)
    metricName: Optional[str] = None
    observedValue: Optional[float] = None
    expectedBaseline: Optional[float] = None
    unit: Optional[str] = None

class AnomalyDetectionRequest(BaseModel):
    metrics: List[ServiceMetricItem]

class AnomalyItem(BaseModel):
    id: str
    service: str
    timestamp: str
    metric_name: str
    anomaly_score: float
    severity: str
    observed_value: float
    expected_baseline: float
    unit: str
    description: str
    feature_contributions: Dict[str, float]

class AnomalyDetectionResponse(BaseModel):
    status: str
    modelVersion: str
    detection_engine: str
    anomalies: List[AnomalyItem]
    total_metrics_evaluated: int
    score_disclaimer: str

@app.get("/health")
def health():
    if model is None:
        raise HTTPException(status_code=503, detail="Isolation Forest model not initialized")
    return {
        "status": "healthy",
        "service": "tracewise-ml",
        "version": "1.0.0",
        "model_loaded": True,
        "model_version": metadata.get("model_version", "unknown")
    }

@app.post("/api/v1/detect-anomalies", response_model=AnomalyDetectionResponse)
def detect_anomalies(request: AnomalyDetectionRequest):
    if not request.metrics:
        return AnomalyDetectionResponse(
            status="SUCCESS",
            modelVersion=metadata.get("model_version", "isolation-forest-v1.0"),
            detection_engine="sklearn.ensemble.IsolationForest",
            anomalies=[],
            total_metrics_evaluated=0,
            score_disclaimer="Isolation Forest scores reflect relative decision boundary divergence, not calibrated failure probabilities."
        )

    # 1. Prepare feature matrix
    records = []
    for m in request.metrics:
        limit = max(1, m.connectionPoolLimit)
        pool_util = m.activeConnections / limit
        records.append({
            "service": m.name,
            "timestamp": m.timestamp,
            "cpu_percent": float(m.cpuPercent),
            "latency_ms": float(m.latencyMs),
            "error_rate": float(m.errorRate),
            "active_connections": float(m.activeConnections),
            "pool_utilization": float(pool_util),
            "pool_limit": limit
        })

    df = pd.DataFrame(records)
    feature_cols = metadata.get("features", [
        "cpu_percent", "latency_ms", "error_rate", "active_connections", "pool_utilization"
    ])
    X = df[feature_cols]

    # 2. Model inference
    # decision_function: lower values = more abnormal
    scores = model.decision_function(X)
    preds = model.predict(X)  # -1 is anomaly, 1 is normal

    baseline_stats = metadata.get("baseline_statistics", {})
    anomalies = []
    counter = 1

    for idx, (is_anom, dec_score) in enumerate(zip(preds, scores)):
        row = df.iloc[idx]
        is_saturated = row["pool_utilization"] >= 0.85 or row["cpu_percent"] >= 80.0 or row["error_rate"] >= 0.08 or row["latency_ms"] >= 400.0

        is_nominal_envelope = (
            row["latency_ms"] < 250.0
            and row["cpu_percent"] < 50.0
            and row["error_rate"] < 0.02
            and row["pool_utilization"] < 0.70
        )

        if is_saturated or (is_anom == -1 and not is_nominal_envelope):
            # Normalize decision score into [0.50, 0.99]
            norm_score = float(np.clip(0.60 + (-dec_score) * 2.0, 0.50, 0.98))

            # Calculate feature deviations (z-scores relative to training baseline)
            contributions = {}
            dominant_feature = "latency_ms"
            max_dev = -1.0

            for feat in feature_cols:
                val = float(row[feat])
                stats = baseline_stats.get(feat, {"mean": val, "std": 1.0})
                mean = stats["mean"]
                std = max(0.001, stats["std"])
                z = (val - mean) / std
                contributions[feat] = round(float(z), 2)
                if z > max_dev:
                    max_dev = z
                    dominant_feature = feat

            # Determine dominant metric name and unit
            if row["pool_utilization"] >= 0.85:
                metric_name = "CONNECTION_POOL_EXHAUSTION"
                unit = "connections"
                observed = row["active_connections"]
                baseline = row["pool_limit"] * 0.25
                desc = f"Connection pool critical saturation at {row['pool_utilization']*100:.1f}% ({int(row['active_connections'])}/{int(row['pool_limit'])})"
                severity = "CRITICAL" if row["pool_utilization"] >= 0.95 else "WARNING"
            elif row["cpu_percent"] >= 75.0 or dominant_feature == "cpu_percent":
                metric_name = "CPU_SATURATION"
                unit = "%"
                observed = row["cpu_percent"]
                baseline = baseline_stats.get("cpu_percent", {}).get("mean", 20.0)
                desc = f"Elevated compute saturation: {row['cpu_percent']:.1f}% CPU utilization"
                severity = "CRITICAL" if row["cpu_percent"] >= 90.0 else "WARNING"
            elif row["error_rate"] >= 0.05 or dominant_feature == "error_rate":
                metric_name = "ERROR_RATE_SURGE"
                unit = "%"
                observed = row["error_rate"] * 100.0
                baseline = baseline_stats.get("error_rate", {}).get("mean", 0.002) * 100.0
                desc = f"Error rate spike to {row['error_rate']*100:.1f}%"
                severity = "CRITICAL" if row["error_rate"] >= 0.20 else "WARNING"
            else:
                metric_name = "LATENCY_SPIKE"
                unit = "ms"
                observed = row["latency_ms"]
                baseline = baseline_stats.get("latency_ms", {}).get("mean", 45.0)
                desc = f"Response latency degradation to {row['latency_ms']:.0f} ms"
                severity = "CRITICAL" if row["latency_ms"] >= 2000.0 else "WARNING"

            anomalies.append(AnomalyItem(
                id=f"ML-ANOM-{counter}",
                service=row["service"],
                timestamp=str(row["timestamp"]),
                metric_name=metric_name,
                anomaly_score=round(norm_score, 2),
                severity=severity,
                observed_value=round(float(observed), 2),
                expected_baseline=round(float(baseline), 2),
                unit=unit,
                description=desc,
                feature_contributions=contributions
            ))
            counter += 1

    return AnomalyDetectionResponse(
        status="SUCCESS",
        modelVersion=metadata.get("model_version", "isolation-forest-v1.0"),
        detection_engine="sklearn.ensemble.IsolationForest",
        anomalies=anomalies,
        total_metrics_evaluated=len(request.metrics),
        score_disclaimer="Isolation Forest scores reflect relative decision boundary divergence, not calibrated failure probabilities."
    )

@app.get("/api/v1/evaluate")
def get_evaluation():
    if not metadata:
        raise HTTPException(status_code=503, detail="Model metadata not initialized.")
    return {
        "modelVersion": metadata.get("model_version"),
        "algorithm": metadata.get("algorithm"),
        "features": metadata.get("features"),
        "validationMetrics": metadata.get("validation_metrics"),
        "baselineStatistics": metadata.get("baseline_statistics"),
        "scoreSemantics": metadata.get("score_semantics")
    }

if __name__ == "__main__":
    import os
    import uvicorn
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=False)

