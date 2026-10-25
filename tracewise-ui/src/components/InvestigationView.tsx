import React, { useState } from 'react';
import {
  IncidentResponse
} from '../types/incident';
import { HypothesesView } from './HypothesesView';
import { DependencyGraphView } from './DependencyGraphView';
import { TimelineView } from './TimelineView';
import { MetricsChartsView } from './MetricsChartsView';
import { AffectedServicesTable } from './AffectedServicesTable';
import {
  ShieldAlert,
  Clock,
  Radio,
  FileText,
  Layers,
  Activity,
  AlertTriangle,
  GitBranch,
  Terminal,
  Share2
} from 'lucide-react';

interface InvestigationViewProps {
  incident: IncidentResponse | null;
  onOpenReport: () => void;
  onOpenSimulate: () => void;
}

export const InvestigationView: React.FC<InvestigationViewProps> = ({
  incident,
  onOpenReport,
  onOpenSimulate
}) => {
  const [activeSubTab, setActiveSubTab] = useState<'hypotheses' | 'topology' | 'timeline' | 'metrics' | 'services'>('hypotheses');
  const [selectedServiceForChart, setSelectedServiceForChart] = useState<string | undefined>(undefined);

  if (!incident) {
    return (
      <div className="p-12 text-center bg-midnight-900 border border-midnight-800 rounded-2xl space-y-4 max-w-xl mx-auto shadow-2xl">
        <AlertTriangle className="w-12 h-12 text-brand-amber mx-auto" />
        <h3 className="text-lg font-bold text-white">No Incident Under Active Investigation</h3>
        <p className="text-xs text-slate-400 leading-relaxed">
          Launch a simulated failure scenario or upload telemetry to begin investigating cascading failure dynamics.
        </p>
        <button
          onClick={onOpenSimulate}
          className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-brand-cyan to-brand-turquoise text-midnight-950 font-bold text-xs shadow-lg shadow-brand-cyan/20"
        >
          Simulate an Incident
        </button>
      </div>
    );
  }

  const { summary, hypotheses, dependencyGraph, timeline, affectedServices, engineMode } = incident;

  return (
    <div className="space-y-6">
      {/* Incident Header Card */}
      <div className="p-6 rounded-2xl bg-midnight-900 border border-midnight-800 shadow-2xl space-y-4">
        {/* Top Badges */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-xs font-bold text-white bg-midnight-800 px-3 py-1 rounded-lg border border-midnight-700">
              #{incident.incidentId}
            </span>

            <span className={`text-xs px-2.5 py-1 rounded-full font-bold uppercase tracking-wider ${
              summary?.severity === 'CRITICAL'
                ? 'bg-brand-rose/20 text-brand-rose border border-brand-rose/40'
                : 'bg-brand-amber/20 text-brand-amber border border-brand-amber/40'
            }`}>
              {summary?.severity || 'HIGH'}
            </span>

            <span className={`text-xs px-2.5 py-1 rounded-full font-semibold font-mono ${
              summary?.status === 'NO_INCIDENT_DETECTED'
                ? 'bg-brand-emerald/10 text-brand-emerald border border-brand-emerald/30'
                : 'bg-brand-cyan/10 text-brand-cyan border border-brand-cyan/30'
            }`}>
              {summary?.status || 'ROOT_CAUSE_IDENTIFIED'}
            </span>

            <span className="text-xs px-2.5 py-1 rounded-full bg-midnight-850 text-slate-400 font-mono border border-midnight-800">
              Engine: <span className="text-brand-cyan">{engineMode}</span>
            </span>
          </div>

          {/* Export Report Action */}
          <button
            onClick={onOpenReport}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-midnight-800 hover:bg-midnight-750 text-white font-semibold text-xs border border-midnight-700 shadow-md transition-all"
          >
            <FileText className="w-4 h-4 text-brand-cyan" />
            <span>Export Post-Mortem Report</span>
          </button>
        </div>

        {/* Primary Root Cause Banner */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 pt-2">
          <div className="lg:col-span-2 space-y-1.5">
            <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
              Identified Root-Cause Origin:
            </span>
            <h2 className="text-2xl font-bold text-white tracking-tight">
              {summary?.primaryRootCause ?? 'Unknown'}
            </h2>
            <p className="text-xs text-slate-300 leading-relaxed">
              {summary?.executiveSummary ?? 'Diagnostic investigation completed.'}
            </p>
          </div>

          {/* Incident Telemetry Stats */}
          <div className="flex flex-wrap lg:flex-nowrap items-center justify-between gap-4 p-4 rounded-xl bg-midnight-950/70 border border-midnight-800">
            <div>
              <span className="text-[10px] uppercase font-mono text-slate-500 block">Initiating Service</span>
              <span className="text-sm font-bold text-brand-sky font-mono mt-0.5 block">
                {summary?.primaryAffectedService ?? 'NONE'}
              </span>
            </div>

            <div className="w-px h-8 bg-midnight-800 hidden sm:block" />

            <div>
              <span className="text-[10px] uppercase font-mono text-slate-500 block">Cascade Duration</span>
              <span className="text-sm font-bold text-white font-mono mt-0.5 block flex items-center gap-1">
                <Clock className="w-3.5 h-3.5 text-brand-amber" />
                <span>{summary?.cascadeDuration ?? 'N/A'}</span>
              </span>
            </div>

            <div className="w-px h-8 bg-midnight-800 hidden sm:block" />

            <div>
              <span className="text-[10px] uppercase font-mono text-slate-500 block">Blast Radius</span>
              <span className="text-sm font-bold text-brand-rose font-mono mt-0.5 block">
                {summary?.blastRadiusPercent ?? 0}%
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Investigation Sub-navigation Tabs */}
      <div className="flex flex-wrap items-center gap-2 border-b border-midnight-800 pb-3">
        <button
          onClick={() => setActiveSubTab('hypotheses')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
            activeSubTab === 'hypotheses'
              ? 'bg-midnight-800 text-brand-cyan border border-brand-cyan/30 shadow'
              : 'text-slate-400 hover:text-white hover:bg-midnight-900'
          }`}
        >
          <ShieldAlert className="w-3.5 h-3.5 text-brand-cyan" />
          <span>Ranked Hypotheses ({hypotheses?.length || 0})</span>
        </button>

        <button
          onClick={() => setActiveSubTab('topology')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
            activeSubTab === 'topology'
              ? 'bg-midnight-800 text-brand-cyan border border-brand-cyan/30 shadow'
              : 'text-slate-400 hover:text-white hover:bg-midnight-900'
          }`}
        >
          <GitBranch className="w-3.5 h-3.5 text-brand-turquoise" />
          <span>Dependency Cascade Graph</span>
        </button>

        <button
          onClick={() => setActiveSubTab('timeline')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
            activeSubTab === 'timeline'
              ? 'bg-midnight-800 text-brand-cyan border border-brand-cyan/30 shadow'
              : 'text-slate-400 hover:text-white hover:bg-midnight-900'
          }`}
        >
          <Clock className="w-3.5 h-3.5 text-brand-amber" />
          <span>Unified Timeline ({timeline?.length || 0})</span>
        </button>

        <button
          onClick={() => setActiveSubTab('metrics')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
            activeSubTab === 'metrics'
              ? 'bg-midnight-800 text-brand-cyan border border-brand-cyan/30 shadow'
              : 'text-slate-400 hover:text-white hover:bg-midnight-900'
          }`}
        >
          <Activity className="w-3.5 h-3.5 text-brand-sky" />
          <span>Telemetry Metrics</span>
        </button>

        <button
          onClick={() => setActiveSubTab('services')}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all ${
            activeSubTab === 'services'
              ? 'bg-midnight-800 text-brand-cyan border border-brand-cyan/30 shadow'
              : 'text-slate-400 hover:text-white hover:bg-midnight-900'
          }`}
        >
          <Layers className="w-3.5 h-3.5 text-purple-400" />
          <span>Services Health Matrix ({affectedServices?.length || 0})</span>
        </button>
      </div>

      {/* Tab Panels */}
      <div>
        {activeSubTab === 'hypotheses' && (
          <HypothesesView hypotheses={hypotheses} />
        )}

        {activeSubTab === 'topology' && (
          <DependencyGraphView
            graph={dependencyGraph}
            primaryRootCauseService={summary?.primaryAffectedService}
            onSelectService={(sName) => {
              setSelectedServiceForChart(sName);
              setActiveSubTab('metrics');
            }}
          />
        )}

        {activeSubTab === 'timeline' && (
          <TimelineView timeline={timeline} />
        )}

        {activeSubTab === 'metrics' && (
          <MetricsChartsView
            metrics={incident.anomalies ? incident.anomalies.map(a => ({
              name: a.service,
              timestamp: a.timestamp,
              cpuPercent: a.metricName === 'CPU_SATURATION' ? a.observedValue : 25,
              latencyMs: a.metricName === 'LATENCY_SPIKE' ? a.observedValue : 50,
              errorRate: a.metricName === 'ERROR_RATE_SURGE' ? a.observedValue / 100 : 0.01,
              activeConnections: a.metricName === 'CONNECTION_POOL_EXHAUSTION' ? Math.round(a.observedValue) : 20,
              connectionPoolLimit: 100
            })) : []}
            initialSelectedService={selectedServiceForChart}
          />
        )}

        {activeSubTab === 'services' && (
          <AffectedServicesTable
            services={affectedServices}
            onSelectService={(sName) => {
              setSelectedServiceForChart(sName);
              setActiveSubTab('metrics');
            }}
          />
        )}
      </div>
    </div>
  );
};
