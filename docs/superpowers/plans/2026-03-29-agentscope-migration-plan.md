# AgentScope Java 迁移实现计划

**创建日期：** 2026-03-29
**设计文档：** [2026-03-27-agentscope-migration-design.md](../specs/2026-03-27-agentscope-migration-design.md)
**预计工期：** 16-25 天
**状态：** 待执行

## 概述

本计划基于设计文档，将 DataAgent 从 Spring AI Alibaba Graph 完全迁移到 AgentScope Java 框架。采用分层适配器模式，保持 API 完全兼容，保留所有核心特性。

## 前置条件

- [ ] 已阅读并理解设计文档
- [ ] 已安装 JDK 17+
- [ ] 已安装 Maven 3.6+
- [ ] 本地 MySQL 数据库可用
- [ ] 已了解 AgentScope Java 基本概念
- [ ] 已备份当前代码（main 分支）

## 阶段 1：准备阶段（1-2 天）

### 任务 1.1：创建新分支
```bash
git checkout -b feature/agentscope-migration
git push -u origin feature/agentscope-migration
```

### 任务 1.2：添加 AgentScope Java 依赖

编辑 `pom.xml`，在 `<dependencies>` 中添加：

```xml
<!-- AgentScope Java -->
<dependency>
    <groupId>com.agentscope</groupId>
    <artifactId>agentscope-core</artifactId>
    <version>1.0.9</version>
</dependency>
```

**注意：** 暂时保留 Spring AI Alibaba 依赖，共存期间两套系统并行。

### 任务 1.3：创建基础包结构

创建以下目录结构：

```
data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/agentscope/
├── agent/
├── workflow/
├── adapter/
├── message/
└── service/
```

执行命令：
```bash
cd data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent
mkdir -p agentscope/{agent,workflow,adapter,message,service}
```

### 任务 1.4：验证环境

```bash
# 编译验证
./mvnw clean compile

# 运行现有测试确保基线正常
./mvnw test
```

**检查点 1：**
- ✓ 新分支已创建
- ✓ AgentScope 依赖已添加
- ✓ 包结构已创建
- ✓ 现有测试全部通过

---

## 阶段 2：核心框架搭建（3-5 天）

### 任务 2.1：实现消息定义（0.5 天）

**文件：** `agentscope/message/MessageType.java`
```java
package com.alibaba.cloud.ai.dataagent.agentscope.message;

public enum MessageType {
    TEXT,
    JSON,
    SQL,
    PYTHON,
    HTML,
    MARKDOWN,
    ERROR,
    SUCCESS,
    HUMAN_FEEDBACK_REQUIRED
}
```

**文件：** `agentscope/message/AgentMessage.java`
```java
package com.alibaba.cloud.ai.dataagent.agentscope.message;

import com.agentscope.message.Msg;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentMessage extends Msg {
    private String messageId;
    private String threadId;
    private String fromAgent;
    private String toAgent;
    private MessageType type;
    private Map<String, Object> metadata;
    private long timestamp;

    @Builder
    public AgentMessage(String name, String content, String url,
                       MessageType type, Map<String, Object> metadata) {
        super(name, content, url);
        this.messageId = UUID.randomUUID().toString();
        this.type = type != null ? type : MessageType.TEXT;
        this.metadata = metadata;
        this.timestamp = System.currentTimeMillis();
    }

    public static AgentMessage success(Object content) {
        return AgentMessage.builder()
            .content(content.toString())
            .type(MessageType.SUCCESS)
            .build();
    }

    public static AgentMessage error(String message) {
        return AgentMessage.builder()
            .content(message)
            .type(MessageType.ERROR)
            .build();
    }
}
```

### 任务 2.2：实现 MessageBus（0.5 天）

**文件：** `agentscope/message/MessageBus.java`
```java
package com.alibaba.cloud.ai.dataagent.agentscope.message;

import com.alibaba.cloud.ai.dataagent.agentscope.service.AgentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageBus {
    private final AgentRegistry agentRegistry;

    public AgentMessage send(String toAgent, AgentMessage message) {
        log.debug("Sending message to agent: {}", toAgent);
        var agent = agentRegistry.getAgent(toAgent);
        message.setToAgent(toAgent);
        return (AgentMessage) agent.reply(message);
    }

    public Mono<AgentMessage> sendAsync(String toAgent, AgentMessage message) {
        return Mono.fromCallable(() -> send(toAgent, message));
    }

    public void broadcast(AgentMessage message) {
        agentRegistry.getAllAgents().forEach(agent -> {
            try {
                agent.reply(message);
            } catch (Exception e) {
                log.error("Failed to broadcast to agent: {}", agent.getName(), e);
            }
        });
    }

    public Flux<AgentMessage> stream(String toAgent, AgentMessage message) {
        return Flux.create(sink -> {
            try {
                AgentMessage response = send(toAgent, message);
                sink.next(response);
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }
}
```

