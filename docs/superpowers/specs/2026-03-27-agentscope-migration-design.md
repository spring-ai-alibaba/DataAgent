# AgentScope Java 迁移设计方案

**日期：** 2026-03-27
**作者：** Claude Code
**状态：** 设计阶段

## 1. 项目概述

### 1.1 迁移目标

将 DataAgent 从 Spring AI Alibaba Graph 架构完全迁移到 AgentScope Java 框架，采用 Agent-Oriented Programming 范式重构工作流引擎。

### 1.2 迁移范围

- **完全替换** Spring AI Alibaba Graph 依赖
- **完全替换** Spring AI 的 ChatModel/EmbeddingModel，使用 AgentScope 模型层
- **保持 API 完全兼容**，前端无需改动
- **保留所有核心特性**：Human-in-the-loop、多轮对话、RAG、Python 执行、流式输出、多模型切换、MCP 服务、Langfuse 监控

### 1.3 迁移动机

AgentScope Java 提供更好的 Agent-Oriented Programming 模型，更适合构建复杂的智能体系统：
- 每个 Agent 有独立的角色、记忆和行为
- 更清晰的 Agent 间消息传递机制
- 更灵活的工作流编排能力
- 更好的可扩展性和可维护性

## 2. 整体架构设计

### 2.1 分层架构

采用**分层适配器模式**，在 AgentScope Java 之上构建适配层，保持现有接口不变：

```
┌─────────────────────────────────────────────────┐
│  API Layer (保持不变)                            │
│  - GraphController (SSE)                        │
│  - AgentController, ModelConfigController       │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│  Adapter Layer (新增)                            │
│  - GraphServiceAdapter: 适配 StateGraph 接口     │
│  - StreamingAdapter: 适配 SSE 流式输出           │
│  - StateConverter: 状态转换器                    │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│  AgentScope Core (新)                            │
│  - AgentWorkflow: 工作流编排                     │
│  - Agent Registry: Agent 注册和管理              │
│  - Message Bus: Agent 间消息传递                 │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│  Agent Layer (新)                                │
│  - IntentAgent: 意图识别                         │
│  - EvidenceAgent: 证据召回                       │
│  - PlannerAgent: 计划生成                        │
│  - SqlAgent: SQL 生成和执行                      │
│  - PythonAgent: Python 生成和执行                │
│  - ReportAgent: 报告生成                         │
│  - HumanFeedbackAgent: 人工反馈                  │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│  Service Layer (部分保留，部分重写)               │
│  - AgentModelService: 模型服务 (新)              │
│  - VectorStoreService: 向量检索 (保留)           │
│  - CodeExecutorService: Python 执行 (保留)       │
│  - McpServerService: MCP 服务 (保留)             │
│  - LangfuseService: 可观测性 (保留)              │
└─────────────────────────────────────────────────┘
```

### 2.2 包结构

```
com.alibaba.cloud.ai.dataagent.agentscope/
├── agent/                    # Agent 实现
│   ├── BaseDataAgent.java
│   ├── IntentAgent.java
│   ├── EvidenceAgent.java
│   ├── PlannerAgent.java
│   ├── SqlAgent.java
│   ├── PythonAgent.java
│   ├── ReportAgent.java
│   └── HumanFeedbackAgent.java
├── workflow/                 # 工作流编排
│   ├── AgentWorkflow.java
│   ├── WorkflowState.java
│   └── WorkflowEvent.java
├── adapter/                  # 适配层
│   ├── GraphServiceAdapter.java
│   ├── StreamingAdapter.java
│   ├── StateConverter.java
│   └── AgentContextManager.java
├── message/                  # 消息定义
│   ├── AgentMessage.java
│   ├── MessageBus.java
│   └── MessageType.java
└── service/                  # AgentScope 服务
    ├── AgentModelService.java
    ├── ModelServiceWrapper.java
    └── AgentRegistry.java
```

## 3. 核心组件设计

### 3.1 Agent 设计

#### 3.1.1 BaseDataAgent

所有 Agent 继承自 `BaseDataAgent`，遵循 AgentScope 的 Agent-Oriented Programming 范式：

```java
public abstract class BaseDataAgent extends AgentBase {
    protected AgentModelService modelService;
    protected Memory memory;  // Agent 记忆
    protected String role;    // Agent 角色

    // AgentScope 标准方法
    @Override
    public Message reply(Message input);

    // 扩展方法
    protected abstract Message process(Message input);
    protected void updateMemory(Message input, Message output);
}
```

