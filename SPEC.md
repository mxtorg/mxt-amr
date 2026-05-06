# 可视化流程编排引擎 (Visual Flow Orchestration Engine) - 编程规范

## 1. 系统概述

### 1.1 项目定位
构建一个基于 Web 的 DAG（有向无环图）流程编排系统，支持通过拖拽方式组合 HTTP、DB、FILE 等组件，实现可视化的业务逻辑编排与执行。

### 1.2 核心功能清单
| 功能模块 | 描述 |
|---------|------|
| 组件面板 | 提供 START、END、HTTP、ROUTER、DB、FILE 六种组件 |
| 画布交互 | 拖拽构建流程图，节点连线定义执行顺序 |
| 属性配置 | 点击节点弹出属性抽屉表单 |
| 路由分发 | ROUTER 节点支持分组并发执行策略 |
| 执行引擎 | DAG 拓扑排序执行，支持变量替换与数据映射 |
| 监控追踪 | 执行状态实时监控，日志链路追踪 |

---

## 2. 技术架构

### 2.1 系统架构图
```
┌─────────────────────────────────────────┐
│              Frontend (React/Vue)       │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ │
│  │ Palette │ │ Canvas  │ │ Drawer  │ │
│  │ (Left)  │ │(Center) │ │ (Right) │ │
│  └─────────┘ └─────────┘ └─────────┘ │
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│              Backend (Java/Go)          │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ │
│  │ Flow    │ │ Node    │ │ Execute │ │
│  │ Service │ │ Service │ │ Engine  │ │
│  └─────────┘ └─────────┘ └─────────┘ │
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│              Data Layer                 │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ │
│  │ Flow    │ │ Node    │ │ Runtime │ │
│  │ Metadata│ │ Config  │ │  Log    │ │
│  └─────────┘ └─────────┘ └─────────┘ │
└─────────────────────────────────────────┘
```

### 2.2 技术栈选型
| 层级 | 技术选型 |
|------|---------|
| 前端框架 | React 18 + TypeScript |
| 画布引擎 | ReactFlow / X6（蚂蚁） |
| 表单渲染 | FormRender（阿里）或 @rjsf/core |
| 状态管理 | Zustand |
| UI 组件库 | Ant Design 5.x |
| 后端框架 | Spring Boot (Java) |
| 数据库 | MySQL 5.7+ |
| 缓存 | Redis |
| 消息队列 | RabbitMQ |

---

## 3. 数据模型定义

### 3.1 枚举类型
```typescript
// 节点类型枚举
enum NodeType {
  START = 'START',     // 起点节点
  END = 'END',         // 终点节点
  HTTP = 'HTTP',       // HTTP 任务节点
  ROUTER = 'ROUTER',   // 路由控制节点
  DB = 'DB',           // 数据库任务节点
  FILE = 'FILE'         // 文件操作节点
}

// 节点状态枚举
enum NodeStatus {
  INIT = 'INIT',       // 初始化
  RUNNING = 'RUNNING', // 运行中
  SUCCESS = 'SUCCESS', // 执行成功
  FAILED = 'FAILED',   // 执行失败
  SKIPPED = 'SKIPPED'  // 已跳过
}

// 数据库操作类型
enum DbOperationType {
  QUERY = 'QUERY',
  INSERT = 'INSERT',
  UPDATE = 'UPDATE',
  DELETE = 'DELETE'
}

// 文件操作类型
enum FileOperationType {
  READ = 'READ',
  WRITE = 'WRITE',
  DELETE = 'DELETE'
}

// 路由执行策略
enum RouterStrategyType {
  ALL = 'ALL',         // 全部执行
  FIRST = 'FIRST',     // 首个成功
  RANDOM = 'RANDOM'    // 随机执行
}

// 等待模式
enum WaitMode {
  FORK_JOIN = 'FORK_JOIN',
  COMPLETABLE_FUTURE = 'COMPLETABLE_FUTURE'
}
```

### 3.2 接口/类定义

