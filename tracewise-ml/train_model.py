"""
TraceWise AI: Telemetry Anomaly Detection Training Pipeline
Algorithm: Scikit-learn Isolation Forest
Features:
  - cpu_percent: CPU utilization [0 - 100]
  - latency_ms: Service round-trip response time (ms)
  - error_rate: HTTP 5xx / application error ratio [0.0 - 1.0]
  - active_connections: Concurrently active connection threads
  - pool_utilization: Ratio of active connections to configured connection limit [0.0 - 1.0]

Isolation Forest is trained exclusively on normal synthetic multi-service baselines.
Evaluation is executed on held-out controlled anomalous telemetry scenarios.
Scores are transparently exposed as Isolation Forest decision function deviations,
never misrepresented as calibrated failure probabilities.
"""

import json
import os
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.metrics import classification_report, confusion_matrix, precision_recall_fscore_support
import joblib

RANDOM_SEED = 42
MODEL_VERSION = "isolation-forest-v1.0"
ARTIFACT_DIR = os.path.join(os.path.dirname(__file__), "model_artifacts")

FEATURE_NAMES = [
    "cpu_percent",
    "latency_ms",
    "error_rate",
    "active_connections",
    "pool_utilization"
]

def generate_normal_telemetry(n_samples: int = 5000, seed: int = RANDOM_SEED) -> pd.DataFrame:
    """Generate reproducible normal microservice telemetry under standard operating variance."""
    rng = np.random.default_rng(seed)

    # CPU centered at 22% with natural load fluctuations
    cpu = rng.normal(loc=22.0, scale=7.5, size=n_samples)
    cpu = np.clip(cpu, 1.0, 58.0)

    # Latency centered around 45ms
    latency = rng.exponential(scale=35.0, size=n_samples) + 12.0
    latency = np.clip(latency, 5.0, 180.0)

    # Nominal healthy error rate (near zero with occasional transient retries)
    error_rate = rng.exponential(scale=0.0015, size=n_samples)
    error_rate = np.clip(error_rate, 0.0, 0.015)

    # Active connections within safe operating range
    pool_limits = rng.choice([50, 100, 200], size=n_samples)
    active_conns = rng.binomial(n=pool_limits, p=0.25)
    active_conns = np.maximum(1, active_conns)

    pool_util = active_conns / pool_limits
    pool_util = np.clip(pool_util, 0.01, 0.65)

    df = pd.DataFrame({
        "cpu_percent": cpu,
        "latency_ms": latency,
        "error_rate": error_rate,
        "active_connections": active_conns,
        "pool_utilization": pool_util,
        "is_anomaly": 0
    })
    return df

