中文 | [English](./DEVELOPER_GUIDE-en.md)

# 开发者文档

欢迎参与 DataAgent 项目的开发！本文档将帮助您了解如何为项目做出贡献。

## 🚀 开发环境搭建

### 前置要求

- **JDK**: 17 或更高版本
- **Maven**: 3.6 或更高版本
- **Node.js**: 22
- **pnpm**: 11
- **MySQL**: 5.7 或更高版本
- **Docker**: 仅运行或验证 Python 工作流时需要
- **Git**: 版本控制工具
- **IDE**: IntelliJ IDEA 或 Eclipse (推荐 IntelliJ IDEA)

### 克隆项目

```bash
git clone https://github.com/spring-ai-alibaba/DataAgent.git
cd DataAgent
```

### 后端开发环境

1. **导入项目到 IDE**
   - 使用 IntelliJ IDEA 打开项目根目录
   - IDE 会自动识别为 Maven 项目并下载依赖

2. **配置数据库**
   - 创建 MySQL 数据库
   - 修改 `data-agent-management/src/main/resources/application.yml` 中的数据库配置

3. **启动后端服务**
   ```bash
   ./mvnw -pl data-agent-management spring-boot:run
   ```

### 前端开发环境

1. **安装依赖**
   ```bash
   cd data-agent-frontend-nuxt
   pnpm install
   ```

2. **启动开发服务器**
   ```bash
   pnpm dev
   ```

3. **访问应用**
   - 打开浏览器访问 http://localhost:3000



## 🔧 核心模块说明

### 1. StateGraph 工作流引擎

工作流基于 Spring AI Alibaba 的 StateGraph 实现，核心节点包括：

- **IntentRecognitionNode**: 意图识别
- **EvidenceRecallNode**: 证据召回
- **PlannerNode**: 计划生成
- **SqlGenerateNode**: SQL 生成
- **PythonGenerateNode**: Python 代码生成
- **PythonExecuteNode**: 解析 PEP 723 元数据并调度 SAA 沙盒执行
- **PythonAnalyzeNode**: 分析 Python 结果并更新步骤状态
- **ReportGeneratorNode**: 报告生成

### 2. 多模型调度

通过 `AiModelRegistry` 实现多模型管理和热切换：

```java
@Service
public class AiModelRegistry {
    private ChatModel currentChatModel;
    private EmbeddingModel currentEmbeddingModel;
    
    public void refreshChatModel(ModelConfig config) {
        // 动态创建和切换 Chat 模型
    }
    
    public void refreshEmbeddingModel(ModelConfig config) {
        // 动态创建和切换 Embedding 模型
    }
}
```

### 3. 向量检索服务

`AgentVectorStoreService` 提供统一的向量检索接口：

```java
@Service
public class AgentVectorStoreService {
    public List<Document> retrieve(String query, 
                                   String agentId, 
                                   VectorType vectorType) {
        // 向量检索逻辑
    }
}
```

## 🎨 编码规范

### Java 编码规范

1. **命名规范**
   - 类名：大驼峰命名法 (PascalCase)
   - 方法名：小驼峰命名法 (camelCase)
   - 常量：全大写下划线分隔 (UPPER_SNAKE_CASE)

2. **注释规范**
   - 所有公共类和方法必须有 JavaDoc 注释
   - 复杂逻辑需要添加行内注释

3. **代码格式**
   - 使用 4 个空格缩进
   - 每行代码不超过 120 字符
   - 使用 Google Java Style Guide

### TypeScript 编码规范

1. **命名规范**
   - 组件名：大驼峰命名法
   - 变量/函数：小驼峰命名法
   - 接口：I 前缀 + 大驼峰命名法

2. **类型定义**
   - 优先使用 interface 而非 type
   - 避免使用 any 类型
   - 为所有函数参数和返回值添加类型

3. **代码格式**
   - 使用 2 个空格缩进
   - 使用 Prettier 格式化代码
   - 使用 ESLint 检查代码质量

## ⚙️ 开发配置手册