#### 3.1.2 七个核心 Agent

1. **IntentAgent** - 意图识别
   - 角色：判断用户输入是闲聊还是数据分析
   - 输入：用户问题
   - 输出：意图类型 + 是否继续

2. **EvidenceAgent** - 证据召回
   - 角色：从向量库检索相关业务知识
   - 输入：用户问题 + 多轮上下文
   - 输出：召回的证据文档

3. **PlannerAgent** - 计划生成
   - 角色：生成 SQL + Python 执行计划
   - 输入：问题 + 证据 + Schema
   - 输出：结构化执行计划

4. **SqlAgent** - SQL 处理
   - 角色：生成、验证、执行 SQL
   - 输入：计划中的 SQL 步骤
   - 输出：SQL 结果

5. **PythonAgent** - Python 处理
   - 角色：生成、执行 Python 分析代码
   - 输入：计划中的 Python 步骤 + SQL 结果
   - 输出：分析结果

6. **ReportAgent** - 报告生成
   - 角色：汇总结果生成 HTML/Markdown 报告
   - 输入：所有执行结果
   - 输出：最终报告

7. **HumanFeedbackAgent** - 人工反馈
   - 角色：处理人工审核和反馈
   - 输入：计划 + 人工反馈
   - 输出：继续/重新规划

### 3.2 工作流编排

#### 3.2.1 AgentWorkflow

```java
public class AgentWorkflow {
    private AgentRegistry agentRegistry;
    private MessageBus messageBus;
    private WorkflowState state;

    // 工作流执行
    public Flux<WorkflowEvent> execute(WorkflowRequest request);

    // 支持中断和恢复（Human-in-the-loop）
    public void interrupt(String threadId);
    public Flux<WorkflowEvent> resume(String threadId, Message feedback);
}
```

#### 3.2.2 执行流程

```
用户请求
  ↓
IntentAgent → 判断意图
  ↓ (需要分析)
EvidenceAgent → 召回证据
  ↓
QueryEnhanceAgent → 查询增强
  ↓
SchemaRecallAgent → 召回 Schema
  ↓
TableRelationAgent → 推断表关系
  ↓
FeasibilityAgent → 可行性评估
  ↓
PlannerAgent → 生成计划
  ↓
HumanFeedbackAgent → 人工审核 (可选)
  ↓ (批准)
PlanExecutor → 执行计划
  ├─ SqlAgent (循环执行 SQL 步骤)
  └─ PythonAgent (循环执行 Python 步骤)
  ↓
ReportAgent → 生成报告
  ↓
返回结果
```

### 3.3 消息传递机制

#### 3.3.1 AgentMessage

```java
public class AgentMessage extends Message {
    private String messageId;
    private String threadId;
    private String fromAgent;
    private String toAgent;
    private MessageType type;  // TEXT, JSON, SQL, PYTHON, HTML
    private Object content;
    private Map<String, Object> metadata;
    private long timestamp;
}
```

#### 3.3.2 MessageBus

```java
public class MessageBus {
    // 同步消息传递
    public Message send(String toAgent, Message message);

    // 异步消息传递
    public Mono<Message> sendAsync(String toAgent, Message message);

    // 广播消息
    public void broadcast(Message message);

    // 流式消息
    public Flux<Message> stream(String toAgent, Message message);
}
```

### 3.4 状态管理

#### 3.4.1 WorkflowState

```java
public class WorkflowState {
    private String threadId;
    private String agentId;
    private String currentNode;
    private Map<String, Object> globalState;  // 全局状态
    private List<Message> messageHistory;     // 消息历史
    private Map<String, Object> agentMemory;  // Agent 记忆
    private WorkflowStatus status;            // RUNNING, INTERRUPTED, COMPLETED, FAILED

    // 状态持久化（支持中断恢复）
    public void save();
    public static WorkflowState load(String threadId);
}
```

#### 3.4.2 StateConverter

```java
public class StateConverter {
    // AgentScope State → Spring AI State
    public OverAllState toOverAllState(WorkflowState workflowState);

    // Spring AI State → AgentScope State
    public WorkflowState fromOverAllState(OverAllState overAllState);
}
```

## 4. 关键特性实现

### 4.1 Human-in-the-Loop