def generate_abnormal_validation_telemetry(n_samples: int = 400, seed: int = RANDOM_SEED + 99) -> pd.DataFrame:
    """Generate held-out synthetic failure modes for model validation."""
    rng = np.random.default_rng(seed)
    records = []

    # 1. Database Connection Pool Saturation
    for _ in range(n_samples // 3):
        limit = rng.choice([50, 100])
        conns = int(limit * rng.uniform(0.92, 1.0))
        records.append({
            "cpu_percent": rng.uniform(40.0, 75.0),
            "latency_ms": rng.uniform(350.0, 2500.0),
            "error_rate": rng.uniform(0.08, 0.35),
            "active_connections": conns,
            "pool_utilization": conns / limit,
            "is_anomaly": 1
        })

    # 2. CPU Saturation
    for _ in range(n_samples // 3):
        limit = rng.choice([50, 100])
        conns = int(limit * rng.uniform(0.40, 0.70))
        records.append({
            "cpu_percent": rng.uniform(88.0, 99.5),
            "latency_ms": rng.uniform(1200.0, 4500.0),
            "error_rate": rng.uniform(0.12, 0.40),
            "active_connections": conns,
            "pool_utilization": conns / limit,
            "is_anomaly": 1
        })

    # 3. Latency & Network Degradation
    for _ in range(n_samples - len(records)):
        limit = rng.choice([50, 100])
        conns = int(limit * rng.uniform(0.30, 0.60))
        records.append({
            "cpu_percent": rng.uniform(15.0, 45.0),
            "latency_ms": rng.uniform(2800.0, 8500.0),
            "error_rate": rng.uniform(0.20, 0.55),
            "active_connections": conns,
            "pool_utilization": conns / limit,
            "is_anomaly": 1
        })

    return pd.DataFrame(records)

def train_and_evaluate():
    print(f"=== TraceWise AI Isolation Forest Model Training [{MODEL_VERSION}] ===")
    os.makedirs(ARTIFACT_DIR, exist_ok=True)

    # 1. Dataset Generation
    train_normal = generate_normal_telemetry(n_samples=5000, seed=RANDOM_SEED)
    test_normal = generate_normal_telemetry(n_samples=1000, seed=RANDOM_SEED + 10)
    test_abnormal = generate_abnormal_validation_telemetry(n_samples=400, seed=RANDOM_SEED + 20)

    test_combined = pd.concat([test_normal, test_abnormal], ignore_index=True)
    X_train = train_normal[FEATURE_NAMES]
    X_test = test_combined[FEATURE_NAMES]
    y_test = test_combined["is_anomaly"].values

    print(f"Training dataset size: {len(X_train)} samples (100% normal baseline)")
    print(f"Validation dataset size: {len(X_test)} samples ({len(test_normal)} normal, {len(test_abnormal)} abnormal)")

    # Baseline statistics for feature contribution calculation
    baseline_stats = {
        col: {
            "mean": float(train_normal[col].mean()),
            "std": float(train_normal[col].std()),
            "p95": float(train_normal[col].quantile(0.95))
        }
        for col in FEATURE_NAMES
    }

    # 2. Train Isolation Forest
    # contamination tuned for standard low rate of baseline outliers
    model = IsolationForest(
        n_estimators=120,
        contamination=0.03,
        max_samples="auto",
        random_state=RANDOM_SEED,
        n_jobs=-1
    )

    model.fit(X_train)
    print("Model fitting complete.")

    # 3. Model Evaluation on Held-Out Validation Test Set
    # IsolationForest returns -1 for anomaly, 1 for inlier
    raw_preds = model.predict(X_test)
    y_pred = (raw_preds == -1).astype(int)

    precision, recall, f1, _ = precision_recall_fscore_support(y_test, y_pred, average="binary")
    cm = confusion_matrix(y_test, y_pred)
    tn, fp, fn, tp = cm.ravel()

    print("\n--- Model Evaluation Results ---")
    print(f"Precision: {precision:.4f}")
    print(f"Recall:    {recall:.4f}")
    print(f"F1 Score:  {f1:.4f}")
    print(f"Confusion Matrix: TP={tp}, FP={fp}, FN={fn}, TN={tn}")
    print("\nDetailed Classification Report:")
    print(classification_report(y_test, y_pred, target_names=["Normal", "Anomaly"]))

    # 4. Save Model Artifacts
    model_path = os.path.join(ARTIFACT_DIR, "isolation_forest.joblib")
    joblib.dump(model, model_path)
    print(f"Saved fitted model to: {model_path}")

    metadata = {
        "model_version": MODEL_VERSION,
        "algorithm": "sklearn.ensemble.IsolationForest",
        "n_estimators": 120,
        "contamination": 0.03,
        "features": FEATURE_NAMES,
        "baseline_statistics": baseline_stats,
        "validation_metrics": {
            "precision": float(round(precision, 4)),
            "recall": float(round(recall, 4)),
            "f1_score": float(round(f1, 4)),
            "true_positives": int(tp),
            "false_positives": int(fp),
            "false_negatives": int(fn),
            "true_negatives": int(tn),
            "total_test_samples": len(y_test)
        },
        "score_semantics": "Continuous decision function offset. Not calibrated probability of failure."
    }

    meta_path = os.path.join(ARTIFACT_DIR, "metadata.json")
    with open(meta_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)
    print(f"Saved model metadata to: {meta_path}")

    return metadata

if __name__ == "__main__":
    train_and_evaluate()
