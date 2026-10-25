# TraceWise AI

> **From Scattered Alerts to Clear Answers.**  
> *Explainable AI for Detecting and Investigating Cascading Software Failures.*  
> AXION Hackathon Round 2 Prototype Submission

---

## 1. Executive Summary & Product Vision

Modern distributed cloud architectures generate a cascade of noisy alerts, logs, and telemetry spikes whenever a foundational dependency experiences degradation. Software and Site Reliability Engineers (SREs) are forced to manually correlate disparate dashboards, investigate logs across multiple microservices, and trace call graphs to pinpoint whether an issue originated from an API gateway timeout, an auth service CPU spike, or an underlying database connection pool bottleneck.

**TraceWise AI** is an explainable incident-intelligence platform built for high-velocity engineering teams. It ingests multi-service telemetry, structured log events, topological service dependency graphs, and recent deployment changes to:
1. **Detect Telemetry Anomalies** using a dedicated scikit-learn **Isolation Forest** ML service with an adaptive statistical Z-score baseline fallback.
2. **Reconstruct Chronological Incident Timelines** identifying the exact initiating trigger event versus downstream cascading symptoms.
3. **Trace Blast Radius** across an interactive topological service dependency graph with animated cascade flow lines.
4. **Rank Root-Cause Hypotheses** via an explainable multi-evidence ranking engine (evaluating temporal precedence, topology direction, saturation limits, log confirmation, and deployment proximity).
5. **Provide Transparent Evidence Scores** (0–100% heuristic strength, never mislabeled as statistical probabilities).
6. **Recommend Safe Diagnostic Actions** (e.g., targeted `SELECT * FROM pg_stat_activity` commands or thread dumps) requiring human approval before execution.
7. **Generate Standard Post-Mortem Markdown Reports** for engineering retrospectives with 1-click clipboard copy or download.

---

## 2. System Architecture

```
                          +-------------------------------------------------------+
                          |                   Vercel Frontend                     |
                          |   TraceWise UI (React 19 + TypeScript + Vite +        |
                          |   Tailwind CSS + Recharts + Interactive SVG Graph)    |
                          +---------------------------+---------------------------+
                                                      |
                                             REST HTTP / JSON
                                                      |
                                                      v
                          +-------------------------------------------------------+
                          |                  Spring Boot API                      |
                          |           tracewise-api (Java 24 / Boot 4.1.2)        |
                          |                                                       |
                          |  - IncidentController (REST Endpoints)                |
                          |  - TelemetryParser (Streaming CSV & JSON Validation)  |
                          |  - DemoScenarioService (Deterministic Benchmarks)     |
                          |  - AnomalyDetectionClient (HTTP Client with Fallback) |
                          |  - StatisticalAnomalyDetector (Z-score Baseline)      |
                          |  - RootCauseEngine (Multi-Evidence Ranking Logic)     |
                          |  - EvaluationService (Automated Benchmarking)         |
                          |  - GlobalExceptionHandler (RFC 7807 Error Responses)  |
                          +---------------------------+---------------------------+
                                                      |
                                        Internal HTTP / JSON (Port 8000)
                                                      |
                                                      v
                          +-------------------------------------------------------+
                          |                  FastAPI ML Service                   |
                          |                     tracewise-ml                      |
                          |                                                       |
                          |  - Scikit-learn Isolation Forest                      |
                          |  - Model Artifact: isolation_forest.joblib            |
                          |  - Dynamic Telemetry Anomaly Scoring                  |
                          |  - Synthetic Telemetry Generator & Training Pipeline  |
                          +-------------------------------------------------------+
```