#### 4.1.1 中断机制

```java
public class HumanFeedbackAgent extends BaseDataAgent {
    @Override
    public Message reply(Message input) {
        // 1. 检查是否启用人工审核
        if (!isHumanReviewEnabled(input)) {
            return Message.success("approved");
        }

        // 2. 发送中断信号
        WorkflowState state = getWorkflowState();
        state.setStatus(WorkflowStatus.INTERRUPTED);
        state.save();

        // 3. 返回等待反馈的消息
        return Message.builder()
            .type(MessageType.HUMAN_FEEDBACK_REQUIRED)
            .content(extractPlanForReview(input))
            .metadata(Map.of("threadId", state.getThreadId()))
            .build();
    }
}
```

#### 4.1.2 恢复机制

```java
public class AgentWorkflow {
    public Flux<WorkflowEvent> resume(String threadId, Message feedback) {
        // 1. 加载保存的状态
        WorkflowState state = WorkflowState.load(threadId);

        // 2. 处理反馈
        HumanFeedbackAgent feedbackAgent = getAgent("HumanFeedbackAgent");
        Message result = feedbackAgent.processFeedback(threadId, feedback.getContent());

        // 3. 根据结果决定下一步
        if (result.getType() == MessageType.SUCCESS) {
            return continueExecution(state);
        } else {
            return replan(state, result);
        }
    }
}
```

### 4.2 多轮对话上下文

```java
public class AgentContextManager {
    private Map<String, ConversationContext> contexts = new ConcurrentHashMap<>();

    public void beginTurn(String agentId, String threadId, String query);
    public void finishTurn(String agentId, String threadId, String plan);
    public List<Turn> getRecentTurns(String agentId, int maxTurns);
}
```

集成到 Agent：

```java
public abstract class BaseDataAgent extends AgentBase {
    @Override
    public Message reply(Message input) {
        // 1. 获取多轮上下文
        List<Turn> history = contextManager.getRecentTurns(getAgentId(), 5);

        // 2. 将历史注入到 prompt
        Message enrichedInput = enrichWithHistory(input, history);

        // 3. 处理
        Message output = process(enrichedInput);

        // 4. 更新上下文
        contextManager.finishTurn(getAgentId(), input.getThreadId(), output.getContent());

        return output;
    }
}
```

### 4.3 RAG 检索增强

```java
public class EvidenceAgent extends BaseDataAgent {
    private AgentVectorStoreService vectorStoreService;
    private HybridRetrievalStrategy hybridStrategy;

    @Override
    public Message reply(Message input) {
        // 1. 查询重写（使用 LLM）
        String standaloneQuery = rewriteQuery(input);

        // 2. 向量检索
        List<Document> vectorDocs = vectorStoreService.retrieve(
            standaloneQuery,
            input.getMetadata().get("agentId"),
            VectorType.BUSINESS_KNOWLEDGE
        );

        // 3. 混合检索（可选）
        if (hybridStrategy.isEnabled()) {
            List<Document> hybridDocs = hybridStrategy.retrieve(
                standaloneQuery,
                vectorDocs
            );
            vectorDocs = hybridDocs;
        }

        // 4. 返回证据
        return Message.builder()
            .type(MessageType.JSON)
            .content(formatEvidence(vectorDocs))
            .build();
    }
}
```

### 4.4 模型服务

```java
public class AgentModelService {
    private ModelServiceWrapper chatModelWrapper;
    private ModelServiceWrapper embeddingModelWrapper;
    private ModelRegistry registry;

    // 聊天模型调用
    public String chat(String prompt);
    public Flux<String> chatStream(String prompt);

    // Embedding 调用
    public List<Double> embed(String text);

    // 模型切换
    public void switchChatModel(String modelId);
}
```

与 AgentScope 模型层集成：

```java
public class ModelServiceWrapper {
    private ModelWrapperBase agentScopeModel;

    public String invoke(String prompt) {
        ModelResponse response = agentScopeModel.call(
            List.of(new Msg("user", prompt))
        );
        return response.getText();
    }

    public Flux<String> stream(String prompt) {
        return Flux.create(sink -> {
            agentScopeModel.stream(
                List.of(new Msg("user", prompt)),
                chunk -> sink.next(chunk.getText())
            );
            sink.complete();
        });
    }
}
```

### 4.5 流式输出适配

