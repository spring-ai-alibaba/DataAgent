中文 | [English](./ARCHITECTURE-en.md)

# 架构设计

本文档详细介绍 DataAgent 的系统架构、核心能力和技术实现。

## 📐 总体架构图

```mermaid
%%{init: {"theme": "base", "flowchart": {"curve": "basis", "nodeSpacing": 35, "rankSpacing": 45}, "themeVariables": {"lineColor": "#475569", "primaryTextColor": "#1F2937"}}}%%
flowchart LR
  subgraph Clients[Clients]
    UserUI[data-agent-frontend UI]
    AdminUI[Admin Console]
    MCPClient[MCP Client]
  end

  subgraph Access[Access Layer]
    RestAPI[REST API]
    SSE[SSE Stream]
  end

  subgraph Management[data-agent-management Spring Boot]
    GraphCtl[GraphController]
    AgentCtl[AgentController]
    PromptCtl[PromptConfigController]
    ModelCtl[ModelConfigController]
    GraphSvc[GraphServiceImpl]
    Context[MultiTurnContextManager]
    Graph[StateGraph Workflow]
    LlmSvc[LlmService]
    ModelRegistry[AiModelRegistry]
    VectorSvc[AgentVectorStoreService]
    Hybrid[HybridRetrievalStrategy]
    CodePool[CodePoolExecutorService]
    McpSvc[McpServerService]
    LangfuseSvc[LangfuseService]
  end

  subgraph Observability[Observability]
    Langfuse[Langfuse Platform]
  end

  subgraph Data[Data Storage]
    BizDB[(Business DB)]
    MetaDB[(Management DB)]
    VectorDB[(Vector Store)]
    Files[(Knowledge Files)]
  end

  subgraph LLMs[LLM Providers]
    ChatLLM[Chat Model]
    EmbeddingLLM[Embedding Model]
  end

  subgraph Exec[Python Runtime]
    Docker[Docker Executor]
    Local[Local Executor]
    AISim[AI Simulation Executor]
  end

  UserUI --> RestAPI
  UserUI --> SSE
  AdminUI --> RestAPI
  MCPClient --> McpSvc
  RestAPI --> AgentCtl
  RestAPI --> PromptCtl
  RestAPI --> ModelCtl
  SSE --> GraphCtl
  GraphCtl --> GraphSvc
  GraphSvc --> Context
  GraphSvc --> Graph
  Graph --> LlmSvc
  GraphSvc --> VectorSvc
  VectorSvc --> Hybrid
  VectorSvc --> VectorDB
  VectorSvc --> Files
  Graph --> BizDB
  GraphSvc --> ModelRegistry
  ModelRegistry --> ChatLLM
  ModelRegistry --> EmbeddingLLM
  GraphSvc --> CodePool
  CodePool --> Docker
  CodePool --> Local
  CodePool --> AISim
  GraphSvc --> LangfuseSvc
  LangfuseSvc --> Langfuse
  AgentCtl --> MetaDB
  PromptCtl --> MetaDB
  ModelCtl --> MetaDB

  classDef client fill:#FFF4E6,stroke:#D97706,stroke-width:1px,color:#1F2937;
  classDef access fill:#E0F2FE,stroke:#0284C7,stroke-width:1px,color:#1F2937;
  classDef api fill:#DBEAFE,stroke:#2563EB,stroke-width:1px,color:#1F2937;
  classDef service fill:#ECFDF3,stroke:#16A34A,stroke-width:1px,color:#1F2937;
  classDef workflow fill:#F0FDF4,stroke:#22C55E,stroke-width:1.5px,color:#1F2937;
  classDef data fill:#FEF3C7,stroke:#F59E0B,stroke-width:1px,color:#1F2937;
  classDef llm fill:#E0F7FA,stroke:#06B6D4,stroke-width:1px,color:#1F2937;
  classDef exec fill:#FFE4E6,stroke:#EF4444,stroke-width:1px,color:#1F2937;
  classDef observability fill:#F3E8FF,stroke:#9333EA,stroke-width:1px,color:#1F2937;

  class UserUI,AdminUI,MCPClient client
  class RestAPI,SSE access
  class GraphCtl,AgentCtl,PromptCtl,ModelCtl api
  class GraphSvc,Context,LlmSvc,ModelRegistry,VectorSvc,Hybrid,CodePool,McpSvc,LangfuseSvc service
  class Graph workflow
  class BizDB,MetaDB,VectorDB,Files data
  class ChatLLM,EmbeddingLLM llm
  class Docker,Local,AISim exec
  class Langfuse observability

  style Clients fill:#FFF7ED,stroke:#D97706,stroke-width:1.5px
  style Access fill:#EFF6FF,stroke:#0284C7,stroke-width:1.5px
  style Management fill:#F0FDF4,stroke:#16A34A,stroke-width:1.5px
  style Data fill:#FFFBEB,stroke:#F59E0B,stroke-width:1.5px
  style LLMs fill:#ECFEFF,stroke:#06B6D4,stroke-width:1.5px
  style Exec fill:#FFF1F2,stroke:#EF4444,stroke-width:1.5px
  style Observability fill:#FAF5FF,stroke:#9333EA,stroke-width:1.5px
```