本项目的所有配置项均位于 `spring.ai.alibaba.data-agent` 前缀下。

### 1. 通用配置

| 配置项                                                    | 说明 | 默认值    |
|--------------------------------------------------------|------|--------|
| `spring.ai.alibaba.data-agent.llm-service-type`        | LLM服务类型 (STREAM/BLOCK) | STREAM |
| `spring.ai.alibaba.data-agent.max-sql-retry-count`     | SQL执行失败重试次数 | 10     |
| `spring.ai.alibaba.data-agent.max-sql-optimize-count`  | SQL优化最多次数 | 10     |
| `spring.ai.alibaba.data-agent.sql-score-threshold`     | SQL优化分数阈值 | 0.95   |
| `spring.ai.alibaba.data-agent.max-columns-per-table`   | 每张表的最大预估列数 | 50     |
| `spring.ai.alibaba.data-agent.fusion-strategy`         | 多路召回结果融合策略 | rrf    |
| `spring.ai.alibaba.data-agent.enable-sql-result-chart` | 是否启用SQL执行结果图表判断 | true   |
| `spring.ai.alibaba.data-agent.enrich-sql-result-timeout` | 执行SQL结果图表化超时时间，单位毫秒 | 3000   |

### 2. 对话记忆

配置前缀：`spring.ai.alibaba.data-agent.memory`

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `recent-turns` | Spring AI `ChatMemory` 保留的最近成功对话轮次数 | 3 |
| `max-summary-length` | 可重建滚动摘要的最大字符数 | 4000 |
| `summary-cache-max-entries` | 单节点保留的滚动摘要投影上限；超限淘汰最旧项 | 10000 |
| `max-result-summary-length` | 单轮结果摘要最大字符数 | 2000 |
| `max-context-length` | 单次请求注入的全部记忆上下文字符硬上限 | 16000 |
| `long-term-top-k` | 每次注入的已确认长期记忆上限 | 5 |
| `episodic-top-k` | 可信用户跨会话召回上限 | 3 |
| `user-scope-enabled` | 个人记忆预留开关；当前版本缺少可信用户身份，设为 `true` 会拒绝启动 | false |
| `vector-index-enabled` | 是否启用可选语义索引；MySQL 始终是事实源 | false |
| `vector-similarity-threshold` | 记忆向量召回阈值 | 0.6 |
| `outbox-batch-size` | 单批投影事件数量 | 20 |
| `outbox-max-attempts` | 可重建投影进入死信前的最大尝试次数；删除、遗忘和 checkpoint 释放持续退避重试 | 5 |
| `outbox-initial-delay-ms` | 应用启动后首次轮询 Outbox 的延迟（毫秒） | 10000 |
| `outbox-poll-delay-ms` | 上一轮投影完成到下一轮轮询的固定延迟（毫秒） | 2000 |
| `outbox-completed-retention-days` | 已成功投影事件的保留天数；失败和死信不会自动删除 | 7 |
| `outbox-cleanup-batch-size` | 单次清理的已完成事件上限 | 1000 |
| `outbox-cleanup-initial-delay-ms` | 应用启动后首次清理已完成事件的延迟（毫秒） | 60000 |
| `outbox-cleanup-delay-ms` | 上一轮清理完成到下一轮清理的固定延迟（毫秒） | 3600000 |

Graph 检查点配置前缀：`spring.ai.alibaba.data-agent.checkpoint`

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `type` | 框架 CheckpointSaver 类型：`mysql` 或 `memory` | mysql |

`MysqlSaver.release()` 逻辑释放后的行会随对应的已完成 Outbox 事件一起保留，达到
`memory.outbox-completed-retention-days` 后先按逻辑 thread 名物理删除所有 checkpoint 代次（包括与逻辑释放竞争而重建的行），再删除 Outbox 事件；单批上限复用
`memory.outbox-cleanup-batch-size`。
已发布 graph-core `1.1.2.3` 使用 `(thread_name, is_released)` 唯一索引；若跨节点写入在首次释放后
重建 active 代次，下一次框架释放会与旧 released 代次冲突。Worker 只在框架释放失败且两种代次同时存在时
删除旧 released 代次，然后重试框架 `release()`；正常 checkpoint 读写、恢复与释放语义仍由框架负责。

