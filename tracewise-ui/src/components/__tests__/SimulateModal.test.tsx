import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import React from 'react';
import { SimulateModal } from '../SimulateModal';
import { ScenarioCatalogItem } from '../../types/incident';

const mockScenarios: ScenarioCatalogItem[] = [
  {
    id: 'db-pool-exhaustion',
    title: 'Database Connection Pool Exhaustion',
    description: 'Postgres active connections hit 100/100 limit',
    severity: 'CRITICAL',
    affectedTopology: '4 Services (postgres-db, order-service, payment-service, api-gateway)',
    groundTruthCategory: 'DATABASE_EXHAUSTION',
    groundTruthService: 'postgres-db'
  },
  {
    id: 'cpu-saturation',
    title: 'CPU Saturation via Regressive Deployment',
    description: 'Auth Service CPU surges to 98.5%',
    severity: 'HIGH',
    affectedTopology: '4 Services (auth-service, web-portal, user-service, redis-session-store)',
    groundTruthCategory: 'RESOURCE_SATURATION',
    groundTruthService: 'auth-service'
  }
];

describe('SimulateModal Workflow', () => {
  it('renders scenarios and highlights default selected scenario', () => {
    render(
      <SimulateModal
        isOpen={true}
        onClose={vi.fn()}
        onSelectScenario={vi.fn().mockResolvedValue(undefined)}
        scenarios={mockScenarios}
        isLoading={false}
      />
    );

    expect(screen.getByText('Simulate Cascading Incident')).toBeDefined();
    expect(screen.getByText('Database Connection Pool Exhaustion')).toBeDefined();
    expect(screen.getByText('CPU Saturation via Regressive Deployment')).toBeDefined();

    // Default scenario has "Selected" badge
    expect(screen.getByText('Selected')).toBeDefined();
  });

  it('visibly changes selection when clicking a different scenario card', () => {
    render(
      <SimulateModal
        isOpen={true}
        onClose={vi.fn()}
        onSelectScenario={vi.fn().mockResolvedValue(undefined)}
        scenarios={mockScenarios}
        isLoading={false}
      />
    );

    const cpuCard = screen.getByText('CPU Saturation via Regressive Deployment').closest('[role="radio"]');
    expect(cpuCard).not.toBeNull();
    fireEvent.click(cpuCard!);

    // Now cpu-saturation should be selected
    expect(cpuCard?.getAttribute('aria-checked')).toBe('true');
  });

  it('successfully starts scenario and closes modal on Launch Investigation click', async () => {
    const handleSelectScenario = vi.fn().mockResolvedValue(undefined);
    const handleClose = vi.fn();

    render(
      <SimulateModal
        isOpen={true}
        onClose={handleClose}
        onSelectScenario={handleSelectScenario}
        scenarios={mockScenarios}
        isLoading={false}
      />
    );

    // Select second scenario
    const cpuCard = screen.getByText('CPU Saturation via Regressive Deployment').closest('[role="radio"]');
    fireEvent.click(cpuCard!);

    // Click Launch Investigation
    const launchBtn = screen.getByRole('button', { name: /launch investigation/i });
    fireEvent.click(launchBtn);

    await waitFor(() => {
      expect(handleSelectScenario).toHaveBeenCalledWith('cpu-saturation');
      expect(handleClose).toHaveBeenCalledTimes(1);
    });
  });

  it('triggers immediate execution when clicking Run button on card', async () => {
    const handleSelectScenario = vi.fn().mockResolvedValue(undefined);
    const handleClose = vi.fn();

    render(
      <SimulateModal
        isOpen={true}
        onClose={handleClose}
        onSelectScenario={handleSelectScenario}
        scenarios={mockScenarios}
        isLoading={false}
      />
    );

    const runButtons = screen.getAllByRole('button', { name: /run/i });
    // Click the Run button on the second card
    fireEvent.click(runButtons[1]);

    await waitFor(() => {
      expect(handleSelectScenario).toHaveBeenCalledWith('cpu-saturation');
      expect(handleClose).toHaveBeenCalledTimes(1);
    });
  });

  it('shows loading state while simulation is in flight', async () => {
    let resolvePromise: () => void = () => {};
    const pendingPromise = new Promise<void>((resolve) => {
      resolvePromise = resolve;
    });
    const handleSelectScenario = vi.fn().mockReturnValue(pendingPromise);

    render(
      <SimulateModal
        isOpen={true}
        onClose={vi.fn()}
        onSelectScenario={handleSelectScenario}
        scenarios={mockScenarios}
        isLoading={false}
      />
    );

    const launchBtn = screen.getByRole('button', { name: /launch investigation/i });
    fireEvent.click(launchBtn);

    // While in flight, loading text should be visible and button disabled
    expect(screen.getByText(/running anomaly engine/i)).toBeDefined();
    expect(launchBtn.getAttribute('disabled')).not.toBeNull();

    // Resolve the promise inside act
    await act(async () => {
      resolvePromise();
      await pendingPromise;
    });
  });

  it('handles API error, keeps modal open, displays error feedback, and allows retry', async () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const handleSelectScenario = vi.fn()
      .mockRejectedValueOnce(new Error('HTTP Error 504: Gateway Timeout'))
      .mockResolvedValueOnce(undefined);
    const handleClose = vi.fn();

    render(
      <SimulateModal
        isOpen={true}
        onClose={handleClose}
        onSelectScenario={handleSelectScenario}
        scenarios={mockScenarios}
        isLoading={false}
      />
    );

    const launchBtn = screen.getByRole('button', { name: /launch investigation/i });
    fireEvent.click(launchBtn);

    // Modal must NOT close on error
    await waitFor(() => {
      expect(handleClose).not.toHaveBeenCalled();
      expect(screen.getByText('Simulation Request Failed')).toBeDefined();
      expect(screen.getByText(/HTTP Error 504: Gateway Timeout/)).toBeDefined();
    });

    // Click Retry
    const retryBtn = screen.getByRole('button', { name: /retry/i });
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(handleSelectScenario).toHaveBeenCalledTimes(2);
      expect(handleClose).toHaveBeenCalledTimes(1);
    });

    consoleSpy.mockRestore();
  });
});