## 🔄 运行时主流程

```mermaid
%%{init: {"theme": "base", "flowchart": {"curve": "basis", "nodeSpacing": 30, "rankSpacing": 40}, "themeVariables": {"lineColor": "#475569", "primaryTextColor": "#1F2937"}}}%%
flowchart TD
  Start([Start]) --> BuildCtx[Build MultiTurn Context]
  BuildCtx --> Intent[IntentRecognitionNode]
  Intent --> IntentGate{Need analysis}
  IntentGate -->|no| End([End])
  IntentGate -->|yes| Evidence[EvidenceRecallNode]
  Evidence --> Rewrite[QueryEnhanceNode]
  Rewrite --> Schema[SchemaRecallNode]
  Schema --> Relation[TableRelationNode]
  Relation --> RelGate{Relation ok}
  RelGate -->|retry| Relation
  RelGate --> Feasible[FeasibilityAssessmentNode]
  Feasible --> FeasibleGate{Feasible}
  FeasibleGate -->|no| End
  FeasibleGate --> Planner[PlannerNode]
  Planner --> PlanValidate[PlanExecutor validate]
  PlanValidate -->|invalid| Planner
  PlanValidate --> HumanGate{Human review}
  HumanGate -->|yes| Human[HumanFeedbackNode]
  HumanGate -->|no| StepSelect[Select next step]

  Human -->|approve| StepSelect
  Human -->|reject| Planner

  StepSelect --> SQLGate{SQL step}
  SQLGate -->|yes| SQLGen[SqlGenerateNode]
  SQLGen --> SemCheck[SemanticConsistencyNode]
  SemCheck --> SemGate{Semantics ok}
  SemGate -->|no| SQLGen
  SemGate --> SQLExec[SqlExecuteNode]
  SQLExec --> SQLGate2{SQL exec ok}
  SQLGate2 -->|no| SQLGen
  SQLGate2 --> StoreSQL[Store SQL Result]
  StoreSQL --> StepSelect

  StepSelect --> PyGate{Python step}
  PyGate -->|yes| PyGen[PythonGenerateNode]
  PyGen --> PyExec[PythonExecuteNode]
  PyExec --> PyGate2{Python ok}
  PyGate2 -->|no| PyGen
  PyGate2 --> PyAnalyze[PythonAnalyzeNode]
  PyAnalyze --> StorePy[Store Analysis]
  StorePy --> StepSelect

  StepSelect --> ReportGate{Report step}
  ReportGate -->|yes| Report[ReportGeneratorNode]
  ReportGate -->|no| End
  Report --> End

  classDef input fill:#E0F2FE,stroke:#0284C7,stroke-width:1px,color:#1F2937;
  classDef retrieval fill:#E0F7FA,stroke:#06B6D4,stroke-width:1px,color:#1F2937;
  classDef planning fill:#ECFDF3,stroke:#16A34A,stroke-width:1px,color:#1F2937;
  classDef decision fill:#F3F4F6,stroke:#6B7280,stroke-width:1px,color:#1F2937;
  classDef execution fill:#FFF8E1,stroke:#F59E0B,stroke-width:1px,color:#1F2937;
  classDef data fill:#FEF3C7,stroke:#D97706,stroke-width:1px,color:#1F2937;
  classDef feedback fill:#FFF4E6,stroke:#D97706,stroke-width:1px,color:#1F2937;
  classDef output fill:#E0F2FE,stroke:#0284C7,stroke-width:1px,color:#1F2937;
  classDef terminal fill:#E5E7EB,stroke:#9CA3AF,stroke-width:1px,color:#1F2937;

  class Start,End terminal
  class BuildCtx,Intent input
  class Evidence,Rewrite,Schema,Relation retrieval
  class Feasible,Planner,PlanValidate,StepSelect planning
  class IntentGate,RelGate,FeasibleGate,HumanGate,SQLGate,SemGate,SQLGate2,PyGate,PyGate2,ReportGate decision
  class Human feedback
  class SQLGen,SemCheck,SQLExec,PyGen,PyExec,PyAnalyze execution
  class StoreSQL,StorePy data
  class Report output
```

