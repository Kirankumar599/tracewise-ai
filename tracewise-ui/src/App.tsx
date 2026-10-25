import React, { useState, useEffect } from 'react';
import { Navbar } from './components/Navbar';
import { OverviewView } from './components/OverviewView';
import { InvestigationView } from './components/InvestigationView';
import { NewInvestigationView } from './components/NewInvestigationView';
import { EvaluationView } from './components/EvaluationView';
import { SettingsView } from './components/SettingsView';
import { SimulateModal } from './components/SimulateModal';
import { ReportModal } from './components/ReportModal';
import { ApiClient } from './services/api';
import {
  IncidentResponse,
  IncidentRequest,
  SystemHealthResponse,
  ScenarioCatalogItem,
  EvaluationResult
} from './types/incident';
import { AlertCircle, CheckCircle2, Info } from 'lucide-react';

export const App: React.FC = () => {
  const [currentTab, setCurrentTab] = useState<string>('overview');
  const [currentIncident, setCurrentIncident] = useState<IncidentResponse | null>(null);
  const [recentIncidents, setRecentIncidents] = useState<IncidentResponse[]>([]);
  const [systemHealth, setSystemHealth] = useState<SystemHealthResponse | null>(null);
  const [scenarioCatalog, setScenarioCatalog] = useState<ScenarioCatalogItem[]>([]);
  const [evaluationResult, setEvaluationResult] = useState<EvaluationResult | null>(null);

  const [isSimulateOpen, setIsSimulateOpen] = useState(false);
  const [isReportOpen, setIsReportOpen] = useState(false);
  const [reportMarkdown, setReportMarkdown] = useState<string>('');
  const [isLoading, setIsLoading] = useState(false);
  const [toast, setToast] = useState<{ type: 'success' | 'error' | 'info'; message: string } | null>(null);

  const showToast = (type: 'success' | 'error' | 'info', message: string) => {
    setToast({ type, message });
    setTimeout(() => setToast(null), 4000);
  };

  // Initial Data Loading
  useEffect(() => {
    const initializeApp = async () => {
      try {
        // 1. Health
        const health = await ApiClient.getHealth().catch(() => null);
        if (health) setSystemHealth(health);

        // 2. Scenarios
        const catalog = await ApiClient.getScenarioCatalog().catch(() => []);
        if (catalog && catalog.length > 0) setScenarioCatalog(catalog);

        // 3. Evaluation
        const evalRes = await ApiClient.getEvaluation().catch(() => null);
        if (evalRes) setEvaluationResult(evalRes);

        // 4. Recent Incidents
        const recents = await ApiClient.getRecentIncidents().catch(() => []);
        if (recents && recents.length > 0) {
          setRecentIncidents(recents);
          setCurrentIncident(recents[0]);
        } else {
          // Pre-load default demo scenario for immediate demonstration
          const defaultDemo = await ApiClient.runDemoIncident('db-pool-exhaustion').catch(() => null);
          if (defaultDemo) {
            setCurrentIncident(defaultDemo);
            setRecentIncidents([defaultDemo]);
          }
        }
      } catch (e: any) {
        console.warn('Initialization notice:', e.message);
      }
    };

    initializeApp();
  }, []);

  // Run Demo Scenario
  const handleSelectScenario = async (scenarioId: string) => {
    setIsLoading(true);
    try {
      const response = await ApiClient.runDemoIncident(scenarioId);
      setCurrentIncident(response);
      setRecentIncidents(prev => {
        const filtered = prev.filter(i => i.incidentId !== response.incidentId);
        return [response, ...filtered];
      });
      setCurrentTab('investigation');
      showToast('success', `Scenario launched: ${response.summary?.primaryRootCause || 'Investigation Complete'}`);
    } catch (err: any) {
      showToast('error', `Failed to run scenario: ${err.message}`);
      throw err;
    } finally {
      setIsLoading(false);
    }
  };

  // Analyze Direct JSON
  const handleAnalyzeRequest = async (request: IncidentRequest) => {
    setIsLoading(true);
    try {
      const response = await ApiClient.analyzeIncident(request);
      setCurrentIncident(response);
      setRecentIncidents(prev => [response, ...prev.filter(i => i.incidentId !== response.incidentId)]);
      setCurrentTab('investigation');
      showToast('success', `Incident ${response.incidentId} analyzed successfully!`);
    } catch (err: any) {
      showToast('error', `Analysis failed: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  // Upload File
  const handleUploadFile = async (file: File) => {
    setIsLoading(true);
    try {
      const response = await ApiClient.uploadTelemetryFile(file);
      setCurrentIncident(response);
      setRecentIncidents(prev => [response, ...prev.filter(i => i.incidentId !== response.incidentId)]);
      setCurrentTab('investigation');
      showToast('success', `File ${file.name} uploaded and analyzed successfully!`);
    } catch (err: any) {
      showToast('error', `File upload analysis failed: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  // Open Post-Mortem Report
  const handleOpenReport = async () => {
    if (!currentIncident) return;
    try {
      const md = await ApiClient.getIncidentReportMarkdown(currentIncident.incidentId);
      setReportMarkdown(md);
      setIsReportOpen(true);
    } catch (err: any) {
      showToast('error', `Could not fetch report: ${err.message}`);
    }
  };

  // Refresh Evaluation
  const handleRefreshEvaluation = async () => {
    setIsLoading(true);
    try {
      const evalRes = await ApiClient.getEvaluation();
      setEvaluationResult(evalRes);
      showToast('success', 'Benchmark evaluation refreshed with latest held-out suite results.');
    } catch (err: any) {
      showToast('error', `Evaluation failed: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-midnight-950 text-slate-100 flex flex-col selection:bg-brand-cyan/20 selection:text-brand-sky">
      {/* Toast Notification */}
      {toast && (
        <div className={`fixed top-20 right-6 z-50 p-4 rounded-xl shadow-2xl flex items-center gap-3 text-xs font-semibold animate-in slide-in-from-top duration-200 border ${
          toast.type === 'success'
            ? 'bg-midnight-900 border-brand-emerald/40 text-brand-emerald shadow-brand-emerald/10'
            : toast.type === 'error'
            ? 'bg-midnight-900 border-brand-rose/40 text-brand-rose shadow-brand-rose/10'
            : 'bg-midnight-900 border-brand-cyan/40 text-brand-cyan shadow-brand-cyan/10'
        }`}>
          {toast.type === 'success' && <CheckCircle2 className="w-4 h-4 shrink-0" />}
          {toast.type === 'error' && <AlertCircle className="w-4 h-4 shrink-0" />}
          {toast.type === 'info' && <Info className="w-4 h-4 shrink-0" />}
          <span>{toast.message}</span>
        </div>
      )}

      {/* Navigation Bar */}
      <Navbar
        currentTab={currentTab}
        onTabChange={setCurrentTab}
        onOpenSimulate={() => setIsSimulateOpen(true)}
        systemHealth={systemHealth}
        hasActiveIncident={Boolean(currentIncident)}
      />

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {currentTab === 'overview' && (
          <OverviewView
            recentIncidents={recentIncidents}
            currentIncident={currentIncident}
            onOpenSimulate={() => setIsSimulateOpen(true)}
            onNavigateTab={setCurrentTab}
            onSelectIncident={setCurrentIncident}
          />
        )}

        {currentTab === 'investigation' && (
          <InvestigationView
            incident={currentIncident}
            onOpenReport={handleOpenReport}
            onOpenSimulate={() => setIsSimulateOpen(true)}
          />
        )}

        {currentTab === 'new' && (
          <NewInvestigationView
            onAnalyze={handleAnalyzeRequest}
            onUploadFile={handleUploadFile}
            isLoading={isLoading}
          />
        )}

        {currentTab === 'evaluation' && (
          <EvaluationView
            evaluation={evaluationResult}
            onRefresh={handleRefreshEvaluation}
            isLoading={isLoading}
          />
        )}

        {currentTab === 'settings' && (
          <SettingsView systemHealth={systemHealth} />
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-midnight-850 bg-midnight-950 py-6 text-center text-xs text-slate-500 font-mono">
        <div className="max-w-7xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <span className="font-bold text-slate-300">TraceWise AI</span>
            <span>— Explainable Incident Intelligence for Cascading Software Failures</span>
          </div>
          <div className="flex items-center gap-4 text-slate-400">
            <span>Spring Boot 4.1.2</span>
            <span>•</span>
            <span>FastAPI ML</span>
            <span>•</span>
            <span>React + Vite</span>
          </div>
        </div>
      </footer>

      {/* Modals */}
      <SimulateModal
        isOpen={isSimulateOpen}
        onClose={() => setIsSimulateOpen(false)}
        onSelectScenario={handleSelectScenario}
        scenarios={scenarioCatalog}
        isLoading={isLoading}
      />

      <ReportModal
        isOpen={isReportOpen}
        onClose={() => setIsReportOpen(false)}
        incident={currentIncident}
        reportMarkdown={reportMarkdown}
      />
    </div>
  );
};

export default App;