#### 3.2.1 基础节点接口 (BaseNode)
```typescript
interface BaseNode {
  id: string;                    // 全局唯一节点 ID，UUID v4
  flowId: string;               // 所属流程 ID
  pids: string[];                // 父节点 ID 列表，空数组表示起始节点
  nids: string[];                // 子节点 ID 列表，空数组表示终止节点
  nodeType: NodeType;            // 枚举: START | END | HTTP | ROUTER | DB | FILE
  name: string;                  // 节点显示名称
  description?: string;          // 节点业务描述
  position: { x: number; y: number };  // 画布坐标
  status: NodeStatus;            // 状态: INIT | RUNNING | SUCCESS | FAILED | SKIPPED
  createTime: number;            // 创建时间戳
  updateTime: number;            // 更新时间戳
}
```

#### 3.2.2 HTTP 节点 (HttpNode)
```typescript
interface HttpNode extends BaseNode {
  nodeType: NodeType.HTTP;

  // 请求定义
  source: string;                 // 数据源标识，如 "百行征信"
  groupId: number;               // 路由分组 ID，用于 ROUTER 分组策略
  api: string;                   // 请求 URL，支持变量占位符 ${var}
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';

  // Schema 定义
  inputSchema: JSONSchema;        // 请求参数 JSON Schema
  outputSchema: JSONSchema;       // 响应数据 JSON Schema

  // 业务属性
  price: number;                 // 调用单价，单位：分
  timeout: number;               // 超时时间，单位：ms，默认 30000

  // 运行时数据（非持久化）
  runtime?: {
    requestBody?: object;
    responseBody?: object;
    statusCode?: number;
    errorMsg?: string;
  };
}
```

#### 3.2.3 ROUTER 节点 (RouterNode)
```typescript
interface RouterNode extends BaseNode {
  nodeType: NodeType.ROUTER;

  // 数据映射
  mappingSchema: {
    mappings: Array<{
      source: string;             // 源数据 JSON Path，如 "$.group0[0].data"
      target: string;             // 目标字段，对应前序节点 outputSchema 字段
      transform?: string;         // 可选转换函数名
    }>;
  };

  // 拦截器配置
  requestIntercept?: JSONSchema;  // 请求拦截器配置 Schema
  responseIntercept?: JSONSchema; // 响应拦截器配置 Schema

  // 路由策略
  strategySchema: {
    type: RouterStrategyType;     // 执行策略：全部/首个/随机
    waitMode: WaitMode;           // 并发等待模式
    retryCount: number;           // 失败重试次数，默认 0
    fallbackNodeId?: string;      // 失败降级节点 ID
  };
}
```

#### 3.2.4 DB 节点 (DbNode)
```typescript
interface DbNode extends BaseNode {
  nodeType: NodeType.DB;

  // 连接配置
  jdbcUrl: string;                // JDBC 连接串
  username: string;               // 用户名
  password: string;               // 密码（加密存储）

  // 操作定义
  operationType: DbOperationType; // 操作类型
  sql: string;                    // SQL 语句，支持占位符 ?
  parameters?: JSONSchema;        // 参数绑定 Schema

  // 异步标记
  asyncSave: boolean;             // 是否异步保存，默认 true
}
```

#### 3.2.5 FILE 节点 (FileNode)
```typescript
interface FileNode extends BaseNode {
  nodeType: NodeType.FILE;

  operationType: FileOperationType;  // 操作类型
  filePath: string;                  // 文件路径，支持变量
  contentType?: string;              // 文件 MIME 类型
}
```

#### 3.2.6 执行上下文 (FlowContext)
```typescript
class FlowContext {
  flowId: string;                                     // 流程 ID
  executionId: string;                                // 执行实例 ID
  globalVars: Map<string, any>;                       // 全局变量
  nodeOutputs: Map<string, NodeResult>;               // 节点输出缓存

  // 变量替换：支持 ${nodeId.output.field} 语法
  resolveVariable(expression: string): any;

  // 设置节点输出
  setNodeOutput(nodeId: string, result: NodeResult): void;

  // 获取节点输出
  getNodeOutput(nodeId: string): NodeResult;
}
```

#### 3.2.7 节点执行结果 (NodeResult)
```typescript
class NodeResult {
  success: boolean;          // 是否成功
  output: any;               // 输出数据
  errorMsg?: string;         // 错误信息
  durationMs: number;         // 执行耗时

  static success(output: any): NodeResult;
  static fail(errorMsg: string): NodeResult;
}
```

