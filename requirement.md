

我来分析这张流程编排系统的设计图，梳理需求并按规范编写 Spec。
需求分析
这是一个可视化流程编排引擎，核心功能：
1. 组件系统（左侧面板）
表格
组件	类型	用途
开始	起点节点	流程入口
结束	终点节点	流程出口
HTTP	任务节点	发起 HTTP 请求
ROUTER	控制节点	路由分发/并发调用
DB	任务节点	数据库操作
FILE	任务节点	文件操作
2. 画布交互
拖拽组件到画布构建流程图
节点连线定义执行顺序（PIDS → ID → NIDS）
点击节点右侧滑出属性抽屉表单
3. 核心执行链路示例
plain
复制
[开始] → [HTTP: API-1] → [ROUTER] ─┬─→ [HTTP group0: API-2] → [DB: biz]
                                  ├─→ [HTTP group1: API-3] 
                                  └─→ [HTTP group1: API-3] ──→ [DB: 3th]
4. ROUTER 节点路由策略
分组并发：按 group-id 分组，同组节点并发执行
线程模型：每组启动 N 个线程并行调用
等待策略：ForkJoin 或 CompletedFuture 等待全组返回
数据映射：通过 jsonpath 从响应提取数据，赋值给前序节点的 output-schema
持久化：结果异步保存至数据库
5. 节点属性 Schema
通用字段（所有节点）：
node-type: HTTP/ROUTER/DB/FILE/...
PIDS: 父节点 ID 集合（逗号分隔）
ID: 当前节点 ID
NIDS: 子节点 ID 集合（逗号分隔）
HTTP 节点字段：
source: 数据源标识（如"百行征信"）
group-id: 分组 ID（用于路由）
api: 请求地址
input-schema: JSON Schema 请求参数
output-schema: JSON Schema 响应参数
description: 接口描述
price: 调用单价
ROUTER 节点字段：
mapping-schema: JSON Path 接口映射
request-intercept: 请求拦截器配置（JSON Schema）
response-intercept: 响应拦截器配置（JSON Schema）
strategy-schema: 路由策略配置（JSON Schema）
DB 节点字段：
jdbc-url: 数据库连接串
username-pass: 认证信息
Spec 编程规范
spec
复制
# 可视化流程编排引擎 (Visual Flow Orchestration Engine)

## 1. 概述
构建一个基于 Web 的 DAG（有向无环图）流程编排系统，支持通过拖拽方式组合 HTTP、DB、FILE 等组件，实现可视化的业务逻辑编排与执行。

## 2. 术语表
| 术语 | 定义 |
|------|------|
| DAG | 有向无环图，表示流程的执行依赖关系 |
| Node | 流程图中的组件实例，包含类型、属性、连接关系 |
| Edge | 节点间的有向连线，定义执行顺序 |
| Group | ROUTER 节点下的逻辑分组，同组节点并行执行 |
| Schema | JSON Schema 定义的数据结构规范 |
| PIDS | Parent IDs，父节点标识集合 |
| NIDS | Next IDs，子节点标识集合 |

## 3. 系统架构

### 3.1 模块划分
┌─────────────────────────────────────────┐
│              Frontend (React/Vue)       │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ │
│  │ Palette │ │ Canvas  │ │ Drawer  │ │  组件面板 / 画布 / 属性抽屉
│  │ (Left)  │ │(Center) │ │ (Right) │ │
│  └─────────┘ └─────────┘ └─────────┘ │
└─────────────────────────────────────────┘
│
┌─────────────────────────────────────────┐
│              Backend (Java/Go)          │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ │
│  │ Flow    │ │ Node    │ │ Execute │ │  流程定义 / 节点管理 / 执行引擎
│  │ Service │ │ Service │ │ Engine  │ │
│  └─────────┘ └─────────┘ └─────────┘ │
└─────────────────────────────────────────┘
│
┌─────────────────────────────────────────┐
│              Data Layer                 │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ │
│  │ Flow    │ │ Node    │ │ Runtime │ │  流程元数据 / 节点配置 / 执行日志
│  │ Metadata│ │ Config  │ │  Log    │ │
│  └─────────┘ └─────────┘ └─────────┘ │
└─────────────────────────────────────────┘
plain
复制

## 4. 数据模型

