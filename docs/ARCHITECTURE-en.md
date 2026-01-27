[中文](./ARCHITECTURE.md) | English

# Architecture Design

This document provides a detailed introduction to DataAgent's system architecture, core capabilities, and technical implementation.

## Overall Architecture Diagram

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

  class UserUI,AdminUI,MCPClient client
  class RestAPI,SSE access
  class GraphCtl,AgentCtl,PromptCtl,ModelCtl api
  class GraphSvc,Context,LlmSvc,ModelRegistry,VectorSvc,Hybrid,CodePool,McpSvc service
  class Graph workflow
  class BizDB,MetaDB,VectorDB,Files data
  class ChatLLM,EmbeddingLLM llm
  class Docker,Local,AISim exec

  style Clients fill:#FFF7ED,stroke:#D97706,stroke-width:1.5px
  style Access fill:#EFF6FF,stroke:#0284C7,stroke-width:1.5px
  style Management fill:#F0FDF4,stroke:#16A34A,stroke-width:1.5px
  style Data fill:#FFFBEB,stroke:#F59E0B,stroke-width:1.5px
  style LLMs fill:#ECFEFF,stroke:#06B6D4,stroke-width:1.5px
  style Exec fill:#FFF1F2,stroke:#EF4444,stroke-width:1.5px
```

## Runtime Main Flow

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

## Key Capability Description

### 1. Human Feedback Mechanism

#### Key Points

- **Entry**: Runtime request parameter `humanFeedback=true` (`GraphController` → `GraphServiceImpl`)
- **Data Field**: `human_review_enabled` uses request parameter
- **Graph Orchestration**: `PlanExecutorNode` detects `HUMAN_REVIEW_ENABLED`, transitions to `HumanFeedbackNode`
- **Pause and Resume**: `CompiledGraph` uses `interruptBefore(HUMAN_FEEDBACK_NODE)`, enters "wait" state when no feedback, continues execution through `threadId` when feedback arrives
- **Feedback Result**: Approve continues execution; Reject returns to `PlannerNode` and triggers replanning

#### Architecture Diagram

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

#### Flow Diagram

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

### 2. Prompt Configuration and Auto-Optimization

#### Key Points

- **Configuration Entry**: `/api/prompt-config/*`, data table `user_prompt_config`
- **Scope**: Supports binding by `agentId` or global configuration (`agentId` is null)
- **Prompt Types**: `report-generator`, `planner`, `sql-generator`, `python-generator`, `rewrite`
- **Auto-Optimization Method**: `ReportGeneratorNode` fetches enabled configurations (sorted by `priority` and `display_order`), concatenates "optimization requirements" through `PromptHelper.buildReportGeneratorPromptWithOptimization`
- **Current Implementation Focus**: Report generation node has implemented optimization; other types are reserved capabilities

#### Architecture Diagram

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

#### Flow Diagram

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

  A->>API: Save and enable optimization config
  API->>Svc: saveOrUpdateConfig
  Svc->>Mapper: insert or update
  Mapper->>DB: write config
  A->>R: Trigger report generation
  R->>Svc: getActiveConfigsByType
  Svc->>Mapper: select active configs
  Mapper->>DB: read configs
  R->>H: build optimized prompt
  H-->>R: prompt text
  R->>L: generate report
  L-->>R: report content
```

### 3. RAG Retrieval Enhancement

#### Key Points

- **Query Rewriting**: `EvidenceRecallNode` calls LLM to generate independent retrieval questions
- **Recall Channels**: `AgentVectorStoreService` performs vector retrieval; optional hybrid retrieval (vector + keyword, `AbstractHybridRetrievalStrategy`)
- **Document Types**: Business knowledge + Agent knowledge, filtered by metadata and merged as evidence injected into subsequent prompts
- **Key Configuration**: `spring.ai.alibaba.data-agent.vector-store.enable-hybrid-search` and similarity/TopK parameters

#### Architecture Diagram

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

#### Flow Diagram

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

  U->>E: Original question
  E->>L: Query rewrite with multi-turn context injection
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

### 4. Report Generation and Summary Generation

#### Key Points

- **Report Node**: `ReportGeneratorNode` reads plan, SQL/Python results and summary suggestions (`summary_and_recommendations`)
- **Output Format**: Default HTML, `plainReport=true` outputs Markdown (concise report)
- **Optimization Prompts**: Automatically concatenates optimization configuration before generating report

#### Architecture Diagram

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

#### Flow Diagram

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

  P->>R: Plan and execution results
  R->>S: get optimization configs
  S-->>R: configs
  R->>H: build report prompt
  H-->>R: prompt text
  R->>L: generate report
  L-->>R: report content
  R-->>C: HTML Markdown streaming output
```

### 5. Streaming Output and Multi-turn Conversation

#### Key Points

- **Streaming Output**: `GraphController` SSE + `GraphServiceImpl` streaming processing
- **Text Markers**: `TextType` marks SQL/JSON/HTML/Markdown in the stream, frontend renders accordingly
- **Multi-turn Conversation**: `MultiTurnContextManager` records "user question + planning results", injected into subsequent requests
- **Mode Switching**: `spring.ai.alibaba.data-agent.llm-service-type` supports `STREAM/BLOCK`

#### Architecture Diagram

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

#### Flow Diagram

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

### 6. MCP and Multi-Model Scheduling

#### Key Points

- **MCP**: `McpServerService` provides NL2SQL and Agent list tools, using Mcp Server Boot Starter
- **Multi-Model Scheduling**: `ModelConfig*` configures models, `AiModelRegistry` caches current Chat/Embedding models and supports hot-swapping (only one active model per type at a time)
- **Built-in Tools**: `nl2SqlToolCallback`, `listAgentsToolCallback`

#### Architecture Diagram

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

#### Flow Diagram

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

### 7. API Key and Permission Management

#### Key Points

- **Management**: `AgentController` supports generating, resetting, deleting, and enabling/disabling API Keys
- **Data Fields**: `agent.api_key` and `agent.api_key_enabled`
- **Calling Method**: Request header `X-API-Key` (requires implementing backend validation logic yourself)
- **Note**: By default, the backend does not intercept `X-API-Key` for authentication; production needs to add validation yourself

#### Architecture Diagram

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

#### Flow Diagram

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

  U->>API: Generate and enable API Key
  API->>S: generateApiKey
  S->>M: update agent key
  M->>DB: write api_key
  U->>G: Call business interface with X-API-Key
  opt custom auth enabled
    G->>Auth: validate api key
    Auth->>DB: check api_key_enabled
  end
  G-->>U: response
```

### 8. Python Execution and Result Return

#### Key Points

- **Code Generation**: `PythonGenerateNode` generates Python based on plan and SQL results
- **Code Execution**: `PythonExecuteNode` uses `CodePoolExecutorService` (Docker/Local/AI simulation)
- **Execution Configuration**: `spring.ai.alibaba.data-agent.code-executor.*` (default Docker image `continuumio/anaconda3:latest`)
- **Result Return**: Execution results are written back to `PYTHON_EXECUTE_NODE_OUTPUT`, `PythonAnalyzeNode` summarizes and writes to `SQL_EXECUTE_NODE_OUTPUT` for final report

#### Architecture Diagram

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

#### Flow Diagram

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

  P->>G: Enter Python step with instructions
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



## Related Documents

- [Quick Start](QUICK_START-en.md) - Installation and configuration guide
- [Advanced Features](ADVANCED_FEATURES-en.md) - API calls and MCP server
- [Developer Documentation](DEVELOPER_GUIDE-en.md) - Contribution guide