#### 3.2.8 流程执行结果 (FlowResult)
```typescript
class FlowResult {
  executionId: string;       // 执行实例 ID
  status: 'SUCCESS' | 'FAILED' | 'TIMEOUT';
  output: any;              // 最终输出
  nodeResults: Map<string, NodeResult>;  // 各节点执行结果
  totalDurationMs: number;  // 总耗时
}
```

---

## 4. 核心执行链路示例

### 4.1 流程 DSL 示例
```yaml
# 流程配置示例
flow:
  id: "flow_001"
  name: "征信查询流程"
  nodes:
    - id: "start_1"
      nodeType: "START"
      nids: ["http_1"]

    - id: "http_1"
      nodeType: "HTTP"
      pids: ["start_1"]
      nids: ["router_1"]
      source: "百行征信"
      api: "http://api-1/xxx"
      groupId: 0
      inputSchema: { ... }
      outputSchema: { ... }

    - id: "router_1"
      nodeType: "ROUTER"
      pids: ["http_1"]
      nids: ["http_2", "http_3", "http_4"]
      strategySchema:
        type: "ALL"
        waitMode: "COMPLETABLE_FUTURE"

    - id: "http_2"
      nodeType: "HTTP"
      pids: ["router_1"]
      nids: ["db_1"]
      groupId: 0
      api: "http://api-2/xxx"

    - id: "http_3"
      nodeType: "HTTP"
      pids: ["router_1"]
      nids: ["db_2"]
      groupId: 1
      api: "http://api-3/xxx"

    - id: "http_4"
      nodeType: "HTTP"
      pids: ["router_1"]
      nids: ["db_2"]
      groupId: 1
      api: "http://api-3/xxx"

    - id: "db_1"
      nodeType: "DB"
      pids: ["http_2"]
      nids: ["end_1"]
      jdbcUrl: "jdbc:mysql://115.159.24.40:13306/usm"
      operationType: "INSERT"
      asyncSave: true

    - id: "db_2"
      nodeType: "DB"
      pids: ["http_3", "http_4"]
      nids: ["end_1"]
      jdbcUrl: "jdbc:mysql://..."
      operationType: "INSERT"

    - id: "end_1"
      nodeType: "END"
      pids: ["db_1", "db_2"]
```

### 4.2 执行流程图示
```
[开始] → [HTTP: API-1] → [ROUTER] ─┬─→ [HTTP group0: API-2] → [DB: biz]
                                   ├─→ [HTTP group1: API-3]
                                   └─→ [HTTP group1: API-3] ──→ [DB: 3th]
```

---

## 5. 执行引擎算法

### 5.1 伪代码
```
算法: ExecuteFlow(flowId, context)
输入: 流程定义, 运行时上下文
输出: 执行结果

1. 加载流程定义，构建 DAG 邻接表
2. 初始化节点状态表 status[nodeId] = INIT
3. 计算入度表 inDegree，入度为 0 的节点加入就绪队列
4. WHILE 就绪队列不为空:
   5.    node = 就绪队列.pop()
   6.    IF node.type == ROUTER:
   7.        groups = 按 groupId 分组子节点
   8.        FOR EACH group IN groups:
   9.            启动线程池执行 group 内所有节点（并行）
   10.           等待全部完成（ForkJoin / CompletableFuture）
   11.           执行 response-intercept 拦截器
   12.           执行 mapping-schema 数据映射
   13.           将映射结果写入前序节点 outputSchema
   14.           IF asyncSave:
   15.              异步提交 DB 保存任务
   16.    ELSE IF node.type == HTTP:
   17.        渲染 inputSchema（变量替换）
   18.        执行 request-intercept 拦截器
   19.        发起 HTTP 请求
   20.        校验 outputSchema
   21.    ELSE IF node.type == DB:
   22.        渲染 SQL 参数
   23.        执行数据库操作
   24.    status[node.id] = SUCCESS / FAILED
   25.    遍历子节点，入度减 1，入度为 0 则加入就绪队列
26. RETURN 执行报告
```

---

## 6. ROUTER 节点路由策略

### 6.1 分组并发机制
- **分组规则**: 按 `group-id` 分组，同组节点并发执行
- **线程模型**: 每组启动 N 个线程并行调用
- **等待策略**: ForkJoin 或 CompletableFuture 等待全组返回