### 4.1 节点基类 (BaseNode)
```typescript
interface BaseNode {
  // 身份标识
  id: string;              // 全局唯一节点 ID，UUID v4
  flowId: string;          // 所属流程 ID
  
  // 图结构关系
  pids: string[];           // 父节点 ID 列表，空数组表示起始节点
  nids: string[];           // 子节点 ID 列表，空数组表示终止节点
  
  // 基础属性
  nodeType: NodeType;       // 枚举: START | END | HTTP | ROUTER | DB | FILE
  name: string;             // 节点显示名称
  description?: string;      // 节点业务描述
  position: { x: number; y: number };  // 画布坐标
  
  // 状态管理
  status: NodeStatus;       // 枚举: INIT | RUNNING | SUCCESS | FAILED | SKIPPED
  createTime: number;       // 创建时间戳
  updateTime: number;       // 更新时间戳
}

enum NodeType {
  START = 'START',
  END = 'END', 
  HTTP = 'HTTP',
  ROUTER = 'ROUTER',
  DB = 'DB',
  FILE = 'FILE'
}

enum NodeStatus {
  INIT = 'INIT',
  RUNNING = 'RUNNING',
  SUCCESS = 'SUCCESS',
  FAILED = 'FAILED',
  SKIPPED = 'SKIPPED'
}
4.2 HTTP 节点 (HttpNode)
TypeScript
复制
interface HttpNode extends BaseNode {
  nodeType: NodeType.HTTP;
  
  // 请求定义
  source: string;                    // 数据源标识，如 "百行征信"
  groupId: number;                   // 路由分组 ID，用于 ROUTER 分组策略
  api: string;                       // 请求 URL，支持变量占位符 ${var}
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  
  // Schema 定义
  inputSchema: JSONSchema;           // 请求参数 JSON Schema
  outputSchema: JSONSchema;          // 响应数据 JSON Schema
  
  // 业务属性
  price: number;                    // 调用单价，单位：分
  timeout: number;                   // 超时时间，单位：ms，默认 30000
  
  // 运行时数据（非持久化）
  runtime?: {
    requestBody?: object;
    responseBody?: object;
    statusCode?: number;
    errorMsg?: string;
  };
}
4.3 ROUTER 节点 (RouterNode)
TypeScript
复制
interface RouterNode extends BaseNode {
  nodeType: NodeType.ROUTER;
  
  // 数据映射
  mappingSchema: {
    mappings: Array<{
      source: string;              // 源数据 JSON Path，如 "$.group0[0].data"
      target: string;              // 目标字段，对应前序节点 outputSchema 字段
      transform?: string;          // 可选转换函数名
    }>;
  };
  
  // 拦截器配置
  requestIntercept?: JSONSchema;    // 请求拦截器配置 Schema
  responseIntercept?: JSONSchema;   // 响应拦截器配置 Schema
  
  // 路由策略
  strategySchema: {
    type: 'ALL' | 'FIRST' | 'RANDOM';  // 执行策略：全部/首个/随机
    waitMode: 'FORK_JOIN' | 'COMPLETABLE_FUTURE';  // 并发等待模式
    retryCount: number;                // 失败重试次数，默认 0
    fallbackNodeId?: string;          // 失败降级节点 ID
  };
}
4.4 DB 节点 (DbNode)
TypeScript
复制
interface DbNode extends BaseNode {
  nodeType: NodeType.DB;
  
  // 连接配置
  jdbcUrl: string;                   // JDBC 连接串
  username: string;                 // 用户名
  password: string;                  // 密码（加密存储）
  
  // 操作定义
  operationType: 'QUERY' | 'INSERT' | 'UPDATE' | 'DELETE';
  sql: string;                       // SQL 语句，支持占位符 ?
  parameters?: JSONSchema;           // 参数绑定 Schema
  
  // 异步标记
  asyncSave: boolean;                // 是否异步保存，默认 true
}
4.5 FILE 节点 (FileNode)
TypeScript
复制
interface FileNode extends BaseNode {
  nodeType: NodeType.FILE;
  
  operationType: 'READ' | 'WRITE' | 'DELETE';
  filePath: string;                  // 文件路径，支持变量
  contentType?: string;              // 文件 MIME 类型
}
5. 核心流程
5.1 流程定义 DSL
yaml
复制
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
5.2 执行引擎算法
plain
复制
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
6. API 接口规范
6.1 流程管理
http
复制
POST /api/v1/flows
创建流程

GET /api/v1/flows/{flowId}
获取流程定义

PUT /api/v1/flows/{flowId}
更新流程（全量替换节点配置）

DELETE /api/v1/flows/{flowId}
删除流程

POST /api/v1/flows/{flowId}/execute
执行流程（同步/异步）
Request: { "input": { ... }, "mode": "SYNC" }
Response: { "executionId": "...", "status": "...", "output": { ... } }
6.2 节点管理
http
复制
POST /api/v1/flows/{flowId}/nodes
添加节点

PUT /api/v1/flows/{flowId}/nodes/{nodeId}
更新节点属性

DELETE /api/v1/flows/{flowId}/nodes/{nodeId}
删除节点（级联删除连线）

POST /api/v1/flows/{flowId}/edges
创建连线（自动维护 PIDS/NIDS）
Request: { "from": "nodeA", "to": "nodeB" }
6.3 执行监控
http
复制
GET /api/v1/executions/{executionId}
查询执行状态

GET /api/v1/executions/{executionId}/logs
获取执行日志

GET /api/v1/executions/{executionId}/nodes/{nodeId}/runtime
获取节点运行时数据
7. 前端交互规范
7.1 画布操作
表格
操作	行为
拖拽 Palette 组件到 Canvas	创建新节点，自动生成 ID
点击节点	右侧滑出 Drawer，加载对应表单 Schema
拖拽节点边缘连接点	创建 Edge，自动更新双方 PIDS/NIDS
双击 ROUTER 节点	展开/折叠分组视图
右键节点	上下文菜单：删除、复制、查看日志
7.2 抽屉表单动态渲染
TypeScript
复制
// 根据 nodeType 动态加载表单配置
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
8. 安全规范
8.1 数据安全
密码字段（DB username-pass）必须使用 AES-256-GCM 加密存储
敏感字段（api-key、token）通过环境变量注入，禁止硬编码
流程定义导出时自动脱敏密码字段
8.2 执行安全
DAG 必须无环，保存前执行环检测算法（DFS）
HTTP 请求默认 30s 超时，防止长时间阻塞
DB 连接使用连接池，最大连接数限制
异步任务提交至有界队列，防止内存溢出
9. 异常处理
表格
异常场景	处理策略
节点执行失败	标记 FAILED，触发 fallback 节点（如有），否则终止流程
超时	中断线程，标记 TIMEOUT，尝试重试（strategySchema.retryCount）
路由分组全失败	若 strategySchema.type == 'ALL'，整体失败；若为 'FIRST'，继续执行
数据映射失败	记录 WARN 日志，跳过该字段映射，继续执行
环检测失败	保存时拒绝，提示用户调整连线
10. 扩展性设计
10.1 节点类型扩展
新增节点类型只需实现：
NodeExecutor 接口（后端执行逻辑）
FormSchema 配置（前端表单定义）
NodeRenderer 组件（前端画布渲染）
10.2 拦截器扩展
支持 SPI 机制加载自定义拦截器：
RequestInterceptor: 修改请求参数/Header
ResponseInterceptor: 修改响应数据/状态码
MappingInterceptor: 自定义数据转换逻辑
plain
复制

---

如需进一步细化某个模块（如执行引擎的 Java 实现、前端 React 组件设计、或数据库表结构），可以继续展开。
继续
编辑
复制
分享
继续细化各模块的详细设计：
11. 数据库设计
11.1 ER 关系图
plain
复制
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
11.2 表结构
sql
复制
-- 流程定义表
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

-- 节点定义表
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

-- 节点参数表（按节点类型分表存储，避免稀疏列）
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

-- 连线定义表
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

-- 执行实例表
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

-- 节点执行记录表
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

-- 执行日志表
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
11.3 参数存储映射
表格
节点类型	param_key	param_value 示例	value_type
HTTP	source	"百行征信"	STRING
HTTP	groupId	0	STRING
HTTP	api	"http://api-1/xxx"	STRING
HTTP	inputSchema	{"type":"object","properties":{...}}	JSON
HTTP	outputSchema	{"type":"object","properties":{...}}	JSON
HTTP	price	1	STRING
ROUTER	mappingSchema	{"mappings":[...]}	JSON
ROUTER	strategySchema	{"type":"ALL","waitMode":"COMPLETABLE_FUTURE"}	JSON
DB	jdbcUrl	"jdbc:mysql://..."	STRING
DB	username	"root"	STRING
DB	password	"加密密文..."	ENCRYPT
DB	asyncSave	true	STRING
12. 后端核心实现
12.1 项目结构
plain
复制
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
12.2 核心接口定义
java
复制
// ==================== 执行器接口 ====================
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

// ==================== 执行结果 ====================
@Data
public class NodeResult {
    private boolean success;
    private Object output;
    private String errorMsg;
    private long durationMs;
    
    public static NodeResult success(Object output) {
        NodeResult r = new NodeResult();
        r.success = true;
        r.output = output;
        return r;
    }
    
    public static NodeResult fail(String errorMsg) {
        NodeResult r = new NodeResult();
        r.success = false;
        r.errorMsg = errorMsg;
        return r;
    }
}

// ==================== 流程上下文 ====================
public class FlowContext {
    private String flowId;
    private String executionId;
    private Map<String, Object> globalVars = new ConcurrentHashMap<>();
    private Map<String, NodeResult> nodeOutputs = new ConcurrentHashMap<>();
    private Map<String, Object> config = new HashMap<>();
    
    // 变量替换：支持 ${nodeId.output.field} 语法
    public Object resolveVariable(String expression) {
        // 解析表达式并返回对应值
    }
    
    public void setNodeOutput(String nodeId, NodeResult result) {
        nodeOutputs.put(nodeId, result);
    }
    
    public NodeResult getNodeOutput(String nodeId) {
        return nodeOutputs.get(nodeId);
    }
}
12.3 HTTP 执行器实现
java
复制
@Component
public class HttpExecutor implements NodeExecutor<HttpNode> {
    
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private RequestInterceptor requestInterceptor;
    @Autowired
    private ResponseInterceptor responseInterceptor;
    
    @Override
    public NodeType getType() {
        return NodeType.HTTP;
    }
    
    @Override
    public NodeResult execute(HttpNode node, FlowContext context) {
        long start = System.currentTimeMillis();
        try {
            // 1. 构建请求参数（变量替换）
            Map<String, Object> params = buildRequestParams(node, context);
            
            // 2. 请求拦截
            if (requestInterceptor != null) {
                params = requestInterceptor.intercept(node, params);
            }
            
            // 3. 构建 HTTP 请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> entity = new HttpEntity<>(params, headers);
            
            // 4. 执行请求（带超时）
            ResponseEntity<String> response = restTemplate.exchange(
                node.getApi(),
                HttpMethod.valueOf(node.getMethod()),
                entity,
                String.class
            );
            
            // 5. 解析响应
            Object output = JsonPath.parse(response.getBody()).json();
            
            // 6. 响应拦截
            if (responseInterceptor != null) {
                output = responseInterceptor.intercept(node, output);
            }
            
            // 7. Schema 校验
            validateOutput(node.getOutputSchema(), output);
            
            long duration = System.currentTimeMillis() - start;
            return NodeResult.success(output).setDurationMs(duration);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            return NodeResult.fail(e.getMessage()).setDurationMs(duration);
        }
    }
    
    private Map<String, Object> buildRequestParams(HttpNode node, FlowContext context) {
        // 基于 inputSchema 和上下文变量构建参数
        Map<String, Object> params = new HashMap<>();
        // ... 变量替换逻辑
        return params;
    }
    
    private void validateOutput(JSONSchema schema, Object output) {
        // JSON Schema 校验
    }
}
12.4 ROUTER 执行器实现
java
复制
@Component
public class RouterExecutor implements NodeExecutor<RouterNode> {
    
    @Autowired
    private NodeExecutorFactory executorFactory;
    @Autowired
    private ThreadPoolExecutor routerThreadPool;
    
    @Override
    public NodeType getType() {
        return NodeType.ROUTER;
    }
    
    @Override
    public NodeResult execute(RouterNode node, FlowContext context) {
        // 1. 获取所有子节点并按 groupId 分组
        List<BaseNode> children = loadChildren(node);
        Map<Integer, List<BaseNode>> groups = children.stream()
            .collect(Collectors.groupingBy(this::getGroupId));
        
        Map<Integer, List<NodeResult>> groupResults = new HashMap<>();
        
        // 2. 按组串行，组内并行
        for (Map.Entry<Integer, List<BaseNode>> entry : groups.entrySet()) {
            Integer groupId = entry.getKey();
            List<BaseNode> groupNodes = entry.getValue();
            
            // 组内并行执行
            List<CompletableFuture<NodeResult>> futures = groupNodes.stream()
                .map(child -> CompletableFuture.supplyAsync(
                    () -> executeChild(child, context),
                    routerThreadPool
                ))
                .collect(Collectors.toList());
            
            // 等待全组完成（或超时）
            List<NodeResult> results = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            ).thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList())
            ).orTimeout(30, TimeUnit.SECONDS).join();
            
            groupResults.put(groupId, results);
        }
        
        // 3. 执行数据映射（mappingSchema）
        Map<String, Object> mappedData = executeMapping(
            node.getMappingSchema(), 
            groupResults, 
            context
        );
        
        // 4. 将映射结果写入前序节点的 output（供后续节点使用）
        context.setNodeOutput(node.getId(), NodeResult.success(mappedData));
        
        // 5. 异步保存到数据库（如果配置）
        if (node.isAsyncSave()) {
            asyncSaveToDb(mappedData);
        }
        
        return NodeResult.success(mappedData);
    }
    
    private NodeResult executeChild(BaseNode child, FlowContext context) {
        NodeExecutor executor = executorFactory.getExecutor(child.getNodeType());
        return executor.execute(child, context);
    }
    
    private Map<String, Object> executeMapping(
        MappingSchema mapping, 
        Map<Integer, List<NodeResult>> groupResults,
        FlowContext context
    ) {
        Map<String, Object> result = new HashMap<>();
        for (MappingItem item : mapping.getMappings()) {
            // 使用 JSONPath 提取数据
            Object value = JsonPath.read(groupResults, item.getSource());
            // 可选：执行转换
            if (item.getTransform() != null) {
                value = TransformFactory.get(item.getTransform()).apply(value);
            }
            result.put(item.getTarget(), value);
        }
        return result;
    }
}
12.5 DAG 执行引擎
java
复制
@Service
public class FlowEngine {
    
    @Autowired
    private FlowRepository flowRepository;
    @Autowired
    private NodeExecutorFactory executorFactory;
    @Autowired
    private FlowExecRepository execRepository;
    
    /**
     * 执行流程
     */
    public FlowResult execute(String flowId, Map<String, Object> input) {
        // 1. 加载流程定义
        FlowDef flow = flowRepository.findById(flowId);
        String executionId = generateExecutionId();
        
        // 2. 初始化执行上下文
        FlowContext context = new FlowContext();
        context.setFlowId(flowId);
        context.setExecutionId(executionId);
        context.setGlobalVars(input);
        
        // 3. 构建 DAG 并拓扑排序
        DagGraph graph = DagGraph.build(flow.getNodes(), flow.getEdges());
        if (!graph.isAcyclic()) {
            throw new FlowException("流程存在循环依赖");
        }
        
        // 4. 初始化执行记录
        FlowExec exec = new FlowExec();
        exec.setId(executionId);
        exec.setFlowId(flowId);
        exec.setStatus("RUNNING");
        exec.setInputParam(JsonUtils.toJson(input));
        execRepository.save(exec);
        
        try {
            // 5. 拓扑执行
            List<String> topoOrder = graph.topologicalSort();
            for (String nodeId : topoOrder) {
                BaseNode node = graph.getNode(nodeId);
                
                // 检查前置节点是否全部成功
                if (!checkPredecessorsSuccess(node, context)) {
                    log.warn("前置节点未全部成功，跳过节点: {}", nodeId);
                    continue;
                }
                
                // 执行节点
                NodeExecutor executor = executorFactory.getExecutor(node.getNodeType());
                NodeResult result = executor.execute(node, context);
                
                // 保存节点执行记录
                saveNodeExec(executionId, node, result);
                
                // 保存输出到上下文
                context.setNodeOutput(nodeId, result);
                
                // 失败处理
                if (!result.isSuccess()) {
                    handleNodeFailure(node, result, context);
                    if (!canContinue(node, context)) {
                        break;
                    }
                }
            }
            
            // 6. 完成执行
            FlowResult flowResult = buildFlowResult(context);
            exec.setStatus("SUCCESS");
            exec.setOutputResult(JsonUtils.toJson(flowResult));
            exec.setEndTime(LocalDateTime.now());
            execRepository.save(exec);
            
            return flowResult;
            
        } catch (Exception e) {
            exec.setStatus("FAILED");
            exec.setErrorMsg(e.getMessage());
            exec.setEndTime(LocalDateTime.now());
            execRepository.save(exec);
            throw e;
        }
    }
    
    private boolean checkPredecessorsSuccess(BaseNode node, FlowContext context) {
        for (String pid : node.getPids()) {
            NodeResult result = context.getNodeOutput(pid);
            if (result == null || !result.isSuccess()) {
                return false;
            }
        }
        return true;
    }
}
13. 前端实现
13.1 技术栈
框架: React 18 + TypeScript
画布引擎: ReactFlow / X6（蚂蚁）
表单渲染: FormRender（阿里）或 @rjsf/core
状态管理: Zustand
UI 组件: Ant Design 5.x
13.2 组件架构
plain
复制
src/
├── components/
│   ├── Palette/              # 左侧组件面板
│   │   ├── index.tsx
│   │   ├── NodeItem.tsx      # 可拖拽节点项
│   │   └── style.less
│   ├── Canvas/               # 中央画布
│   │   ├── index.tsx
│   │   ├── FlowCanvas.tsx    # ReactFlow 画布
│   │   ├── CustomNode.tsx    # 自定义节点渲染
│   │   └── EdgeLine.tsx      # 自定义连线
│   ├── Drawer/               # 右侧属性抽屉
│   │   ├── index.tsx
│   │   ├── FormRender.tsx    # 动态表单渲染
│   │   ├── HttpForm.tsx      # HTTP 节点表单
│   │   ├── RouterForm.tsx    # ROUTER 节点表单
│   │   └── DbForm.tsx        # DB 节点表单
│   └── Toolbar/              # 顶部工具栏
├── stores/
│   └── flowStore.ts          # 流程状态管理
├── hooks/
│   ├── useFlow.ts            # 流程操作 Hook
│   ├── useNode.ts            # 节点操作 Hook
│   └── useExecute.ts         # 执行 Hook
├── utils/
│   ├── dag.ts                # DAG 工具函数
│   ├── schema.ts             # Schema 工具
│   └── validator.ts          # 校验工具
└── types/
    └── flow.ts               # TypeScript 类型定义
13.3 画布核心实现
TypeScript
复制
// stores/flowStore.ts
import { create } from 'zustand';
import { Node, Edge } from 'reactflow';

interface FlowState {
  nodes: Node[];
  edges: Edge[];
  selectedNode: Node | null;
  
  // Actions
  addNode: (type: NodeType, position: { x: number; y: number }) => void;
  updateNode: (id: string, data: Partial<NodeData>) => void;
  removeNode: (id: string) => void;
  addEdge: (from: string, to: string) => void;
  removeEdge: (id: string) => void;
  setSelectedNode: (node: Node | null) => void;
  validateDag: () => { valid: boolean; message?: string };
}

export const useFlowStore = create<FlowState>((set, get) => ({
  nodes: [],
  edges: [],
  selectedNode: null,
  
  addNode: (type, position) => {
    const id = `${type.toLowerCase()}_${Date.now()}`;
    const newNode: Node = {
      id,
      type: 'custom',
      position,
      data: {
        nodeType: type,
        label: getDefaultLabel(type),
        // 初始化默认属性
        ...(type === 'HTTP' ? { groupId: 0, method: 'POST' } : {}),
        ...(type === 'ROUTER' ? { strategySchema: { type: 'ALL', waitMode: 'COMPLETABLE_FUTURE' } } : {}),
      },
    };
    set(state => ({ nodes: [...state.nodes, newNode] }));
  },
  
  addEdge: (from, to) => {
    // 检查是否形成环
    const { edges, nodes } = get();
    const newEdges = [...edges, { id: `e_${from}_${to}`, source: from, target: to }];
    
    if (hasCycle(nodes, newEdges)) {
      message.error('不能形成循环依赖');
      return;
    }
    
    // 更新 PIDS/NIDS
    const fromNode = nodes.find(n => n.id === from);
    const toNode = nodes.find(n => n.id === to);
    if (fromNode && toNode) {
      fromNode.data.nids = [...(fromNode.data.nids || []), to];
      toNode.data.pids = [...(toNode.data.pids || []), from];
    }
    
    set({ edges: newEdges });
  },
  
  validateDag: () => {
    const { nodes, edges } = get();
    if (nodes.length === 0) return { valid: false, message: '流程为空' };
    
    const startNodes = nodes.filter(n => n.data.nodeType === 'START');
    if (startNodes.length === 0) return { valid: false, message: '缺少开始节点' };
    if (startNodes.length > 1) return { valid: false, message: '只能有一个开始节点' };
    
    const endNodes = nodes.filter(n => n.data.nodeType === 'END');
    if (endNodes.length === 0) return { valid: false, message: '缺少结束节点' };
    
    if (hasCycle(nodes, edges)) return { valid: false, message: '存在循环依赖' };
    
    // 检查孤立节点
    const isolated = nodes.filter(n => 
      n.data.nodeType !== 'START' && n.data.nodeType !== 'END' &&
      (!n.data.pids?.length && !n.data.nids?.length)
    );
    if (isolated.length > 0) return { valid: false, message: '存在孤立节点' };
    
    return { valid: true };
  },
}));

// 环检测算法（DFS）
function hasCycle(nodes: Node[], edges: Edge[]): boolean {
  const adj = new Map<string, string[]>();
  edges.forEach(e => {
    if (!adj.has(e.source)) adj.set(e.source, []);
    adj.get(e.source)!.push(e.target);
  });
  
  const visited = new Set<string>();
  const recStack = new Set<string>();
  
  function dfs(nodeId: string): boolean {
    visited.add(nodeId);
    recStack.add(nodeId);
    
    const neighbors = adj.get(nodeId) || [];
    for (const neighbor of neighbors) {
      if (!visited.has(neighbor) && dfs(neighbor)) return true;
      if (recStack.has(neighbor)) return true;
    }
    
    recStack.delete(nodeId);
    return false;
  }
  
  for (const node of nodes) {
    if (!visited.has(node.id) && dfs(node.id)) return true;
  }
  return false;
}
13.4 动态表单渲染
TypeScript
复制
// components/Drawer/FormRender.tsx
import Form from '@rjsf/antd';
import validator from '@rjsf/validator-ajv8';

const schemaMap: Record<NodeType, RJSFSchema> = {
  HTTP: {
    type: 'object',
    required: ['source', 'api', 'inputSchema', 'outputSchema'],
    properties: {
      source: { type: 'string', title: '数据源', default: '' },
      groupId: { type: 'number', title: '分组 ID', default: 0 },
      api: { type: 'string', title: '接口地址', format: 'uri' },
      method: { 
        type: 'string', 
        title: '请求方法', 
        enum: ['GET', 'POST', 'PUT', 'DELETE'],
        default: 'POST'
      },
      inputSchema: { 
        type: 'object', 
        title: '请求参数 Schema',
        description: 'JSON Schema 格式'
      },
      outputSchema: { 
        type: 'object', 
        title: '响应参数 Schema',
        description: 'JSON Schema 格式'
      },
      description: { type: 'string', title: '接口描述' },
      price: { type: 'number', title: '调用单价（分）', minimum: 0 },
      timeout: { type: 'number', title: '超时时间（ms）', default: 30000 },
    },
  },
  ROUTER: {
    type: 'object',
    properties: {
      mappingSchema: {
        type: 'object',
        title: '数据映射配置',
        properties: {
          mappings: {
            type: 'array',
            title: '映射规则',
            items: {
              type: 'object',
              properties: {
                source: { 
                  type: 'string', 
                  title: '源数据路径（JSONPath）',
                  description: '如: $.group0[0].data.name'
                },
                target: { 
                  type: 'string', 
                  title: '目标字段',
                  description: '对应前序节点 outputSchema 字段名'
                },
                transform: { 
                  type: 'string', 
                  title: '转换函数',
                  enum: ['NONE', 'UPPER_CASE', 'LOWER_CASE', 'DATE_FORMAT']
                },
              },
            },
          },
        },
      },
      requestIntercept: {
        type: 'object',
        title: '请求拦截器配置',
        description: 'JSON Schema 格式定义拦截规则',
      },
      responseIntercept: {
        type: 'object',
        title: '响应拦截器配置',
        description: 'JSON Schema 格式定义拦截规则',
      },
      strategySchema: {
        type: 'object',
        title: '路由策略',
        required: ['type', 'waitMode'],
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
  DB: {
    type: 'object',
    required: ['jdbcUrl', 'username', 'password', 'operationType'],
    properties: {
      jdbcUrl: { 
        type: 'string', 
        title: 'JDBC URL',
        format: 'uri',
        default: 'jdbc:mysql://host:port/db'
      },
      username: { type: 'string', title: '用户名' },
      password: { type: 'string', title: '密码', format: 'password' },
      operationType: {
        type: 'string',
        title: '操作类型',
        enum: ['QUERY', 'INSERT', 'UPDATE', 'DELETE'],
      },
      sql: { type: 'string', title: 'SQL 语句' },
      parameters: {
        type: 'object',
        title: '参数绑定 Schema',
      },
      asyncSave: { 
        type: 'boolean', 
        title: '异步保存', 
        default: true,
        description: '执行结果是否异步写入数据库'
      },
    },
  },
};

export const NodeForm: React.FC<{ node: Node }> = ({ node }) => {
  const { updateNode } = useFlowStore();
  const schema = schemaMap[node.data.nodeType];
  
  if (!schema) return <div>不支持的节点类型</div>;
  
  return (
    <Form
      schema={schema}
      validator={validator}
      formData={node.data}
      onChange={(e) => {
        updateNode(node.id, e.formData);
      }}
      uiSchema={{
        password: { 'ui:widget': 'password' },
        sql: { 'ui:widget': 'textarea' },
        inputSchema: { 'ui:widget': 'jsonEditor' },
        outputSchema: { 'ui:widget': 'jsonEditor' },
        mappingSchema: { 'ui:widget': 'jsonEditor' },
      }}
    />
  );
};
13.5 自定义节点渲染
TypeScript
复制
// components/Canvas/CustomNode.tsx
import { Handle, Position } from 'reactflow';
import { Badge, Tooltip } from 'antd';

const nodeConfig: Record<NodeType, { color: string; icon: React.ReactNode }> = {
  START: { color: '#52c41a', icon: <PlayCircleOutlined /> },
  END: { color: '#f5222d', icon: <StopOutlined /> },
  HTTP: { color: '#1890ff', icon: <ApiOutlined /> },
  ROUTER: { color: '#fa8c16', icon: <BranchesOutlined /> },
  DB: { color: '#722ed1', icon: <DatabaseOutlined /> },
  FILE: { color: '#13c2c2', icon: <FileOutlined /> },
};

export const CustomNode: React.FC<NodeProps> = ({ id, data, selected }) => {
  const config = nodeConfig[data.nodeType];
  const { setSelectedNode } = useFlowStore();
  
  return (
    <div 
      className={`custom-node ${selected ? 'selected' : ''}`}
      style={{ borderColor: config.color }}
      onClick={() => setSelectedNode({ id, data })}
    >
      <Handle type="target" position={Position.Left} />
      
      <div className="node-content">
        <Badge 
          color={config.color} 
          text={
            <span style={{ color: config.color, fontWeight: 'bold' }}>
              {config.icon} {data.label || data.nodeType}
            </span>
          } 
        />
        {data.nodeType === 'HTTP' && data.groupId !== undefined && (
          <Tag color="blue" size="small">group={data.groupId}</Tag>
        )}
        {data.description && (
          <Tooltip title={data.description}>
            <InfoCircleOutlined className="info-icon" />
          </Tooltip>
        )}
      </div>
      
      <Handle type="source" position={Position.Right} />
      
      {/* 状态指示器 */}
      {data.status && (
        <div className={`status-indicator status-${data.status.toLowerCase()}`} />
      )}
    </div>
  );
};
14. 部署架构
plain
复制
┌─────────────────────────────────────────┐
│              Nginx (LB)                 │
│         SSL 终止 / 静态资源              │
└─────────────────────────────────────────┘
                   │
    ┌──────────────┼──────────────┐
    ▼              ▼              ▼
┌─────────┐   ┌─────────┐   ┌─────────┐
│ Web App │   │ Web App │   │ Web App │
│  (CDN)  │   │  (CDN)  │   │  (CDN)  │
└─────────┘   └─────────┘   └─────────┘
                   │
┌─────────────────────────────────────────┐
│           API Gateway (Kong/Spring)     │
│      鉴权 / 限流 / 路由 / 日志           │
└─────────────────────────────────────────┘
                   │
    ┌──────────────┼──────────────┐
    ▼              ▼              ▼
┌─────────┐   ┌─────────┐   ┌─────────┐
│ Flow-API│   │ Flow-API│   │ Flow-API│
│ Service │   │ Service │   │ Service │
└─────────┘   └─────────┘   └─────────┘
    │              │              │
    └──────────────┼──────────────┘
                   ▼
┌─────────────────────────────────────────┐
│        Flow-Engine (核心执行引擎)        │
│    有状态服务，负责 DAG 执行调度          │
└─────────────────────────────────────────┘
                   │
    ┌──────────────┼──────────────┐
    ▼              ▼              ▼
┌─────────┐   ┌─────────┐   ┌─────────┐
│  MySQL  │   │  Redis  │   │ RabbitMQ│
│(元数据)  │   │ (缓存)  │   │(异步任务)│
└─────────┘   └─────────┘   └─────────┘
15. 测试策略
表格
层级	内容	工具
单元测试	Executor、Interceptor、Schema 校验	JUnit 5 + Mockito
集成测试	数据库操作、HTTP 调用、MQ 消费	TestContainers
契约测试	API 接口前后端契约	Spring Cloud Contract
E2E 测试	完整流程编排与执行	Cypress / Playwright
性能测试	并发路由执行、大数据量映射	JMeter / k6
如需继续深入某个具体模块（如 JSONPath 映射引擎实现、分布式事务一致性保证、前端画布性能优化 或 监控告警体系），请指示。
继续
编辑
复制
分享
聚焦JSONPath映射与监控
继续深入核心子系统的设计：
16. JSONPath 映射引擎
16.1 设计目标
支持从多组并行响应中提取、转换、合并数据
支持变量占位符 ${} 的动态解析
支持内置转换函数与自定义 SPI 扩展
16.2 映射语法规范
plain
复制
# 源数据引用语法
${groupId[index].nodeId.output.jsonPath}

# 示例：引用 group=0 的第 1 个节点响应中 data.name 字段
${group0[0].http_2.output.data.name}

# 示例：引用当前流程输入参数
${input.userId}

# 示例：引用上游节点输出
${http_1.output.token}
16.3 映射配置 Schema
TypeScript
复制
interface MappingSchema {
  version: '1.0';
  mappings: MappingRule[];
  // 组合模式：merge 为合并所有映射，overwrite 为后者覆盖前者
  mergeStrategy: 'MERGE' | 'OVERWRITE';
}

interface MappingRule {
  // 源路径，支持 JSONPath + 变量语法
  source: string;
  // 目标字段，支持嵌套路径，如 "user.info.name"
  target: string;
  // 转换函数链
  transforms?: TransformConfig[];
  // 默认值，当源数据为空时
  defaultValue?: any;
  // 是否必填，缺失时抛异常
  required?: boolean;
}

interface TransformConfig {
  // 内置：UPPER_CASE, LOWER_CASE, DATE_FORMAT, BASE64_ENCODE, MD5, SUBSTRING, JSON_STRINGIFY
  // 自定义：通过 SPI 注册
  name: string;
  params?: Record<string, any>;
}
16.4 引擎实现
java
复制
@Component
public class MappingEngine {
    
    private final Configuration jsonPathConfig = Configuration.builder()
        .mappingProvider(new JacksonMappingProvider())
        .jsonProvider(new JacksonJsonProvider())
        .build();
    
    @Autowired
    private TransformRegistry transformRegistry;
    
    /**
     * 执行映射
     * @param schema 映射配置
     * @param groupResults 各组执行结果
     * @param context 流程上下文
     */
    public Map<String, Object> execute(
        MappingSchema schema,
        Map<Integer, List<NodeResult>> groupResults,
        FlowContext context
    ) {
        Map<String, Object> result = new HashMap<>();
        
        for (MappingRule rule : schema.getMappings()) {
            try {
                Object value = resolveSource(rule.getSource(), groupResults, context);
                
                // 应用转换链
                if (rule.getTransforms() != null) {
                    for (TransformConfig tf : rule.getTransforms()) {
                        TransformFunction func = transformRegistry.get(tf.getName());
                        value = func.apply(value, tf.getParams());
                    }
                }
                
                // 处理默认值
                if (value == null && rule.getDefaultValue() != null) {
                    value = rule.getDefaultValue();
                }
                
                // 必填校验
                if (rule.isRequired() && value == null) {
                    throw new MappingException(
                        String.format("映射字段 [%s] 为必填项，但源数据为空", rule.getTarget())
                    );
                }
                
                // 写入目标路径（支持嵌套）
                setNestedValue(result, rule.getTarget(), value);
                
            } catch (Exception e) {
                log.error("映射规则执行失败: source={}, target={}", 
                    rule.getSource(), rule.getTarget(), e);
                if (rule.isRequired()) {
                    throw e;
                }
            }
        }
        
        return result;
    }
    
    private Object resolveSource(
        String source, 
        Map<Integer, List<NodeResult>> groupResults,
        FlowContext context
    ) {
        // 1. 处理 ${} 变量语法
        if (source.startsWith("${") && source.endsWith("}")) {
            String expr = source.substring(2, source.length() - 1);
            return resolveVariable(expr, groupResults, context);
        }
        
        // 2. 纯 JSONPath，从上下文全局变量解析
        return JsonPath.using(jsonPathConfig).parse(context.getGlobalVars()).read(source);
    }
    
    private Object resolveVariable(
        String expr,
        Map<Integer, List<NodeResult>> groupResults,
        FlowContext context
    ) {
        String[] parts = expr.split("\\.");
        
        // input.xxx 流程输入参数
        if (parts[0].equals("input")) {
            Object input = context.getGlobalVars();
            return JsonPath.read(input, "$." + expr.substring(6));
        }
        
        // groupN[M].nodeId.output.xxx 分组结果引用
        if (parts[0].startsWith("group")) {
            int groupId = Integer.parseInt(parts[0].replace("group", ""));
            int index = Integer.parseInt(parts[1].replaceAll("[\\[\\]]", ""));
            String nodeId = parts[2];
            // 校验 nodeId 是否属于该 group
            String jsonPath = "$." + String.join(".", Arrays.copyOfRange(parts, 4, parts.length));
            
            List<NodeResult> group = groupResults.get(groupId);
            if (group == null || index >= group.size()) {
                throw new MappingException("分组结果不存在: " + expr);
            }
            
            NodeResult result = group.get(index);
            return JsonPath.read(result.getOutput(), jsonPath);
        }
        
        // nodeId.output.xxx 普通节点引用
        if (parts.length >= 2 && parts[1].equals("output")) {
            String nodeId = parts[0];
            NodeResult result = context.getNodeOutput(nodeId);
            if (result == null) {
                throw new MappingException("节点输出不存在: " + nodeId);
            }
            String jsonPath = "$." + String.join(".", Arrays.copyOfRange(parts, 2, parts.length));
            return JsonPath.read(result.getOutput(), jsonPath);
        }
        
        throw new MappingException("不支持的变量表达式: " + expr);
    }
    
    private void setNestedValue(Map<String, Object> target, String path, Object value) {
        String[] keys = path.split("\\.");
        Map<String, Object> current = target;
        
        for (int i = 0; i < keys.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(
                keys[i], k -> new HashMap<>()
            );
        }
        current.put(keys[keys.length - 1], value);
    }
}
16.5 内置转换函数
java
复制
public interface TransformFunction {
    String getName();
    Object apply(Object input, Map<String, Object> params);
}

@Component
public class DateFormatTransform implements TransformFunction {
    @Override
    public String getName() { return "DATE_FORMAT"; }
    
    @Override
    public Object apply(Object input, Map<String, Object> params) {
        String pattern = (String) params.getOrDefault("pattern", "yyyy-MM-dd HH:mm:ss");
        String timezone = (String) params.getOrDefault("timezone", "Asia/Shanghai");
        
        Instant instant;
        if (input instanceof Number) {
            instant = Instant.ofEpochMilli(((Number) input).longValue());
        } else if (input instanceof String) {
            instant = Instant.parse((String) input);
        } else {
            throw new IllegalArgumentException("不支持的时间格式: " + input.getClass());
        }
        
        return DateTimeFormatter.ofPattern(pattern)
            .withZone(ZoneId.of(timezone))
            .format(instant);
    }
}

@Component
public class SubstringTransform implements TransformFunction {
    @Override
    public String getName() { return "SUBSTRING"; }
    
    @Override
    public Object apply(Object input, Map<String, Object> params) {
        String str = input.toString();
        int begin = ((Number) params.get("begin")).intValue();
        int end = params.containsKey("end") 
            ? ((Number) params.get("end")).intValue() 
            : str.length();
        return str.substring(begin, Math.min(end, str.length()));
    }
}
17. 执行链路追踪与可观测性
17.1 追踪模型
TypeScript
复制
interface TraceSpan {
  traceId: string;           // 全局链路 ID
  spanId: string;            // 当前 span ID
  parentSpanId?: string;     // 父 span ID
  name: string;              // 操作名，如 "HTTP:api-1"
  nodeType: NodeType;        // 节点类型
  nodeId: string;            // 节点 ID
  startTime: number;         // 开始时间戳（微秒）
  duration: number;          // 耗时（微秒）
  tags: Record<string, string>;  // 标签
  logs: Array<{
    timestamp: number;
    fields: Record<string, any>;
  }>;
  // 子 span（用于 ROUTER 并发组）
  children?: TraceSpan[];
}
17.2 节点级指标采集
java
复制
@Component
public class ExecutionMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // 节点执行耗时 Histogram
    private final Map<NodeType, Timer> nodeTimers = new EnumMap<>(NodeType.class);
    
    // 节点成功率 Counter
    private final Map<NodeType, Counter> successCounters = new EnumMap<>(NodeType.class);
    private final Map<NodeType, Counter> failCounters = new EnumMap<>(NodeType.class);
    
    @PostConstruct
    public void init() {
        for (NodeType type : NodeType.values()) {
            nodeTimers.put(type, Timer.builder("flow.node.duration")
                .tag("type", type.name())
                .register(meterRegistry));
            successCounters.put(type, Counter.builder("flow.node.total")
                .tag("type", type.name())
                .tag("status", "success")
                .register(meterRegistry));
            failCounters.put(type, Counter.builder("flow.node.total")
                .tag("type", type.name())
                .tag("status", "failed")
                .register(meterRegistry));
        }
    }
    
    public void record(NodeType type, long durationMs, boolean success) {
        nodeTimers.get(type).record(durationMs, TimeUnit.MILLISECONDS);
        if (success) {
            successCounters.get(type).increment();
        } else {
            failCounters.get(type).increment();
        }
    }
}
17.3 日志规范
java
复制
@Slf4j
@Component
public class ExecutionLogger {
    
    private static final String LOG_PATTERN = 
        "[{}] [node={}] [type={}] [exec={}] {}";
    
    public void logNodeStart(String execId, BaseNode node) {
        MDC.put("executionId", execId);
        MDC.put("nodeId", node.getId());
        MDC.put("nodeType", node.getNodeType().name());
        log.info(LOG_PATTERN, "START", node.getId(), node.getNodeType(), execId, 
            JsonUtils.toJson(node.getInputSnapshot()));
    }
    
    public void logNodeEnd(String execId, BaseNode node, NodeResult result) {
        log.info(LOG_PATTERN, "END", node.getId(), node.getNodeType(), execId,
            String.format("success=%s, duration=%dms", result.isSuccess(), result.getDurationMs()));
        MDC.clear();
    }
    
    public void logIntercept(String execId, String nodeId, String phase, Object data) {
        log.debug("[{}] [node={}] [phase={}] intercept data: {}", 
            execId, nodeId, phase, JsonUtils.toJson(data));
    }
}
17.4 前端执行监控面板
TypeScript
复制
// 实时执行看板组件
interface ExecutionDashboardProps {
  executionId: string;
}

const ExecutionDashboard: React.FC<ExecutionDashboardProps> = ({ executionId }) => {
  const [spanTree, setSpanTree] = useState<TraceSpan | null>(null);
  const [stats, setStats] = useState<ExecutionStats | null>(null);
  
  useEffect(() => {
    // WebSocket 实时推送执行状态
    const ws = new WebSocket(`wss://api.host/ws/executions/${executionId}`);
    ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      setSpanTree(data.spanTree);
      setStats(data.stats);
    };
    return () => ws.close();
  }, [executionId]);
  
  return (
    <div className="execution-dashboard">
      <Row gutter={16}>
        <Col span={6}>
          <Statistic title="总耗时" value={stats?.totalDuration} suffix="ms" />
        </Col>
        <Col span={6}>
          <Statistic title="节点数" value={stats?.nodeCount} />
        </Col>
        <Col span={6}>
          <Statistic title="成功" value={stats?.successCount} valueStyle={{ color: '#3f8600' }} />
        </Col>
        <Col span={6}>
          <Statistic title="失败" value={stats?.failCount} valueStyle={{ color: '#cf1322' }} />
        </Col>
      </Row>
      
      {/* Gantt 图展示节点执行时间线 */}
      <ExecutionGantt spans={spanTree} />
      
      {/* 拓扑图高亮执行状态 */}
      <ExecutionFlowGraph 
        spans={spanTree} 
        onNodeClick={(nodeId) => showNodeDetail(nodeId)}
      />
    </div>
  );
};
18. 版本管理与灰度发布
18.1 版本模型
sql
复制
-- 流程版本表（与 flow_def 分离）
CREATE TABLE flow_version (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    flow_id         VARCHAR(32) NOT NULL COMMENT '流程ID',
    version         INT NOT NULL COMMENT '版本号',
    dag_json        LONGTEXT NOT NULL COMMENT 'DAG完整JSON',
    status          VARCHAR(16) DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED/DEPRECATED',
    publish_time    DATETIME COMMENT '发布时间',
    publish_by      VARCHAR(64) COMMENT '发布人',
    change_log      VARCHAR(1024) COMMENT '变更说明',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_flow_ver (flow_id, version),
    INDEX idx_flow_status (flow_id, status)
) ENGINE=InnoDB COMMENT='流程版本表';
18.2 版本状态机
plain
复制
[DRAFT] --发布--> [PUBLISHED] --废弃--> [DEPRECATED]
   ↑___________________________|
         复制为新版本
