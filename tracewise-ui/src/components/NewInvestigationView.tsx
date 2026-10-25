import React, { useState, useRef } from 'react';
import {
  UploadCloud,
  FileCode,
  FileSpreadsheet,
  Download,
  AlertCircle,
  CheckCircle2,
  Play,
  ArrowRight,
  Loader2,
  FileText
} from 'lucide-react';
import { IncidentRequest } from '../types/incident';

interface NewInvestigationViewProps {
  onAnalyze: (request: IncidentRequest) => Promise<void>;
  onUploadFile: (file: File) => Promise<void>;
  isLoading: boolean;
}

export const NewInvestigationView: React.FC<NewInvestigationViewProps> = ({
  onAnalyze,
  onUploadFile,
  isLoading
}) => {
  const [dragActive, setDragActive] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [jsonText, setJsonText] = useState<string>('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleDrag = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true);
    } else if (e.type === 'dragleave') {
      setDragActive(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFile(e.dataTransfer.files[0]);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      handleFile(e.target.files[0]);
    }
  };

  const handleFile = (file: File) => {
    setErrorMsg(null);
    const ext = file.name.split('.').pop()?.toLowerCase();
    if (ext !== 'csv' && ext !== 'json') {
      setErrorMsg('Invalid file format. Please upload a .csv or .json telemetry file.');
      setSelectedFile(null);
      return;
    }
    setSelectedFile(file);
  };

  const handleUploadSubmit = async () => {
    if (!selectedFile) return;
    try {
      setErrorMsg(null);
      await onUploadFile(selectedFile);
    } catch (err: any) {
      setErrorMsg(err.message || 'File upload analysis failed.');
    }
  };

  const handleJsonSubmit = async () => {
    if (!jsonText.trim()) return;
    try {
      setErrorMsg(null);
      const parsed = JSON.parse(jsonText);
      if (!parsed.incidentId || !parsed.services || parsed.services.length === 0) {
        throw new Error("JSON payload must contain 'incidentId' and a non-empty 'services' array.");
      }
      await onAnalyze(parsed);
    } catch (err: any) {
      setErrorMsg(err.message || 'Malformed JSON incident payload.');
    }
  };

  const loadSampleJson = () => {
    const sample = {
      incidentId: "INC-SAMPLE-MANUAL-01",
      services: [
        { name: "postgres-db", timestamp: "2026-10-24T10:00:00", cpuPercent: 18.0, latencyMs: 12.0, errorRate: 0.001, activeConnections: 24, connectionPoolLimit: 100 },
        { name: "order-service", timestamp: "2026-10-24T10:00:00", cpuPercent: 22.0, latencyMs: 45.0, errorRate: 0.002, activeConnections: 10, connectionPoolLimit: 50 },
        { name: "postgres-db", timestamp: "2026-10-24T10:02:00", cpuPercent: 68.0, latencyMs: 350.0, errorRate: 0.15, activeConnections: 100, connectionPoolLimit: 100 },
        { name: "order-service", timestamp: "2026-10-24T10:02:00", cpuPercent: 55.0, latencyMs: 2800.0, errorRate: 0.28, activeConnections: 50, connectionPoolLimit: 50 }
      ],
      logs: [
        { timestamp: "2026-10-24T10:02:00", service: "postgres-db", level: "ERROR", message: "HikariPool-1 - Connection pool exhausted! 100/100 active connections in use." },
        { timestamp: "2026-10-24T10:02:15", service: "order-service", level: "ERROR", message: "Connection acquisition timeout (30000ms) waiting for postgres-db" }
      ],
      dependencies: [
        { from: "order-service", to: "postgres-db" }
      ],
      recentChanges: [
        { timestamp: "2026-10-24T09:48:00", service: "postgres-db", description: "Migration: Alter table billing_invoices" }
      ]
    };
    setJsonText(JSON.stringify(sample, null, 2));
    setErrorMsg(null);
  };

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      {/* Title */}
      <div>
        <h2 className="text-xl font-bold text-white">New Incident Investigation</h2>
        <p className="text-xs text-slate-400 mt-1">
          Upload distributed microservice telemetry files (CSV or JSON) or paste structured payloads to identify initiating failure origins.
        </p>
      </div>

      {errorMsg && (
        <div className="p-4 rounded-xl bg-brand-rose/10 border border-brand-rose/30 flex items-start gap-3 text-xs text-brand-rose animate-in fade-in">
          <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
          <div className="leading-relaxed">
            <strong className="font-semibold">Validation Error: </strong>
            {errorMsg}
          </div>
        </div>
      )}

      {/* Grid: Upload Box + JSON Box */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Method 1: File Upload */}
        <div className="p-6 rounded-2xl bg-midnight-900 border border-midnight-800 shadow-xl space-y-5 flex flex-col justify-between">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <FileSpreadsheet className="w-5 h-5 text-brand-cyan" />
              <h3 className="font-semibold text-white">Upload Telemetry File</h3>
            </div>
            <p className="text-xs text-slate-400">
              Drag and drop a .csv or .json file containing service timestamps, CPU, latency, error rates, and connection stats.
            </p>

            {/* Drop Zone */}
            <div
              onDragEnter={handleDrag}
              onDragLeave={handleDrag}
              onDragOver={handleDrag}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              className={`mt-4 p-8 border-2 border-dashed rounded-xl flex flex-col items-center justify-center text-center cursor-pointer transition-all ${
                dragActive
                  ? 'border-brand-cyan bg-brand-cyan/10'
                  : 'border-midnight-750 hover:border-midnight-600 bg-midnight-950/50'
              }`}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept=".csv,.json"
                onChange={handleFileChange}
                className="hidden"
              />
              <UploadCloud className="w-10 h-10 text-brand-cyan/80 mb-3" />
              <span className="text-sm font-semibold text-white">
                {selectedFile ? selectedFile.name : 'Click to browse or drop file here'}
              </span>
              <span className="text-xs text-slate-500 mt-1">Supports CSV and structured JSON files up to 10MB</span>
            </div>

            {selectedFile && (
              <div className="mt-3 p-3 rounded-lg bg-midnight-850 border border-midnight-800 flex items-center justify-between text-xs text-slate-300">
                <span className="font-mono">{selectedFile.name} ({(selectedFile.size / 1024).toFixed(1)} KB)</span>
                <span className="text-brand-emerald flex items-center gap-1 font-semibold">
                  <CheckCircle2 className="w-3.5 h-3.5" /> Ready
                </span>
              </div>
            )}
          </div>

          <div className="space-y-4 pt-4 border-t border-midnight-800/80">
            {/* Sample Downloads */}
            <div className="space-y-1.5">
              <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                Downloadable Test Fixtures:
              </span>
              <div className="flex flex-wrap items-center gap-2 text-xs">
                <a
                  href="/sample-data/sample_database_pool_exhaustion.csv"
                  download="sample_database_pool_exhaustion.csv"
                  className="px-2.5 py-1 rounded-md bg-midnight-850 hover:bg-midnight-800 text-brand-cyan border border-brand-cyan/20 flex items-center gap-1 transition-colors"
                >
                  <Download className="w-3 h-3" />
                  <span>db_pool.csv</span>
                </a>
                <a
                  href="/sample-data/sample_cpu_saturation.csv"
                  download="sample_cpu_saturation.csv"
                  className="px-2.5 py-1 rounded-md bg-midnight-850 hover:bg-midnight-800 text-brand-amber border border-brand-amber/20 flex items-center gap-1 transition-colors"
                >
                  <Download className="w-3 h-3" />
                  <span>cpu_saturation.csv</span>
                </a>
                <a
                  href="/sample-data/sample_incident_db_pool.json"
                  download="sample_incident_db_pool.json"
                  className="px-2.5 py-1 rounded-md bg-midnight-850 hover:bg-midnight-800 text-brand-sky border border-brand-sky/20 flex items-center gap-1 transition-colors"
                >
                  <Download className="w-3 h-3" />
                  <span>incident.json</span>
                </a>
              </div>
            </div>

            <button
              onClick={handleUploadSubmit}
              disabled={!selectedFile || isLoading}
              className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-gradient-to-r from-brand-cyan to-brand-turquoise hover:from-cyan-400 hover:to-teal-400 text-midnight-950 font-semibold text-xs shadow-md transition-all disabled:opacity-50"
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Analyzing Uploaded Telemetry...</span>
                </>
              ) : (
                <>
                  <span>Analyze Uploaded File</span>
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </div>
        </div>

        {/* Method 2: Direct JSON Input */}
        <div className="p-6 rounded-2xl bg-midnight-900 border border-midnight-800 shadow-xl space-y-4 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <FileCode className="w-5 h-5 text-brand-sky" />
                <h3 className="font-semibold text-white">Direct JSON Payload</h3>
              </div>
              <button
                onClick={loadSampleJson}
                className="text-xs text-brand-cyan hover:underline font-mono"
              >
                Insert Sample JSON
              </button>
            </div>
            <p className="text-xs text-slate-400">
              Paste a custom incident JSON schema with services, logs, dependencies, and change events.
            </p>

            <textarea
              rows={12}
              value={jsonText}
              onChange={(e) => setJsonText(e.target.value)}
              placeholder="Paste IncidentRequest JSON here..."
              className="mt-4 w-full p-3 rounded-xl bg-midnight-950 border border-midnight-800 text-xs font-mono text-slate-200 focus:outline-none focus:border-brand-cyan resize-none selection:bg-brand-cyan/20"
            />
          </div>

          <button
            onClick={handleJsonSubmit}
            disabled={!jsonText.trim() || isLoading}
            className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-midnight-800 hover:bg-midnight-750 text-brand-cyan border border-brand-cyan/30 font-semibold text-xs shadow-md transition-all disabled:opacity-50"
          >
            {isLoading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>Processing JSON Telemetry...</span>
              </>
            ) : (
              <>
                <span>Submit JSON for Analysis</span>
                <Play className="w-3.5 h-3.5 fill-current" />
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
};
