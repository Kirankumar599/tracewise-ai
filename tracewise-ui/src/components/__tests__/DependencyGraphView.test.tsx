import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import React from 'react';
import { DependencyGraphView } from '../DependencyGraphView';
import { DependencyGraph } from '../../types/incident';

describe('DependencyGraphView Inferred Edges and Resilience', () => {
  it('renders explicit edges when provided in telemetry', () => {
    const graphWithEdges: DependencyGraph = {
      nodes: [
        { id: 'web-portal', name: 'web-portal', tier: 'GATEWAY', status: 'DEGRADED', anomalyScore: 0.5, isRootCause: false, incomingErrors: 1 },
        { id: 'auth-service', name: 'auth-service', tier: 'SERVICE', status: 'FAILING', anomalyScore: 0.9, isRootCause: true, incomingErrors: 5 }
      ],
      edges: [
        { from: 'web-portal', to: 'auth-service', status: 'BLOCKED', errorRate: 35.0, latencyMs: 2500.0, isCriticalPath: true }
      ]
    };

    render(<DependencyGraphView graph={graphWithEdges} primaryRootCauseService="auth-service" />);

    expect(screen.getByText(/2 Services • 1 Edges/)).toBeDefined();
    expect(screen.getByText('web-portal')).toBeDefined();
    expect(screen.getByText('auth-service')).toBeDefined();
  });

  it('infers and renders architectural edges when 0 edges are provided (e.g. raw CSV telemetry upload)', () => {
    const graphWithoutEdges: DependencyGraph = {
      nodes: [
        { id: 'api-gateway', name: 'api-gateway', tier: 'GATEWAY', status: 'DEGRADED', anomalyScore: 0.4, isRootCause: false, incomingErrors: 0 },
        { id: 'order-service', name: 'order-service', tier: 'SERVICE', status: 'DEGRADED', anomalyScore: 0.6, isRootCause: false, incomingErrors: 2 },
        { id: 'postgres-db', name: 'postgres-db', tier: 'DATABASE', status: 'FAILING', anomalyScore: 0.95, isRootCause: true, incomingErrors: 4 },
        { id: 'redis-cache', name: 'redis-cache', tier: 'CACHE', status: 'HEALTHY', anomalyScore: 0.1, isRootCause: false, incomingErrors: 0 }
      ],
      edges: [] // 0 explicit edges in input
    };

    render(<DependencyGraphView graph={graphWithoutEdges} primaryRootCauseService="postgres-db" />);

    expect(screen.getByText(/Inferred Topology/)).toBeDefined();
    expect(screen.getByText(/4 Services • [1-9] Edges/)).toBeDefined();
    expect(screen.getByText(/Inferred Architectural Flow:/)).toBeDefined();
    expect(screen.getByText(/Inferred Flow/)).toBeDefined();
    expect(screen.getByText(/Observed Trace/)).toBeDefined();
  });

  it('clearly distinguishes inferred edges when inferredTopology is true from backend', () => {
    const graphInferred: DependencyGraph = {
      nodes: [
        { id: 'api-gateway', name: 'api-gateway', tier: 'GATEWAY', status: 'DEGRADED', anomalyScore: 0.4, isRootCause: false, incomingErrors: 0 },
        { id: 'postgres-db', name: 'postgres-db', tier: 'DATABASE', status: 'FAILING', anomalyScore: 0.95, isRootCause: true, incomingErrors: 4 }
      ],
      edges: [
        { from: 'api-gateway', to: 'postgres-db', status: 'NORMAL', errorRate: 0.0, latencyMs: 50.0, isCriticalPath: false, isInferred: true }
      ],
      inferredTopology: true
    };

    const { container } = render(<DependencyGraphView graph={graphInferred} primaryRootCauseService="postgres-db" />);

    expect(screen.getByText(/Inferred Topology/)).toBeDefined();
    expect(screen.getByText(/Inferred Architectural Flow:/)).toBeDefined();

    // Check SVG paths have stroke-dasharray attribute for inferred edge
    const paths = container.querySelectorAll('path[stroke-dasharray="4 4"]');
    expect(paths.length).toBeGreaterThan(0);
  });
});