删除、遗忘、记忆失效和 checkpoint 释放属于必须最终执行的事件，不受最大尝试次数限制。升级前若旧版
Worker 已将这四类事件标记为 `DEAD`，新版 Worker 会先自动恢复为 `FAILED` 再继续退避重试；可重建投影的
死信不会被自动恢复。

记忆能力按“框架负责通用存储语义，应用负责业务真实性”分层：

| 层次 | 实现 | 职责 |
|------|------|------|
| 最近消息窗口 | Spring AI `MessageWindowChatMemory` + `JdbcChatMemoryRepository` | 只保存已成功提交的用户/最终助手消息对；投影为空、损坏、不完整或未填满当前窗口时，只读回退到 `conversation_turn` 最近 N 轮 |
| 滚动摘要 | Spring AI Alibaba `Store` + `MemoryStore` | 有界节点本地缓存可从成功轮次重建的摘要投影；每次读取用关系库摘要边界校验，缓存丢失、过期或跨节点不一致时自动重建 |
| Graph 检查点 | Spring AI Alibaba graph-core `1.1.2.3` `MysqlSaver` | 保存和恢复 Graph 执行状态及人工审核中断点；`maxCachedThreads(0)` 关闭节点本地 latest cache，成功、失败、取消和会话删除提交后均由 Outbox 重试调用框架 `release`；框架逻辑释放后的行在对应 Outbox 事件保留期满后物理删除 |
| 业务事实与审核 | `conversation_turn`、`turn_run`、`turn_artifact`、`memory_item`、`memory_outbox` | 执行审计、长期记忆审核、事务 outbox；不重复实现框架 ChatMemory/Checkpoint |
| 语义索引 | Spring AI `VectorStore`（可选） | 加速长期记忆和历史轮次召回；可重建且不是事实源 |

Spring AI JDBC ChatMemory 表由 `spring.ai.chat.memory.repository.jdbc.initialize-schema=always`
交给框架初始化；Graph checkpoint 表由 `MysqlSaver` 的 `CreateOption.CREATE_IF_NOT_EXISTS`
创建。业务迁移脚本只管理 DataAgent 自有事实表和补充字段，不复制这两套框架表结构。

请求标识遵循固定生命周期：`conversationId` 在整个会话中保持稳定；新查询的 `threadId` 始终由服务端生成，
而 `turnId` 只在 `conversationId` 对应当前智能体的有效持久化会话时生成。需要持久化记忆或人工审核恢复时，
客户端应先创建会话并使用响应中的会话 ID；恢复同一次人工审核时，再把上一条 SSE 响应的 `threadId` 与
`turnId` 原样传回。

长期记忆 REST 接口：

| 方法 | 路径 | 语义 |
|------|------|------|
| `GET` | `/api/agents/{agentId}/memories?status={status}` | 列出记忆；`status` 可选：`CANDIDATE`、`CONFIRMED`、`SUPERSEDED`、`INVALIDATED` |
| `POST` | `/api/agents/{agentId}/memories` | 创建 `CANDIDATE`；必填 `scopeType`、`memoryKind`、`memoryKey`、JSON `value` |
| `POST` | `/api/agents/{agentId}/memories/{memoryId}/confirm` | 确认候选并进入可召回状态；同作用域同键冲突按替换语义处理 |
| `POST` | `/api/agents/{agentId}/memories/{memoryId}/invalidate` | 失效记忆并异步移除可选向量投影 |

上述四个接口都要求目标智能体已启用 API Key，并携带 `X-API-Key` 或 Bearer 凭证。
请求字段或 `supersedesId` 关系校验错误返回 `400`，错误凭证返回 `401`，跨智能体的记忆 ID 返回 `404`，
状态或并发冲突返回 `409`。内置页面使用的原生 `EventSource` 不能设置认证头；API Key 开启后的流查询应使用
支持 Header 的外部 SSE 客户端，不能把密钥放入 URL。