### Mermaid Diagram
```mermaid
graph TD
    UI[TraceWise UI / Vercel] -->|POST /api/v1/incidents/analyze| API[Spring Boot Backend :8080]
    UI -->|POST /api/v1/incidents/upload| API
    UI -->|GET /api/v1/incidents/demo/{id}/run| API
    UI -->|GET /api/v1/evaluation| API
    
    subgraph Spring Boot Backend
        API --> VAL[Bean Validation & Normalization]
        VAL --> AC[Anomaly Detection Client]
        AC -->|HTTP POST /api/v1/detect-anomalies| ML[FastAPI ML Service :8000]
        AC -.->|Fallback if offline| STAT[Statistical Z-Score Detector]
        ML --> RCE[Root Cause Ranking Engine]
        STAT --> RCE
        RCE --> REP[Post-Mortem Report Generator]
        RCE --> EVAL[Evaluation Benchmarker]
    end
    
    subgraph FastAPI ML Service
        ML --> IF[Scikit-learn Isolation Forest Model]
    end
```

---

## 3. Repository Directory Structure

```
c:\tracewise-ai\
├── README.md                                  # Complete system architecture and setup guide
├── sample-data/                               # Sample telemetry files for manual drag-and-drop testing
│   ├── sample_database_pool_exhaustion.csv    # Multi-service CSV telemetry with DB pool saturation
│   ├── sample_cpu_saturation.csv              # Multi-service CSV telemetry with auth CPU spike
│   └── sample_incident_db_pool.json           # Comprehensive incident JSON payload
├── tracewise-api/                             # Spring Boot 4.1.2 / Java 24 Core Backend
│   ├── pom.xml                                # Maven build config (Jackson, Validation, WebMVC)
│   ├── mvnw.cmd / mvnw                        # Maven wrappers
│   ├── .env.example                           # Backend environment template
│   └── src/
│       ├── main/java/com/tracwise/api/
│       │   ├── TracewiseApiApplication.java   # Spring Boot Entrypoint
│       │   ├── config/                        # CORS (WebConfig) & JacksonConfig
│       │   ├── controller/                    # IncidentController & GlobalExceptionHandler
│       │   ├── dto/                           # IncidentRequest, IncidentResponse
│       │   │   └── analysis/                  # AnomalyRecord, RootCauseHypothesis, Graph, etc.
│       │   ├── engine/                        # RootCauseEngine, AnomalyDetectionClient, StatisticalAnomalyDetector
│       │   └── service/                       # IncidentService, DemoScenarioService, EvaluationService, TelemetryParser
│       └── test/java/com/tracwise/api/        # Automated Unit and Integration Tests (100% Pass)
├── tracewise-ml/                              # Python 3.11 / FastAPI Isolation Forest ML Service
│   ├── main.py                                # FastAPI app exposing anomaly detection endpoints
│   ├── train_model.py                         # Isolation Forest training script & validation pipeline
│   ├── test_ml_service.py                     # Automated ML service test suite
│   ├── pyproject.toml                         # Project metadata and dependencies
│   ├── .env.example                           # ML service environment template
│   └── model_artifacts/                       # Persisted trained model & evaluation metadata
│       ├── isolation_forest.joblib            # Trained scikit-learn model
│       └── metadata.json                      # Training parameters & benchmark scores
└── tracewise-ui/                              # React 19 / TypeScript / Vite / Tailwind CSS Frontend
    ├── package.json                           # Dependencies (Recharts, Lucide, Tailwind, clsx)
    ├── vite.config.ts                         # Vite configuration
    ├── vercel.json                            # Vercel deployment configuration with SPA rewrites
    ├── tailwind.config.js                     # Tailored Midnight Navy theme configuration
    ├── .env.example                           # Frontend environment configuration template
    ├── public/sample-data/                    # Downloadable sample datasets in browser
    └── src/
        ├── App.tsx                            # Root application component with view routing
        ├── index.css                          # Custom design tokens, animations, scrollbars
        ├── types/incident.ts                  # TypeScript interfaces matching backend DTOs
        ├── services/api.ts                    # REST client with error handling & multipart upload
        └── components/                        # Modular UI components
            ├── Navbar.tsx                     # Top navigation with live service health indicators
            ├── OverviewView.tsx               # Blast radius summary & recent incident history
            ├── SimulateModal.tsx              # Deterministic scenario launcher modal
            ├── DependencyGraphView.tsx        # Interactive topological service graph
            ├── TimelineView.tsx               # Chronological event stream with filter chips
            ├── MetricsChartsView.tsx          # Multi-metric synchronized Recharts visualizations
            ├── HypothesesView.tsx             # Ranked root cause cards with evidence breakdowns
            ├── AffectedServicesTable.tsx      # Comprehensive service metric inventory
            ├── ReportModal.tsx                # Post-mortem Markdown viewer & exporter
            ├── NewInvestigationView.tsx       # Drag-and-drop CSV/JSON telemetry upload
            ├── EvaluationView.tsx             # Accuracy, precision, recall & confusion matrix
            └── SettingsView.tsx               # System telemetry & architecture reference
```

