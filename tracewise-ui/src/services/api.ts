import type {
  IncidentRequest,
  IncidentResponse,
  ScenarioCatalogItem,
  EvaluationResult,
  SystemHealthResponse
} from '../types/incident';

const RAW_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081';
const API_BASE_URL = RAW_BASE_URL.replace(/\/+$/, '');

export class ApiClient {
  private static async request<T>(endpoint: string, options?: RequestInit): Promise<T> {
    const url = `${API_BASE_URL}${endpoint}`;
    try {
      const response = await fetch(url, {
        headers: {
          'Content-Type': 'application/json',
          ...options?.headers,
        },
        ...options,
      });

      if (!response.ok) {
        let errMessage = `HTTP Error ${response.status}: ${response.statusText}`;
        try {
          const errorJson = await response.json();
          if (errorJson.message) {
            errMessage = errorJson.message;
            if (errorJson.details && errorJson.details.length > 0) {
              errMessage += ` (${errorJson.details.join(', ')})`;
            }
          }
        } catch {
          // ignore parsing error
        }
        throw new Error(errMessage);
      }

      return await response.json();
    } catch (err: any) {
      console.warn(`API request to ${url} failed:`, err.message);
      throw err;
    }
  }

  static async analyzeIncident(request: IncidentRequest): Promise<IncidentResponse> {
    return this.request<IncidentResponse>('/api/v1/incidents/analyze', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  static async getScenarioCatalog(): Promise<ScenarioCatalogItem[]> {
    return this.request<ScenarioCatalogItem[]>('/api/v1/incidents/demo/catalog');
  }

  static async getDemoIncident(scenarioId: string): Promise<IncidentRequest> {
    return this.request<IncidentRequest>(`/api/v1/incidents/demo/${scenarioId}`);
  }

  static async runDemoIncident(scenarioId: string): Promise<IncidentResponse> {
    return this.request<IncidentResponse>(`/api/v1/incidents/demo/${scenarioId}/run`, {
      method: 'POST',
    });
  }

  static async uploadTelemetryFile(file: File): Promise<IncidentResponse> {
    const formData = new FormData();
    formData.append('file', file);

    const url = `${API_BASE_URL}/api/v1/incidents/upload`;
    try {
      const response = await fetch(url, {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        let errMessage = `Upload failed with status ${response.status}`;
        try {
          const errJson = await response.json();
          if (errJson.message) {
            errMessage = errJson.message;
            if (errJson.details && errJson.details.length > 0) {
              errMessage += ` (${errJson.details.join(', ')})`;
            }
          }
        } catch {
          // ignore parsing error
        }
        throw new Error(errMessage);
      }

      return await response.json();
    } catch (err: any) {
      console.warn(`API upload request to ${url} failed:`, err.message);
      throw err;
    }
  }

  static async getRecentIncidents(): Promise<IncidentResponse[]> {
    return this.request<IncidentResponse[]>('/api/v1/incidents');
  }

  static async getIncidentById(incidentId: string): Promise<IncidentResponse> {
    return this.request<IncidentResponse>(`/api/v1/incidents/${incidentId}`);
  }

  static async getIncidentReportMarkdown(incidentId: string): Promise<string> {
    const url = `${API_BASE_URL}/api/v1/incidents/${incidentId}/report`;
    try {
      const response = await fetch(url);
      if (!response.ok) {
        let errMessage = `Failed to fetch report: ${response.statusText}`;
        try {
          const errJson = await response.json();
          if (errJson.message) errMessage = errJson.message;
        } catch {
          // ignore
        }
        throw new Error(errMessage);
      }
      return await response.text();
    } catch (err: any) {
      console.warn(`API report request to ${url} failed:`, err.message);
      throw err;
    }
  }

  static async getEvaluation(): Promise<EvaluationResult> {
    return this.request<EvaluationResult>('/api/v1/evaluation');
  }

  static async getHealth(): Promise<SystemHealthResponse> {
    return this.request<SystemHealthResponse>('/api/v1/health');
  }
}