### 6.2 数据映射规则
- **映射方式**: 通过 JSONPath 从响应提取数据
- **目标位置**: 赋值给前序节点的 output-schema
- **持久化**: 结果异步保存至数据库

---

## 7. API 接口规范

### 7.1 流程管理
| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/v1/flows | 创建流程 |
| GET | /api/v1/flows/{flowId} | 获取流程定义 |
| PUT | /api/v1/flows/{flowId} | 更新流程（全量替换节点配置） |
| DELETE | /api/v1/flows/{flowId} | 删除流程 |
| POST | /api/v1/flows/{flowId}/execute | 执行流程（同步/异步） |

**执行接口请求示例**:
```json
{
  "input": { ... },
  "mode": "SYNC"
}
```

**执行接口响应示例**:
```json
{
  "executionId": "...",
  "status": "...",
  "output": { ... }
}
```

### 7.2 节点管理
| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/v1/flows/{flowId}/nodes | 添加节点 |
| PUT | /api/v1/flows/{flowId}/nodes/{nodeId} | 更新节点属性 |
| DELETE | /api/v1/flows/{flowId}/nodes/{nodeId} | 删除节点（级联删除连线） |
| POST | /api/v1/flows/{flowId}/edges | 创建连线（自动维护 PIDS/NIDS） |

**连线请求示例**:
```json
{
  "from": "nodeA",
  "to": "nodeB"
}
```

### 7.3 执行监控
| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/v1/executions/{executionId} | 查询执行状态 |
| GET | /api/v1/executions/{executionId}/logs | 获取执行日志 |
| GET | /api/v1/executions/{executionId}/nodes/{nodeId}/runtime | 获取节点运行时数据 |

---

## 8. 前端交互规范

### 8.1 画布操作行为
| 操作 | 行为 |
|------|------|
| 拖拽 Palette 组件到 Canvas | 创建新节点，自动生成 ID |
| 点击节点 | 右侧滑出 Drawer，加载对应表单 Schema |
| 拖拽节点边缘连接点 | 创建 Edge，自动更新双方 PIDS/NIDS |
| 双击 ROUTER 节点 | 展开/折叠分组视图 |
| 右键节点 | 上下文菜单：删除、复制、查看日志 |

### 8.2 表单 Schema 映射
```typescript
const FormSchemaMap: Record<NodeType, JSONSchema> = {
  [NodeType.HTTP]: {
    type: "object",
    properties: {
      source: { type: "string", title: "数据源" },
      groupId: { type: "number", title: "分组 ID" },
      api: { type: "string", format: "uri", title: "接口地址" },
      inputSchema: { type: "object", title: "请求参数 Schema" },
      outputSchema: { type: "object", title: "响应参数 Schema" },
      price: { type: "number", title: "调用单价" }
    },
    required: ["source", "api", "inputSchema", "outputSchema"]
  },
  [NodeType.ROUTER]: {
    type: "object",
    properties: {
      mappingSchema: { type: "object", title: "数据映射" },
      requestIntercept: { type: "object", title: "请求拦截" },
      responseIntercept: { type: "object", title: "响应拦截" },
      strategySchema: { type: "object", title: "路由策略" }
    }
  },
  [NodeType.DB]: {
    type: "object",
    properties: {
      jdbcUrl: { type: "string", format: "uri", title: "JDBC URL" },
      username: { type: "string", title: "用户名" },
      password: { type: "string", format: "password", title: "密码" },
      operationType: { type: "string", enum: ["QUERY", "INSERT", "UPDATE", "DELETE"] }
    }
  }
};
```

---

## 9. 数据库设计

### 9.1 ER 关系图
```
┌─────────────┐       ┌─────────────┐       ┌─────────────┐
│   flow_def  │ 1───N │   node_def  │ 1───N │  node_param │
│  (流程定义)  │       │  (节点定义)  │       │  (节点参数)  │
└─────────────┘       └─────────────┘       └─────────────┘
       │                      │
       │ 1                    │ 1
       ▼                      ▼
┌─────────────┐       ┌─────────────┐
│ flow_version│       │   edge_def  │
│ (版本管理)   │       │  (连线定义)  │
└─────────────┘       └─────────────┘

┌─────────────┐       ┌─────────────┐       ┌─────────────┐
│ flow_exec   │ 1───N │  node_exec  │ 1───N │   exec_log  │
│ (执行实例)   │       │ (节点执行)   │       │  (执行日志)  │
└─────────────┘       └─────────────┘       └─────────────┘
```

