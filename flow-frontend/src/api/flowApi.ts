import axios from 'axios';
import { FlowDefinition, FlowResult, NodeResult } from '../types/flow';

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 60000,
});

export const flowApi = {
  list: async (): Promise<FlowDefinition[]> => {
    const response = await api.get('/flows');
    return response.data;
  },

  get: async (flowId: string): Promise<FlowDefinition> => {
    const response = await api.get(`/flows/${flowId}`);
    return response.data;
  },

  create: async (flow: FlowDefinition): Promise<string> => {
    const response = await api.post('/flows', flow);
    return response.data;
  },

  update: async (flowId: string, flow: FlowDefinition): Promise<void> => {
    await api.put(`/flows/${flowId}`, flow);
  },

  delete: async (flowId: string): Promise<void> => {
    await api.delete(`/flows/${flowId}`);
  },

  execute: async (flowId: string, input: Record<string, any>, mode: string = 'SYNC'): Promise<FlowResult> => {
    const response = await api.post(`/flows/${flowId}/execute`, { input, mode });
    return response.data;
  },

  getExecution: async (executionId: string): Promise<FlowResult> => {
    const response = await api.get(`/executions/${executionId}`);
    return response.data;
  },

  getExecutionLogs: async (executionId: string): Promise<string[]> => {
    const response = await api.get(`/executions/${executionId}/logs`);
    return response.data;
  },

  getNodeRuntime: async (executionId: string, nodeId: string): Promise<NodeResult> => {
    const response = await api.get(`/executions/${executionId}/nodes/${nodeId}/runtime`);
    return response.data;
  },
};

export default api;
