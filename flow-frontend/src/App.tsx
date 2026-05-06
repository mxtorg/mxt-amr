import React, { useState, useCallback } from 'react';
import { Layout, Button, Input, Space, Modal, message, Dropdown, Tag } from 'antd';
import { SaveOutlined, PlayCircleOutlined, PlusOutlined, FolderOpenOutlined, DeleteOutlined, MoreOutlined } from '@ant-design/icons';
import Canvas from './components/Canvas';
import Palette from './components/Palette';
import NodeDrawer from './components/Drawer';
import { useFlowStore } from './stores/flowStore';
import { flowApi } from './api/flowApi';
import { NodeType } from './types/flow';

const { Header, Content } = Layout;

const App: React.FC = () => {
  const {
    flowName,
    setFlowName,
    nodes,
    edges,
    selectedNode,
    isDrawerOpen,
    openDrawer,
    closeDrawer,
    validateDag,
    getFlowDefinition,
    resetFlow,
    executionId,
    setExecutionId,
  } = useFlowStore();

  const [isExecuting, setIsExecuting] = useState(false);

  const handleDragStart = (event: React.DragEvent, nodeType: NodeType) => {
    event.dataTransfer.setData('application/reactflow', nodeType);
    event.dataTransfer.effectAllowed = 'move';
  };

  const handleNodeClick = useCallback((node: any) => {
    openDrawer();
  }, [openDrawer]);

  const handleSave = async () => {
    const validation = validateDag();
    if (!validation.valid) {
      message.error(validation.message);
      return;
    }

    const flowDef = getFlowDefinition();
    try {
      await flowApi.create(flowDef as any);
      message.success('流程保存成功');
    } catch (error) {
      message.error('保存失败');
    }
  };

  const handleExecute = async () => {
    const validation = validateDag();
    if (!validation.valid) {
      message.error(validation.message);
      return;
    }

    setIsExecuting(true);
    try {
      const flowDef = getFlowDefinition();
      const result = await flowApi.execute(flowDef.id, {}, 'SYNC');
      setExecutionId(result.executionId);
      message.success(`执行完成: ${result.status}`);
    } catch (error) {
      message.error('执行失败');
    } finally {
      setIsExecuting(false);
    }
  };

  const handleNewFlow = () => {
    Modal.confirm({
      title: '确认新建流程',
      content: '确定要新建流程吗？当前未保存的内容将丢失。',
      onOk: () => {
        resetFlow();
        message.info('已新建空白流程');
      },
    });
  };

  const items = [
    { key: 'save', icon: <SaveOutlined />, label: '保存流程' },
    { key: 'execute', icon: <PlayCircleOutlined />, label: '执行流程' },
    { type: 'divider' as const },
    { key: 'new', icon: <PlusOutlined />, label: '新建流程' },
    { key: 'open', icon: <FolderOpenOutlined />, label: '打开流程' },
  ];

  return (
    <Layout className="app-container">
      <Header className="toolbar">
        <Space size="large">
          <Input
            value={flowName}
            onChange={(e) => setFlowName(e.target.value)}
            placeholder="流程名称"
            style={{ width: 200 }}
            variant="borderless"
          />
          {executionId && (
            <Tag color="blue">执行ID: {executionId.slice(0, 8)}...</Tag>
          )}
        </Space>

        <Space style={{ marginLeft: 'auto' }}>
          <Button icon={<PlusOutlined />} onClick={handleNewFlow}>新建</Button>
          <Button icon={<SaveOutlined />} onClick={handleSave}>保存</Button>
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            onClick={handleExecute}
            loading={isExecuting}
          >
            执行
          </Button>
          <Dropdown menu={{ items }} trigger={['click']}>
            <Button icon={<MoreOutlined />} />
          </Dropdown>
        </Space>
      </Header>

      <Content className="main-content">
        <Palette onDragStart={handleDragStart} />
        <Canvas onNodeClick={handleNodeClick} />
        <NodeDrawer
          open={isDrawerOpen}
          onClose={closeDrawer}
          node={selectedNode}
        />
      </Content>
    </Layout>
  );
};

export default App;