18.3 灰度策略
java
复制
@Component
public class FlowRouter {
    
    @Autowired
    private FlowVersionRepository versionRepo;
    @Autowired
    private GrayRuleEngine grayEngine;
    
    /**
     * 根据请求路由到对应版本
     */
    public FlowVersion route(String flowId, Map<String, Object> input) {
        // 1. 查询已发布版本列表
        List<FlowVersion> published = versionRepo.findPublished(flowId);
        
        if (published.isEmpty()) {
            throw new FlowException("流程无可用版本: " + flowId);
        }
        
        // 2. 无灰度规则，返回最新版本
        if (published.size() == 1) {
            return published.get(0);
        }
        
        // 3. 按灰度规则匹配
        for (FlowVersion version : published) {
            GrayRule rule = grayEngine.getRule(flowId, version.getVersion());
            if (rule != null && rule.match(input)) {
                return version;
            }
        }
        
        // 4. 默认返回基线版本（version 最小且状态为 PUBLISHED）
        return published.stream()
            .min(Comparator.comparingInt(FlowVersion::getVersion))
            .orElseThrow();
    }
}
18.4 灰度规则配置
yaml
复制
# 灰度规则示例
gray_rules:
  flow_001:
    - version: 2
      priority: 100
      conditions:
        - field: "input.userType"
          operator: "IN"
          value: ["VIP", "ENTERPRISE"]
        - field: "input.region"
          operator: "EQ"
          value: "shanghai"
      percentage: 10  # 满足条件后，再按 10% 流量灰度
      
    - version: 2
      priority: 50
      conditions:
        - field: "input.userId"
          operator: "MOD"
          value: 100
          range: [0, 9]  # userId % 100 在 0-9 之间的流量
