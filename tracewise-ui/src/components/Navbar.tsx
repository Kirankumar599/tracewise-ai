import React from 'react';
import {
  Activity,
  Layers,
  Search,
  CheckCircle2,
  AlertTriangle,
  PlayCircle,
  FileText,
  BarChart3,
  Server,
  Cpu
} from 'lucide-react';
import { SystemHealthResponse } from '../types/incident';

interface NavbarProps {
  currentTab: string;
  onTabChange: (tab: string) => void;
  onOpenSimulate: () => void;
  systemHealth: SystemHealthResponse | null;
  hasActiveIncident: boolean;
}

export const Navbar: React.FC<NavbarProps> = ({
  currentTab,
  onTabChange,
  onOpenSimulate,
  systemHealth,
  hasActiveIncident
}) => {
  return (
    <header className="sticky top-0 z-40 w-full border-b border-midnight-800 bg-midnight-950/80 backdrop-blur-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Logo & Brand */}
          <div className="flex items-center gap-3 cursor-pointer" onClick={() => onTabChange('overview')}>
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-brand-cyan to-brand-turquoise flex items-center justify-center shadow-lg shadow-brand-cyan/20">
              <Activity className="w-5 h-5 text-midnight-950 stroke-[2.5]" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-bold text-lg tracking-tight text-white">TraceWise</span>
                <span className="text-xs px-2 py-0.5 rounded-full bg-brand-cyan/10 text-brand-cyan font-mono border border-brand-cyan/30">AI</span>
              </div>
              <p className="text-[11px] text-slate-400 font-medium">Explainable Incident Intelligence</p>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="hidden md:flex items-center gap-1">
            <button
              onClick={() => onTabChange('overview')}
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-all ${
                currentTab === 'overview'
                  ? 'bg-midnight-800 text-brand-cyan border border-brand-cyan/30'
                  : 'text-slate-400 hover:text-white hover:bg-midnight-850'
              }`}
            >
              <div className="flex items-center gap-2">
                <Layers className="w-4 h-4" />
                <span>Overview</span>
              </div>
            </button>

            <button
              onClick={() => onTabChange('new')}
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-all ${
                currentTab === 'new'
                  ? 'bg-midnight-800 text-brand-cyan border border-brand-cyan/30'
                  : 'text-slate-400 hover:text-white hover:bg-midnight-850'
              }`}
            >
              <div className="flex items-center gap-2">
                <Search className="w-4 h-4" />
                <span>New Investigation</span>
              </div>
            </button>

            <button
              onClick={() => onTabChange('investigation')}
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-all relative ${
                currentTab === 'investigation'
                  ? 'bg-midnight-800 text-brand-cyan border border-brand-cyan/30'
                  : 'text-slate-400 hover:text-white hover:bg-midnight-850'
              }`}
            >
              <div className="flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-brand-amber" />
                <span>Active Investigation</span>
                {hasActiveIncident && (
                  <span className="w-2 h-2 rounded-full bg-brand-rose animate-ping absolute top-2 right-2" />
                )}
              </div>
            </button>

            <button
              onClick={() => onTabChange('evaluation')}
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-all ${
                currentTab === 'evaluation'
                  ? 'bg-midnight-800 text-brand-cyan border border-brand-cyan/30'
                  : 'text-slate-400 hover:text-white hover:bg-midnight-850'
              }`}
            >
              <div className="flex items-center gap-2">
                <BarChart3 className="w-4 h-4 text-brand-sky" />
                <span>Evaluation</span>
              </div>
            </button>

            <button
              onClick={() => onTabChange('settings')}
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-all ${
                currentTab === 'settings'
                  ? 'bg-midnight-800 text-brand-cyan border border-brand-cyan/30'
                  : 'text-slate-400 hover:text-white hover:bg-midnight-850'
              }`}
            >
              <div className="flex items-center gap-2">
                <Server className="w-4 h-4" />
                <span>System & Architecture</span>
              </div>
            </button>
          </nav>

          {/* Right Actions: Status Chips + Simulate Button */}
          <div className="flex items-center gap-3">
            {/* System Status Chips */}
            <div className="hidden lg:flex items-center gap-2">
              <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-midnight-900 border border-midnight-800 text-xs text-slate-300">
                <span className={`w-2 h-2 rounded-full ${systemHealth ? 'bg-brand-emerald' : 'bg-brand-amber animate-pulse'}`} />
                <span className="font-mono text-[11px]">API: {systemHealth ? 'Connected' : 'Connecting'}</span>
              </div>

              <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-midnight-900 border border-midnight-800 text-xs text-slate-300">
                <Cpu className="w-3.5 h-3.5 text-brand-cyan" />
                <span className="font-mono text-[11px]">
                  ML: {systemHealth?.mlServiceConnected ? 'IsolationForest' : 'Statistical'}
                </span>
              </div>
            </div>

            {/* Simulate Incident Button */}
            <button
              onClick={onOpenSimulate}
              className="flex items-center gap-2 px-4 py-2 rounded-xl bg-gradient-to-r from-brand-cyan to-brand-turquoise hover:from-cyan-400 hover:to-teal-400 text-midnight-950 font-semibold text-sm shadow-md shadow-brand-cyan/20 transition-all transform hover:-translate-y-0.5 active:translate-y-0"
            >
              <PlayCircle className="w-4 h-4 stroke-[2.5]" />
              <span>Simulate Incident</span>
            </button>
          </div>
        </div>
      </div>
    </header>
  );
};
