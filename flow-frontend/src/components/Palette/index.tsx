import React from 'react';
import { Card, Typography } from 'antd';
import { NodeType } from '../../types/flow';
import { PlayCircleOutlined, StopOutlined, ApiOutlined, BranchesOutlined, DatabaseOutlined, FileOutlined } from '@ant-design/icons';

const { Title } = Typography;

interface PaletteItemProps {
  type: NodeType;
  label: string;
  icon: React.ReactNode;
  color: string;
}

const nodeConfig: Record<NodeType, { label: string; icon: React.ReactNode; color: string }> = {
  [NodeType.START]: { label: '开始', icon: <PlayCircleOutlined />, color: '#52c41a' },
  [NodeType.END]: { label: '结束', icon: <StopOutlined />, color: '#f5222d' },
  [NodeType.HTTP]: { label: 'HTTP请求', icon: <ApiOutlined />, color: '#1890ff' },
  [NodeType.ROUTER]: { label: '路由分发', icon: <BranchesOutlined />, color: '#fa8c16' },
  [NodeType.DB]: { label: '数据库', icon: <DatabaseOutlined />, color: '#722ed1' },
  [NodeType.FILE]: { label: '文件操作', icon: <FileOutlined />, color: '#13c2c2' },
};

interface PaletteProps {
  onDragStart: (event: React.DragEvent, nodeType: NodeType) => void;
}

const Palette: React.FC<PaletteProps> = ({ onDragStart }) => {
  return (
    <div className="palette">
      <Title level={5} style={{ marginBottom: 16 }}>组件面板</Title>
      {Object.entries(nodeConfig).map(([type, config]) => (
        <div
          key={type}
          className={`node-item ${type.toLowerCase()}`}
          draggable
          onDragStart={(e) => onDragStart(e, type as NodeType)}
          style={{ borderLeftColor: config.color }}
        >
          <span style={{ color: config.color, fontSize: 18 }}>{config.icon}</span>
          <span>{config.label}</span>
        </div>
      ))}
    </div>
  );
};

export default Palette;