## 🎯 关键能力说明

### 1. 人类反馈机制

#### 说明要点

- **入口**: 运行时请求参数 `humanFeedback=true`（`GraphController` → `GraphServiceImpl`）
- **数据字段**: `human_review_enabled`以运行时请求参数为准
- **图编排**: `PlanExecutorNode` 检测 `HUMAN_REVIEW_ENABLED`，转入 `HumanFeedbackNode`
- **暂停与恢复**: `CompiledGraph` 使用 `interruptBefore(HUMAN_FEEDBACK_NODE)`，无反馈时进入"等待"，反馈到达后通过 `threadId` 继续执行
- **反馈结果**: 同意继续执行；拒绝则回到 `PlannerNode` 并触发重新规划

#### 架构图

```mermaid
flowchart LR
  UI[Run UI] --> GraphAPI[GraphController SSE]
  GraphAPI --> GraphSvc[GraphServiceImpl]
  GraphSvc --> StreamCtx[StreamContext]
  GraphSvc --> Graph[CompiledGraph]
  Graph --> PlanExec[PlanExecutorNode]
  PlanExec --> Human[HumanFeedbackNode]
  Human --> FeedbackPayload[HumanFeedback payload]
  FeedbackPayload --> StateSnap[StateSnapshot]
  StateSnap --> GraphSvc
  GraphSvc --> GraphAPI

  classDef client fill:#FFF4E6,stroke:#D97706,stroke-width:1px,color:#1F2937;
  classDef api fill:#E0F2FE,stroke:#0284C7,stroke-width:1px,color:#1F2937;
  classDef service fill:#ECFDF3,stroke:#16A34A,stroke-width:1px,color:#1F2937;
  classDef state fill:#F3F4F6,stroke:#6B7280,stroke-width:1px,color:#1F2937;
  classDef feedback fill:#FFF8E1,stroke:#F59E0B,stroke-width:1px,color:#1F2937;

  class UI client
  class GraphAPI api
  class GraphSvc,Graph,PlanExec service
  class StreamCtx,StateSnap state
  class Human,FeedbackPayload feedback
```

#### 流程图

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#E3F2FD", "primaryBorderColor": "#1E88E5", "primaryTextColor": "#1F2937", "lineColor": "#4B5563", "secondaryColor": "#E8F5E9", "tertiaryColor": "#FFF1D6", "actorBkg": "#F3F4F6", "actorBorder": "#9CA3AF", "actorTextColor": "#111827", "noteBkgColor": "#FFF8E1", "noteTextColor": "#1F2937"}}}%%
sequenceDiagram
  autonumber
  participant U as User UI
  participant API as GraphController SSE
  participant GS as GraphServiceImpl
  participant G as CompiledGraph
  participant HF as HumanFeedbackNode
  participant CTX as MultiTurnContextManager
  participant SS as StateSnapshot

  U->>API: stream search with humanFeedback true
  API->>GS: graphStreamProcess
  GS->>CTX: buildContext and beginTurn
  GS->>G: fluxStream interruptBefore HumanFeedback
  G-->>API: plan stream chunks
  G-->>HF: wait for feedback
  HF-->>G: wait state ends

  Note over U,API: user submits feedback and threadId
  U->>API: stream search with feedback content
  API->>GS: handleHumanFeedback resume
  GS->>SS: getState threadId
  GS->>G: fluxStreamFromInitialNode
  HF-->>G: approve or reject
  G-->>API: continue execution stream
  GS->>CTX: finishTurn update history