```java
public class StreamingAdapter {
    public Flux<ServerSentEvent<GraphNodeResponse>> adapt(
        Flux<WorkflowEvent> workflowEvents
    ) {
        return workflowEvents
            .map(event -> convertToGraphNodeResponse(event))
            .map(response -> ServerSentEvent.builder(response).build());
    }

    private GraphNodeResponse convertToGraphNodeResponse(WorkflowEvent event) {
        // 转换逻辑：
        // - 保持 TextType 标记
        // - 保持节点名称
        // - 保持数据格式
    }
}
```

## 5. 错误处理

### 5.1 分层错误处理

```java
// Agent 层错误
public abstract class BaseDataAgent extends AgentBase {
    @Override
    public Message reply(Message input) {
        try {
            return process(input);
        } catch (AgentException e) {
            log.error("Agent {} failed: {}", getName(), e.getMessage());
            return Message.error(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in agent {}", getName(), e);
            return Message.error("Internal error: " + e.getMessage());
        }
    }
}

// Workflow 层错误
public class AgentWorkflow {
    public Flux<WorkflowEvent> execute(WorkflowRequest request) {
        return Flux.create(sink -> {
            try {
                executeInternal(request, sink);
            } catch (WorkflowException e) {
                sink.next(WorkflowEvent.error(e));
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        }).onErrorResume(e -> handleWorkflowError(e));
    }
}
```

### 5.2 重试机制

```java
public class SqlAgent extends BaseDataAgent {
    private static final int MAX_RETRY = 3;

    @Override
    protected Message process(Message input) {
        int retryCount = 0;
        Exception lastError = null;

        while (retryCount < MAX_RETRY) {
            try {
                String sql = generateSql(input);
                if (!checkSemanticConsistency(sql, input)) {
                    retryCount++;
                    continue;
                }
                Object result = executeSql(sql);
                return Message.success(result);
            } catch (SQLException e) {
                lastError = e;
                retryCount++;
                log.warn("SQL execution failed, retry {}/{}", retryCount, MAX_RETRY);
            }
        }

        throw new AgentException("SQL execution failed after retries", lastError);
    }
}
```

## 6. 测试策略

### 6.1 单元测试

```java
@SpringBootTest
class IntentAgentTest {
    @Autowired
    private IntentAgent intentAgent;

    @MockBean
    private AgentModelService modelService;

    @Test
    void testDataAnalysisIntent() {
        when(modelService.chat(anyString()))
            .thenReturn("{\"intent\": \"data_analysis\", \"needAnalysis\": true}");

        Message input = Message.builder()
            .content("查询销售额前10的商品")
            .build();

        Message output = intentAgent.reply(input);

        assertThat(output.getType()).isEqualTo(MessageType.JSON);
    }
}
```

### 6.2 集成测试

```java
@SpringBootTest
@Testcontainers
class AgentWorkflowIntegrationTest {
    @Autowired
    private AgentWorkflow agentWorkflow;

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Test
    void testCompleteWorkflow() {
        WorkflowRequest request = WorkflowRequest.builder()
            .query("查询销售额前10的商品")
            .agentId("test-agent")
            .build();

        List<WorkflowEvent> events = agentWorkflow.execute(request)
            .collectList()
            .block();

        assertThat(events).isNotEmpty();
        assertThat(events.get(events.size() - 1).getType())
            .isEqualTo(EventType.REPORT_GENERATED);
    }
}
```