19. 稳定性保障（限流熔断）
19.1 HTTP 节点熔断器
java
复制
@Component
public class HttpCircuitBreakerManager {
    
    // 按 source + api 维度隔离熔断器
    private final ConcurrentHashMap<String, CircuitBreaker> breakers = 
        new ConcurrentHashMap<>();
    
    public CircuitBreaker getBreaker(HttpNode node) {
        String key = node.getSource() + ":" + node.getApi();
        return breakers.computeIfAbsent(key, k -> CircuitBreaker.ofDefaults(k));
    }
}

@Component
public class HttpExecutor implements NodeExecutor<HttpNode> {
    
    @Autowired
    private HttpCircuitBreakerManager cbManager;
    
    @Override
    public NodeResult execute(HttpNode node, FlowContext context) {
        CircuitBreaker breaker = cbManager.getBreaker(node);
        
        return breaker.executeSupplier(() -> {
            // 限流检查
            if (!rateLimiter.tryAcquire(node)) {
                throw new FlowException("RATE_LIMITED", "接口调用过于频繁");
            }
            
            // 实际 HTTP 调用
            return doHttpCall(node, context);
        });
    }
}
19.2 节点级限流
java
复制
@Component
public class NodeRateLimiter {
    
    // Guava RateLimiter 或 Redis 分布式限流
    private final LoadingCache<String, RateLimiter> limiters = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.HOURS)
        .build(new CacheLoader<>() {
            @Override
            public RateLimiter load(String key) {
                // QPS 从节点配置或全局默认
                double qps = getQpsConfig(key);
                return RateLimiter.create(qps);
            }
        });
    
    public boolean tryAcquire(HttpNode node) {
        try {
            return limiters.get(node.getId()).tryAcquire(
                node.getTimeout(), TimeUnit.MILLISECONDS
            );
        } catch (ExecutionException e) {
            return true; // 降级：限流器异常时放行
        }
    }
}
19.3 重试与降级
java
复制
public class RetryPolicy {
    private int maxRetries = 3;
    private long initialInterval = 1000;  // ms
    private double multiplier = 2.0;       // 指数退避
    private Set<Class<? extends Exception>> retryableExceptions = Set.of(
        IOException.class, 
        TimeoutException.class,
        ServiceUnavailableException.class
    );
}