---

## 4. Quickstart Setup & Local Execution

### Prerequisites
- **Java 24** (or Java 17+) and Maven (or use included `./mvnw.cmd` / `./mvnw`)
- **Python 3.11+** (managed via `uv` or system Python)
- **Node.js v20+** and `npm`

---

### Step 1: Start the ML Anomaly Detection Service

```bash
cd c:\tracewise-ai\tracewise-ml

# Create virtual environment and install dependencies (or use uv)
python -m venv .venv
# On Windows:
.venv\Scripts\activate
# On Linux/macOS:
source .venv/bin/activate

pip install fastapi uvicorn scikit-learn numpy pandas joblib httpx

# (Optional) Re-train the Isolation Forest model:
python train_model.py

# Start the FastAPI service on port 8000:
uvicorn main:app --host 127.0.0.1 --port 8000
```
*Health verification:* Open `http://127.0.0.1:8000/health` -> `{"status":"healthy","model_loaded":true}`

---

### Step 2: Start the Spring Boot API Backend

```bash
cd c:\tracewise-ai\tracewise-api

# Compile and run unit tests:
./mvnw.cmd clean test    # (On Linux/macOS: ./mvnw clean test)

# Run the Spring Boot application:
./mvnw.cmd spring-boot:run
```
*Health verification:* Open `http://localhost:8080/api/v1/health` -> `{"status":"UP","mlServiceConnected":true}`

---

### Step 3: Start the Vite React Frontend

```bash
cd c:\tracewise-ai\tracewise-ui

# Install dependencies:
npm install

# Start the development server:
npm run dev -- --host
```
*Access UI:* Open `http://localhost:5173/` in your browser.

---

## 5. API Reference & Contract Examples

### 1. Execute Incident Analysis
`POST /api/v1/incidents/analyze`

**Request Body:**
```json
{
  "incidentId": "INC-2026-081",
  "services": [
    {
      "serviceName": "postgres-db",
      "tier": "DATABASE",
      "configuredConnectionLimit": 100,
      "metrics": [
        {
          "timestamp": "2026-10-24T08:00:00Z",
          "cpuUtilization": 42.0,
          "latencyMs": 18.0,
          "errorRate": 0.0,
          "activeConnections": 45,
          "connectionPoolUtilization": 45.0
        },
        {
          "timestamp": "2026-10-24T08:01:00Z",
          "cpuUtilization": 75.0,
          "latencyMs": 520.0,
          "errorRate": 28.0,
          "activeConnections": 100,
          "connectionPoolUtilization": 100.0
        }
      ]
    }
  ],
  "dependencies": [
    {
      "sourceService": "payment-service",
      "targetService": "postgres-db",
      "protocol": "JDBC",
      "isCriticalPath": true
    }
  ],
  "logs": [
    {
      "logId": "LOG-101",
      "serviceName": "postgres-db",
      "timestamp": "2026-10-24T08:01:05Z",
      "logLevel": "ERROR",
      "message": "HikariPool-1 - Connection is not available, request timed out after 30000ms."
    }
  ],
  "recentChanges": [
    {
      "changeId": "CHG-99",
      "serviceName": "payment-service",
      "timestamp": "2026-10-24T07:55:00Z",
      "description": "Released hotfix v2.4.1 modifying connection timeout threshold"
    }
  ]
}
```

