import React, { useState } from 'react';
import {
  DependencyGraph,
  GraphNode
} from '../types/incident';
import {
  Server,
  Database,
  Globe,
  Radio,
  Layers
} from 'lucide-react';

interface DependencyGraphViewProps {
  graph: DependencyGraph;
  primaryRootCauseService?: string;
  onSelectService?: (serviceName: string) => void;
}

export const DependencyGraphView: React.FC<DependencyGraphViewProps> = ({
  graph,
  primaryRootCauseService,
  onSelectService
}) => {
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);

  if (!graph || !graph.nodes || graph.nodes.length === 0) {
    return (
      <div className="p-8 text-center text-slate-500 bg-midnight-900 border border-midnight-800 rounded-2xl">
        No dependency graph data available for this incident.
      </div>
    );
  }

  // Calculate layout coordinates for nodes by tier
  // Tier positions along X-axis: GATEWAY (100) -> SERVICE (350) -> DATABASE/CACHE/EXTERNAL (650)
  const tierX: Record<string, number> = {
    GATEWAY: 120,
    SERVICE: 380,
    DATABASE: 660,
    CACHE: 660,
    EXTERNAL: 660
  };

  // Group nodes by tier to compute vertical distribution
  const nodesByTier: Record<string, GraphNode[]> = {
    GATEWAY: [],
    SERVICE: [],
    DATABASE: [],
    CACHE: [],
    EXTERNAL: []
  };

  graph.nodes.forEach(node => {
    const tier = node.tier && tierX[node.tier] ? node.tier : 'SERVICE';
    if (!nodesByTier[tier]) nodesByTier[tier] = [];
    nodesByTier[tier].push(node);
  });

  const nodePositions: Record<string, { x: number; y: number }> = {};
  const svgHeight = 440;
  const svgWidth = 800;

  Object.entries(nodesByTier).forEach(([tier, tierNodes]) => {
    const x = tierX[tier] || 380;
    const count = tierNodes.length;
    tierNodes.forEach((node, idx) => {
      const step = svgHeight / (count + 1);
      const y = (idx + 1) * step;
      nodePositions[node.id] = { x, y };
      nodePositions[node.id.toLowerCase().trim()] = { x, y };
      if (node.name) {
        nodePositions[node.name.toLowerCase().trim()] = { x, y };
      }
    });
  });

  const getNodePos = (id: string) => {
    if (!id) return undefined;
    if (nodePositions[id]) return nodePositions[id];
    const normalized = id.toLowerCase().trim();
    if (nodePositions[normalized]) return nodePositions[normalized];
    const matchKey = Object.keys(nodePositions).find(k => k.toLowerCase().trim() === normalized);
    return matchKey ? nodePositions[matchKey] : undefined;
  };

  // Derive inferred edges if telemetry or upload contained no explicit dependency edges
  const rawEdges = graph.edges || [];
  let effectiveEdges = [...rawEdges];
  const hasInferredEdges = effectiveEdges.length === 0 && graph.nodes.length > 1;

  if (hasInferredEdges) {
    const gateways = graph.nodes.filter(n => n.tier === 'GATEWAY');
    const services = graph.nodes.filter(n => n.tier === 'SERVICE');
    const backends = graph.nodes.filter(n => ['DATABASE', 'CACHE', 'EXTERNAL'].includes(n.tier));

    gateways.forEach(gw => {
      services.forEach(svc => {
        effectiveEdges.push({
          from: gw.id,
          to: svc.id,
          status: svc.status === 'FAILING' ? 'BLOCKED' : 'NORMAL',
          errorRate: svc.status === 'FAILING' ? 18.0 : 0.0,
          latencyMs: svc.status === 'FAILING' ? 850.0 : 35.0,
          isCriticalPath: svc.isRootCause
        });
      });
    });

    services.forEach(svc => {
      backends.forEach(backend => {
        const isCrit = backend.isRootCause || backend.status === 'FAILING';
        effectiveEdges.push({
          from: svc.id,
          to: backend.id,
          status: isCrit ? 'BLOCKED' : 'NORMAL',
          errorRate: isCrit ? 28.0 : 0.0,
          latencyMs: isCrit ? 1400.0 : 40.0,
          isCriticalPath: isCrit
        });
      });
    });

    if (effectiveEdges.length === 0) {
      for (let i = 0; i < graph.nodes.length - 1; i++) {
        effectiveEdges.push({
          from: graph.nodes[i].id,
          to: graph.nodes[i + 1].id,
          status: 'NORMAL',
          errorRate: 0.0,
          latencyMs: 45.0,
          isCriticalPath: false
        });
      }
    }
  }

  const getNodeIcon = (tier: string) => {
    switch (tier) {
      case 'GATEWAY':
        return <Globe className="w-4 h-4 text-brand-cyan" />;
      case 'DATABASE':
        return <Database className="w-4 h-4 text-purple-400" />;
      case 'CACHE':
        return <Radio className="w-4 h-4 text-emerald-400" />;
      case 'EXTERNAL':
        return <Layers className="w-4 h-4 text-amber-400" />;
      default:
        return <Server className="w-4 h-4 text-brand-sky" />;
    }
  };

  const getStatusColor = (status: string, isRoot: boolean) => {
    if (isRoot) return {
      border: 'border-brand-rose',
      bg: 'bg-brand-rose/10',
      text: 'text-brand-rose',
      glow: 'shadow-lg shadow-brand-rose/20 ring-2 ring-brand-rose/50 animate-pulse'
    };
    if (status === 'FAILING') return {
      border: 'border-brand-rose/70',
      bg: 'bg-brand-rose/5',
      text: 'text-brand-rose',
      glow: 'shadow-md shadow-brand-rose/10'
    };
    if (status === 'DEGRADED') return {
      border: 'border-brand-amber/70',
      bg: 'bg-brand-amber/5',
      text: 'text-brand-amber',
      glow: 'shadow-md shadow-brand-amber/10'
    };
    return {
      border: 'border-midnight-700',
      bg: 'bg-midnight-850',
      text: 'text-slate-300',
      glow: ''
    };
  };

  const selectedNode = graph.nodes.find(n => n.id === selectedNodeId);

  return (
    <div className="bg-midnight-900 border border-midnight-800 rounded-2xl overflow-hidden shadow-xl">
      {/* Header & Legend */}
      <div className="px-6 py-4 border-b border-midnight-800 bg-midnight-850/60 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 className="font-semibold text-white flex items-center gap-2">
            <span>Service Dependency Impact Graph</span>
            <span className="text-xs px-2 py-0.5 rounded-full bg-midnight-800 text-slate-400 font-mono">
              {graph.nodes.length} Services • {effectiveEdges.length} Edges
              {hasInferredEdges && <span className="ml-1.5 text-brand-cyan font-sans text-[10px] bg-brand-cyan/10 px-1.5 py-0.5 rounded border border-brand-cyan/20">Inferred Topology</span>}
            </span>
          </h3>
          <p className="text-xs text-slate-400 mt-0.5">
            Topology propagation direction showing cascading degradation from initiating failure to downstream callers
          </p>
        </div>

        {/* Legend */}
        <div className="flex items-center gap-3 text-xs text-slate-300">
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-brand-rose shadow-sm shadow-brand-rose/50" />
            <span>Root Cause / Critical</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-brand-amber" />
            <span>Degraded</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-brand-emerald" />
            <span>Healthy</span>
          </div>
        </div>
      </div>

      {/* SVG Canvas */}
      <div className="relative p-4 overflow-x-auto flex justify-center bg-grid-pattern">
        <svg
          viewBox={`0 0 ${svgWidth} ${svgHeight}`}
          className="w-full max-w-4xl h-[400px] select-none"
        >
          <defs>
            {/* Arrow markers */}
            <marker
              id="arrow-normal"
              viewBox="0 0 10 10"
              refX="18"
              refY="5"
              markerWidth="6"
              markerHeight="6"
              orient="auto-angle"
            >
              <path d="M 0 1 L 10 5 L 0 9 z" fill="#334155" />
            </marker>
            <marker
              id="arrow-critical"
              viewBox="0 0 10 10"
              refX="18"
              refY="5"
              markerWidth="6"
              markerHeight="6"
              orient="auto-angle"
            >
              <path d="M 0 1 L 10 5 L 0 9 z" fill="#EF4444" />
            </marker>
            <marker
              id="arrow-degraded"
              viewBox="0 0 10 10"
              refX="18"
              refY="5"
              markerWidth="6"
              markerHeight="6"
              orient="auto-angle"
            >
              <path d="M 0 1 L 10 5 L 0 9 z" fill="#F59E0B" />
            </marker>
          </defs>

          {/* Column Background Tier Guides */}
          <text x="120" y="30" textAnchor="middle" fill="#64748B" fontSize="11" fontFamily="sans-serif" fontWeight="600">INGRESS / GATEWAY</text>
          <text x="380" y="30" textAnchor="middle" fill="#64748B" fontSize="11" fontFamily="sans-serif" fontWeight="600">APPLICATION SERVICES</text>
          <text x="660" y="30" textAnchor="middle" fill="#64748B" fontSize="11" fontFamily="sans-serif" fontWeight="600">DATA & EXTERNAL</text>

          <line x1="250" y1="40" x2="250" y2="420" stroke="#1E293B" strokeDasharray="4 4" />
          <line x1="520" y1="40" x2="520" y2="420" stroke="#1E293B" strokeDasharray="4 4" />

          {/* Edges */}
          {effectiveEdges.map((edge, idx) => {
            const fromPos = getNodePos(edge.from);
            const toPos = getNodePos(edge.to);
            if (!fromPos || !toPos) return null;

            const isCritical = edge.isCriticalPath || edge.status === 'BLOCKED';
            const isDegraded = edge.status === 'DEGRADED';
            const marker = isCritical ? 'url(#arrow-critical)' : isDegraded ? 'url(#arrow-degraded)' : 'url(#arrow-normal)';
            const strokeColor = isCritical ? '#EF4444' : isDegraded ? '#F59E0B' : '#334155';

            // Curved cubic bezier line
            const dx = toPos.x - fromPos.x;
            const cx1 = fromPos.x + dx * 0.5;
            const cy1 = fromPos.y;
            const cx2 = fromPos.x + dx * 0.5;
            const cy2 = toPos.y;
            const pathD = `M ${fromPos.x} ${fromPos.y} C ${cx1} ${cy1}, ${cx2} ${cy2}, ${toPos.x} ${toPos.y}`;

            return (
              <g key={`edge-${idx}`}>
                <path
                  d={pathD}
                  fill="none"
                  stroke={strokeColor}
                  strokeWidth={isCritical ? "2.5" : "1.5"}
                  strokeDasharray={isCritical ? "6 3" : undefined}
                  className={isCritical ? "animate-pulse" : ""}
                  markerEnd={marker}
                />
                {/* Edge Label on hover or if degraded */}
                {(isCritical || isDegraded) && (
                  <rect
                    x={(fromPos.x + toPos.x) / 2 - 25}
                    y={(fromPos.y + toPos.y) / 2 - 10}
                    width="50"
                    height="18"
                    rx="4"
                    fill="#0B0F19"
                    stroke={strokeColor}
                    strokeWidth="1"
                  />
                )}
                {(isCritical || isDegraded) && (
                  <text
                    x={(fromPos.x + toPos.x) / 2}
                    y={(fromPos.y + toPos.y) / 2 + 3}
                    textAnchor="middle"
                    fill={strokeColor}
                    fontSize="9"
                    fontFamily="monospace"
                  >
                    {edge.latencyMs.toFixed(0)}ms
                  </text>
                )}
              </g>
            );
          })}

          {/* Nodes */}
          {graph.nodes.map(node => {
            const pos = nodePositions[node.id];
            if (!pos) return null;

            const isRoot = node.isRootCause || node.id === primaryRootCauseService;
            const _style = getStatusColor(node.status, isRoot);
            const isSelected = selectedNodeId === node.id;

            return (
              <g
                key={node.id}
                transform={`translate(${pos.x - 70}, ${pos.y - 30})`}
                onClick={() => {
                  setSelectedNodeId(node.id);
                  onSelectService?.(node.id);
                }}
                className="cursor-pointer group"
              >
                {/* Node Box */}
                <rect
                  width="140"
                  height="60"
                  rx="12"
                  fill="#0F1626"
                  stroke={isRoot ? "#EF4444" : isSelected ? "#06B6D4" : node.status === 'FAILING' ? "#EF4444" : node.status === 'DEGRADED' ? "#F59E0B" : "#1E293B"}
                  strokeWidth={isRoot || isSelected ? "2.5" : "1.5"}
                  className="transition-all duration-200 group-hover:stroke-brand-cyan"
                />

                {/* Root Cause Badge */}
                {isRoot && (
                  <g transform="translate(10, -8)">
                    <rect width="80" height="16" rx="4" fill="#EF4444" />
                    <text x="40" y="11" textAnchor="middle" fill="#FFFFFF" fontSize="8" fontWeight="bold">ROOT CAUSE</text>
                  </g>
                )}

                {/* Node Text & Status */}
                <text
                  x="70"
                  y="26"
                  textAnchor="middle"
                  fill="#FFFFFF"
                  fontSize="12"
                  fontWeight="600"
                  className="pointer-events-none"
                >
                  {node.name.length > 15 ? node.name.substring(0, 13) + '..' : node.name}
                </text>

                <text
                  x="70"
                  y="44"
                  textAnchor="middle"
                  fill={isRoot ? "#FCA5A5" : node.status === 'FAILING' ? "#FCA5A5" : node.status === 'DEGRADED' ? "#FDE68A" : "#64748B"}
                  fontSize="10"
                  fontFamily="monospace"
                  className="pointer-events-none"
                >
                  {isRoot ? 'INITIATING ORIGIN' : node.status}
                </text>
              </g>
            );
          })}
        </svg>
      </div>

      {/* Selected Node Details Drawer */}
      {selectedNode && (
        <div className="px-6 py-3.5 border-t border-midnight-800 bg-midnight-850/80 flex flex-wrap items-center justify-between gap-4 animate-in fade-in">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-midnight-800 border border-midnight-700">
              {getNodeIcon(selectedNode.tier)}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-semibold text-white">{selectedNode.name}</span>
                <span className="text-xs px-2 py-0.5 rounded-full bg-midnight-800 text-slate-300 font-mono">
                  Tier: {selectedNode.tier}
                </span>
                {selectedNode.isRootCause && (
                  <span className="text-xs px-2 py-0.5 rounded-full bg-brand-rose/20 text-brand-rose border border-brand-rose/30 font-semibold">
                    Primary Origin
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-400 mt-0.5">
                Status: <span className="font-medium text-slate-200">{selectedNode.status}</span> • Error logs logged: <span className="font-mono text-brand-amber">{selectedNode.incomingErrors}</span>
              </p>
            </div>
          </div>
          <button
            onClick={() => onSelectService?.(selectedNode.id)}
            className="text-xs px-3 py-1.5 rounded-lg bg-midnight-800 hover:bg-midnight-700 text-brand-cyan border border-brand-cyan/20 transition-colors"
          >
            Inspect Metrics in Chart
          </button>
        </div>
      )}
    </div>
  );
};
