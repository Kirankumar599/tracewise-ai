import React, { useState } from 'react';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend
} from 'recharts';
import { ServiceMetric } from '../types/incident';
import { Activity, Clock, AlertCircle, Database } from 'lucide-react';

interface MetricsChartsViewProps {
  metrics: ServiceMetric[];
  initialSelectedService?: string;
}

export const MetricsChartsView: React.FC<MetricsChartsViewProps> = ({
  metrics,
  initialSelectedService: _initialSelectedService
}) => {
  const [selectedMetricType, setSelectedMetricType] = useState<'latency' | 'cpu' | 'errors' | 'connections'>('latency');
  const allServices = Array.from(new Set((metrics || []).map(m => m.name)));
  const [activeServices, setActiveServices] = useState<string[]>(allServices);

  if (!metrics || metrics.length === 0) {
    return (
      <div className="p-8 text-center text-slate-500 bg-midnight-900 border border-midnight-800 rounded-2xl">
        No metric telemetry available to chart.
      </div>
    );
  }

  // Colors for multi-line overlay
  const serviceColors: Record<string, string> = {
    'postgres-db': '#EF4444',
    'order-service': '#06B6D4',
    'payment-service': '#38BDF8',
    'api-gateway': '#10B981',
    'auth-service': '#EF4444',
    'web-portal': '#06B6D4',
    'user-service': '#10B981',
    'redis-session-store': '#A855F7',
    'third-party-shipping-api': '#EF4444',
    'checkout-service': '#06B6D4',
    'inventory-service': '#10B981',
    'ingress-router': '#F59E0B'
  };

  const getServiceColor = (name: string, idx: number) => {
    if (serviceColors[name]) return serviceColors[name];
    const fallback = ['#06B6D4', '#EF4444', '#F59E0B', '#10B981', '#A855F7', '#EC4899'];
    return fallback[idx % fallback.length];
  };

  // Group metrics by timestamp for Recharts
  const timestamps = Array.from(new Set(metrics.map(m => m.timestamp))).sort();
  const chartData = timestamps.map(ts => {
    const timeLabel = new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    const row: Record<string, any> = { timestamp: timeLabel, rawTime: ts };

    metrics.filter(m => m.timestamp === ts).forEach(m => {
      row[`${m.name}_latency`] = m.latencyMs;
      row[`${m.name}_cpu`] = m.cpuPercent;
      row[`${m.name}_error`] = m.errorRate * 100;
      row[`${m.name}_conns`] = m.activeConnections;
      row[`${m.name}_limit`] = m.connectionPoolLimit;
    });

    return row;
  });

  const toggleService = (name: string) => {
    setActiveServices(prev =>
      prev.includes(name) ? prev.filter(s => s !== name) : [...prev, name]
    );
  };

  return (
    <div className="bg-midnight-900 border border-midnight-800 rounded-2xl p-6 shadow-xl space-y-6">
      {/* Header and Metric Tabs */}
      <div className="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-midnight-800">
        <div>
          <h3 className="font-semibold text-white flex items-center gap-2">
            <Activity className="w-4 h-4 text-brand-cyan" />
            <span>Interactive Telemetry Explorer</span>
          </h3>
          <p className="text-xs text-slate-400 mt-0.5">
            Synchronized service metrics across incident timeline with anomaly threshold markers
          </p>
        </div>

        {/* Metric Selector Buttons */}
        <div className="flex items-center gap-1.5 p-1 rounded-xl bg-midnight-950 border border-midnight-800 text-xs">
          <button
            onClick={() => setSelectedMetricType('latency')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all ${
              selectedMetricType === 'latency'
                ? 'bg-midnight-800 text-brand-cyan font-semibold shadow'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <Clock className="w-3.5 h-3.5" />
            <span>Latency (ms)</span>
          </button>

          <button
            onClick={() => setSelectedMetricType('cpu')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all ${
              selectedMetricType === 'cpu'
                ? 'bg-midnight-800 text-brand-amber font-semibold shadow'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <Activity className="w-3.5 h-3.5" />
            <span>CPU (%)</span>
          </button>

          <button
            onClick={() => setSelectedMetricType('errors')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all ${
              selectedMetricType === 'errors'
                ? 'bg-midnight-800 text-brand-rose font-semibold shadow'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <AlertCircle className="w-3.5 h-3.5" />
            <span>Error Rate (%)</span>
          </button>

          <button
            onClick={() => setSelectedMetricType('connections')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all ${
              selectedMetricType === 'connections'
                ? 'bg-midnight-800 text-brand-turquoise font-semibold shadow'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <Database className="w-3.5 h-3.5" />
            <span>Active Connections</span>
          </button>
        </div>
      </div>

      {/* Service Toggles */}
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <span className="text-slate-400 mr-1 font-medium">Overlay Services:</span>
        {allServices.map((name, idx) => {
          const isActive = activeServices.includes(name);
          const color = getServiceColor(name, idx);
          return (
            <button
              key={name}
              onClick={() => toggleService(name)}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg border font-mono transition-all ${
                isActive
                  ? 'bg-midnight-850 text-white border-midnight-700 shadow-sm'
                  : 'bg-midnight-950 text-slate-500 border-midnight-850 opacity-50'
              }`}
            >
              <span className="w-2 h-2 rounded-full" style={{ backgroundColor: color }} />
              <span>{name}</span>
            </button>
          );
        })}
      </div>

      {/* Recharts Canvas */}
      <div className="h-80 w-full pt-2">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={chartData} margin={{ top: 10, right: 20, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1E293B" />
            <XAxis
              dataKey="timestamp"
              stroke="#64748B"
              fontSize={11}
              fontFamily="monospace"
              tickLine={false}
            />
            <YAxis
              stroke="#64748B"
              fontSize={11}
              fontFamily="monospace"
              tickLine={false}
              unit={selectedMetricType === 'latency' ? 'ms' : selectedMetricType === 'cpu' || selectedMetricType === 'errors' ? '%' : ''}
            />
            <Tooltip
              contentStyle={{
                backgroundColor: '#0F172A',
                borderColor: '#334155',
                borderRadius: '12px',
                fontSize: '12px',
                fontFamily: 'monospace',
                color: '#F8FAFC'
              }}
            />
            <Legend
              wrapperStyle={{ fontSize: '11px', fontFamily: 'monospace', paddingTop: '10px' }}
            />

            {allServices.map((name, idx) => {
              if (!activeServices.includes(name)) return null;
              const color = getServiceColor(name, idx);
              const dataKey = selectedMetricType === 'latency'
                ? `${name}_latency`
                : selectedMetricType === 'cpu'
                ? `${name}_cpu`
                : selectedMetricType === 'errors'
                ? `${name}_error`
                : `${name}_conns`;

              return (
                <Line
                  key={name}
                  type="monotone"
                  dataKey={dataKey}
                  name={name}
                  stroke={color}
                  strokeWidth={2}
                  dot={{ r: 3, fill: color }}
                  activeDot={{ r: 6, fill: '#FFFFFF', stroke: color, strokeWidth: 2 }}
                />
              );
            })}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