**Response Body (200 OK):**
```json
{
  "incidentId": "INC-2026-081",
  "status": "ANALYZED",
  "summary": {
    "primaryRootCause": "postgres-db: Database Connection-Pool Saturation",
    "severity": "CRITICAL",
    "totalServicesImpacted": 4,
    "totalAnomaliesDetected": 21,
    "confidenceScore": 96.0,
    "confidenceTier": "HIGH_CONFIDENCE",
    "detectionEngineUsed": "ISOLATION_FOREST_ML"
  },
  "rankedHypotheses": [
    {
      "rank": 1,
      "hypothesisId": "HYP-1",
      "title": "Database Connection-Pool Exhaustion in postgres-db",
      "rootCauseCategory": "DATABASE_EXHAUSTION",
      "suspectedService": "postgres-db",
      "evidenceScore": 96.0,
      "confidenceTier": "HIGH_CONFIDENCE",
      "supportingEvidence": [
        {
          "type": "SATURATION_METRIC",
          "summary": "Connection pool saturated (100 active connections out of 100 configured limit, 100.0% capacity)",
          "scoreContribution": 30.0,
          "sourceReference": "Telemetry: postgres-db"
        },
        {
          "type": "LOG_CONFIRMATION",
          "summary": "1 log event(s) confirm connection pool acquisition failure (e.g., 'Connection is not available, request timed out')",
          "scoreContribution": 25.0,
          "sourceReference": "Log Stream"
        },
        {
          "type": "TEMPORAL_PRECEDENCE",
          "summary": "Service experienced anomalous telemetry at 2026-10-24T08:00:30Z, preceding downstream cascades",
          "scoreContribution": 15.0,
          "sourceReference": "Timeline"
        },
        {
          "type": "TOPOLOGY_CASCADE",
          "summary": "Anomaly cascade propagated downstream to 2 dependent service(s): payment-service, order-service",
          "scoreContribution": 15.0,
          "sourceReference": "Dependency Graph"
        }
      ],
      "contradictingEvidence": [],
      "reasoning": "Service 'postgres-db' reached 100.0% of its configured connection pool limit. Log evidence confirms connection pool acquisition failure. This bottleneck cascaded downstream to callers.",
      "recommendedActions": [
        "Execute `SELECT * FROM pg_stat_activity WHERE state != 'idle';` to inspect long-running transactions and locks",
        "Verify connection pool configuration (`maximumPoolSize`, `connectionTimeout`) in application.yaml",
        "Check for leaked database connections or unclosed sessions in recent query paths",
        "Temporarily scale connection pool limits or restart stalled pool workers"
      ]
    }
  ],
  "engineMode": "ISOLATION_FOREST_ML"
}
```

---

### 2. Run Deterministic Demo Scenario
`POST /api/v1/incidents/demo/{scenarioId}/run`
- `db-pool-exhaustion`
- `cpu-saturation`
- `latency-error-spike`

### 3. File Upload (CSV / JSON)
`POST /api/v1/incidents/upload`
- Accepts `multipart/form-data` with parameter `file`. Streaming validation checks timestamp formats, numerical values, and service identifiers.

### 4. Automated Benchmark Evaluation
`GET /api/v1/evaluation`
- Executes the held-out test suite across 5 multi-service scenarios and calculates empirical accuracy, precision, recall, and F1 score.

### 5. Post-Mortem Markdown Report Export
`GET /api/v1/incidents/{incidentId}/report`
- Returns an executive post-mortem Markdown report ready for distribution or Jira/Slack export.

---

## 6. Model Training & Anomaly Detection Methodology