### 9.2 表结构

#### 9.2.1 流程定义表 (flow_def)
```sql
CREATE TABLE flow_def (
    id              VARCHAR(32) PRIMARY KEY COMMENT '流程ID',
    name            VARCHAR(128) NOT NULL COMMENT '流程名称',
    description     VARCHAR(512) COMMENT '流程描述',
    status          TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-启用 2-草稿',
    version         INT DEFAULT 1 COMMENT '当前版本号',
    dag_json        LONGTEXT COMMENT 'DAG完整JSON（冗余备份）',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by       VARCHAR(64) COMMENT '创建人',
    INDEX idx_status (status),
    INDEX idx_name (name)
) ENGINE=InnoDB COMMENT='流程定义主表';
```

#### 9.2.2 节点定义表 (node_def)
```sql
CREATE TABLE node_def (
    id              VARCHAR(32) PRIMARY KEY COMMENT '节点ID',
    flow_id         VARCHAR(32) NOT NULL COMMENT '所属流程ID',
    node_type       VARCHAR(16) NOT NULL COMMENT '节点类型: START/END/HTTP/ROUTER/DB/FILE',
    node_name       VARCHAR(128) NOT NULL COMMENT '节点显示名',
    description     VARCHAR(512) COMMENT '节点描述',
    pos_x           DECIMAL(10,2) COMMENT '画布X坐标',
    pos_y           DECIMAL(10,2) COMMENT '画布Y坐标',
    pids            VARCHAR(512) COMMENT '父节点ID集合,逗号分隔',
    nids            VARCHAR(512) COMMENT '子节点ID集合,逗号分隔',
    sort_order      INT DEFAULT 0 COMMENT '同层级排序',
    FOREIGN KEY (flow_id) REFERENCES flow_def(id) ON DELETE CASCADE,
    INDEX idx_flow_type (flow_id, node_type)
) ENGINE=InnoDB COMMENT='节点定义表';
```

#### 9.2.3 节点参数表 (node_param)
```sql
CREATE TABLE node_param (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_id         VARCHAR(32) NOT NULL COMMENT '节点ID',
    param_key       VARCHAR(64) NOT NULL COMMENT '参数键',
    param_value     LONGTEXT COMMENT '参数值（JSON/加密字符串）',
    value_type      VARCHAR(16) DEFAULT 'STRING' COMMENT '值类型: STRING/JSON/ENCRYPT',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (node_id) REFERENCES node_def(id) ON DELETE CASCADE,
    UNIQUE KEY uk_node_key (node_id, param_key),
    INDEX idx_node (node_id)
) ENGINE=InnoDB COMMENT='节点参数KV表';
```

#### 9.2.4 连线定义表 (edge_def)
```sql
CREATE TABLE edge_def (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    flow_id         VARCHAR(32) NOT NULL COMMENT '所属流程ID',
    from_node       VARCHAR(32) NOT NULL COMMENT '源节点ID',
    to_node         VARCHAR(32) NOT NULL COMMENT '目标节点ID',
    condition       VARCHAR(512) COMMENT '连线条件表达式（预留）',
    FOREIGN KEY (flow_id) REFERENCES flow_def(id) ON DELETE CASCADE,
    UNIQUE KEY uk_edge (flow_id, from_node, to_node),
    INDEX idx_from (from_node),
    INDEX idx_to (to_node)
) ENGINE=InnoDB COMMENT='节点连线表';
```