// 在 RouterExecutor 中应用重试
private NodeResult executeWithRetry(BaseNode child, FlowContext context, RetryPolicy policy) {
    int attempts = 0;
    long interval = policy.getInitialInterval();
    
    while (attempts <= policy.getMaxRetries()) {
        try {
            return executeChild(child, context);
        } catch (Exception e) {
            attempts++;
            if (attempts > policy.getMaxRetries() || !policy.isRetryable(e)) {
                throw e;
            }
            Thread.sleep(interval);
            interval *= policy.getMultiplier();
        }
    }
    throw new IllegalStateException("不应到达此处");
}
20. 插件化扩展机制（SPI）
20.1 扩展点定义
java
复制
// SPI 接口包：flow-spi
public interface FlowPlugin {
    String getName();
    String getVersion();
    void init(PluginContext context);
}

public interface NodeExecutorPlugin extends FlowPlugin {
    NodeType supportType();
    NodeResult execute(NodeDefinition node, FlowContext context);
}

public interface TransformPlugin extends FlowPlugin {
    String transformName();
    Object apply(Object input, Map<String, Object> params);
}

public interface InterceptorPlugin extends FlowPlugin {
    // 执行顺序，数字越小越先执行
    int order();
    Object intercept(NodeDefinition node, Object data, InterceptPhase phase);
}

