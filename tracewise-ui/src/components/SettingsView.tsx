import React from 'react';
import { SystemHealthResponse } from '../types/incident';
import {
  Server,
  Cpu,
  Layers,
  Shield,
  FileCode,
  CheckCircle2,
  AlertTriangle
} from 'lucide-react';

interface SettingsViewProps {
  systemHealth: SystemHealthResponse | null;
}

export const SettingsView: React.FC<SettingsViewProps> = ({ systemHealth }) => {
  return (
    <div className="max-w-5xl mx-auto space-y-6">
      {/* Header */}
      <div>
        <h2 className="text-xl font-bold text-white">System Architecture & Diagnostic Settings</h2>
        <p className="text-xs text-slate-400 mt-1">
          Internal configuration, service health, and explainability methodology specifications
        </p>
      </div>

      {/* Connection & Engine Status Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Backend Spring Boot Service */}
        <div className="p-5 rounded-2xl bg-midnight-900 border border-midnight-800 shadow-xl space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <div className="p-2 rounded-xl bg-midnight-950 border border-midnight-800">
                <Server className="w-5 h-5 text-brand-cyan" />
              </div>
              <div>
                <h3 className="font-semibold text-white">Spring Boot REST API</h3>
                <span className="text-[11px] text-slate-400 font-mono">Port 8080 • Java 24 / Spring Boot 4.1.2</span>
              </div>
            </div>
            <span className="text-xs px-2.5 py-1 rounded-full bg-brand-emerald/10 text-brand-emerald border border-brand-emerald/30 font-semibold flex items-center gap-1 font-mono">
              <CheckCircle2 className="w-3.5 h-3.5" /> UP
            </span>
          </div>

          <div className="pt-2 border-t border-midnight-800/80 text-xs text-slate-400 space-y-1.5 font-mono">
            <div className="flex justify-between">
              <span>API Version:</span>
              <span className="text-slate-200">{systemHealth?.apiVersion || '1.0.0'}</span>
            </div>
            <div className="flex justify-between">
              <span>Engine Version:</span>
              <span className="text-slate-200">{systemHealth?.rootCauseEngineVersion || 'TraceWise-MultiEvidence-Ranking-v1.2'}</span>
            </div>
            <div className="flex justify-between">
              <span>Investigations Cached:</span>
              <span className="text-slate-200">{systemHealth?.totalInvestigationsCompleted ?? 1}</span>
            </div>
          </div>
        </div>

        {/* Python ML Anomaly Service */}
        <div className="p-5 rounded-2xl bg-midnight-900 border border-midnight-800 shadow-xl space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <div className="p-2 rounded-xl bg-midnight-950 border border-midnight-800">
                <Cpu className="w-5 h-5 text-brand-turquoise" />
              </div>
              <div>
                <h3 className="font-semibold text-white">ML Anomaly Detection Service</h3>
                <span className="text-[11px] text-slate-400 font-mono">Port 8000 • FastAPI / scikit-learn</span>
              </div>
            </div>
            <span className={`text-xs px-2.5 py-1 rounded-full font-semibold flex items-center gap-1 font-mono ${
              systemHealth?.mlServiceConnected
                ? 'bg-brand-emerald/10 text-brand-emerald border border-brand-emerald/30'
                : 'bg-brand-amber/10 text-brand-amber border border-brand-amber/30'
            }`}>
              {systemHealth?.mlServiceConnected ? (
                <>
                  <CheckCircle2 className="w-3.5 h-3.5" /> IsolationForest Online
                </>
              ) : (
                <>
                  <AlertTriangle className="w-3.5 h-3.5" /> Statistical Fallback Active
                </>
              )}
            </span>
          </div>

          <div className="pt-2 border-t border-midnight-800/80 text-xs text-slate-400 space-y-1.5 font-mono">
            <div className="flex justify-between">
              <span>ML Endpoint:</span>
              <span className="text-slate-200">{systemHealth?.mlServiceUrl || 'http://localhost:8000'}</span>
            </div>
            <div className="flex justify-between">
              <span>Model Algorithm:</span>
              <span className="text-slate-200">IsolationForest (120 Estimators, Contamination 0.03)</span>
            </div>
            <div className="flex justify-between">
              <span>Active Model Version:</span>
              <span className="text-slate-200">{systemHealth?.mlModelVersion || 'IsolationForest-v1.0'}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Architecture Overview Diagram */}
      <div className="p-6 rounded-2xl bg-midnight-900 border border-midnight-800 shadow-xl space-y-4">
        <h3 className="font-semibold text-white flex items-center gap-2">
          <Layers className="w-4 h-4 text-brand-cyan" />
          <span>System Architecture & Telemetry Pipeline</span>
        </h3>

        <div className="p-4 rounded-xl bg-midnight-950 border border-midnight-800 font-mono text-xs text-slate-300 leading-relaxed overflow-x-auto">
          <div className="flex flex-col md:flex-row items-center justify-between gap-4 text-center">
            <div className="p-3 rounded-lg bg-midnight-850 border border-midnight-700 w-full md:w-1/4">
              <span className="text-brand-sky font-bold block mb-1">1. Frontend Client</span>
              <span className="text-slate-400 text-[11px]">Vite + React + Tailwind</span>
              <span className="text-slate-500 text-[10px] block mt-1">Vercel Deployment</span>
            </div>

            <span className="text-brand-cyan hidden md:block">── REST / JSON ──▶</span>

            <div className="p-3 rounded-lg bg-midnight-850 border border-brand-cyan/40 w-full md:w-1/4">
              <span className="text-brand-cyan font-bold block mb-1">2. Spring Boot API</span>
              <span className="text-slate-400 text-[11px]">Normalization & Dispatch</span>
              <span className="text-slate-500 text-[10px] block mt-1">Multi-Evidence Engine</span>
            </div>

            <span className="text-brand-cyan hidden md:block">── HTTP 8000 ──▶</span>

            <div className="p-3 rounded-lg bg-midnight-850 border border-brand-turquoise/40 w-full md:w-1/4">
              <span className="text-brand-turquoise font-bold block mb-1">3. FastAPI ML</span>
              <span className="text-slate-400 text-[11px]">scikit-learn Isolation Forest</span>
              <span className="text-slate-500 text-[10px] block mt-1">Telemetry Deviations</span>
            </div>
          </div>
        </div>
      </div>

      {/* Input Schema Documentation */}
      <div className="p-6 rounded-2xl bg-midnight-900 border border-midnight-800 shadow-xl space-y-3">
        <h3 className="font-semibold text-white flex items-center gap-2">
          <FileCode className="w-4 h-4 text-brand-sky" />
          <span>Normalized Incident Schema Contract</span>
        </h3>
        <p className="text-xs text-slate-400">
          The platform accepts normalized multi-service telemetry in both JSON and CSV formats:
        </p>

        <div className="p-4 rounded-xl bg-midnight-950 border border-midnight-800 font-mono text-xs text-slate-300">
          <pre className="text-slate-300 overflow-x-auto whitespace-pre">
{`{
  "incidentId": "INC-2026-EXAMPLE",
  "services": [
    {
      "name": "service-name",            // string (required)
      "timestamp": "2026-10-24T10:00:00", // ISO-8601 UTC (required)
      "cpuPercent": 45.2,                // number >= 0 [0 - 100]
      "latencyMs": 120.0,                // number >= 0 (milliseconds)
      "errorRate": 0.05,                 // number [0.0 - 1.0] (5% errors)
      "activeConnections": 88,           // integer >= 0
      "connectionPoolLimit": 100         // integer >= 0
    }
  ],
  "logs": [
    {
      "timestamp": "2026-10-24T10:00:15",
      "service": "service-name",
      "level": "ERROR",                  // INFO | WARN | ERROR | FATAL
      "message": "Connection acquisition timeout"
    }
  ],
  "dependencies": [
    { "from": "order-service", "to": "postgres-db" } // from calls to
  ],
  "recentChanges": [
    { "timestamp": "2026-10-24T09:45:00", "service": "postgres-db", "description": "Release v2.1" }
  ]
}`}
          </pre>
        </div>
      </div>

      {/* Safety & Operational Disclaimer */}
      <div className="p-5 rounded-2xl bg-midnight-850/90 border border-midnight-800 flex items-start gap-3.5 text-xs text-slate-400">
        <Shield className="w-5 h-5 text-brand-cyan shrink-0 mt-0.5" />
        <div className="space-y-1">
          <span className="text-white font-semibold">Security & Operational Safety Notice:</span>
          <p className="leading-relaxed">
            TraceWise AI operates strictly as an observational advisory engine. Recommended diagnostic checks (such as thread dumps, query locks, and replica scaling) are presented as actionable suggestions requiring explicit human review and approval. The platform performs zero automated writes, deployments, or destructive actions against user environments.
          </p>
        </div>
      </div>
    </div>
  );
};