当前不使用已发布 graph-core `1.1.2.3` 的 `DatabaseStore`：该版本写入仍固定使用 H2
`MERGE ... KEY(...)`，与项目默认 MySQL 不兼容。待框架发布方言感知修复后，可替换 `MemoryStore`，
不需要改变摘要服务或关系库事实源。

已有 MySQL 环境升级时，需先执行版本化脚本
`data-agent-management/src/main/resources/sql/migration/V20260729_01__create_durable_memory.sql`，再执行
`data-agent-management/src/main/resources/sql/migration/V20260820_01__add_datasource_schema_revision.sql`；
全新环境继续使用 `data-agent-management/src/main/resources/sql/schema.sql`。必需的记忆表或
`datasource.schema_revision`、`datasource.schema_generation`、`memory_outbox.lease_token` 字段不存在时，
应用会在启动时列出缺失项并失败，不会降级为旧记忆实现。
部署前应先完成或取消旧版本中仍处于人工审核等待态的 Graph 执行：旧 checkpoint 没有对应的
`conversation_turn`/`turn_run` 业务事实，无法安全推导租户、会话和执行归属，因此升级后不会被自动接管。
升级后还需要为每个已有数据源重新执行一次 Schema 初始化，以生成稳定的 `schema_revision`。初始化开始时会先
使旧 revision 失效，只有同一 generation 的全部 Schema 向量发布成功后才写入新 revision；初始化失败期间，
依赖 Schema 的纠错和查询模式记忆会被安全地排除在召回结果之外。

真实 MySQL 8.4 迁移、约束、跨实例发布锁和 generation fencing 回归可按仓库的 Failsafe
`integration` Profile 运行：

```bash
./mvnw -pl data-agent-management -Pintegration \
  -Dspotless.apply.skip=true \
  -Dit.test=MysqlMemorySchemaPublicationIT \
  test-compile failsafe:integration-test failsafe:verify
```

该 `*IT` 是显式集成测试，不会被默认的 `make verify` 自动执行。

### 3. 嵌入模型批处理策略 (Embedding Batch)

配置前缀: `spring.ai.alibaba.data-agent.embedding-batch`

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `encoding-type` | 文本编码类型 (参考 com.knuddels.jtokkit.api.EncodingType) | cl100k_base |
| `max-token-count` | 每批次最大令牌数。建议值：2000-8000 | 8000 |
| `reserve-percentage` | 预留百分比 (用于缓冲空间) | 0.2 |
| `max-text-count` | 每批次最大文本数量 (DashScope限制为10) | 10 |

### 4. 向量库配置 (Vector Store)

配置前缀: `spring.ai.alibaba.data-agent.vector-store`

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `default-similarity-threshold` | 全局默认相似度阈值（用于业务知识、智能体知识等） | 0.4 |
| `table-similarity-threshold` | 召回表的相似度阈值（设置较低以尽量避免表召回遗漏） | 0.2 |
| `batch-del-topk-limit` | 批量删除时的最大文档数量 | 5000 |
| `default-topk-limit` | 全局默认查询返回的最大文档数量（目前只有业务知识和智能体知识在使用） | 8 |
| `table-topk-limit` | 召回表的最大文档数量 | 10 |
| `embedding-dimension` | 持久化向量库期望的向量维度校验值，需与嵌入模型输出维度一致；设为 `0` 时关闭校验（内存向量库默认即为 0） | 0 |
| `enable-hybrid-search` | 是否启用混合搜索（向量检索 + ES 关键词检索），仅在使用 Elasticsearch 时生效 | false |
| `hybrid-search-timeout-ms` | 混合检索中每个检索分支的最大等待时间（毫秒） | 3000 |
| `elasticsearch-min-score` | ES 关键词搜索的最小分数阈值，用于过滤相关性较低的文档 | 0.5 |
| `file-path` | `SimpleVectorStore` 本地序列化文件地址（仅内存向量库使用） | `./vectorstore/vectorstore.json` |

