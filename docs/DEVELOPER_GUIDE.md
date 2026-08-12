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
| `spring.ai.alibaba.data-agent.maxturnhistory`          | 最多保留的对话轮数 | 5      |
| `spring.ai.alibaba.data-agent.maxplanlength`           | 单次规划最大长度限制 | 2000   |
| `spring.ai.alibaba.data-agent.max-columns-per-table`   | 每张表的最大预估列数 | 50     |
| `spring.ai.alibaba.data-agent.fusion-strategy`         | 多路召回结果融合策略 | rrf    |
| `spring.ai.alibaba.data-agent.enable-sql-result-chart` | 是否启用SQL执行结果图表判断 | true   |
| `spring.ai.alibaba.data-agent.enrich-sql-result-timeout` | 执行SQL结果图表化超时时间，单位毫秒 | 3000   |

### 2. 嵌入模型批处理策略 (Embedding Batch)

配置前缀: `spring.ai.alibaba.data-agent.embedding-batch`

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `encoding-type` | 文本编码类型 (参考 com.knuddels.jtokkit.api.EncodingType) | cl100k_base |
| `max-token-count` | 每批次最大令牌数。建议值：2000-8000 | 8000 |
| `reserve-percentage` | 预留百分比 (用于缓冲空间) | 0.2 |
| `max-text-count` | 每批次最大文本数量 (DashScope限制为10) | 10 |

### 3. 向量库配置 (Vector Store)

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

### 4. 文本切分配置 (Text Splitter)

配置前缀: `spring.ai.alibaba.data-agent.text-splitter`

#### 4.1 全局配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `chunk-size` | 默认分块大小（基于token数量，所有策略共享） | 1000 |

#### 4.2 TokenTextSplitter 配置 (token)

配置前缀: `spring.ai.alibaba.data-agent.text-splitter.token`

基于 Token 数量的文本切分策略，适用于需要精确控制 token 数量的场景。

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `min-chunk-size-chars` | 最小分块字符数 | 400 |
| `min-chunk-length-to-embed` | 嵌入最小分块长度 | 10 |
| `max-num-chunks` | 最大分块数量 | 5000 |
| `keep-separator` | 是否保留分隔符 | true |

#### 4.3 RecursiveCharacterTextSplitter 配置 (recursive)

配置前缀: `spring.ai.alibaba.data-agent.text-splitter.recursive`

递归字符文本切分策略，按照字符顺序递归尝试不同的分隔符进行切分。

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `chunk-overlap` | 重叠区域字符数（相邻分块之间的重叠字符数） | 200 |
| `separators` | 自定义分隔符列表（数组格式，如果为 null 则使用默认分隔符列表） | null |

#### 4.4 SentenceTextSplitter 配置 (sentence)

配置前缀: `spring.ai.alibaba.data-agent.text-splitter.sentence`

基于句子的文本切分策略，按照句子边界进行切分，适合处理自然语言文本。

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `sentence-overlap` | 句子重叠数量（保留前一个分块的最后 N 个句子） | 1 |

#### 4.5 SemanticTextSplitter 配置 (semantic)

配置前缀: `spring.ai.alibaba.data-agent.text-splitter.semantic`

基于语义相似度的文本切分策略，通过 Embedding 模型计算语义相似度来决定切分点，能够保持语义完整性。

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `min-chunk-size` | 最小分块大小（字符数） | 200 |
| `max-chunk-size` | 最大分块大小（字符数） | 1000 |
| `similarity-threshold` | 语义相似度阈值（0-1之间，值越低越容易分块） | 0.5 |

#### 4.6 ParagraphTextSplitter 配置 (paragraph)

配置前缀: `spring.ai.alibaba.data-agent.text-splitter.paragraph`

基于段落的文本切分策略，按照段落边界进行切分。

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `paragraph-overlap-chars` | 段落重叠字符数（保留前一个分块的最后 N 个字符，而非段落数量） | 200 |


### 5. 代码执行器配置 (Code Executor)

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

### 6. 文件存储配置 (File Storage)

配置前缀: `spring.ai.alibaba.data-agent.file`

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `type` | 存储类型 (LOCAL/OSS) | LOCAL |
| `path` | 本地上传目录路径 | ./uploads |
| `url-prefix` | 对外暴露的访问前缀 | /uploads |
| `image-size` | 图片大小上限 (字节) | 2097152 (2MB) |
| `path-prefix` | 对象存储路径前缀 | "" |

### 7. 阿里云 OSS 配置 (OSS Storage)

配置前缀: `spring.ai.alibaba.data-agent.file.oss`

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `access-key-id` | OSS 访问密钥 ID | - |
| `access-key-secret` | OSS 访问密钥 Secret | - |
| `endpoint` | OSS 端点地址 | - |
| `bucket-name` | OSS 存储桶名称 | - |
| `custom-domain` | 自定义域名 | - |


### 8. 数据库初始化配置 (Database Initialization)

配置前缀: `spring.sql.init`

| 配置项 | 说明 | 默认值 | 备注 |
|--------|------|--------|------|
| `mode` | 初始化模式 (always/never) | never | 仅在明确需要初始化时设置为 `always` |
| `schema-locations` | 表结构脚本路径 | classpath:sql/schema.sql | |
| `data-locations` | 数据脚本路径 | classpath:sql/data.sql | |

### 9. 模型依赖手动管理 (Manual Model Dependency)

如果您选择不使用 Spring AI Alibaba Starter 而是手动引入 OpenAI 或其他厂商的 Starter：
- 请确保移除默认的 Starter 依赖，避免冲突。
- 您可能需要手动配置 `ChatClient`, `ChatModel` 和 `EmbeddingModel` 的 Bean。

### 10. 报告资源配置 (Report Resources)

配置前缀: `spring.ai.alibaba.data-agent.report-template`

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `marked-url` | Marked.js 路径 (Markdown渲染库) | https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/marked/12.0.0/marked.min.js |
| `echarts-url` | ECharts 路径 (图表库) | https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/echarts/5.5.0/echarts.min.js |

### 11. Langfuse 可观测性配置 (Langfuse Observability)

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