public enum InterceptPhase {
    BEFORE_REQUEST,   // HTTP 请求前
    AFTER_RESPONSE,   // HTTP 响应后
    BEFORE_MAPPING,   // 数据映射前
    AFTER_MAPPING     // 数据映射后
}
20.2 插件加载机制
java
复制
@Component
public class PluginRegistry {
    
    private final Map<String, TransformPlugin> transforms = new HashMap<>();
    private final Map<NodeType, NodeExecutorPlugin> executors = new HashMap<>();
    private final List<InterceptorPlugin> interceptors = new ArrayList<>();
    
    @PostConstruct
    public void loadPlugins() {
        // 1. 加载 classpath 下 META-INF/services/ 中注册的插件
        ServiceLoader<FlowPlugin> loader = ServiceLoader.load(FlowPlugin.class);
        for (FlowPlugin plugin : loader) {
            register(plugin);
        }
        
        // 2. 加载外部插件目录（支持热插拔）
        File pluginDir = new File(System.getProperty("flow.plugin.dir", "./plugins"));
        if (pluginDir.exists()) {
            loadExternalPlugins(pluginDir);
        }
    }
    
    private void register(FlowPlugin plugin) {
        plugin.init(new PluginContext());
        
        if (plugin instanceof TransformPlugin) {
            TransformPlugin tp = (TransformPlugin) plugin;
            transforms.put(tp.transformName(), tp);
        } else if (plugin instanceof NodeExecutorPlugin) {
            NodeExecutorPlugin ep = (NodeExecutorPlugin) plugin;
            executors.put(ep.supportType(), ep);
        } else if (plugin instanceof InterceptorPlugin) {
            interceptors.add((InterceptorPlugin) plugin);
            interceptors.sort(Comparator.comparingInt(InterceptorPlugin::order));
        }
    }
}
20.3 自定义插件示例
java
复制
// 自定义加密转换插件
public class AesEncryptTransform implements TransformPlugin {
    
