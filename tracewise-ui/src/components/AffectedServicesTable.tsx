import React from 'react';
import { ServiceHealthSummary } from '../types/incident';
import { Server, AlertTriangle, CheckCircle2, ShieldAlert } from 'lucide-react';

interface AffectedServicesTableProps {
  services: ServiceHealthSummary[];
  onSelectService?: (serviceName: string) => void;
}

export const AffectedServicesTable: React.FC<AffectedServicesTableProps> = ({
  services,
  onSelectService
}) => {
  if (!services || services.length === 0) {
    return null;
  }

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'FAILING':
        return (
          <span className="text-xs px-2.5 py-1 rounded-full bg-brand-rose/10 text-brand-rose border border-brand-rose/30 font-semibold flex items-center gap-1 w-fit">
            <ShieldAlert className="w-3.5 h-3.5" />
            <span>Failing</span>
          </span>
        );
      case 'DEGRADED':
        return (
          <span className="text-xs px-2.5 py-1 rounded-full bg-brand-amber/10 text-brand-amber border border-brand-amber/30 font-semibold flex items-center gap-1 w-fit">
            <AlertTriangle className="w-3.5 h-3.5" />
            <span>Degraded</span>
          </span>
        );
      default:
        return (
          <span className="text-xs px-2.5 py-1 rounded-full bg-brand-emerald/10 text-brand-emerald border border-brand-emerald/30 font-semibold flex items-center gap-1 w-fit">
            <CheckCircle2 className="w-3.5 h-3.5" />
            <span>Healthy</span>
          </span>
        );
    }
  };

  return (
    <div className="bg-midnight-900 border border-midnight-800 rounded-2xl overflow-hidden shadow-xl">
      <div className="px-6 py-4 border-b border-midnight-800 bg-midnight-850/60 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Server className="w-4 h-4 text-brand-cyan" />
          <h3 className="font-semibold text-white">Affected Services Health Matrix</h3>
        </div>
        <span className="text-xs text-slate-400 font-mono">
          {services.filter(s => s.status !== 'HEALTHY').length} of {services.length} services degraded
        </span>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs font-mono">
          <thead className="bg-midnight-950/80 text-slate-400 border-b border-midnight-800">
            <tr>
              <th className="px-6 py-3 font-semibold uppercase tracking-wider">Service Name</th>
              <th className="px-6 py-3 font-semibold uppercase tracking-wider">Status</th>
              <th className="px-6 py-3 font-semibold uppercase tracking-wider">Peak CPU</th>
              <th className="px-6 py-3 font-semibold uppercase tracking-wider">Peak Latency</th>
              <th className="px-6 py-3 font-semibold uppercase tracking-wider">Peak Error Rate</th>
              <th className="px-6 py-3 font-semibold uppercase tracking-wider">Connection Pool</th>
              <th className="px-6 py-3 font-semibold uppercase tracking-wider">Anomalies</th>
              <th className="px-6 py-3 font-semibold uppercase tracking-wider text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-midnight-800/60">
            {services.map((s) => {
              const poolUtil = s.connectionPoolLimit > 0 ? (s.maxActiveConnections / s.connectionPoolLimit) * 100 : 0;
              return (
                <tr key={s.serviceName} className="hover:bg-midnight-850/50 transition-colors">
                  <td className="px-6 py-4 font-semibold text-white flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-brand-cyan" />
                    <span>{s.serviceName}</span>
                  </td>
                  <td className="px-6 py-4">
                    {getStatusBadge(s.status)}
                  </td>
                  <td className="px-6 py-4">
                    <span className={s.peakCpuPercent >= 80 ? 'text-brand-rose font-bold' : s.peakCpuPercent >= 60 ? 'text-brand-amber font-semibold' : 'text-slate-300'}>
                      {s.peakCpuPercent.toFixed(1)}%
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <span className={s.peakLatencyMs >= 1000 ? 'text-brand-rose font-bold' : s.peakLatencyMs >= 300 ? 'text-brand-amber font-semibold' : 'text-slate-300'}>
                      {s.peakLatencyMs.toFixed(0)} ms
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <span className={s.peakErrorRate >= 10 ? 'text-brand-rose font-bold' : s.peakErrorRate >= 3 ? 'text-brand-amber font-semibold' : 'text-slate-300'}>
                      {s.peakErrorRate.toFixed(1)}%
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    {s.connectionPoolLimit > 0 ? (
                      <div>
                        <div className="flex items-center gap-1.5">
                          <span className={poolUtil >= 90 ? 'text-brand-rose font-bold' : 'text-slate-300'}>
                            {s.maxActiveConnections}/{s.connectionPoolLimit}
                          </span>
                          <span className="text-[10px] text-slate-500">({poolUtil.toFixed(0)}%)</span>
                        </div>
                      </div>
                    ) : (
                      <span className="text-slate-600">N/A</span>
                    )}
                  </td>
                  <td className="px-6 py-4">
                    <span className={`px-2 py-0.5 rounded text-[11px] ${
                      s.anomalyCount > 0 ? 'bg-brand-rose/20 text-brand-rose font-bold' : 'text-slate-500'
                    }`}>
                      {s.anomalyCount}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right">
                    <button
                      onClick={() => onSelectService?.(s.serviceName)}
                      className="text-xs px-2.5 py-1 rounded-md bg-midnight-800 hover:bg-midnight-700 text-brand-cyan border border-brand-cyan/20 transition-colors"
                    >
                      Inspect
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};
