import React, { memo } from 'react';
import { Handle, Position, NodeProps } from 'reactflow';
import { Badge, Tag, Tooltip } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';
import { NodeType, NodeStatus } from '../../types/flow';
import { PlayCircleOutlined, StopOutlined, ApiOutlined, BranchesOutlined, DatabaseOutlined, FileOutlined } from '@ant-design/icons';

const nodeConfig: Record<NodeType, { color: string; icon: React.ReactNode }> = {
  [NodeType.START]: { color: '#52c41a', icon: <PlayCircleOutlined /> },
  [NodeType.END]: { color: '#f5222d', icon: <StopOutlined /> },
  [NodeType.HTTP]: { color: '#1890ff', icon: <ApiOutlined /> },
  [NodeType.ROUTER]: { color: '#fa8c16', icon: <BranchesOutlined /> },
  [NodeType.DB]: { color: '#722ed1', icon: <DatabaseOutlined /> },
  [NodeType.FILE]: { color: '#13c2c2', icon: <FileOutlined /> },
};

const CustomNode: React.FC<NodeProps> = ({ id, data, selected }) => {
  const config = nodeConfig[data.nodeType] || { color: '#d9d9d9', icon: null };

  const getStatusColor = (status?: NodeStatus): string => {
    switch (status) {
      case NodeStatus.RUNNING: return '#1890ff';
      case NodeStatus.SUCCESS: return '#52c41a';
      case NodeStatus.FAILED: return '#f5222d';
      case NodeStatus.SKIPPED: return '#fa8c16';
      default: return 'transparent';
    }
  };

  return (
    <div
      className={`custom-node ${selected ? 'selected' : ''}`}
      style={{ borderColor: config.color, position: 'relative' }}
    >
      <Handle type="target" position={Position.Left} style={{ background: config.color }} />

      <div className="node-header">
        <Badge color={config.color} text={
          <span style={{ color: config.color, fontWeight: 600 }}>
            {config.icon} {data.label || data.nodeType}
          </span>
        } />
      </div>

      <div className="node-body">
        {data.nodeType === NodeType.HTTP && data.groupId !== undefined && (
          <Tag color="blue" size="small">group={data.groupId}</Tag>
        )}
        {data.nodeType === NodeType.HTTP && data.api && (
          <div style={{ fontSize: 11, color: '#999', maxWidth: 120, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {data.api}
          </div>
        )}
        {data.description && (
          <Tooltip title={data.description}>
            <InfoCircleOutlined style={{ color: '#faad14', marginLeft: 4 }} />
          </Tooltip>
        )}
      </div>

      <Handle type="source" position={Position.Right} style={{ background: config.color }} />

      {data.status && (
        <div
          className={`status-indicator ${data.status.toLowerCase()}`}
          style={{ background: getStatusColor(data.status) }}
        />
      )}
    </div>
  );
};

export default memo(CustomNode);