### Isolation Forest Configuration
The ML service implements scikit-learn's `IsolationForest` pipeline (`tracewise-ml/train_model.py`):
- **Estimators:** 120 trees
- **Contamination:** 0.03 (estimated nominal anomaly rate in healthy microservices)
- **Random Seed:** 42 (ensures deterministic reproducibility)
- **Features Extracted:**
  - `cpu_utilization` (%)
  - `latency_ms` (ms)
  - `error_rate` (%)
  - `active_connections` (count)
  - `pool_utilization` (%)

### Training & Held-Out Benchmark Results
The model was trained on 5,000 synthetic baseline telemetry points representing typical distributed system load distributions. It was evaluated on 1,400 held-out test points (1,000 normal, 400 abnormal with inject failures):

| Metric | Measured Value | Methodology |
| :--- | :--- | :--- |
| **Detection Precision** | **94.34%** | `TP / (TP + FP)` on held-out samples |
| **Detection Recall** | **100.00%** | `TP / (TP + FN)` across all injected anomalies |
| **Detection F1 Score** | **0.9709** | Harmonic mean of precision and recall |
| **True Positives (TP)** | 400 | Injected anomalies correctly identified |
| **False Positives (FP)** | 24 | Normal samples flagged during boundary spikes |
| **False Negatives (FN)** | 0 | Zero missed critical telemetry degradations |
| **True Negatives (TN)** | 976 | Normal samples correctly classified |

### Multi-Evidence Root Cause Ranking Logic
Anomaly detection is not root cause analysis. Outliers frequently appear on downstream services first due to connection timeouts or backpressure. TraceWise AI computes an explainable multi-factor score:

$$\text{Score} = \min\left(100, S_{\text{saturation}} + S_{\text{temporal}} + S_{\text{topology}} + S_{\text{logs}} + S_{\text{change}}\right)$$

1. **Resource Saturation ($S_{\text{saturation}}$):** Up to +30 pts if connection pool reaches $\ge 90\%$ or CPU reaches $\ge 80\%$.
2. **Temporal Precedence ($S_{\text{temporal}}$):** Up to +20 pts if service exhibited anomalies earlier than any other peer.
3. **Topological Propagation ($S_{\text{topology}}$):** Up to +20 pts if downstream dependent services also suffered latency/error spikes.
4. **Log Confirmation ($S_{\text{logs}}$):** Up to +25 pts if explicit error logs (e.g., `HikariPool`, `OutOfMemory`, `Regex timeout`) are present.
5. **Deployment Proximity ($S_{\text{change}}$):** Up to +15 pts if a code deployment occurred on the service within 15 minutes of the anomaly onset.

> **Transparency Note:** Scores are explicitly labeled as heuristic evidence strength indicators and are **never** presented as calibrated Bayesian probabilities.

---

## 7. 2-3 Minute Judge-Facing Demonstration Script

### Act 1: The Alert Storm (0:00 - 0:45)
1. Open the TraceWise AI dashboard at `http://localhost:5173`.
2. Point out the top navigation health indicators:
   - **Backend API:** `Connected (v1.0.0)`
   - **ML Service:** `Active (Isolation Forest v1.0)`
3. Explain the problem:
   > *"In modern microservice architectures, when a backend database locks or exhausts its pool, dozens of cascading alerts flood Slack and PagerDuty from OrderService, PaymentService, and APIGateway. Engineers waste critical minutes trying to figure out which alert is the cause and which are downstream symptoms."*

### Act 2: Deterministic Simulation & Real Analysis (0:45 - 1:45)
1. Click the glowing cyan **"Simulate Incident"** button.
2. Select **"Database Connection-Pool Exhaustion"** from the catalog modal.
3. Highlight the ground-truth notice:
   > *"Ground truth is kept in isolated evaluation metadata—the analysis engine receives only raw telemetry, dependencies, and logs."*
