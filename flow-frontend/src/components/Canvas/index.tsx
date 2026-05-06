import React, { useCallback } from 'react';
import ReactFlow, {
  Background,
  Controls,
  MiniMap,
  addEdge,
  Connection,
  Edge,
  Node,
  OnConnect,
  OnNodesChange,
  OnEdgesChange,
  applyNodeChanges,
  applyEdgeChanges,
} from 'reactflow';
import 'reactflow/dist/style.css';
import { message } from 'antd';
import { useFlowStore } from '../../stores/flowStore';
import { NodeType } from '../../types/flow';
import CustomNode from './CustomNode';

const nodeTypes = {
  custom: CustomNode,
};

interface CanvasProps {
  onNodeClick: (node: Node) => void;
}

const Canvas: React.FC<CanvasProps> = ({ onNodeClick }) => {
  const {
    nodes,
    edges,
    addNode,
    addEdge: storeAddEdge,
    onNodesChange: storeOnNodesChange,
    onEdgesChange: storeOnEdgesChange,
    setSelectedNode,
  } = useFlowStore();

  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();

      const type = event.dataTransfer.getData('application/reactflow') as NodeType;
      if (!type) return;

      const reactFlowBounds = event.currentTarget.getBoundingClientRect();
      const position = {
        x: event.clientX - reactFlowBounds.left - 70,
        y: event.clientY - reactFlowBounds.top - 25,
      };

      addNode(type, position);
    },
    [addNode]
  );

  const onConnect: OnConnect = useCallback(
    (params: Connection) => {
      if (params.source && params.target) {
        storeAddEdge(params.source, params.target);
      }
    },
    [storeAddEdge]
  );

  const onNodesChange: OnNodesChange = useCallback(
    (changes) => {
      const newNodes = applyNodeChanges(changes, nodes);
      storeOnNodesChange?.(newNodes as any);
    },
    [nodes, storeOnNodesChange]
  );

  const onEdgesChange: OnEdgesChange = useCallback(
    (changes) => {
      const newEdges = applyEdgeChanges(changes, edges);
      storeOnEdgesChange?.(newEdges as any);
    },
    [edges, storeOnEdgesChange]
  );

  const onNodeClick = useCallback(
    (_: React.MouseEvent, node: Node) => {
      setSelectedNode(node);
      onNodeClick(node);
    },
    [setSelectedNode, onNodeClick]
  );

  return (
    <div className="canvas-wrapper" onDrop={onDrop} onDragOver={onDragOver}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onNodeClick={onNodeClick}
        nodeTypes={nodeTypes}
        fitView
        snapToGrid
        snapGrid={[16, 16]}
        defaultEdgeOptions={{
          style: { strokeWidth: 2 },
          type: 'smoothstep',
        }}
      >
        <Background color="#e8e8e8" gap={16} />
        <Controls />
        <MiniMap
          nodeColor={(node) => {
            switch (node.data.nodeType) {
              case NodeType.START: return '#52c41a';
              case NodeType.END: return '#f5222d';
              case NodeType.HTTP: return '#1890ff';
              case NodeType.ROUTER: return '#fa8c16';
              case NodeType.DB: return '#722ed1';
              case NodeType.FILE: return '#13c2c2';
              default: return '#d9d9d9';
            }
          }}
          maskColor="rgba(0, 0, 0, 0.1)"
        />
      </ReactFlow>
    </div>
  );
};

export default Canvas;
