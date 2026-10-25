export interface ServiceMetric {
  name: string;
  timestamp: string;
  cpuPercent: number;
  latencyMs: number;
  errorRate: number;
  activeConnections: number;
  connectionPoolLimit: number;
}

export interface LogEvent {
  timestamp: string;
  service: string;
  level: string;
  message: string;
}

export interface Dependency {
  from: string;
  to: string;
}

export interface ChangeEvent {
  timestamp: string;
  service: string;
  description: string;
}

export interface IncidentRequest {
  incidentId: string;
  services: ServiceMetric[];
  logs?: LogEvent[];
  dependencies?: Dependency[];
  recentChanges?: ChangeEvent[];
}

export interface IncidentSummary {
  title: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  status: string;
  detectedAt: string;
  primaryRootCause: string;
  primaryAffectedService: string;
  blastRadiusPercent: number;
  cascadeDuration: string;
  executiveSummary: string;
}

export interface AnomalyRecord {
  id: string;
  service: string;
  timestamp: string;
  metricName: string;
  anomalyScore: number;
  severity: 'CRITICAL' | 'WARNING' | 'INFO';
  observedValue: number;
  expectedBaseline: number;
  unit: string;
  description: string;
  featureContributions?: Record<string, number>;
  detectionMethod: string;
}

export interface TimelineEvent {
  id: string;
  timestamp: string;
  eventType: 'CHANGE' | 'METRIC_ANOMALY' | 'ERROR_LOG' | 'WARN_LOG' | 'ALERT';
  service: string;
  summary: string;
  details: string;
  severity: 'CRITICAL' | 'WARNING' | 'INFO';
  isInitiatingEvent: boolean;
}

export interface GraphNode {
  id: string;
  name: string;
  tier: 'GATEWAY' | 'SERVICE' | 'DATABASE' | 'CACHE' | 'EXTERNAL';
  status: 'HEALTHY' | 'DEGRADED' | 'FAILING';
  anomalyScore: number;
  isRootCause: boolean;
  incomingErrors: number;
}

export interface GraphEdge {
  from: string;
  to: string;
  status: 'NORMAL' | 'DEGRADED' | 'BLOCKED';
  errorRate: number;
  latencyMs: number;
  isCriticalPath: boolean;
}

export interface DependencyGraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export interface EvidenceItem {
  type: string;
  summary: string;
  scoreContribution: number;
  sourceReference: string;
}

export interface RootCauseHypothesis {
  rank: number;
  hypothesisId: string;
  title: string;
  rootCauseCategory: string;
  suspectedService: string;
  evidenceScore: number;
  confidenceTier: 'HIGH_CONFIDENCE' | 'MODERATE_CONFIDENCE' | 'LOW_CONFIDENCE';
  supportingEvidence: EvidenceItem[];
  contradictingEvidence: EvidenceItem[];
  reasoning: string;
  recommendedActions: string[];
}

export interface ServiceHealthSummary {
  serviceName: string;
  status: 'HEALTHY' | 'DEGRADED' | 'FAILING';
  peakCpuPercent: number;
  peakLatencyMs: number;
  peakErrorRate: number;
  maxActiveConnections: number;
  connectionPoolLimit: number;
  errorCount: number;
  anomalyCount: number;
}

export interface DiagnosticAction {
  id: string;
  title: string;
  commandOrCheck: string;
  rationale: string;
  priority: 'P1' | 'P2' | 'P3';
  targetedService: string;
}

export interface IncidentResponse {
  incidentId: string;
  status: string;
  message: string;
  serviceCount: number;
  logCount: number;
  dependencyCount: number;
  changeCount: number;
  summary: IncidentSummary;
  anomalies: AnomalyRecord[];
  timeline: TimelineEvent[];
  dependencyGraph: DependencyGraph;
  hypotheses: RootCauseHypothesis[];
  affectedServices: ServiceHealthSummary[];
  recommendedActions: DiagnosticAction[];
  engineMode: string;
}

export interface ScenarioEvaluationDetail {
  scenarioId: string;
  scenarioName: string;
  groundTruthRootCause: string;
  predictedTop1RootCause: string;
  top1EvidenceScore: number;
  isTop1Correct: boolean;
  isTop3Correct: boolean;
  anomalyCountDetected: number;
  notes: string;
}

export interface EvaluationResult {
  evaluationSuiteVersion: string;
  evaluatedAt: string;
  totalTestScenarios: number;
  detectionPrecision: number;
  detectionRecall: number;
  detectionF1Score: number;
  truePositives: number;
  falsePositives: number;
  falseNegatives: number;
  trueNegatives: number;
  top1RootCauseAccuracy: number;
  top3RootCauseAccuracy: number;
  scenarioDetails: ScenarioEvaluationDetail[];
  evaluationLimitations: Record<string, string>;
}

export interface SystemHealthResponse {
  status: string;
  apiVersion: string;
  timestamp: string;
  mlServiceConnected: boolean;
  mlServiceUrl: string;
  mlModelVersion: string;
  rootCauseEngineVersion: string;
  totalInvestigationsCompleted: number;
}

export interface ScenarioCatalogItem {
  id: string;
  title: string;
  description: string;
  severity: string;
  affectedTopology: string;
  groundTruthCategory: string;
  groundTruthService: string;
}