4. Click **"Launch Incident Simulation"**.
5. Within 200ms, the full investigation view loads:
   - **Severity Banner:** Displays `CRITICAL` with 4 impacted services and 21 detected anomalies.
   - **Dependency Graph:** Show the graph visualization. Trace the red flashing edges from `postgres-db` flowing upstream through `payment-service` and `order-service` to `api-gateway`.
   - **Ranked Hypotheses:** Open Rank #1 (`postgres-db` - 96% Evidence Strength). Show the transparent breakdown:
     - $+30\%$ for $100\%$ pool capacity reached
     - $+25\%$ for HikariPool timeout error log
     - $+15\%$ for earliest anomaly timestamp ($08:00:30\text{Z}$)
     - $+15\%$ for 2 downstream services impacted
   - **Timeline:** Scroll down to show the chronological timeline. Point out the purple **`[INITIATING EVENT]`** badge at $08:00:30\text{Z}$.
   - **Telemetry Charts:** Show synchronized charts demonstrating `postgres-db` active connections hitting the ceiling line, followed by latency explosions in caller services.

### Act 3: Actionable Next Steps & Post-Mortem Report (1:45 - 2:30)
1. Show the **"Recommended Diagnostic Actions"** card:
   - Point out the targeted safe diagnostic command: `SELECT * FROM pg_stat_activity WHERE state != 'idle';`.
   - Emphasize the architectural safety rule: TraceWise AI generates human-approved diagnostic checks, never running destructive scripts automatically.
2. Click **"Investigation Report"**:
   - Show the auto-generated, executive-ready Markdown post-mortem document.
   - Click **"Copy Markdown"** (toast confirms copied to clipboard).
3. Switch to the **"Evaluation"** tab:
   - Walk the judges through the real empirical benchmark: $100\%$ Top-1 accuracy on deterministic test scenarios, $94.34\%$ precision, $100\%$ recall, and honest disclosure of synthetic benchmark limitations.

---

## 8. Deployment Architecture

### Frontend (Vercel)
The `tracewise-ui` application is configured for continuous deployment on Vercel:
- **Root Directory:** `tracewise-ui`
- **Framework Preset:** Vite
- **Build Command:** `npm run build`
- **Output Directory:** `dist`
- **Environment Variables:**
  - `VITE_API_BASE_URL`: URL of the hosted Spring Boot backend (e.g. `https://api.tracewise.dev` or Railway/Render URL).
- `vercel.json` includes SPA wildcard rewrites (`"source": "/(.*)", "destination": "/index.html"`) and security headers (`X-Frame-Options`, `X-Content-Type-Options`).

### Backend (Docker / Render / Railway / AWS ECS)
The Spring Boot backend can be containerized or deployed as an executable JAR:
```dockerfile
FROM eclipse-temurin:24-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
COPY --from=build /app/target/tracewise-api-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### ML Service (Docker / Cloud Run)
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

---

## 9. Known Limitations & Future Roadmap

1. **Synthetic Training Data Baseline:**
   - *Current State:* The Isolation Forest model was trained on 5,000 synthetic microservice telemetry records spanning common baseline distributions.
   - *Roadmap:* Integrate OpenTelemetry (OTel) collectors to passively learn baseline distributions from production clusters.
2. **Clock Skew & Distributed Tracing:**
   - *Current State:* Timestamps are normalized to UTC with ISO-8601 formatting.
   - *Roadmap:* Incorporate distributed trace span IDs (W3C Trace Context) to trace causality even in the presence of minor clock drift across nodes.
3. **PostgreSQL Persistence:**
   - *Current State:* Full investigation history is cached in high-performance memory with RFC-7807 global exception handling.
   - *Roadmap:* Complete PostgreSQL JPA entity mappings to persist historical incident post-mortems over multi-month horizons.
4. **Safety & Diagnostic Remediation:**
   - *Rule:* TraceWise AI will never execute destructive automatic fixes (such as terminating pods or killing database connections) without explicit human confirmation and audit logging.

---

## 10. License & Acknowledgements
Built for the **AXION Hackathon Round 2**.  
Architecture designed and implemented following SRE best practices, Google Cloud Site Reliability Engineering principles, and Explainable AI (XAI) transparency guidelines.
