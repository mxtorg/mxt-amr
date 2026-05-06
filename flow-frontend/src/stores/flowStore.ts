import { create } from 'zustand';
import { Node, Edge } from 'reactflow';
import { v4 as uuidv4 } from 'uuid';
import { NodeType, FlowNode, Edge as FlowEdge, NodeStatus, RouterStrategyType, WaitMode, DbOperationType, FileOperationType } from '../types/flow';

interface FlowState {
  flowId: string;
  flowName: string;
  nodes: Node[];
  edges: Edge[];
  selectedNode: Node | null;
  isDrawerOpen: boolean;
  executionId: string | null;

  setFlowId: (id: string) => void;
  setFlowName: (name: string) => void;
  addNode: (type: NodeType, position: { x: number; y: number }) => void;
  updateNode: (id: string, data: Partial<FlowNode>) => void;
  removeNode: (id: string) => void;
  addEdge: (source: string, target: string) => void;
  removeEdge: (id: string) => void;
  setSelectedNode: (node: Node | null) => void;
  openDrawer: () => void;
  closeDrawer: () => void;
  setExecutionId: (id: string | null) => void;
  loadFlow: (flowId: string, flowName: string, nodes: Node[], edges: Edge[]) => void;
  validateDag: () => { valid: boolean; message?: string };
  getFlowDefinition: () => { id: string; name: string; nodes: FlowNode[]; edges: FlowEdge[] };
  resetFlow: () => void;
}

const getDefaultNodeData = (type: NodeType): Record<string, any> => {
  const base = {
    nodeType: type,
    label: getNodeLabel(type),
    name: getNodeLabel(type),
    pids: [],
    nids: [],
    description: '',
  };

  switch (type) {
    case NodeType.HTTP:
      return {
        ...base,
        source: '',
        groupId: 0,
        api: '',
        method: 'POST',
        inputSchema: { type: 'object', properties: {} },
        outputSchema: { type: 'object', properties: {} },
        price: 0,
        timeout: 30000,
      };
    case NodeType.ROUTER:
      return {
        ...base,
        mappingSchema: { mappings: [] },
        requestIntercept: {},
        responseIntercept: {},
        strategySchema: {
          type: RouterStrategyType.ALL,
          waitMode: WaitMode.COMPLETABLE_FUTURE,
          retryCount: 0,
        },
      };
    case NodeType.DB:
      return {
        ...base,
        jdbcUrl: '',
        username: '',
        password: '',
        operationType: DbOperationType.QUERY,
        sql: '',
        parameters: {},
        asyncSave: true,
      };
    case NodeType.FILE:
      return {
        ...base,
        operationType: FileOperationType.READ,
        filePath: '',
        contentType: 'application/json',
      };
    case NodeType.START:
    case NodeType.END:
    default:
      return base;
  }
};

const getNodeLabel = (type: NodeType): string => {
  const labels: Record<NodeType, string> = {
    [NodeType.START]: '开始',
    [NodeType.END]: '结束',
    [NodeType.HTTP]: 'HTTP请求',
    [NodeType.ROUTER]: '路由分发',
    [NodeType.DB]: '数据库',
    [NodeType.FILE]: '文件操作',
  };
  return labels[type];
};

const hasCycle = (nodes: Node[], edges: Edge[]): boolean => {
  const adj = new Map<string, string[]>();
  edges.forEach((e) => {
    if (!adj.has(e.source)) adj.set(e.source, []);
    adj.get(e.source)!.push(e.target);
  });

  const visited = new Set<string>();
  const recStack = new Set<string>();

  const dfs = (nodeId: string): boolean => {
    visited.add(nodeId);
    recStack.add(nodeId);

    const neighbors = adj.get(nodeId) || [];
    for (const neighbor of neighbors) {
      if (!visited.has(neighbor) && dfs(neighbor)) return true;
      if (recStack.has(neighbor)) return true;
    }

    recStack.delete(nodeId);
    return false;
  };

  for (const node of nodes) {
    if (!visited.has(node.id) && dfs(node.id)) return true;
  }
  return false;
};