### 6.3 端到端测试

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GraphControllerE2ETest {
    @LocalServerPort
    private int port;

    @Test
    void testStreamingQuery() {
        WebClient client = WebClient.create("http://localhost:" + port);

        Flux<ServerSentEvent<GraphNodeResponse>> events = client.get()
            .uri("/api/graph/stream?query=查询销售额&agentId=test")
            .retrieve()
            .bodyToFlux(new ParameterizedTypeReference<>() {});

        StepVerifier.create(events)
            .expectNextMatches(event ->
                event.data().getNodeName().equals("IntentRecognition"))
            .expectComplete()
            .verify();
    }
}
```

## 7. 迁移步骤

### 阶段 1：准备阶段（1-2 天）

1. 创建新分支 `feature/agentscope-migration`
2. 添加 AgentScope Java 依赖到 pom.xml
3. 保留现有 Spring AI Alibaba 依赖（共存期）
4. 创建基础包结构

### 阶段 2：核心框架搭建（3-5 天）

1. 实现 `BaseDataAgent` 抽象类
2. 实现 `AgentWorkflow` 工作流引擎
3. 实现 `MessageBus` 消息总线
4. 实现 `WorkflowState` 状态管理
5. 实现 `AgentModelService` 模型服务
6. 编写单元测试验证核心框架

### 阶段 3：Agent 实现（5-7 天）

按优先级实现 7 个核心 Agent：
1. IntentAgent（1 天）
2. EvidenceAgent（1 天）
3. PlannerAgent（1-2 天）
4. SqlAgent（1-2 天）
5. PythonAgent（1 天）
6. ReportAgent（1 天）
7. HumanFeedbackAgent（1 天）

### 阶段 4：适配层实现（2-3 天）

1. 实现 `GraphServiceAdapter`
2. 实现 `StreamingAdapter`
3. 实现 `StateConverter`
4. 实现 `AgentContextManager`

### 阶段 5：集成测试（2-3 天）

1. 端到端测试完整流程
2. 性能测试（对比迁移前后）
3. 兼容性测试（前端无需改动）
4. 压力测试（并发场景）

### 阶段 6：特性验证（2-3 天）

验证所有关键特性：
- Human-in-the-loop
- 多轮对话
- RAG 检索
- Python 执行
- 流式输出
- 模型切换
- MCP 服务
- Langfuse 监控

### 阶段 7：清理和优化（1-2 天）

1. 移除 Spring AI Alibaba Graph 依赖
2. 删除旧的 workflow/node 代码
3. 更新文档和 CLAUDE.md
4. 代码审查和优化

**总计：16-25 天**

## 8. 风险与缓解措施

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| AgentScope 模型层不支持现有模型 | 高 | 中 | 1. 提前验证模型兼容性<br>2. 保留 Spring AI 模型层作为备选<br>3. 实现自定义 ModelWrapper |
| 性能下降 | 高 | 中 | 1. 性能基准测试<br>2. 优化消息传递<br>3. 使用异步处理 |
| Human-in-the-loop 机制无法实现 | 高 | 低 | 1. 使用 WorkflowState 持久化<br>2. 自定义中断恢复逻辑<br>3. 参考 AgentScope Pipeline |
| 流式输出不兼容 | 中 | 低 | 1. 适配层转换<br>2. 保持 SSE 格式不变<br>3. 测试前端兼容性 |
| 迁移时间超预期 | 中 | 中 | 1. 分阶段迁移<br>2. 每个阶段设置检查点<br>3. 必要时调整范围 |
| 现有功能回归 | 高 | 中 | 1. 完善的测试覆盖<br>2. 保留旧代码作为参考<br>3. 灰度发布 |

## 9. 回滚策略

### 9.1 快速回滚

```bash
# 如果迁移失败，可以快速回滚到 main 分支
git checkout main
git branch -D feature/agentscope-migration
```

### 9.2 渐进式回滚

在适配层保留开关，可以动态切换：

```java
@Configuration
public class WorkflowConfig {
    @Value("${dataagent.use-agentscope:false}")
    private boolean useAgentScope;

    @Bean
    public GraphService graphService() {
        if (useAgentScope) {
            return new GraphServiceAdapter(agentWorkflow);
        } else {
            return new GraphServiceImpl(stateGraph);  // 旧实现
        }
    }
}
```

## 10. 验收标准

### 10.1 功能完整性
- ✓ 所有现有功能正常工作
- ✓ 所有测试用例通过
- ✓ 前端无需任何改动

### 10.2 性能指标
- ✓ 响应时间不超过原系统 120%
- ✓ 并发处理能力不低于原系统
- ✓ 内存占用合理

### 10.3 代码质量
- ✓ 测试覆盖率 > 80%
- ✓ 通过 Checkstyle 和 Spotless 检查
- ✓ 无严重代码异味

### 10.4 文档完整
- ✓ 更新 CLAUDE.md
- ✓ 更新 ARCHITECTURE.md
- ✓ 添加 AgentScope 使用指南

## 11. 参考资料

- [AgentScope Java 官方文档](https://java.agentscope.io/zh/intro.html)
- [Spring AI Alibaba 文档](https://springdoc.cn/spring-ai/)
- [DataAgent 架构文档](../../../docs/ARCHITECTURE.md)
- [DataAgent 开发者指南](../../../docs/DEVELOPER_GUIDE.md)
