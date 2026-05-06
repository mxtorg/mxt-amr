export enum NodeType {
  START = 'START',
  END = 'END',
  HTTP = 'HTTP',
  ROUTER = 'ROUTER',
  DB = 'DB',
  FILE = 'FILE'
}

export enum NodeStatus {
  INIT = 'INIT',
  RUNNING = 'RUNNING',
  SUCCESS = 'SUCCESS',
  FAILED = 'FAILED',
  SKIPPED = 'SKIPPED'
}

export enum DbOperationType {
  QUERY = 'QUERY',
  INSERT = 'INSERT',
  UPDATE = 'UPDATE',
  DELETE = 'DELETE'
}

export enum FileOperationType {
  READ = 'READ',
  WRITE = 'WRITE',
  DELETE = 'DELETE'
}

export enum RouterStrategyType {
  ALL = 'ALL',
  FIRST = 'FIRST',
  RANDOM = 'RANDOM'
}

export enum WaitMode {
  FORK_JOIN = 'FORK_JOIN',
  COMPLETABLE_FUTURE = 'COMPLETABLE_FUTURE'
}

export interface BaseNode {
  id: string;
  flowId?: string;
  pids: string[];
  nids: string[];
  nodeType: NodeType;
  name: string;
  description?: string;
  position: { x: number; y: number };
  status?: NodeStatus;
  createTime?: number;
  updateTime?: number;
}

export interface HttpNode extends BaseNode {
  nodeType: NodeType.HTTP;
  source: string;
  groupId: number;
  api: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  inputSchema: Record<string, any>;
  outputSchema: Record<string, any>;
  price?: number;
  timeout?: number;
}

export interface RouterNode extends BaseNode {
  nodeType: NodeType.ROUTER;
  mappingSchema: {
    mappings: Array<{
      source: string;
      target: string;
      transform?: string;
    }>;
  };
  requestIntercept?: Record<string, any>;
  responseIntercept?: Record<string, any>;
  strategySchema: {
    type: RouterStrategyType;
    waitMode: WaitMode;
    retryCount?: number;
    fallbackNodeId?: string;
  };
}

export interface DbNode extends BaseNode {
  nodeType: NodeType.DB;
  jdbcUrl: string;
  username: string;
  password: string;
  operationType: DbOperationType;
  sql: string;
  parameters?: Record<string, any>;
  asyncSave?: boolean;
}

export interface FileNode extends BaseNode {
  nodeType: NodeType.FILE;
  operationType: FileOperationType;
  filePath: string;
  contentType?: string;
}

export type FlowNode = HttpNode | RouterNode | DbNode | FileNode;

export interface Edge {
  id: string;
  source: string;
  target: string;
  condition?: string;
}

export interface FlowDefinition {
  id: string;
  name: string;
  description?: string;
  status?: number;
  version?: number;
  nodes: FlowNode[];
  edges: Edge[];
}

export interface NodeResult {
  success: boolean;
  output?: any;
  errorMsg?: string;
  durationMs?: number;
}

export interface FlowResult {
  executionId: string;
  status: 'SUCCESS' | 'FAILED' | 'TIMEOUT';
  output?: any;
  nodeResults?: Record<string, NodeResult>;
  totalDurationMs?: number;
}