### 任务 2.3：实现 WorkflowState（1 天）

**文件：** `agentscope/workflow/WorkflowStatus.java`
```java
package com.alibaba.cloud.ai.dataagent.agentscope.workflow;

public enum WorkflowStatus {
    RUNNING,
    INTERRUPTED,
    COMPLETED,
    FAILED
}
```

**文件：** `agentscope/workflow/WorkflowState.java`
```java
package com.alibaba.cloud.ai.dataagent.agentscope.workflow;

import com.agentscope.message.Msg;
import lombok.Data;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class WorkflowState implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Map<String, WorkflowState> STATE_STORE = new ConcurrentHashMap<>();

    private String threadId;
    private String agentId;
    private String currentNode;
    private Map<String, Object> globalState;
    private List<Msg> messageHistory;
    private Map<String, Object> agentMemory;
    private WorkflowStatus status;
    private long createdAt;
    private long updatedAt;

    public WorkflowState(String threadId, String agentId) {
        this.threadId = threadId;
        this.agentId = agentId;
        this.globalState = new HashMap<>();
        this.messageHistory = new ArrayList<>();
        this.agentMemory = new HashMap<>();
        this.status = WorkflowStatus.RUNNING;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public void save() {
        this.updatedAt = System.currentTimeMillis();
        STATE_STORE.put(threadId, this);
    }

    public static WorkflowState load(String threadId) {
        return STATE_STORE.get(threadId);
    }

    public static void remove(String threadId) {
        STATE_STORE.remove(threadId);
    }

    public void addMessage(Msg message) {
        this.messageHistory.add(message);
    }

    public void updateGlobalState(String key, Object value) {
        this.globalState.put(key, value);
    }

    public Object getGlobalState(String key) {
        return this.globalState.get(key);
    }
}
```

**测试文件：** `agentscope/workflow/WorkflowStateTest.java`
```java
package com.alibaba.cloud.ai.dataagent.agentscope.workflow;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkflowStateTest {
    @Test
    void testSaveAndLoad() {
        String threadId = "test-thread-1";
        WorkflowState state = new WorkflowState(threadId, "agent-1");
        state.setCurrentNode("IntentAgent");
        state.updateGlobalState("query", "test query");
        state.save();

        WorkflowState loaded = WorkflowState.load(threadId);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getThreadId()).isEqualTo(threadId);
        assertThat(loaded.getCurrentNode()).isEqualTo("IntentAgent");
        assertThat(loaded.getGlobalState("query")).isEqualTo("test query");
    }
}
```

**检查点 2.1：**
- ✓ MessageType、AgentMessage 已实现
- ✓ MessageBus 已实现
- ✓ WorkflowState 已实现并测试通过

### 任务 2.4：实现 AgentRegistry（0.5 天）

**文件：** `agentscope/service/AgentRegistry.java`

```java
package com.alibaba.cloud.ai.dataagent.agentscope.service;

import com.agentscope.agent.AgentBase;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentRegistry {
    private final Map<String, AgentBase> agents = new ConcurrentHashMap<>();

    public void register(String name, AgentBase agent) {
        agents.put(name, agent);
    }

    public AgentBase getAgent(String name) {
        return agents.get(name);
    }

    public Collection<AgentBase> getAllAgents() {
        return agents.values();
    }
}
```

### 任务 2.5：实现 BaseDataAgent（1 天）

**文件：** `agentscope/agent/BaseDataAgent.java`

核心实现要点：
- 继承 AgentScope 的 AgentBase
- 集成 AgentModelService 和 AgentContextManager
- 实现错误处理和日志记录
- 支持多轮对话上下文

### 任务 2.6：实现 AgentModelService（1 天）

**文件：** `agentscope/service/AgentModelService.java`

核心实现要点：
- 包装 AgentScope 的 ModelWrapperBase
- 支持同步和流式调用
- 支持模型热切换
- 集成现有的 ModelConfig

**检查点 2：**
- ✓ 核心框架类已实现
- ✓ 单元测试通过
- ✓ 可以创建和注册 Agent

---

## 阶段 3：Agent 实现（5-7 天）

### 任务 3.1：IntentAgent（1 天）
- 迁移 IntentRecognitionNode 逻辑
- 测试意图识别准确性

### 任务 3.2：EvidenceAgent（1 天）
- 迁移 EvidenceRecallNode 逻辑
- 集成 VectorStoreService
- 测试 RAG 检索

### 任务 3.3：PlannerAgent（1-2 天）
- 迁移 PlannerNode 逻辑
- 保持计划 JSON 格式
- 测试计划生成

