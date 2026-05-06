import React from 'react';
import { Drawer, Form as AntForm, Input, InputNumber, Select, Switch, Button, Space, Divider, message } from 'antd';
import Form from '@rjsf/antd';
import validator from '@rjsf/validator-ajv8';
import { useFlowStore } from '../../stores/flowStore';
import { NodeType } from '../../types/flow';

const { TextArea } = Input;

const schemaMap: Record<NodeType, any> = {
  [NodeType.HTTP]: {
    type: 'object',
    required: ['source', 'api'],
    properties: {
      name: { type: 'string', title: '节点名称' },
      description: { type: 'string', title: '描述' },
      source: { type: 'string', title: '数据源' },
      groupId: { type: 'number', title: '分组 ID', default: 0 },
      api: { type: 'string', title: '接口地址' },
      method: { type: 'string', title: '请求方法', enum: ['GET', 'POST', 'PUT', 'DELETE'], default: 'POST' },
      inputSchema: { type: 'object', title: '请求参数 Schema', description: 'JSON Schema 格式' },
      outputSchema: { type: 'object', title: '响应参数 Schema', description: 'JSON Schema 格式' },
      price: { type: 'number', title: '调用单价（分）', minimum: 0 },
      timeout: { type: 'number', title: '超时时间（ms）', default: 30000 },
    },
  },
  [NodeType.ROUTER]: {
    type: 'object',
    properties: {
      name: { type: 'string', title: '节点名称' },
      description: { type: 'string', title: '描述' },
      mappingSchema: { type: 'object', title: '数据映射配置' },
      requestIntercept: { type: 'object', title: '请求拦截配置' },
      responseIntercept: { type: 'object', title: '响应拦截配置' },
      strategySchema: {
        type: 'object',
        title: '路由策略',
        properties: {
          type: {
            type: 'string',
            title: '执行策略',
            enum: ['ALL', 'FIRST', 'RANDOM'],
            enumNames: ['全部执行', '首个成功', '随机执行'],
            default: 'ALL',
          },
          waitMode: {
            type: 'string',
            title: '等待模式',
            enum: ['FORK_JOIN', 'COMPLETABLE_FUTURE'],
            enumNames: ['ForkJoin', 'CompletableFuture'],
            default: 'COMPLETABLE_FUTURE',
          },
          retryCount: { type: 'number', title: '重试次数', default: 0, minimum: 0 },
          fallbackNodeId: { type: 'string', title: '失败降级节点 ID' },
        },
      },
    },
  },
  [NodeType.DB]: {
    type: 'object',
    required: ['jdbcUrl', 'operationType'],
    properties: {
      name: { type: 'string', title: '节点名称' },
      description: { type: 'string', title: '描述' },
      jdbcUrl: { type: 'string', title: 'JDBC URL', description: '如: jdbc:mysql://host:port/db' },
      username: { type: 'string', title: '用户名' },
      password: { type: 'string', title: '密码' },
      operationType: {
        type: 'string',
        title: '操作类型',
        enum: ['QUERY', 'INSERT', 'UPDATE', 'DELETE'],
      },
      sql: { type: 'string', title: 'SQL 语句' },
      parameters: { type: 'object', title: '参数绑定' },
      asyncSave: { type: 'boolean', title: '异步保存', default: true },
    },
  },
  [NodeType.FILE]: {
    type: 'object',
    required: ['filePath'],
    properties: {
      name: { type: 'string', title: '节点名称' },
      description: { type: 'string', title: '描述' },
      operationType: {
        type: 'string',
        title: '操作类型',
        enum: ['READ', 'WRITE', 'DELETE'],
      },
      filePath: { type: 'string', title: '文件路径' },
      contentType: { type: 'string', title: 'MIME类型', default: 'application/json' },
    },
  },
  [NodeType.START]: {
    type: 'object',
    properties: {
      name: { type: 'string', title: '节点名称' },
      description: { type: 'string', title: '描述' },
    },
  },
  [NodeType.END]: {
    type: 'object',
    properties: {
      name: { type: 'string', title: '节点名称' },
      description: { type: 'string', title: '描述' },
    },
  },
};

interface NodeDrawerProps {
  open: boolean;
  onClose: () => void;
  node: any;
}

const NodeDrawer: React.FC<NodeDrawerProps> = ({ open, onClose, node }) => {
  const { updateNode } = useFlowStore();
  const [form] = AntForm.useForm();

  React.useEffect(() => {
    if (node && open) {
      form.setFieldsValue({
        ...node.data,
        strategySchema: node.data.strategySchema || {
          type: 'ALL',
          waitMode: 'COMPLETABLE_FUTURE',
          retryCount: 0,
        },
      });
    }
  }, [node, open, form]);

  const handleFinish = (values: any) => {
    if (node) {
      updateNode(node.id, values);
      message.success('节点属性已更新');
    }
  };

  const schema = node ? schemaMap[node.data.nodeType] : null;

  return (
    <Drawer
      title={`编辑节点: ${node?.data?.label || ''}`}
      placement="right"
      width={400}
      onClose={onClose}
      open={open}
      extra={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" onClick={() => form.submit()}>保存</Button>
        </Space>
      }
    >
      {schema && (
        <Form
          form={form}
          schema={schema}
          validator={validator}
          onSubmit={handleFinish}
          uiSchema={{
            password: { 'ui:widget': 'password' },
            sql: { 'ui:widget': 'textarea' },
            description: { 'ui:widget': 'textarea' },
            inputSchema: { 'ui:widget': 'textarea' },
            outputSchema: { 'ui:widget': 'textarea' },
            mappingSchema: { 'ui:widget': 'textarea' },
            requestIntercept: { 'ui:widget': 'textarea' },
            responseIntercept: { 'ui:widget': 'textarea' },
            parameters: { 'ui:widget': 'textarea' },
            strategySchema: {
              type: { 'ui:widget': 'select' },
              waitMode: { 'ui:widget': 'select' },
            },
          }}
        />
      )}
    </Drawer>
  );
};

export default NodeDrawer;