#### 9.2.5 执行实例表 (flow_exec)
```sql
CREATE TABLE flow_exec (
    id              VARCHAR(32) PRIMARY KEY COMMENT '执行ID',
    flow_id         VARCHAR(32) NOT NULL COMMENT '流程ID',
    flow_version    INT NOT NULL COMMENT '执行时版本号',
    status          VARCHAR(16) DEFAULT 'RUNNING' COMMENT '状态: RUNNING/SUCCESS/FAILED/TIMEOUT',
    input_param     LONGTEXT COMMENT '输入参数JSON',
    output_result   LONGTEXT COMMENT '输出结果JSON',
    start_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
    end_time        DATETIME COMMENT '结束时间',
    duration_ms     INT COMMENT '执行耗时(ms)',
    error_msg       TEXT COMMENT '错误信息',
    trace_id        VARCHAR(64) COMMENT '链路追踪ID',
    INDEX idx_flow_time (flow_id, start_time),
    INDEX idx_status (status),
    INDEX idx_trace (trace_id)
) ENGINE=InnoDB COMMENT='流程执行实例';
```

#### 9.2.6 节点执行记录表 (node_exec)
```sql
CREATE TABLE node_exec (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    exec_id         VARCHAR(32) NOT NULL COMMENT '执行实例ID',
    node_id         VARCHAR(32) NOT NULL COMMENT '节点ID',
    node_type       VARCHAR(16) NOT NULL COMMENT '节点类型',
    status          VARCHAR(16) DEFAULT 'RUNNING' COMMENT '状态',
    input_snapshot  LONGTEXT COMMENT '输入参数快照',
    output_snapshot LONGTEXT COMMENT '输出结果快照',
    start_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
    end_time        DATETIME,
    duration_ms     INT,
    error_msg       TEXT,
    retry_count     INT DEFAULT 0 COMMENT '重试次数',
    FOREIGN KEY (exec_id) REFERENCES flow_exec(id) ON DELETE CASCADE,
    INDEX idx_exec_node (exec_id, node_id),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='节点执行记录';
```

#### 9.2.7 执行日志表 (exec_log)
```sql
CREATE TABLE exec_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    exec_id         VARCHAR(32) NOT NULL COMMENT '执行实例ID',
    node_id         VARCHAR(32) COMMENT '节点ID（可为空，记录流程级日志）',
    log_level       VARCHAR(8) DEFAULT 'INFO' COMMENT '日志级别: DEBUG/INFO/WARN/ERROR',
    log_type        VARCHAR(16) DEFAULT 'SYSTEM' COMMENT '日志类型: SYSTEM/BUSINESS/INTERCEPT',
    content         TEXT NOT NULL COMMENT '日志内容',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_exec_time (exec_id, create_time),
    INDEX idx_level (log_level)
) ENGINE=InnoDB COMMENT='执行日志';
```

---

## 10. 后端核心实现

### 10.1 项目结构
```
flow-engine/
├── flow-api/                    # API 接口层
│   ├── controller/              # REST Controller
│   └── dto/                     # 请求/响应 DTO
├── flow-core/                   # 核心引擎
│   ├── executor/                # 执行器
│   │   ├── NodeExecutor.java    # 执行器接口
│   │   ├── HttpExecutor.java    # HTTP执行器
│   │   ├── RouterExecutor.java  # 路由执行器
│   │   ├── DbExecutor.java      # DB执行器
│   │   └── FileExecutor.java    # 文件执行器
│   ├── interceptor/             # 拦截器
│   │   ├── RequestInterceptor.java
│   │   └── ResponseInterceptor.java
│   ├── strategy/                # 路由策略
│   │   ├── ExecutionStrategy.java
│   │   ├── AllStrategy.java     # 全部执行
│   │   ├── FirstStrategy.java   # 首个成功
│   │   └── RandomStrategy.java  # 随机执行
│   └── context/                 # 执行上下文
│       ├── FlowContext.java     # 流程上下文
│       └── NodeContext.java     # 节点上下文
├── flow-domain/                 # 领域模型
│   ├── entity/                  # 实体类
│   ├── enums/                   # 枚举
│   └── vo/                      # 值对象
├── flow-infrastructure/         # 基础设施
│   ├── repository/              # 数据访问
│   ├── cache/                   # 缓存
│   └── mq/                      # 消息队列
└── flow-spi/                    # 扩展SPI
    └── com.flow.engine.spi      # SPI接口定义
```

### 10.2 执行器接口
```java
public interface NodeExecutor<T extends BaseNode> {
    /**
     * 执行节点逻辑
     * @param node 节点定义
     * @param context 执行上下文
     * @return 执行结果
     */
    NodeResult execute(T node, FlowContext context);

    /**
     * 获取支持的节点类型
     */
    NodeType getType();
}
```