```

### 2. Prompt 配置与自动优化

#### 说明要点

- **配置入口**: `/api/prompt-config/*`，数据表 `user_prompt_config`
- **作用范围**: 支持按 `agentId` 绑定或全局配置（`agentId` 为空）
- **Prompt 类型**: `report-generator`、`planner`、`sql-generator`、`python-generator`、`rewrite`
- **自动优化方式**: `ReportGeneratorNode` 拉取启用配置（按 `priority` 与 `display_order` 排序），通过 `PromptHelper.buildReportGeneratorPromptWithOptimization` 拼接"优化要求"
- **当前实现重点**: 报告生成节点已落地优化；其他类型为预留能力

#### 架构图

```mermaid
flowchart LR
  UI[Admin UI] --> PromptAPI[PromptConfigController]
  PromptAPI --> PromptSvc[UserPromptService]
  PromptSvc --> PromptMapper[UserPromptConfigMapper]
  PromptMapper --> PromptDB[(user_prompt_config)]
  Report[ReportGeneratorNode] --> PromptSvc
  Report --> PromptHelper
  PromptHelper --> Templates[PromptConstant templates]
  Report --> LlmSvc[LlmService]

  classDef client fill:#FFF4E6,stroke:#D97706,stroke-width:1px,color:#1F2937;
  classDef api fill:#E0F2FE,stroke:#0284C7,stroke-width:1px,color:#1F2937;
  classDef service fill:#ECFDF3,stroke:#16A34A,stroke-width:1px,color:#1F2937;
  classDef data fill:#FEF3C7,stroke:#F59E0B,stroke-width:1px,color:#1F2937;
  classDef llm fill:#E0F7FA,stroke:#06B6D4,stroke-width:1px,color:#1F2937;

  class UI client
  class PromptAPI api
  class PromptSvc,PromptMapper,Report,PromptHelper service
  class PromptDB data
  class Templates data
  class LlmSvc llm
```

#### 流程图

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#E3F2FD", "primaryBorderColor": "#1E88E5", "primaryTextColor": "#1F2937", "lineColor": "#4B5563", "secondaryColor": "#E8F5E9", "tertiaryColor": "#FFF1D6", "actorBkg": "#F3F4F6", "actorBorder": "#9CA3AF", "actorTextColor": "#111827", "noteBkgColor": "#FFF8E1", "noteTextColor": "#1F2937"}}}%%
sequenceDiagram
  autonumber
  participant A as Admin
  participant API as PromptConfigController
  participant Svc as UserPromptService
  participant Mapper as UserPromptConfigMapper
  participant DB as user_prompt_config
  participant R as ReportGeneratorNode
  participant H as PromptHelper
  participant L as LLM

  A->>API: 保存并启用优化配置
  API->>Svc: saveOrUpdateConfig
  Svc->>Mapper: insert or update
  Mapper->>DB: write config
  A->>R: 触发报告生成
  R->>Svc: getActiveConfigsByType
  Svc->>Mapper: select active configs
  Mapper->>DB: read configs
  R->>H: build optimized prompt
  H-->>R: prompt text
  R->>L: generate report
  L-->>R: report content
```

### 3. RAG 检索增强

#### 说明要点

- **查询重写**: `EvidenceRecallNode` 调用 LLM 生成独立检索问题
- **召回通道**: `AgentVectorStoreService` 执行向量检索；可选混合检索（向量+关键词，`AbstractHybridRetrievalStrategy`）
- **文档类型**: 业务知识 + 智能体知识，按元数据过滤并合并为 evidence 注入后续 prompt
- **关键配置**: `spring.ai.alibaba.data-agent.vector-store.enable-hybrid-search` 及相似度/TopK 等参数

#### 架构图

```mermaid
flowchart LR
  Evidence[EvidenceRecallNode] --> LLM[LLM Query Rewrite]
  Evidence --> MultiTurn[MultiTurn Context]
  Evidence --> VectorSvc[AgentVectorStoreService]
  VectorSvc --> Filter[DynamicFilterService]
  Filter --> VectorStore[VectorStore]
  VectorSvc --> Hybrid[HybridRetrievalStrategy]
  Hybrid --> Keyword[Keyword Search ES]
  Hybrid --> VectorStore
  Hybrid --> Fusion[FusionStrategy]
  Fusion --> Evidence
  Evidence --> KnowledgeMapper[AgentKnowledgeMapper]
  KnowledgeMapper --> KnowledgeDB[("agent_knowledge and business_knowledge")]
  Evidence --> Prompt[Build Evidence Prompt]

  classDef service fill:#ECFDF3,stroke:#16A34A,stroke-width:1px,color:#1F2937;
  classDef llm fill:#E0F7FA,stroke:#06B6D4,stroke-width:1px,color:#1F2937;
  classDef data fill:#FEF3C7,stroke:#F59E0B,stroke-width:1px,color:#1F2937;
  classDef control fill:#F3F4F6,stroke:#6B7280,stroke-width:1px,color:#1F2937;

  class Evidence,VectorSvc,Hybrid,Fusion,Prompt service
  class LLM llm
  class VectorStore,KnowledgeDB data
  class Filter,MultiTurn,KnowledgeMapper control
  class Keyword data
```

#### 流程图

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#E3F2FD", "primaryBorderColor": "#1E88E5", "primaryTextColor": "#1F2937", "lineColor": "#4B5563", "secondaryColor": "#E8F5E9", "tertiaryColor": "#FFF1D6", "actorBkg": "#F3F4F6", "actorBorder": "#9CA3AF", "actorTextColor": "#111827", "noteBkgColor": "#FFF8E1", "noteTextColor": "#1F2937"}}}%%
sequenceDiagram
  autonumber
  participant U as User
  participant E as EvidenceRecallNode
  participant L as LLM
  participant F as DynamicFilterService
  participant H as HybridRetrievalStrategy
  participant V as VectorStore
  participant Fu as FusionStrategy
  participant M as AgentKnowledgeMapper
  participant DB as Knowledge DB

  U->>E: 原始问题
  E->>L: 查询重写并注入多轮上下文
  L-->>E: standaloneQuery
  E->>F: build filter by agent and type
  F-->>E: filter expression
  E->>H: hybrid retrieve
  H->>V: vector search
  H->>Fu: keyword results
  Fu-->>H: fused docs
  H-->>E: evidence docs
  E->>M: fetch titles and metadata
  M->>DB: query knowledge
  DB-->>M: metadata rows
  E-->>U: evidence summary and snippets
```

### 4. 报告生成与摘要生成

#### 说明要点

- **报告节点**: `ReportGeneratorNode` 读取计划、SQL/Python 结果与摘要建议（`summary_and_recommendations`）
- **优化提示词**: 自动拼接优化配置后生成报告

#### 架构图

```mermaid
flowchart LR
  PlanExec[PlanExecutorNode] --> PlanData[Plan JSON]
  PlanExec --> SqlResults[SQL Results]
  PlanExec --> PyResults[Python Results]
  PlanData --> Report[ReportGeneratorNode]
  SqlResults --> Report
  PyResults --> Report
  Report --> PromptSvc[UserPromptService]
  PromptSvc --> PromptDB[(user_prompt_config)]
  Report --> PromptHelper
  PromptHelper --> Templates[PromptConstant templates]
  Report --> LLM[LlmService ChatClient]
  Report --> Stream[SSE Stream Output]

  classDef service fill:#ECFDF3,stroke:#16A34A,stroke-width:1px,color:#1F2937;
  classDef llm fill:#E0F7FA,stroke:#06B6D4,stroke-width:1px,color:#1F2937;
  classDef api fill:#E0F2FE,stroke:#0284C7,stroke-width:1px,color:#1F2937;
  classDef data fill:#FEF3C7,stroke:#F59E0B,stroke-width:1px,color:#1F2937;

  class PlanExec,Report,PromptHelper,PromptSvc service
  class LLM llm
  class Stream api
  class PlanData,SqlResults,PyResults,PromptDB,Templates data
```

#### 流程图

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#E3F2FD", "primaryBorderColor": "#1E88E5", "primaryTextColor": "#1F2937", "lineColor": "#4B5563", "secondaryColor": "#E8F5E9", "tertiaryColor": "#FFF1D6", "actorBkg": "#F3F4F6", "actorBorder": "#9CA3AF", "actorTextColor": "#111827", "noteBkgColor": "#FFF8E1", "noteTextColor": "#1F2937"}}}%%
sequenceDiagram
  autonumber
  participant P as PlanExecutorNode
  participant R as ReportGeneratorNode
  participant S as UserPromptService
  participant H as PromptHelper
  participant L as LLM
  participant C as Client

  P->>R: 计划与执行结果
  R->>S: get optimization configs
  S-->>R: configs
  R->>H: build report prompt
  H-->>R: prompt text
  R->>L: generate report
  L-->>R: report content
  R-->>C: HTML Markdown streaming output
```

### 5. 流式输出与多轮对话

#### 说明要点

- **流式输出**: `GraphController` SSE + `GraphServiceImpl` 流式处理
- **文本标记**: `TextType` 在流中标记 SQL/JSON/HTML/Markdown，前端据此渲染
- **多轮对话**: `MultiTurnContextManager` 记录"用户问题+规划结果"，注入到后续请求
- **模式切换**: `spring.ai.alibaba.data-agent.llm-service-type` 支持 `STREAM/BLOCK`

#### 架构图

```mermaid
flowchart LR
  Client --> SSE[GraphController SSE]
  SSE --> Sink[Sinks Many]
  SSE --> GraphSvc[GraphServiceImpl]
  GraphSvc --> StreamCtx[StreamContext]
  GraphSvc --> Ctx[MultiTurnContextManager]
  GraphSvc --> Graph[CompiledGraph]
  Graph --> LLM[LlmService Stream Block]
  Graph --> TextType[TextType Markers]
  TextType --> Sink
  Sink --> Client
  Client -.-> Stop[StopStreamProcessing]
  Stop -.-> GraphSvc

  classDef client fill:#FFF4E6,stroke:#D97706,stroke-width:1px,color:#1F2937;
  classDef api fill:#E0F2FE,stroke:#0284C7,stroke-width:1px,color:#1F2937;
  classDef service fill:#ECFDF3,stroke:#16A34A,stroke-width:1px,color:#1F2937;
  classDef data fill:#FEF3C7,stroke:#F59E0B,stroke-width:1px,color:#1F2937;
  classDef llm fill:#E0F7FA,stroke:#06B6D4,stroke-width:1px,color:#1F2937;
  classDef control fill:#F3F4F6,stroke:#6B7280,stroke-width:1px,color:#1F2937;

  class Client client
  class SSE,Sink api
  class GraphSvc,Graph service
  class StreamCtx,Ctx data
  class LLM llm
  class TextType,Stop control
```

#### 流程图

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#E3F2FD", "primaryBorderColor": "#1E88E5", "primaryTextColor": "#1F2937", "lineColor": "#4B5563", "secondaryColor": "#E8F5E9", "tertiaryColor": "#FFF1D6", "actorBkg": "#F3F4F6", "actorBorder": "#9CA3AF", "actorTextColor": "#111827", "noteBkgColor": "#FFF8E1", "noteTextColor": "#1F2937"}}}%%
sequenceDiagram
  autonumber
  participant C as Client
  participant API as GraphController SSE
  participant GS as GraphServiceImpl
  participant SC as StreamContext
  participant SK as Sinks Many
  participant CTX as MultiTurnContextManager
  participant G as CompiledGraph
  participant L as LlmService
  participant T as TextType

  C->>API: connect SSE and send query
  API->>GS: graphStreamProcess
  GS->>SC: create or get context
  GS->>CTX: beginTurn
  GS->>G: fluxStream threadId
  G->>L: stream model tokens
  L-->>G: token chunks
  G-->>T: detect text type markers
  G-->>SK: emit chunk
  SK-->>API: SSE data
  API-->>C: stream output
  C-->>API: disconnect
  API->>GS: stopStreamProcessing
  GS->>CTX: discardPending
```

### 6. MCP 与多模型调度

#### 说明要点

- **MCP**: `McpServerService` 提供 NL2SQL 与 Agent 列表工具，使用 Mcp Server Boot Starter
- **多模型调度**: `ModelConfig*` 配置模型，`AiModelRegistry` 缓存当前 Chat/Embedding 模型并支持热切换（同一时间每类仅一个激活模型）
- **已内置工具**: `nl2SqlToolCallback`、`listAgentsToolCallback`

#### 架构图

```mermaid
flowchart LR
  MCPClient --> MCPServer[Mcp Server]
  MCPServer --> ToolProvider[MethodToolCallbackProvider]
  ToolProvider --> McpSvc[McpServerService]
  McpSvc --> GraphSvc[GraphService]

  AdminUI --> ModelAPI[ModelConfigController]
  ModelAPI --> Ops[ModelConfigOpsService]
  Ops --> ModelData[ModelConfigDataService]
  ModelData --> ModelDB[(model_config)]
  Ops --> Registry[AiModelRegistry]
  Registry --> Factory[DynamicModelFactory]
  Factory --> OpenAI[OpenAiApi]
  OpenAI --> ChatLLM[Chat Model]
  OpenAI --> EmbeddingLLM[Embedding Model]

  classDef client fill:#FFF4E6,stroke:#D97706,stroke-width:1px,color:#1F2937;
  classDef api fill:#E0F2FE,stroke:#0284C7,stroke-width:1px,color:#1F2937;
  classDef service fill:#ECFDF3,stroke:#16A34A,stroke-width:1px,color:#1F2937;
  classDef data fill:#FEF3C7,stroke:#F59E0B,stroke-width:1px,color:#1F2937;
  classDef llm fill:#E0F7FA,stroke:#06B6D4,stroke-width:1px,color:#1F2937;

  class MCPClient,AdminUI client
  class MCPServer,ToolProvider,ModelAPI api
  class McpSvc,GraphSvc,Ops,Registry,Factory,ModelData service
  class ModelDB data
  class ChatLLM,EmbeddingLLM llm
```

#### 流程图

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#E3F2FD", "primaryBorderColor": "#1E88E5", "primaryTextColor": "#1F2937", "lineColor": "#4B5563", "secondaryColor": "#E8F5E9", "tertiaryColor": "#FFF1D6", "actorBkg": "#F3F4F6", "actorBorder": "#9CA3AF", "actorTextColor": "#111827", "noteBkgColor": "#FFF8E1", "noteTextColor": "#1F2937"}}}%%
sequenceDiagram
  autonumber
  participant A as Admin
  participant MAPI as ModelConfigController
  participant Ops as ModelConfigOpsService
  participant Reg as AiModelRegistry
  participant Factory as DynamicModelFactory
  participant OpenAI as OpenAiApi
  participant MCP as MCP Client
  participant McpSvc as McpServerService
  participant GS as GraphService

  A->>MAPI: activate model config
  MAPI->>Ops: activateConfig
  Ops->>Reg: refreshChat or refreshEmbedding
  Reg->>Factory: create model instance
  Factory->>OpenAI: build API client
  OpenAI-->>Reg: model ready

  MCP->>McpSvc: call tool nl2SqlToolCallback
  McpSvc->>GS: nl2sql
  GS-->>McpSvc: SQL result
  McpSvc-->>MCP: tool response
```

### 7. API Key 与权限管理

#### 说明要点

- **管理端**: `AgentController` 支持生成、重置、删除与启用/禁用 API Key
- **数据字段**: `agent.api_key` 与 `agent.api_key_enabled`
- **调用方式**: 请求头 `X-API-Key`（需自行实现后端校验逻辑）
- **注意**: 默认后端未对 `X-API-Key` 做鉴权拦截，生产需自行补充

#### 架构图

```mermaid
flowchart LR
  UI --> AgentAPI[AgentController]
  AgentAPI --> AgentSvc[AgentService]
  AgentSvc --> AgentMapper[AgentMapper]
  AgentMapper --> AgentDB[(agent)]
  UI --> GraphAPI[GraphController]
  GraphAPI -.-> Auth[Optional Auth Interceptor]
  Auth -.-> AgentSvc

  classDef client fill:#FFF4E6,stroke:#D97706,stroke-width:1px,color:#1F2937;
  classDef api fill:#E0F2FE,stroke:#0284C7,stroke-width:1px,color:#1F2937;
  classDef service fill:#ECFDF3,stroke:#16A34A,stroke-width:1px,color:#1F2937;
  classDef data fill:#FEF3C7,stroke:#F59E0B,stroke-width:1px,color:#1F2937;
  classDef control fill:#F3F4F6,stroke:#6B7280,stroke-width:1px,color:#1F2937;

  class UI client
  class AgentAPI,GraphAPI api
  class AgentSvc,AgentMapper service
  class AgentDB data
  class Auth control
```

#### 流程图

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#E3F2FD", "primaryBorderColor": "#1E88E5", "primaryTextColor": "#1F2937", "lineColor": "#4B5563", "secondaryColor": "#E8F5E9", "tertiaryColor": "#FFF1D6", "actorBkg": "#F3F4F6", "actorBorder": "#9CA3AF", "actorTextColor": "#111827", "noteBkgColor": "#FFF8E1", "noteTextColor": "#1F2937"}}}%%
sequenceDiagram
  autonumber
  participant U as User
  participant API as AgentController
  participant S as AgentService
  participant M as AgentMapper
  participant DB as agent
  participant G as GraphController
  participant Auth as Optional Auth Interceptor

  U->>API: 生成并启用 API Key
  API->>S: generateApiKey
  S->>M: update agent key
  M->>DB: write api_key
  U->>G: 调用业务接口并带 X-API-Key
  opt custom auth enabled
    G->>Auth: validate api key
    Auth->>DB: check api_key_enabled
  end
  G-->>U: response
```

### 8. Python 执行与结果回传

#### 说明要点

- **代码生成**: `PythonGenerateNode` 根据计划与 SQL 结果生成 Python
- **代码执行**: `PythonExecuteNode` 使用 `CodePoolExecutorService`（Docker/Local/AI 模拟）
- **执行配置**: `spring.ai.alibaba.data-agent.code-executor.*`（默认 Docker 镜像 `continuumio/anaconda3:latest`）
- **结果回传**: 执行结果写回 `PYTHON_EXECUTE_NODE_OUTPUT`，`PythonAnalyzeNode` 汇总后写入 `SQL_EXECUTE_NODE_OUTPUT`，用于最终报告

#### 架构图

```mermaid
flowchart LR
  PyGen[PythonGenerateNode] --> PyExec[PythonExecuteNode]
  PyExec --> ExecSvc[CodePoolExecutorService]
  ExecSvc --> Queue[Task Queue]
  ExecSvc --> Pool[Container Pool]
  Pool --> Docker[Docker Executor]
  Pool --> Local[Local Executor]
  Pool --> AISim[AI Simulation Executor]
  Docker --> TempFiles[Temp Files]
  TempFiles --> StdIO[Stdout Stderr]
  StdIO --> JsonParse[JsonParseUtil]
  JsonParse --> PyAnalyze[PythonAnalyzeNode]
  PyAnalyze --> Report[ReportGeneratorNode]

  classDef service fill:#ECFDF3,stroke:#16A34A,stroke-width:1px,color:#1F2937;
  classDef exec fill:#FFE4E6,stroke:#EF4444,stroke-width:1px,color:#1F2937;
  classDef data fill:#FEF3C7,stroke:#F59E0B,stroke-width:1px,color:#1F2937;

  class PyGen,PyExec,PyAnalyze,Report service
  class ExecSvc,Pool,Docker,Local,AISim exec
  class Queue,TempFiles,StdIO,JsonParse data
```

#### 流程图

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#E3F2FD", "primaryBorderColor": "#1E88E5", "primaryTextColor": "#1F2937", "lineColor": "#4B5563", "secondaryColor": "#E8F5E9", "tertiaryColor": "#FFF1D6", "actorBkg": "#F3F4F6", "actorBorder": "#9CA3AF", "actorTextColor": "#111827", "noteBkgColor": "#FFF8E1", "noteTextColor": "#1F2937"}}}%%
sequenceDiagram
  autonumber
  participant P as PlanExecutorNode
  participant G as PythonGenerateNode
  participant L as LlmService
  participant E as PythonExecuteNode
  participant CP as CodePoolExecutorService
  participant D as Docker Executor
  participant J as JsonParseUtil
  participant A as PythonAnalyzeNode
  participant R as ReportGeneratorNode

  P->>G: 进入Python步骤并传入指令
  G->>L: generate python code
  L-->>G: python code
  G->>E: pass code and sql results
  E->>CP: runTask
  CP->>D: execute in container
  D-->>CP: stdout stderr
  CP-->>E: task response
  E->>J: parse stdout json
  J-->>E: normalized output
  E->>A: analyze result
  A-->>P: update step results
  P->>R: continue to report
```



## 🔗 相关文档

- [快速开始](QUICK_START.md) - 安装配置指南
- [高级功能](ADVANCED_FEATURES.md) - API调用和MCP服务器
- [开发者文档](DEVELOPER_GUIDE.md) - 贡献指南