    @Override
    public String getName() { return "aes-encrypt-plugin"; }
    
    @Override
    public String getVersion() { return "1.0.0"; }
    
    @Override
    public String transformName() { return "AES_ENCRYPT"; }
    
    @Override
    public void init(PluginContext context) {
        // 初始化密钥等
    }
    
    @Override
    public Object apply(Object input, Map<String, Object> params) {
        String key = (String) params.get("key");
        String algorithm = (String) params.getOrDefault("algorithm", "AES/GCM/NoPadding");
        return CryptoUtils.aesEncrypt(input.toString(), key, algorithm);
    }
}

// 注册文件：META-INF/services/com.flow.engine.spi.FlowPlugin
// 内容：com.example.plugin.AesEncryptTransform
21. 安全体系
21.1 数据安全
表格
层级	措施	实现
传输加密	HTTPS/TLS 1.3	Nginx 层配置
存储加密	数据库密码字段 AES-256-GCM	JPA AttributeConverter
密钥管理	密钥托管于 KMS/Vault	启动时从 Vault 拉取
日志脱敏	正则匹配敏感字段（手机号、身份证）	Logback PatternLayout
java
复制
// 数据库密码加密转换器
@Converter
public class EncryptAttributeConverter implements AttributeConverter<String, String> {
    
