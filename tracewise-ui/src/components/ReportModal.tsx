import React, { useState } from 'react';
import {
  X,
  Download,
  Copy,
  Check,
  FileText,
  Share2
} from 'lucide-react';
import { IncidentResponse } from '../types/incident';

interface ReportModalProps {
  isOpen: boolean;
  onClose: () => void;
  incident: IncidentResponse | null;
  reportMarkdown: string;
}

export const ReportModal: React.FC<ReportModalProps> = ({
  isOpen,
  onClose,
  incident,
  reportMarkdown
}) => {
  const [copied, setCopied] = useState(false);

  if (!isOpen || !incident) return null;

  const handleCopy = () => {
    navigator.clipboard.writeText(reportMarkdown);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownload = () => {
    const blob = new Blob([reportMarkdown], { type: 'text/markdown;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `incident-${incident.incidentId}-postmortem.md`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-midnight-950/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-3xl max-h-[90vh] bg-midnight-900 border border-midnight-700 rounded-2xl shadow-2xl flex flex-col overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-midnight-800 bg-midnight-850">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-brand-cyan/10 border border-brand-cyan/20">
              <FileText className="w-5 h-5 text-brand-cyan" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-white">Incident Post-Mortem Report</h2>
              <p className="text-xs text-slate-400 font-mono">Incident #{incident.incidentId} • Ready for engineering distribution</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-midnight-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Markdown Display */}
        <div className="flex-1 overflow-y-auto p-6 bg-midnight-950/60 font-mono text-xs text-slate-200 space-y-4">
          <pre className="whitespace-pre-wrap leading-relaxed select-text font-mono">
            {reportMarkdown}
          </pre>
        </div>

        {/* Footer Actions */}
        <div className="px-6 py-4 border-t border-midnight-800 bg-midnight-850 flex items-center justify-between">
          <span className="text-xs text-slate-400">
            Export format: <span className="text-white font-mono">Markdown (.md)</span>
          </span>

          <div className="flex items-center gap-3">
            <button
              onClick={handleCopy}
              className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-midnight-800 hover:bg-midnight-700 text-slate-200 border border-midnight-700 text-xs font-medium transition-colors"
            >
              {copied ? <Check className="w-4 h-4 text-brand-emerald" /> : <Copy className="w-4 h-4" />}
              <span>{copied ? 'Copied to Clipboard!' : 'Copy Markdown'}</span>
            </button>

            <button
              onClick={handleDownload}
              className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-gradient-to-r from-brand-cyan to-brand-turquoise hover:from-cyan-400 hover:to-teal-400 text-midnight-950 font-semibold text-xs shadow-md shadow-brand-cyan/20 transition-all"
            >
              <Download className="w-4 h-4 stroke-[2.5]" />
              <span>Download Report</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
