import React from 'react';
import { IncidentResponse } from '../types/incident';
import {
  Activity,
  AlertTriangle,
  PlayCircle,
  UploadCloud,
  BarChart3,
  ArrowRight,
  ShieldAlert,
  CheckCircle2,
  Clock,
  Radio,
  Sparkles
} from 'lucide-react';

interface OverviewViewProps {
  recentIncidents: IncidentResponse[];
  currentIncident: IncidentResponse | null;
  onOpenSimulate: () => void;
  onNavigateTab: (tab: string) => void;
  onSelectIncident: (incident: IncidentResponse) => void;
}

export const OverviewView: React.FC<OverviewViewProps> = ({
  recentIncidents,
  currentIncident,
  onOpenSimulate,
  onNavigateTab,
  onSelectIncident
}) => {
  const activeIncident = currentIncident || (recentIncidents.length > 0 ? recentIncidents[0] : null);

  return (
    <div className="space-y-8">
      {/* Hero Banner */}
      <div className="relative p-8 rounded-3xl bg-gradient-to-br from-midnight-900 via-midnight-850 to-midnight-950 border border-midnight-750/80 shadow-2xl overflow-hidden">
        {/* Glow backdrop */}
        <div className="absolute top-0 right-0 w-96 h-96 bg-brand-cyan/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute bottom-0 left-1/3 w-80 h-80 bg-brand-rose/5 rounded-full blur-3xl pointer-events-none" />

        <div className="relative z-10 max-w-3xl space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-brand-cyan/10 border border-brand-cyan/30 text-brand-cyan text-xs font-mono font-medium">
            <Sparkles className="w-3.5 h-3.5" />
            <span>Explainable Distributed Failure Analysis</span>
          </div>

          <h1 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight leading-tight">
            From Scattered Alerts to <span className="text-transparent bg-clip-text bg-gradient-to-r from-brand-cyan via-brand-sky to-brand-turquoise">Clear Answers.</span>
          </h1>

          <p className="text-slate-300 text-sm sm:text-base leading-relaxed">
            TraceWise AI correlates multi-service telemetry deviations, structured error logs, service topologies, and recent deployments to pinpoint the root cause of cascading failures with transparent, explainable evidence.
          </p>

          <div className="pt-2 flex flex-wrap items-center gap-3">
            <button
              onClick={onOpenSimulate}
              className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-brand-cyan to-brand-turquoise hover:from-cyan-400 hover:to-teal-400 text-midnight-950 font-bold text-sm shadow-lg shadow-brand-cyan/25 transition-all transform hover:-translate-y-0.5 active:translate-y-0"
            >
              <PlayCircle className="w-4 h-4 stroke-[2.5]" />
              <span>Simulate Incident</span>
            </button>

            <button
              onClick={() => onNavigateTab('new')}
              className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-midnight-800 hover:bg-midnight-750 text-white font-semibold text-sm border border-midnight-700 shadow-md transition-all"
            >
              <UploadCloud className="w-4 h-4 text-brand-sky" />
              <span>Upload Telemetry File</span>
            </button>

            <button
              onClick={() => onNavigateTab('evaluation')}
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-midnight-900/80 hover:bg-midnight-800 text-slate-300 font-medium text-xs border border-midnight-800 transition-all"
            >
              <BarChart3 className="w-4 h-4 text-brand-cyan" />
              <span>View Benchmarks</span>
            </button>
          </div>
        </div>
      </div>

      {/* Metric Cards Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Active Incidents */}
        <div className="p-5 rounded-2xl bg-midnight-900 border border-midnight-800 shadow-xl">
          <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
            <span>Investigated Incidents</span>
            <Activity className="w-4 h-4 text-brand-cyan" />
          </div>
          <div className="text-3xl font-extrabold text-white font-mono">
            {recentIncidents.length > 0 ? recentIncidents.length : 1}
          </div>
          <p className="text-[11px] text-slate-400 mt-1">
            Persisted in session memory
          </p>
        </div>

        {/* Current Incident Blast Radius */}
        <div className="p-5 rounded-2xl bg-midnight-900 border border-midnight-800 shadow-xl">
          <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
            <span>Cascading Blast Radius</span>
            <ShieldAlert className="w-4 h-4 text-brand-rose" />
          </div>
          <div className="text-3xl font-extrabold text-white font-mono">
            {activeIncident?.summary ? `${activeIncident.summary.blastRadiusPercent}%` : '0%'}
          </div>
          <p className="text-[11px] text-brand-rose mt-1 font-mono font-medium">
            {activeIncident?.summary ? `${activeIncident.affectedServices.filter(s => s.status !== 'HEALTHY').length} of ${activeIncident.serviceCount} services impacted` : 'No incident active'}
          </p>
        </div>

        {/* Top Hypothesis Evidence Score */}
        <div className="p-5 rounded-2xl bg-midnight-900 border border-midnight-800 shadow-xl">
          <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
            <span>Primary Cause Evidence</span>
            <Sparkles className="w-4 h-4 text-brand-turquoise" />
          </div>
          <div className="text-3xl font-extrabold text-brand-cyan font-mono">
            {activeIncident?.hypotheses && activeIncident.hypotheses.length > 0
              ? `${activeIncident.hypotheses[0].evidenceScore.toFixed(1)}%`
              : activeIncident?.summary?.status === 'NO_INCIDENT_DETECTED' ? 'Nominal' : 'N/A'}
          </div>
          <p className="text-[11px] text-slate-400 mt-1 line-clamp-1 font-mono">
            {activeIncident?.hypotheses && activeIncident.hypotheses.length > 0
              ? activeIncident.hypotheses[0].title
              : (activeIncident?.summary?.primaryRootCause ?? 'No Incident Active')}
          </p>
        </div>

        {/* Anomaly Detection Engine */}
        <div className="p-5 rounded-2xl bg-midnight-900 border border-midnight-800 shadow-xl">
          <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
            <span>Detection Engine</span>
            <Radio className="w-4 h-4 text-brand-emerald" />
          </div>
          <div className="text-xl font-bold text-white font-mono mt-1">
            {activeIncident?.engineMode === 'ISOLATION_FOREST_ML' ? 'Isolation Forest' : 'Hybrid Pipeline'}
          </div>
          <p className="text-[11px] text-brand-emerald mt-1 flex items-center gap-1 font-medium font-mono">
            <CheckCircle2 className="w-3 h-3" />
            <span>Active & Calibrated</span>
          </p>
        </div>
      </div>

      {/* Recent Investigations List */}
      <div className="bg-midnight-900 border border-midnight-800 rounded-2xl p-6 shadow-xl space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="font-semibold text-white">Recent Incident Investigations</h3>
            <p className="text-xs text-slate-400 mt-0.5">Click any investigation to inspect root cause evidence and topology</p>
          </div>
          <span className="text-xs text-slate-400 font-mono">
            {recentIncidents.length} recorded
          </span>
        </div>

        {recentIncidents.length === 0 ? (
          <div className="p-8 text-center bg-midnight-950/60 border border-midnight-800/80 rounded-xl space-y-3">
            <AlertTriangle className="w-8 h-8 text-brand-amber mx-auto" />
            <p className="text-sm text-slate-300 font-medium">No recent incidents analyzed in this session.</p>
            <button
              onClick={onOpenSimulate}
              className="text-xs px-4 py-2 rounded-xl bg-brand-cyan/20 text-brand-cyan hover:bg-brand-cyan/30 font-semibold border border-brand-cyan/30 transition-colors"
            >
              Simulate Your First Incident
            </button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs font-mono">
              <thead className="bg-midnight-950/80 text-slate-400 border-b border-midnight-800">
                <tr>
                  <th className="px-4 py-3 font-semibold uppercase">Incident ID</th>
                  <th className="px-4 py-3 font-semibold uppercase">Severity</th>
                  <th className="px-4 py-3 font-semibold uppercase">Initiating Root Cause</th>
                  <th className="px-4 py-3 font-semibold uppercase">Origin Service</th>
                  <th className="px-4 py-3 font-semibold uppercase">Blast Radius</th>
                  <th className="px-4 py-3 font-semibold uppercase">Engine</th>
                  <th className="px-4 py-3 font-semibold uppercase text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-midnight-800/60">
                {recentIncidents.map((inc) => (
                  <tr
                    key={inc.incidentId}
                    onClick={() => {
                      onSelectIncident(inc);
                      onNavigateTab('investigation');
                    }}
                    className="hover:bg-midnight-850/60 cursor-pointer transition-colors"
                  >
                    <td className="px-4 py-3.5 font-bold text-white flex items-center gap-2">
                      <span className="w-2 h-2 rounded-full bg-brand-cyan" />
                      <span>{inc.incidentId}</span>
                    </td>
                    <td className="px-4 py-3.5">
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                        inc.summary?.severity === 'CRITICAL'
                          ? 'bg-brand-rose/20 text-brand-rose border border-brand-rose/30'
                          : 'bg-brand-amber/20 text-brand-amber border border-brand-amber/30'
                      }`}>
                        {inc.summary?.severity || 'HIGH'}
                      </span>
                    </td>
                    <td className="px-4 py-3.5 text-slate-200">
                      {inc.summary?.primaryRootCause ?? 'N/A'}
                    </td>
                    <td className="px-4 py-3.5 text-brand-sky font-bold">
                      {inc.summary?.primaryAffectedService ?? 'NONE'}
                    </td>
                    <td className="px-4 py-3.5">
                      <span className="text-brand-rose font-bold">
                        {inc.summary?.blastRadiusPercent ?? 0}%
                      </span>
                    </td>
                    <td className="px-4 py-3.5 text-slate-400">
                      {inc.engineMode}
                    </td>
                    <td className="px-4 py-3.5 text-right">
                      <span className="text-xs text-brand-cyan hover:underline inline-flex items-center gap-1">
                        <span>Investigate</span>
                        <ArrowRight className="w-3 h-3" />
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
