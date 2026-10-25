import React, { useState } from 'react';
import { TimelineEvent } from '../types/incident';
import {
  GitCommit,
  AlertOctagon,
  FileText,
  AlertTriangle,
  Clock,
  Flame
} from 'lucide-react';

interface TimelineViewProps {
  timeline: TimelineEvent[];
}

export const TimelineView: React.FC<TimelineViewProps> = ({ timeline }) => {
  const [filterType, setFilterType] = useState<string>('ALL');

  if (!timeline || timeline.length === 0) {
    return (
      <div className="p-8 text-center text-slate-500 bg-midnight-900 border border-midnight-800 rounded-2xl">
        No incident timeline events recorded.
      </div>
    );
  }

  const filteredEvents = timeline.filter(event => {
    if (filterType === 'ALL') return true;
    if (filterType === 'INITIATING') return event.isInitiatingEvent;
    if (filterType === 'ANOMALY') return event.eventType === 'METRIC_ANOMALY';
    if (filterType === 'LOG') return event.eventType.includes('LOG');
    if (filterType === 'CHANGE') return event.eventType === 'CHANGE';
    return true;
  });

  const getEventIcon = (event: TimelineEvent) => {
    if (event.isInitiatingEvent) {
      return <Flame className="w-4 h-4 text-brand-rose animate-bounce" />;
    }
    if (event.eventType === 'CHANGE') {
      return <GitCommit className="w-4 h-4 text-brand-cyan" />;
    }
    if (event.eventType === 'METRIC_ANOMALY') {
      return <AlertOctagon className="w-4 h-4 text-brand-rose" />;
    }
    if (event.eventType.includes('ERROR')) {
      return <AlertTriangle className="w-4 h-4 text-brand-rose" />;
    }
    return <FileText className="w-4 h-4 text-brand-amber" />;
  };

  const formatTime = (ts: string) => {
    try {
      const d = new Date(ts);
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } catch {
      return ts;
    }
  };

  return (
    <div className="bg-midnight-900 border border-midnight-800 rounded-2xl p-6 shadow-xl space-y-5">
      {/* Header and Filter Buttons */}
      <div className="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-midnight-800">
        <div>
          <h3 className="font-semibold text-white flex items-center gap-2">
            <Clock className="w-4 h-4 text-brand-cyan" />
            <span>Unified Incident Chronology</span>
            <span className="text-xs px-2 py-0.5 rounded-full bg-midnight-800 text-slate-400 font-mono">
              {filteredEvents.length} Events
            </span>
          </h3>
          <p className="text-xs text-slate-400 mt-0.5">
            Interleaved sequence of recent deployments, initial telemetry breach, and downstream cascading alerts
          </p>
        </div>

        {/* Filters */}
        <div className="flex items-center gap-1.5 p-1 rounded-xl bg-midnight-950 border border-midnight-800 text-xs">
          <button
            onClick={() => setFilterType('ALL')}
            className={`px-3 py-1.5 rounded-lg transition-all ${
              filterType === 'ALL' ? 'bg-midnight-800 text-brand-cyan font-semibold shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            All
          </button>
          <button
            onClick={() => setFilterType('INITIATING')}
            className={`px-3 py-1.5 rounded-lg transition-all ${
              filterType === 'INITIATING' ? 'bg-brand-rose/20 text-brand-rose font-semibold border border-brand-rose/30 shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            Initiating Origin
          </button>
          <button
            onClick={() => setFilterType('ANOMALY')}
            className={`px-3 py-1.5 rounded-lg transition-all ${
              filterType === 'ANOMALY' ? 'bg-midnight-800 text-brand-cyan font-semibold shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            Anomalies
          </button>
          <button
            onClick={() => setFilterType('LOG')}
            className={`px-3 py-1.5 rounded-lg transition-all ${
              filterType === 'LOG' ? 'bg-midnight-800 text-brand-cyan font-semibold shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            Logs
          </button>
          <button
            onClick={() => setFilterType('CHANGE')}
            className={`px-3 py-1.5 rounded-lg transition-all ${
              filterType === 'CHANGE' ? 'bg-midnight-800 text-brand-cyan font-semibold shadow' : 'text-slate-400 hover:text-white'
            }`}
          >
            Deployments
          </button>
        </div>
      </div>

      {/* Timeline Stream */}
      <div className="relative pl-6 space-y-6 before:absolute before:left-3 before:top-2 before:bottom-2 before:w-0.5 before:bg-midnight-800">
        {filteredEvents.map((event, idx) => {
          const isInit = event.isInitiatingEvent;
          return (
            <div key={event.id || idx} className="relative group">
              {/* Dot */}
              <div className={`absolute -left-6 top-1.5 w-6 h-6 rounded-full flex items-center justify-center -translate-x-1/2 transition-transform group-hover:scale-110 ${
                isInit
                  ? 'bg-brand-rose ring-4 ring-brand-rose/30 shadow-lg shadow-brand-rose/40'
                  : event.severity === 'CRITICAL'
                  ? 'bg-brand-rose/20 border border-brand-rose'
                  : event.eventType === 'CHANGE'
                  ? 'bg-brand-cyan/20 border border-brand-cyan'
                  : 'bg-midnight-800 border border-midnight-700'
              }`}>
                {getEventIcon(event)}
              </div>

              {/* Event Card */}
              <div className={`p-4 rounded-xl border transition-all ${
                isInit
                  ? 'bg-brand-rose/10 border-brand-rose/50 ring-1 ring-brand-rose/30 shadow-lg shadow-brand-rose/5'
                  : 'bg-midnight-850/80 border-midnight-800 hover:border-midnight-700'
              }`}>
                <div className="flex flex-wrap items-center justify-between gap-2 mb-1.5">
                  <div className="flex items-center gap-2">
                    {isInit && (
                      <span className="text-[10px] px-2 py-0.5 rounded-full bg-brand-rose text-white font-bold tracking-wide uppercase animate-pulse">
                        Originating Trigger
                      </span>
                    )}
                    <span className="font-mono text-xs font-semibold text-brand-sky">
                      {event.service}
                    </span>
                    <span className="text-[11px] px-2 py-0.5 rounded bg-midnight-800 text-slate-400 font-mono">
                      {event.eventType}
                    </span>
                  </div>

                  <span className="font-mono text-xs text-slate-400">
                    {formatTime(event.timestamp)}
                  </span>
                </div>

                <h4 className="text-sm font-semibold text-white">{event.summary}</h4>
                <p className="text-xs text-slate-400 mt-1 font-mono leading-relaxed bg-midnight-950/60 p-2.5 rounded-lg border border-midnight-800/80">
                  {event.details}
                </p>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