    @Autowired
    private CryptoService cryptoService;
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : cryptoService.encrypt(attribute);
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : cryptoService.decrypt(dbData);
    }
}

// 实体中使用
@Entity
public class NodeParam {
    @Convert(converter = EncryptAttributeConverter.class)
    private String paramValue;
}
21.2 执行安全
java
复制
@Component
public class SecuritySandbox {
    
    // SQL 注入检测
    public void validateSql(String sql) {
        List<String> forbidden = Arrays.asList("DROP", "DELETE", "TRUNCATE", "--", "/*");
        String upper = sql.toUpperCase();
        for (String keyword : forbidden) {
            if (upper.contains(keyword)) {
                throw new SecurityException("SQL 包含危险关键字: " + keyword);
            }
        }
    }
    
    // 防止 SSRF：限制 HTTP 节点只能访问白名单域名
    private final List<String> whitelistDomains = Arrays.asList(
        "api.internal.com",
        "gateway.corp.com"
    );
    
    public void validateUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (!whitelistDomains.contains(host)) {
                throw new SecurityException("非法的目标地址: " + host);
            }
        } catch (URISyntaxException e) {
            throw new SecurityException("URL 格式错误");
        }
    }
}
22. 数据血缘与影响分析
22.1 血缘采集
java
复制
@Component
public class LineageCollector {
    
    /**
     * 解析流程，提取数据血缘关系
     */
    public LineageGraph analyze(FlowDef flow) {
        LineageGraph graph = new LineageGraph();
        
        for (NodeDef node : flow.getNodes()) {
            // HTTP 节点：记录输入输出字段
            if (node.getNodeType() == NodeType.HTTP) {
                HttpNode http = (HttpNode) node;
                graph.addNode(LineageNode.builder()
                    .id(node.getId())
                    .name(node.getNodeName())
                    .type("HTTP_API")
                    .schema(http.getOutputSchema())
                    .build());
            }
            
            // ROUTER 节点：记录映射关系
            if (node.getNodeType() == NodeType.ROUTER) {
                RouterNode router = (RouterNode) node;
                for (MappingItem mapping : router.getMappingSchema().getMappings()) {
                    graph.addEdge(LineageEdge.builder()
                        .source(mapping.getSource())   // 如 $.group0[0].output.data.userId
                        .target(mapping.getTarget())   // 如 request.userId
                        .transform(mapping.getTransforms())
                        .build());
                }
            }
        }
        
        return graph;
    }
}
22.2 影响分析 API
http
复制
GET /api/v1/flows/{flowId}/lineage?field=input.userId

Response:
{
  "field": "input.userId",
  "upstream": [],           # 上游依赖（输入参数无上游）
  "downstream": [
    {
      "nodeId": "http_1",
      "nodeName": "百行征信查询",
      "usage": "inputSchema.properties.userId"
    },
    {
      "nodeId": "router_1",
      "nodeName": "路由分发",
      "usage": "mappingSchema.mappings[0].source"
    }
  ],
  "affectedFlows": ["flow_002", "flow_003"]  # 引用此流程作为子流程的其他流程
}