#### 向量库依赖扩展

项目默认使用内存向量库 (`SimpleVectorStore`)。若需使用持久化向量库（如 PGVector, Milvus 等），请按照以下步骤操作：

1. **引入依赖**: 在 `pom.xml` 中添加相应的 Spring AI Starter。
   
   ```xml
   <!-- 例如：引入 PGvector -->
   <dependency>
       <groupId>org.springframework.ai</groupId>
       <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
   </dependency>
   ```
   
2. **配置属性**: 在 `application.yml` 中添加对应向量库的连接配置。具体参数请参考 [Spring AI 官方文档](https://springdoc.cn/spring-ai/api/vectordbs.html)。

3. **配置 `spring.ai.vectorstore.type`**。具体填写的值可以在引入上面的向量库 starter 后自行搜索 `VectorStoreAutoConfiguration` 自动配置类，比如 `es` 的是 `ElasticsearchVectorStoreAutoConfiguration`，该类里面可以看见 `spring.ai.vectorstore.type` 期望的是 `elasticsearch`。

4. **配置 `embedding-dimension`**: 使用持久化向量库时，建议将 `spring.ai.alibaba.data-agent.vector-store.embedding-dimension` 设置为与嵌入模型输出维度一致的值（如 `1024`），以便启动时校验维度是否匹配，避免写入后检索异常。

#### 开箱即用的配置示例

项目已内置两个可直接激活的向量库示例 Profile，位于 `data-agent-management/src/main/resources/`。通过 `spring.profiles.active` 或环境变量 `SPRING_PROFILES_ACTIVE` 激活对应 Profile 即可，无需手动编写连接配置。

**Milvus (`application-milvus.yml`)**

```yaml
spring:
  ai:
    vectorstore:
      type: milvus
      milvus:
        client:
          host: ${MILVUS_HOST:127.0.0.1}
          port: ${MILVUS_PORT:19530}
        database-name: ${MILVUS_DATABASE:default}
        collection-name: ${MILVUS_COLLECTION:vector_store}
        embedding-dimension: ${MILVUS_DIMENSION:1024}
        initialize-schema: true
    alibaba:
      data-agent:
        vector-store:
          embedding-dimension: ${MILVUS_DIMENSION:1024}
```

`spring-ai-starter-vector-store-milvus` 依赖已包含在 `data-agent-management/pom.xml` 中，无需额外引入，激活 Profile 即可：

```bash
export SPRING_PROFILES_ACTIVE=milvus
# 可选：覆盖默认连接信息
export MILVUS_HOST=127.0.0.1
export MILVUS_PORT=19530
```

**Elasticsearch (`application-elasticsearch.yml`)**

```yaml
spring:
  elasticsearch:
    uris: ${ELASTICSEARCH_URIS:http://127.0.0.1:9200}
    username: ${ELASTICSEARCH_USERNAME:}
    password: ${ELASTICSEARCH_PASSWORD:}
  ai:
    vectorstore:
      type: elasticsearch
      elasticsearch:
        index-name: ${ELASTICSEARCH_INDEX_NAME:spring-ai-document-index}
        dimensions: ${ELASTICSEARCH_DIMENSIONS:1024}
        initialize-schema: ${ELASTICSEARCH_INITIALIZE_SCHEMA:true}
    alibaba:
      data-agent:
        vector-store:
          embedding-dimension: ${ELASTICSEARCH_DIMENSIONS:1024}
```

`spring-ai-starter-vector-store-elasticsearch` 依赖已包含在 `data-agent-management/pom.xml` 中，无需额外引入，激活 Profile 即可：

```bash
export SPRING_PROFILES_ACTIVE=elasticsearch
# 可选：覆盖默认连接信息
export ELASTICSEARCH_URIS=http://127.0.0.1:9200
```

> 提示：Elasticsearch 支持混合检索。激活 ES Profile 后，再将 `spring.ai.alibaba.data-agent.vector-store.enable-hybrid-search` 设为 `true` 即可启用向量检索与关键词检索的混合融合策略。

#### ES Schema 配置示例
以下为 Elasticsearch 的 Schema 结构。其他向量库（如 Milvus, PGVector）可参考此结构建立 Schema，尤其要注意 `metadata` 中的字段数据类型。

```json
{
  "mappings": {
    "properties": {
      "content": {
        "type": "text",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256
          }
        }
      },
      "embedding": {
        "type": "dense_vector",
        "dims": 1024,
        "index": true,
        "similarity": "cosine",
        "index_options": {
          "type": "int8_hnsw",
          "m": 16,
          "ef_construction": 100
        }
      },
      "id": {
        "type": "text",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256
          }
        }
      },
      "metadata": {
        "properties": {
          "agentId": {
            "type": "text",
            "fields": {
              "keyword": {
                "type": "keyword",
                "ignore_above": 256
              }
            }
          },
          "agentKnowledgeId": {
            "type": "long"
          },
          "businessTermId": {
            "type": "long"
          },
          "concreteAgentKnowledgeType": {
            "type": "text",
            "fields": {
              "keyword": {
                "type": "keyword",
                "ignore_above": 256
              }
            }
          },
          "vectorType": {
            "type": "text",
            "fields": {
              "keyword": {
                "type": "keyword",
                "ignore_above": 256
              }
            }
          }
        }
      }
    }
  }
}
```

### 5. 文本切分配置 (Text Splitter)

配置前缀: `spring.ai.alibaba.data-agent.text-splitter`

#### 5.1 全局配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `chunk-size` | 默认分块大小（基于token数量，所有策略共享） | 1000 |

#### 5.2 TokenTextSplitter 配置 (token)

配置前缀: `spring.ai.alibaba.data-agent.text-splitter.token`

基于 Token 数量的文本切分策略，适用于需要精确控制 token 数量的场景。

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `min-chunk-size-chars` | 最小分块字符数 | 400 |
| `min-chunk-length-to-embed` | 嵌入最小分块长度 | 10 |
| `max-num-chunks` | 最大分块数量 | 5000 |
| `keep-separator` | 是否保留分隔符 | true |

#### 5.3 RecursiveCharacterTextSplitter 配置 (recursive)

配置前缀: `spring.ai.alibaba.data-agent.text-splitter.recursive`

递归字符文本切分策略，按照字符顺序递归尝试不同的分隔符进行切分。

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `chunk-overlap` | 重叠区域字符数（相邻分块之间的重叠字符数） | 200 |
| `separators` | 自定义分隔符列表（数组格式，如果为 null 则使用默认分隔符列表） | null |

#### 5.4 SentenceTextSplitter 配置 (sentence)

配置前缀: `spring.ai.alibaba.data-agent.text-splitter.sentence`

基于句子的文本切分策略，按照句子边界进行切分，适合处理自然语言文本。

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `sentence-overlap` | 句子重叠数量（保留前一个分块的最后 N 个句子） | 1 |

#### 5.5 SemanticTextSplitter 配置 (semantic)

配置前缀: `spring.ai.alibaba.data-agent.text-splitter.semantic`

基于语义相似度的文本切分策略，通过 Embedding 模型计算语义相似度来决定切分点，能够保持语义完整性。

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `min-chunk-size` | 最小分块大小（字符数） | 200 |
| `max-chunk-size` | 最大分块大小（字符数） | 1000 |
| `similarity-threshold` | 语义相似度阈值（0-1之间，值越低越容易分块） | 0.5 |

#### 5.6 ParagraphTextSplitter 配置 (paragraph)

配置前缀: `spring.ai.alibaba.data-agent.text-splitter.paragraph`

基于段落的文本切分策略，按照段落边界进行切分。

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `paragraph-overlap-chars` | 段落重叠字符数（保留前一个分块的最后 N 个字符，而非段落数量） | 200 |


### 6. 代码执行器配置 (Code Executor)

配置前缀: `spring.ai.alibaba.data-agent.code-executor`

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `code-timeout` | Python代码执行超时时间 | 60s |
| `limit-memory` | 容器内存限制 (MB) | 500 |
| `cpu-core` | 容器CPU核数 | 1 |
| `python-max-tries-count` | Python执行最大重试次数 | 5 |
| `sandbox.docker-host` | Docker Engine 地址 | `unix:///var/run/docker.sock` |
| `sandbox.image-name` | SAA 基础镜像 | `agentscope-registry.ap-southeast-1.cr.aliyuncs.com/agentscope/runtime-sandbox-base:latest` |
| `sandbox.container-prefix` | 任务容器名称前缀 | `dataagent-sandbox-` |
| `sandbox.max-concurrency` | 最大并发沙盒数 | 4 |
| `sandbox.queue-capacity` | 有界等待队列大小 | 10 |
| `sandbox.max-code-bytes` | Python 源码 UTF-8 字节上限 | 262144（256 KiB） |
| `sandbox.max-input-bytes` | stdin JSON UTF-8 字节上限 | 10485760（10 MiB） |
| `sandbox.max-output-bytes` | stdout UTF-8 字节上限 | 1048576（1 MiB） |
| `sandbox.max-error-bytes` | stderr UTF-8 字节上限 | 262144（256 KiB） |
| `sandbox.max-metadata-bytes` | PEP 723 元数据 UTF-8 字节上限 | 8192（8 KiB） |
| `sandbox.max-dependencies` | 最大直接依赖数 | 20 |
| `sandbox.package-index-url` | 动态依赖包索引 | `https://pypi.org/simple` |
| `sandbox.dependency-install-timeout` | 依赖安装超时 | 3m |
| `sandbox.max-connections` | 容器 `nofile` 上限 | 4096 |

第三方依赖必须在生成脚本的 PEP 723 `dependencies` 中声明。系统不再提供宿主机 Local、
旧 Docker 容器池或 AI Simulation 执行器。

常用环境变量：

| 环境变量 | 对应配置 | 用途 |
|---|---|---|
| `DATAAGENT_SANDBOX_DOCKER_HOST` | `sandbox.docker-host` | 指向本机或远程 Docker Engine |
| `DATAAGENT_SANDBOX_IMAGE` | `sandbox.image-name` | 固定运行时镜像；生产环境应使用 digest |
| `DATAAGENT_PYPI_INDEX_URL` | `sandbox.package-index-url` | 指向企业私有 PyPI 代理 |

每次 Python 任务创建独立 `BaseSandbox`，在同一容器内先安装依赖、再执行代码，最后停止并
删除容器。服务端总等待时间为“依赖安装超时 + 代码执行超时 + 30 秒通信余量”。
`requires-python` 当前会被解析和保留，但不会切换或校验沙盒 Python 版本。

依赖格式、安全限制、运行验证和故障处理见
[高级功能 - Python 执行环境配置](ADVANCED_FEATURES.md#-python-执行环境配置)；实现边界见
[SAA 1.1.2.2 Python 沙盒接入方案](superpowers/specs/2026-07-28-saa-python-sandbox-integration-design.md)。

### 7. 文件存储配置 (File Storage)

配置前缀: `spring.ai.alibaba.data-agent.file`

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `type` | 存储类型 (LOCAL/OSS) | LOCAL |
| `path` | 本地上传目录路径 | ./uploads |
| `url-prefix` | 对外暴露的访问前缀 | /uploads |
| `image-size` | 图片大小上限 (字节) | 2097152 (2MB) |
| `path-prefix` | 对象存储路径前缀 | "" |

### 8. 阿里云 OSS 配置 (OSS Storage)

配置前缀: `spring.ai.alibaba.data-agent.file.oss`

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `access-key-id` | OSS 访问密钥 ID | - |
| `access-key-secret` | OSS 访问密钥 Secret | - |
| `endpoint` | OSS 端点地址 | - |
| `bucket-name` | OSS 存储桶名称 | - |
| `custom-domain` | 自定义域名 | - |


### 9. 数据库初始化配置 (Database Initialization)

配置前缀: `spring.sql.init`

| 配置项 | 说明 | 默认值 | 备注 |
|--------|------|--------|------|
| `mode` | 初始化模式 (always/never) | never | 仅在明确需要初始化时设置为 `always` |
| `schema-locations` | 表结构脚本路径 | classpath:sql/schema.sql | |
| `data-locations` | 数据脚本路径 | classpath:sql/data.sql | |

### 10. 模型依赖手动管理 (Manual Model Dependency)

如果您选择不使用 Spring AI Alibaba Starter 而是手动引入 OpenAI 或其他厂商的 Starter：
- 请确保移除默认的 Starter 依赖，避免冲突。
- 您可能需要手动配置 `ChatClient`, `ChatModel` 和 `EmbeddingModel` 的 Bean。

### 11. 报告资源配置 (Report Resources)

配置前缀: `spring.ai.alibaba.data-agent.report-template`

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `marked-url` | Marked.js 路径 (Markdown渲染库) | https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/marked/12.0.0/marked.min.js |
| `echarts-url` | ECharts 路径 (图表库) | https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/echarts/5.5.0/echarts.min.js |

### 12. Langfuse 可观测性配置 (Langfuse Observability)

配置前缀: `spring.ai.alibaba.data-agent.langfuse`

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `enabled` | 是否启用 Langfuse 可观测性 | true |
| `host` | Langfuse 服务地址（如 `https://cloud.langfuse.com` 或自部署地址） | - |
| `public-key` | Langfuse 项目的 Public Key | - |
| `secret-key` | Langfuse 项目的 Secret Key | - |

对应环境变量: `LANGFUSE_ENABLED`、`LANGFUSE_HOST`、`LANGFUSE_PUBLIC_KEY`、`LANGFUSE_SECRET_KEY`

> 详细使用说明请参考 [高级功能 - Langfuse 可观测性](ADVANCED_FEATURES.md#-langfuse-可观测性)。

## ✅ Python 沙盒验证

不需要 Docker 的单元测试：

```bash
./mvnw -pl data-agent-management \
  -Dtest='PythonDependencyMetadataParserTest,PythonSandboxBootstrapBuilderTest,SandboxExecutionResultParserTest,SaaSandboxPythonCodeExecutorServiceTest,SaaSandboxRuntimeTest,PythonExecuteNodeTest,PythonWorkflowIntegrationTest' \
  test
```

Docker 在线时运行真实 SAA 集成测试：

```bash
docker info
./mvnw -pl data-agent-management -Dtest=SaaSandboxTaskRunnerIT test
```

提交前执行与 CI 对齐的检查：

```bash
make format-check
make checkstyle-check
make test
```

真实端到端验收不能只看 HTTP 200：浏览器时间线应出现依赖安装和 Python 执行结果、最终报告，
SSE 应收到 `event:complete`，并且 `docker ps -a --filter name=dataagent-sandbox-` 不应留下
任务容器。

## 📚 学习资源

### 官方文档

- [Spring AI Alibaba 文档](https://springdoc.cn/spring-ai/)
- [Spring Boot 文档](https://spring.io/projects/spring-boot)
- [Nuxt 文档](https://nuxt.com/docs)
- [Vue 文档](https://vuejs.org/guide/)
- [TypeScript 文档](https://www.typescriptlang.org/)

### 相关技术

- StateGraph 工作流引擎
- MyBatis 数据访问框架
- Vector Store 向量数据库
- Server-Sent Events (SSE)

## 🤝 贡献指南

详细的贡献指南请参考 [CONTRIBUTING-zh.md](../CONTRIBUTING-zh.md)。

### 贡献类型

- 🐛 报告 Bug
- 💡 提出新功能建议
- 📝 改进文档
- 🔧 提交代码修复
- ✨ 开发新功能

### 行为准则

- 尊重所有贡献者
- 保持友好和专业
- 接受建设性批评
- 关注项目目标


---

感谢您对 DataAgent 项目的贡献！🎉