### 10.3 JSONPath 映射语法规范
```typescript
// 源数据引用语法
${groupId[index].nodeId.output.jsonPath}

// 示例：引用 group=0 的第 1 个节点响应中 data.name 字段
${group0[0].http_2.output.data.name}

// 示例：引用当前流程输入参数
${input.userId}

// 示例：引用上游节点输出
${http_1.output.token}
```

---

## 11. 安全规范

### 11.1 数据安全
| 层级 | 措施 | 实现 |
|------|------|------|
| 传输加密 | HTTPS/TLS 1.3 | Nginx 层配置 |
| 存储加密 | 数据库密码字段 AES-256-GCM | JPA AttributeConverter |
| 密钥管理 | 密钥托管于 KMS/Vault | 启动时从 Vault 拉取 |
| 日志脱敏 | 正则匹配敏感字段（手机号、身份证） | Logback PatternLayout |

### 11.2 执行安全
- **DAG 环检测**: 保存前执行环检测算法（DFS）
- **HTTP 超时**: 默认 30s 超时，防止长时间阻塞
- **DB 连接池**: 最大连接数限制
- **异步队列**: 有界队列，防止内存溢出

---

## 12. 异常处理规范

| 异常场景 | 处理策略 |
|---------|---------|
| 节点执行失败 | 标记 FAILED，触发 fallback 节点（如有），否则终止流程 |
| 超时 | 中断线程，标记 TIMEOUT，尝试重试（strategySchema.retryCount） |
| 路由分组全失败 | 若 strategySchema.type == 'ALL'，整体失败；若为 'FIRST'，继续执行 |
| 数据映射失败 | 记录 WARN 日志，跳过该字段映射，继续执行 |
| 环检测失败 | 保存时拒绝，提示用户调整连线 |

---

## 13. 扩展性设计

### 13.1 节点类型扩展
新增节点类型只需实现：
1. `NodeExecutor` 接口（后端执行逻辑）
2. `FormSchema` 配置（前端表单定义）
3. `NodeRenderer` 组件（前端画布渲染）

### 13.2 拦截器扩展
支持 SPI 机制加载自定义拦截器：
- `RequestInterceptor`: 修改请求参数/Header
- `ResponseInterceptor`: 修改响应数据/状态码
- `MappingInterceptor`: 自定义数据转换逻辑

---

## 14. 成功标准

### 14.1 功能验收标准
| ID | 标准描述 | 验证方式 |
|----|---------|---------|
| F1 | 支持拖拽 6 种组件类型构建 DAG | 手动测试 |
| F2 | ROUTER 节点正确实现分组并发 | 执行单元测试 |
| F3 | 支持 JSONPath 变量替换 | 执行单元测试 |
| F4 | 执行引擎正确处理 DAG 拓扑排序 | 执行单元测试 |
| F5 | 支持执行状态实时追踪 | WebSocket 集成测试 |
| F6 | 数据库密码字段加密存储 | 安全测试 |

### 14.2 性能指标
| 指标 | 目标值 |
|------|--------|
| 单流程最大节点数 | 500 |
| ROUTER 组内最大并发 | 50 |
| HTTP 请求默认超时 | 30s |
| 流程执行最大耗时 | 5min |

### 14.3 可观测性指标
| 指标 | 采集方式 |
|------|---------|
| 节点执行耗时 | Micrometer Timer |
| 节点成功率 | Micrometer Counter |
| 链路追踪 | OpenTelemetry Span |
| 日志关联 | MDC + traceId |

---

## 15. 术语表

| 术语 | 定义 |
|------|------|
| DAG | 有向无环图，表示流程的执行依赖关系 |
| Node | 流程图中的组件实例，包含类型、属性、连接关系 |
| Edge | 节点间的有向连线，定义执行顺序 |
| Group | ROUTER 节点下的逻辑分组，同组节点并行执行 |
| Schema | JSON Schema 定义的数据结构规范 |
| PIDS | Parent IDs，父节点标识集合 |
| NIDS | Next IDs，子节点标识集合 |
| SPI | Service Provider Interface，服务提供接口 |

---

*文档版本: 1.0*
*最后更新: 2026-05-06*