export const useFlowStore = create<FlowState>((set, get) => ({
  flowId: uuidv4(),
  flowName: '未命名流程',
  nodes: [],
  edges: [],
  selectedNode: null,
  isDrawerOpen: false,
  executionId: null,

  setFlowId: (id) => set({ flowId: id }),

  setFlowName: (name) => set({ flowName: name }),

  addNode: (type, position) => {
    const id = `${type.toLowerCase()}_${uuidv4().slice(0, 8)}`;
    const newNode: Node = {
      id,
      type: 'custom',
      position,
      data: getDefaultNodeData(type),
    };
    set((state) => ({ nodes: [...state.nodes, newNode] }));
  },

  updateNode: (id, data) => {
    set((state) => ({
      nodes: state.nodes.map((n) =>
        n.id === id ? { ...n, data: { ...n.data, ...data } } : n
      ),
    }));
  },

  removeNode: (id) => {
    set((state) => ({
      nodes: state.nodes.filter((n) => n.id !== id),
      edges: state.edges.filter((e) => e.source !== id && e.target !== id),
      selectedNode: state.selectedNode?.id === id ? null : state.selectedNode,
    }));
  },

  addEdge: (source, target) => {
    const { edges, nodes } = get();
    const newEdges = [...edges, { id: `e_${source}_${target}`, source, target }];

    if (hasCycle(nodes, newEdges)) {
      console.warn('不能形成循环依赖');
      return;
    }

    set((state) => ({
      edges: newEdges,
      nodes: state.nodes.map((n) => {
        if (n.id === source) {
          return { ...n, data: { ...n.data, nids: [...(n.data.nids || []), target] } };
        }
        if (n.id === target) {
          return { ...n, data: { ...n.data, pids: [...(n.data.pids || []), source] } };
        }
        return n;
      }),
    }));
  },

  removeEdge: (id) => {
    const edge = get().edges.find((e) => e.id === id);
    if (edge) {
      set((state) => ({
        edges: state.edges.filter((e) => e.id !== id),
        nodes: state.nodes.map((n) => {
          if (n.id === edge.source) {
            return { ...n, data: { ...n.data, nids: n.data.nids.filter((nid: string) => nid !== edge.target) } };
          }
          if (n.id === edge.target) {
            return { ...n, data: { ...n.data, pids: n.data.pids.filter((pid: string) => pid !== edge.source) } };
          }
          return n;
        }),
      }));
    }
  },

  setSelectedNode: (node) => set({ selectedNode: node }),

  openDrawer: () => set({ isDrawerOpen: true }),

  closeDrawer: () => set({ isDrawerOpen: false, selectedNode: null }),

  setExecutionId: (id) => set({ executionId: id }),

  loadFlow: (flowId, flowName, nodes, edges) => {
    set({ flowId, flowName, nodes, edges, selectedNode: null, isDrawerOpen: false });
  },

  validateDag: () => {
    const { nodes, edges } = get();
    if (nodes.length === 0) return { valid: false, message: '流程为空' };

    const startNodes = nodes.filter((n) => n.data.nodeType === NodeType.START);
    if (startNodes.length === 0) return { valid: false, message: '缺少开始节点' };
    if (startNodes.length > 1) return { valid: false, message: '只能有一个开始节点' };

    const endNodes = nodes.filter((n) => n.data.nodeType === NodeType.END);
    if (endNodes.length === 0) return { valid: false, message: '缺少结束节点' };

    if (hasCycle(nodes, edges)) return { valid: false, message: '存在循环依赖' };

    const isolated = nodes.filter(
      (n) =>
        n.data.nodeType !== NodeType.START &&
        n.data.nodeType !== NodeType.END &&
        (!n.data.pids?.length && !n.data.nids?.length)
    );
    if (isolated.length > 0) return { valid: false, message: '存在孤立节点' };

    return { valid: true };
  },

  getFlowDefinition: () => {
    const { flowId, flowName, nodes, edges } = get();
    return {
      id: flowId,
      name: flowName,
      nodes: nodes.map((n) => ({ ...n.data, position: n.position })) as FlowNode[],
      edges: edges.map((e) => ({ id: e.id, source: e.source, target: e.target })) as FlowEdge[],
    };
  },

  resetFlow: () => {
    set({
      flowId: uuidv4(),
      flowName: '未命名流程',
      nodes: [],
      edges: [],
      selectedNode: null,
      isDrawerOpen: false,
      executionId: null,
    });
  },
}));