### 任务 3.4：SqlAgent（1-2 天）
- 迁移 SQL 生成、验证、执行逻辑
- 保持重试机制
- 测试 SQL 执行

### 任务 3.5：PythonAgent（1 天）
- 迁移 Python 生成和执行逻辑
- 集成 CodeExecutorService
- 测试 Python 执行

### 任务 3.6：ReportAgent（1 天）
- 迁移 ReportGeneratorNode 逻辑
- 保持 HTML/Markdown 格式
- 测试报告生成

### 任务 3.7：HumanFeedbackAgent（1 天）
- 实现中断和恢复机制
- 测试人工反馈流程

**检查点 3：**
- ✓ 7 个 Agent 全部实现
- ✓ 每个 Agent 的单元测试通过
- ✓ Agent 间消息传递正常

---

## 阶段 4：适配层实现（2-3 天）

### 任务 4.1：GraphServiceAdapter（1 天）
- 实现 GraphService 接口
- 适配 graphStreamProcess 方法
- 保持 API 签名不变

### 任务 4.2：StreamingAdapter（0.5 天）
- 转换 WorkflowEvent → GraphNodeResponse
- 保持 TextType 标记

### 任务 4.3：StateConverter（0.5 天）
- 双向转换状态对象
- 测试状态转换准确性

### 任务 4.4：AgentContextManager（1 天）
- 适配 MultiTurnContextManager
- 测试多轮对话

**检查点 4：**
- ✓ 适配层已实现
- ✓ API 兼容性测试通过
- ✓ 前端可以正常调用

---

## 阶段 5：集成测试（2-3 天）

### 任务 5.1：端到端测试
- 测试完整的 NL2SQL 流程
- 测试 Python 分析流程
- 测试报告生成

### 任务 5.2：性能测试
- 对比迁移前后的响应时间
- 测试并发处理能力
- 测试内存占用

### 任务 5.3：兼容性测试
- 验证前端无需改动
- 验证 API 响应格式一致
- 验证流式输出正常

**检查点 5：**
- ✓ 所有集成测试通过
- ✓ 性能指标达标
- ✓ 兼容性验证通过

---

## 阶段 6：特性验证（2-3 天）

验证所有关键特性：
- [ ] Human-in-the-loop 中断和恢复
- [ ] 多轮对话上下文管理
- [ ] RAG 检索增强
- [ ] Python 代码执行
- [ ] SSE 流式输出
- [ ] 多模型热切换
- [ ] MCP 服务集成
- [ ] Langfuse 可观测性

**检查点 6：**
- ✓ 所有特性验证通过
- ✓ 功能完整性达标

---

## 阶段 7：清理和优化（1-2 天）

### 任务 7.1：移除旧依赖
```xml
<!-- 从 pom.xml 移除 -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter</artifactId>
</dependency>
```

### 任务 7.2：删除旧代码
```bash
# 删除旧的 workflow 和 node 代码
rm -rf data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/workflow/node
rm -rf data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher
```

### 任务 7.3：更新文档
- 更新 CLAUDE.md
- 更新 ARCHITECTURE.md
- 添加 AgentScope 使用指南

### 任务 7.4：代码审查
- 运行 Checkstyle
- 运行 Spotless
- 代码审查和优化

**检查点 7：**
- ✓ 旧代码已清理
- ✓ 文档已更新
- ✓ 代码质量达标

---

## 验收标准

### 功能完整性
- [ ] 所有现有功能正常工作
- [ ] 所有测试用例通过（覆盖率 > 80%）
- [ ] 前端无需任何改动

### 性能指标
- [ ] 响应时间 ≤ 原系统 120%
- [ ] 并发处理能力 ≥ 原系统
- [ ] 内存占用合理

### 代码质量
- [ ] 通过 Checkstyle 检查
- [ ] 通过 Spotless 格式化
- [ ] 无严重代码异味

---

## 风险管理

| 风险 | 缓解措施 |
|------|----------|
| AgentScope 模型层不兼容 | 提前验证，保留 Spring AI 作为备选 |
| 性能下降 | 性能基准测试，优化消息传递 |
| Human-in-the-loop 无法实现 | 使用 WorkflowState 持久化 |
| 迁移时间超预期 | 分阶段迁移，设置检查点 |

---

## 回滚策略

**快速回滚：**
```bash
git checkout main
git branch -D feature/agentscope-migration
```

**渐进式回滚：**
在配置中保留开关 `dataagent.use-agentscope=false`

---

## 参考资料

- [设计文档](../specs/2026-03-27-agentscope-migration-design.md)
- [AgentScope Java 官方文档](https://java.agentscope.io/zh/intro.html)
- [DataAgent 架构文档](../../ARCHITECTURE.md)
