import React, { useState } from 'react';
import {
  X,
  Database,
  Cpu,
  Globe,
  ArrowRight,
  ShieldAlert,
  CheckCircle,
  Sparkles,
  Loader2
} from 'lucide-react';
import { ScenarioCatalogItem } from '../types/incident';

interface SimulateModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectScenario: (scenarioId: string) => Promise<void>;
  scenarios: ScenarioCatalogItem[];
  isLoading: boolean;
}

export const SimulateModal: React.FC<SimulateModalProps> = ({
  isOpen,
  onClose,
  onSelectScenario,
  scenarios,
  isLoading
}) => {
  const [selectedId, setSelectedId] = useState<string>('db-pool-exhaustion');

  if (!isOpen) return null;

  const defaultScenarios: ScenarioCatalogItem[] = [
    {
      id: 'db-pool-exhaustion',
      title: 'Database Connection Pool Exhaustion',
      description: 'Postgres active connections hit 100/100 limit under transaction lock, inducing cascading thread timeouts in Order and Payment services.',
      severity: 'CRITICAL',
      affectedTopology: '4 Services (postgres-db, order-service, payment-service, api-gateway)',
      groundTruthCategory: 'DATABASE_EXHAUSTION',
      groundTruthService: 'postgres-db'
    },
    {
      id: 'cpu-saturation',
      title: 'CPU Saturation via Regressive Deployment',
      description: 'Auth Service CPU surges to 98.5% following release v2.4.1 (ReDoS regex tokenizer), exhausting thread pool and choking web portal logins.',
      severity: 'HIGH',
      affectedTopology: '4 Services (auth-service, web-portal, user-service, redis-session-store)',
      groundTruthCategory: 'RESOURCE_SATURATION',
      groundTruthService: 'auth-service'
    },
    {
      id: 'latency-error-spike',
      title: 'Third-Party Upstream Carrier Timeout',
      description: 'External shipping provider latency degrades to 7,800ms with 44% timeouts, cascading into worker thread starvation at checkout.',
      severity: 'HIGH',
      affectedTopology: '4 Services (third-party-shipping-api, checkout-service, inventory-service, ingress-router)',
      groundTruthCategory: 'UPSTREAM_TIMEOUT',
      groundTruthService: 'third-party-shipping-api'
    },
    {
      id: 'bench-healthy-01',
      title: 'Nominal Healthy Baseline',
      description: 'Zero anomalies across 3 services (gateway, user-service, user-db) with sub-second latencies and nominal connection limits.',
      severity: 'LOW',
      affectedTopology: '3 Services (gateway, user-service, user-db)',
      groundTruthCategory: 'NONE',
      groundTruthService: 'NONE'
    },
    {
      id: 'bench-mem-02',
      title: 'Search Indexer Memory Leak (Heap Exhaustion)',
      description: 'Search indexer encounters OutOfMemoryError and heap space exhaustion during batch merge, cascading into 3,100ms latencies for web frontend.',
      severity: 'CRITICAL',
      affectedTopology: '2 Services (web-frontend, search-indexer)',
      groundTruthCategory: 'RESOURCE_SATURATION',
      groundTruthService: 'search-indexer'
    }
  ];

  const activeCatalog = scenarios.length > 0 ? scenarios : defaultScenarios;
  const currentScenario = activeCatalog.find(s => s.id === selectedId) || activeCatalog[0];

  const getIcon = (id: string) => {
    const lower = id.toLowerCase();
    if (lower.includes('db-pool') || lower.includes('database')) {
      return <Database className="w-5 h-5 text-brand-rose" />;
    }
    if (lower.includes('cpu')) {
      return <Cpu className="w-5 h-5 text-brand-amber" />;
    }
    if (lower.includes('latency') || lower.includes('shipping') || lower.includes('carrier')) {
      return <Globe className="w-5 h-5 text-brand-cyan" />;
    }
    if (lower.includes('healthy')) {
      return <CheckCircle className="w-5 h-5 text-brand-emerald" />;
    }
    if (lower.includes('mem') || lower.includes('heap')) {
      return <ShieldAlert className="w-5 h-5 text-brand-rose" />;
    }
    return <Database className="w-5 h-5 text-brand-cyan" />;
  };

  const handleRun = async () => {
    await onSelectScenario(selectedId);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-midnight-950/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-2xl bg-midnight-900 border border-midnight-700/80 rounded-2xl shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-midnight-800 bg-midnight-850">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-brand-cyan/10 border border-brand-cyan/20 flex items-center justify-center">
              <Sparkles className="w-4 h-4 text-brand-cyan" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-white">Simulate Cascading Incident</h2>
              <p className="text-xs text-slate-400">Select a deterministic scenario with isolated ground truth</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-midnight-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-5">
          <p className="text-sm text-slate-300">
            Each scenario generates real, deterministic, timestamped multi-service telemetry, logs, and topology edges.
            The diagnostic engine receives <span className="text-brand-cyan font-medium">zero ground-truth answers</span> in its input.
          </p>

          {/* Scenario Selection Grid */}
          <div className="space-y-3">
            {activeCatalog.map((scenario) => {
              const isSelected = selectedId === scenario.id;
              return (
                <div
                  key={scenario.id}
                  onClick={() => setSelectedId(scenario.id)}
                  className={`p-4 rounded-xl border transition-all cursor-pointer ${
                    isSelected
                      ? 'bg-midnight-800/90 border-brand-cyan shadow-lg shadow-brand-cyan/10 ring-1 ring-brand-cyan/30'
                      : 'bg-midnight-850 border-midnight-800 hover:border-midnight-700 hover:bg-midnight-800/50'
                  }`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-3">
                      <div className="p-2.5 rounded-xl bg-midnight-950 border border-midnight-800">
                        {getIcon(scenario.id)}
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <h3 className="font-semibold text-sm text-white">{scenario.title}</h3>
                          <span className={`text-[10px] px-2 py-0.5 rounded-full font-mono uppercase ${
                            scenario.severity === 'CRITICAL'
                              ? 'bg-brand-rose/10 text-brand-rose border border-brand-rose/20'
                              : 'bg-brand-amber/10 text-brand-amber border border-brand-amber/20'
                          }`}>
                            {scenario.severity}
                          </span>
                        </div>
                        <p className="text-xs text-slate-400 mt-1 leading-relaxed">{scenario.description}</p>
                      </div>
                    </div>
                    <div className="mt-1">
                      <div className={`w-4 h-4 rounded-full border flex items-center justify-center ${
                        isSelected ? 'border-brand-cyan bg-brand-cyan text-midnight-950' : 'border-midnight-600'
                      }`}>
                        {isSelected && <CheckCircle className="w-3 h-3 stroke-[3]" />}
                      </div>
                    </div>
                  </div>

                  <div className="mt-3 pt-3 border-t border-midnight-800/80 flex items-center justify-between text-[11px] text-slate-400">
                    <span className="font-mono">Topology: {scenario.affectedTopology}</span>
                    <span className="text-slate-500 font-mono">ID: {scenario.id}</span>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Ground truth disclaimer box */}
          <div className="p-3.5 rounded-xl bg-midnight-850/80 border border-midnight-800 flex items-center gap-3 text-xs text-slate-400">
            <ShieldAlert className="w-5 h-5 text-brand-cyan shrink-0" />
            <div>
              <span className="text-slate-200 font-medium">Evaluation Integrity: </span>
              Known cause (<span className="text-brand-cyan font-mono">{currentScenario.groundTruthService}</span>) is strictly held out in benchmark metadata for post-analysis accuracy measurement.
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-midnight-800 bg-midnight-850 flex items-center justify-between">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl text-sm font-medium text-slate-400 hover:text-white hover:bg-midnight-800 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleRun}
            disabled={isLoading}
            className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-brand-cyan to-brand-turquoise hover:from-cyan-400 hover:to-teal-400 text-midnight-950 font-semibold text-sm shadow-lg shadow-brand-cyan/20 transition-all disabled:opacity-50"
          >
            {isLoading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>Running Anomaly Engine...</span>
              </>
            ) : (
              <>
                <span>Launch Investigation</span>
                <ArrowRight className="w-4 h-4" />
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
};
