import React, { useState } from 'react';
import {
  RootCauseHypothesis
} from '../types/incident';
import {
  ShieldCheck,
  AlertTriangle,
  HelpCircle,
  Terminal,
  CheckCircle2,
  XCircle,
  Copy,
  Check,
  ChevronDown,
  ChevronUp,
  Clock,
  Network,
  Cpu,
  FileCode,
  Sparkles
} from 'lucide-react';

interface HypothesesViewProps {
  hypotheses: RootCauseHypothesis[];
}

export const HypothesesView: React.FC<HypothesesViewProps> = ({ hypotheses }) => {
  const [copiedCommand, setCopiedCommand] = useState<string | null>(null);
  const [expandedHypothesis, setExpandedHypothesis] = useState<number>(1); // default rank 1 open

  if (!hypotheses || hypotheses.length === 0) {
    return (
      <div className="p-8 text-center text-slate-500 bg-midnight-900 border border-midnight-800 rounded-2xl">
        No root cause hypotheses generated.
      </div>
    );
  }

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedCommand(text);
    setTimeout(() => setCopiedCommand(null), 2000);
  };

  const getEvidenceIcon = (type: string) => {
    switch (type) {
      case 'TEMPORAL_ORDER':
        return <Clock className="w-3.5 h-3.5 text-brand-sky" />;
      case 'DEPENDENCY_CASCADE':
        return <Network className="w-3.5 h-3.5 text-brand-cyan" />;
      case 'SATURATION_METRIC':
        return <Cpu className="w-3.5 h-3.5 text-brand-amber" />;
      case 'ERROR_LOG':
        return <FileCode className="w-3.5 h-3.5 text-brand-rose" />;
      default:
        return <Sparkles className="w-3.5 h-3.5 text-brand-turquoise" />;
    }
  };

  const getConfidenceBadge = (tier: string) => {
    switch (tier) {
      case 'HIGH_CONFIDENCE':
        return (
          <span className="text-xs px-2.5 py-1 rounded-full bg-brand-emerald/10 text-brand-emerald border border-brand-emerald/30 font-semibold flex items-center gap-1">
            <CheckCircle2 className="w-3.5 h-3.5" />
            <span>High Evidence Strength</span>
          </span>
        );
      case 'MODERATE_CONFIDENCE':
        return (
          <span className="text-xs px-2.5 py-1 rounded-full bg-brand-amber/10 text-brand-amber border border-brand-amber/30 font-semibold flex items-center gap-1">
            <AlertTriangle className="w-3.5 h-3.5" />
            <span>Moderate Strength</span>
          </span>
        );
      default:
        return (
          <span className="text-xs px-2.5 py-1 rounded-full bg-slate-800 text-slate-400 border border-slate-700 font-semibold flex items-center gap-1">
            <HelpCircle className="w-3.5 h-3.5" />
            <span>Low Confidence / Inconclusive</span>
          </span>
        );
    }
  };

  return (
    <div className="space-y-4">
      {/* Methodology notice banner */}
      <div className="p-3.5 rounded-xl bg-midnight-900 border border-midnight-800 text-xs text-slate-300 flex items-start gap-3">
        <ShieldCheck className="w-4 h-4 text-brand-cyan shrink-0 mt-0.5" />
        <div className="leading-relaxed">
          <strong className="text-white">Explainable Multi-Evidence Scoring: </strong>
          Hypotheses are ranked by evaluating temporal precedence, dependency graph topology, resource saturation thresholds, log error signatures, and change proximity.
          Evidence scores are <span className="text-brand-cyan font-mono">transparent heuristic strengths</span>, not calibrated probabilities.
        </div>
      </div>

      {/* Hypotheses List */}
      {hypotheses.map((hyp) => {
        const isExpanded = expandedHypothesis === hyp.rank;
        const isTop1 = hyp.rank === 1;

        return (
          <div
            key={hyp.hypothesisId || hyp.rank}
            className={`rounded-2xl border transition-all overflow-hidden ${
              isTop1
                ? 'bg-midnight-900 border-brand-cyan/40 shadow-xl shadow-brand-cyan/5'
                : 'bg-midnight-900/90 border-midnight-800 hover:border-midnight-700'
            }`}
          >
            {/* Card Header */}
            <div
              onClick={() => setExpandedHypothesis(isExpanded ? 0 : hyp.rank)}
              className="p-5 cursor-pointer flex flex-wrap items-center justify-between gap-4 select-none hover:bg-midnight-850/50 transition-colors"
            >
              <div className="flex items-center gap-4">
                {/* Rank Badge */}
                <div className={`w-9 h-9 rounded-xl flex items-center justify-center font-bold text-sm ${
                  isTop1
                    ? 'bg-gradient-to-br from-brand-cyan to-brand-turquoise text-midnight-950 shadow-md shadow-brand-cyan/20'
                    : 'bg-midnight-800 text-slate-400 border border-midnight-700'
                }`}>
                  #{hyp.rank}
                </div>

                <div>
                  <div className="flex items-center gap-2.5 flex-wrap">
                    <h4 className="font-semibold text-base text-white">{hyp.title}</h4>
                    <span className="text-xs px-2 py-0.5 rounded-md bg-midnight-800 text-slate-300 font-mono border border-midnight-700">
                      Service: <span className="text-brand-sky font-semibold">{hyp.suspectedService}</span>
                    </span>
                    <span className="text-[11px] px-2 py-0.5 rounded-md bg-midnight-850 text-slate-400 font-mono">
                      Category: {hyp.rootCauseCategory}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 mt-1 line-clamp-1">{hyp.reasoning}</p>
                </div>
              </div>

              {/* Score Bar & Confidence */}
              <div className="flex items-center gap-5">
                <div className="text-right">
                  <div className="flex items-center gap-2 justify-end">
                    <span className="text-xs text-slate-400">Evidence Strength:</span>
                    <span className="font-mono font-bold text-sm text-brand-cyan">{hyp.evidenceScore.toFixed(1)}%</span>
                  </div>
                  <div className="w-32 h-2 rounded-full bg-midnight-950 border border-midnight-800 overflow-hidden mt-1">
                    <div
                      className={`h-full rounded-full transition-all duration-500 ${
                        hyp.evidenceScore >= 80 ? 'bg-gradient-to-r from-brand-cyan to-brand-emerald' :
                        hyp.evidenceScore >= 60 ? 'bg-brand-amber' : 'bg-slate-500'
                      }`}
                      style={{ width: `${Math.min(100, hyp.evidenceScore)}%` }}
                    />
                  </div>
                </div>

                {getConfidenceBadge(hyp.confidenceTier)}

                <div className="p-1 rounded-lg text-slate-400 hover:text-white">
                  {isExpanded ? <ChevronUp className="w-5 h-5" /> : <ChevronDown className="w-5 h-5" />}
                </div>
              </div>
            </div>

            {/* Expanded Details Body */}
            {isExpanded && (
              <div className="px-6 pb-6 pt-2 border-t border-midnight-800/80 bg-midnight-950/40 space-y-5 animate-in fade-in duration-150">
                {/* Reasoning Statement */}
                <div>
                  <h5 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">
                    Diagnostic Analysis & Reasoning
                  </h5>
                  <p className="text-sm text-slate-200 leading-relaxed bg-midnight-850/80 p-3.5 rounded-xl border border-midnight-800">
                    {hyp.reasoning}
                  </p>
                </div>

                {/* Evidence Grid: Supporting vs Contradicting */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* Supporting Evidence */}
                  <div className="p-4 rounded-xl bg-midnight-900 border border-midnight-800 space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-semibold uppercase tracking-wider text-brand-emerald flex items-center gap-1.5">
                        <CheckCircle2 className="w-4 h-4" />
                        <span>Supporting Evidence ({hyp.supportingEvidence?.length || 0})</span>
                      </span>
                    </div>

                    <div className="space-y-2">
                      {hyp.supportingEvidence && hyp.supportingEvidence.length > 0 ? (
                        hyp.supportingEvidence.map((ev, i) => (
                          <div key={i} className="p-2.5 rounded-lg bg-midnight-850 border border-midnight-800/80 text-xs text-slate-300">
                            <div className="flex items-center justify-between gap-2">
                              <span className="font-semibold text-white flex items-center gap-1.5">
                                {getEvidenceIcon(ev.type)}
                                {ev.type}
                              </span>
                              <span className="font-mono text-brand-emerald text-[11px] font-semibold">
                                +{ev.scoreContribution.toFixed(0)} pts
                              </span>
                            </div>
                            <p className="mt-1 text-slate-300">{ev.summary}</p>
                            <span className="block mt-1 text-[10px] text-slate-500 font-mono">Source: {ev.sourceReference}</span>
                          </div>
                        ))
                      ) : (
                        <p className="text-xs text-slate-500 italic">No direct supporting evidence recorded.</p>
                      )}
                    </div>
                  </div>

                  {/* Contradicting / Missing Evidence */}
                  <div className="p-4 rounded-xl bg-midnight-900 border border-midnight-800 space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
                        <XCircle className="w-4 h-4 text-slate-400" />
                        <span>Contradicting / Missing Evidence ({hyp.contradictingEvidence?.length || 0})</span>
                      </span>
                    </div>

                    <div className="space-y-2">
                      {hyp.contradictingEvidence && hyp.contradictingEvidence.length > 0 ? (
                        hyp.contradictingEvidence.map((ev, i) => (
                          <div key={i} className="p-2.5 rounded-lg bg-midnight-850 border border-midnight-800/80 text-xs text-slate-300">
                            <div className="flex items-center justify-between gap-2">
                              <span className="font-semibold text-slate-300 flex items-center gap-1.5">
                                <AlertTriangle className="w-3.5 h-3.5 text-brand-amber" />
                                {ev.type}
                              </span>
                              <span className="font-mono text-brand-amber text-[11px] font-semibold">
                                {ev.scoreContribution.toFixed(0)} pts
                              </span>
                            </div>
                            <p className="mt-1 text-slate-400">{ev.summary}</p>
                            <span className="block mt-1 text-[10px] text-slate-500 font-mono">Source: {ev.sourceReference}</span>
                          </div>
                        ))
                      ) : (
                        <p className="text-xs text-slate-500 italic">No contradicting counter-evidence detected for this hypothesis.</p>
                      )}
                    </div>
                  </div>
                </div>

                {/* Recommended Diagnostic Verification Checks */}
                {hyp.recommendedActions && hyp.recommendedActions.length > 0 && (
                  <div className="p-4 rounded-xl bg-midnight-900 border border-midnight-800 space-y-2.5">
                    <div className="flex items-center gap-2">
                      <Terminal className="w-4 h-4 text-brand-cyan" />
                      <h5 className="text-xs font-semibold uppercase tracking-wider text-white">
                        Recommended Human Diagnostic Verification Actions
                      </h5>
                    </div>
                    <p className="text-xs text-slate-400">
                      Targeted diagnostics requiring operator review (no automated execution against production systems):
                    </p>

                    <div className="space-y-2 mt-2">
                      {hyp.recommendedActions.map((action, idx) => (
                        <div
                          key={idx}
                          className="flex items-center justify-between gap-3 p-2.5 rounded-lg bg-midnight-950 border border-midnight-800 font-mono text-xs text-brand-sky/90"
                        >
                          <span className="break-all">{action}</span>
                          <button
                            onClick={() => handleCopy(action)}
                            className="p-1.5 rounded-md hover:bg-midnight-800 text-slate-400 hover:text-white transition-colors shrink-0"
                            title="Copy command"
                          >
                            {copiedCommand === action ? (
                              <Check className="w-3.5 h-3.5 text-brand-emerald" />
                            ) : (
                              <Copy className="w-3.5 h-3.5" />
                            )}
                          </button>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
};
