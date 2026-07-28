中文 | [English](./DEVELOPER_GUIDE-en.md)

# 开发者文档

欢迎参与 DataAgent 项目的开发！本文档将帮助您了解如何为项目做出贡献。

## 🚀 开发环境搭建

### 前置要求

- **JDK**: 17 或更高版本
- **Maven**: 3.6 或更高版本
- **Node.js**: 16 或更高版本
- **MySQL**: 5.7 或更高版本
- **Git**: 版本控制工具
- **IDE**: IntelliJ IDEA 或 Eclipse (推荐 IntelliJ IDEA)

### 克隆项目

```bash
git clone https://github.com/your-org/spring-ai-alibaba-data-agent.git
cd spring-ai-alibaba-data-agent
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
   cd data-agent-management
   ./mvnw spring-boot:run
   ```

### 前端开发环境

1. **安装依赖**
   ```bash
   cd data-agent-frontend
   npm install
   ```

2. **启动开发服务器**
   ```bash
   npm run dev
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
| `default-similarity-threshold` | 全局默认相似度阈值 | 0.4 |
| `table-similarity-threshold` | 召回表的相似度阈值 | 0.2 |
| `batch-del-topk-limit` | 批量删除时的最大文档数量 | 5000 |
| `default-topk-limit` | 全局默认查询返回的最大文档数量（目前只有业务知识和智能体知识在使用） | 8 |
| `table-topk-limit` | 召回表的最大文档数量 | 10 |
| `enable-hybrid-search` | 是否启用混合搜索 | false |
| `elasticsearch-min-score` | ES关键词搜索的最小分数阈值 | 0.5 |

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

2. **配置 `spring.ai.vectorstore.type`**。具体填写的值可以在引入上面的向量库starter后自行搜索 `VectorStoreAutoConfiguration`自动配置类，比如`es`的是`ElasticsearchVectorStoreAutoConfiguration`，该类里面可以看见`spring.ai.vectorstore.type`期望的是`elasticsearch`。


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
| `code-pool-executor` | 执行器类型 (DOCKER/LOCAL) | DOCKER (application.yml中默认为local) |
| `image-name` | Docker镜像名称 | continuumio/anaconda3:latest |
| `container-name-prefix` | 容器名称前缀 | nl2sql-python-exec- |
| `host` | 服务主机地址 | null |
| `task-queue-size` | 任务阻塞队列大小 | 5 |
| `core-container-num` | 核心容器数量最大值 | 2 |
| `temp-container-num` | 临时容器数量最大值 | 2 |
| `core-thread-size` | 线程池核心线程数 | 5 |
| `max-thread-size` | 线程池最大线程数 | 5 |
| `code-timeout` | Python代码执行超时时间 | 60s |
| `container-timeout` | 容器最大运行时长 | 3000 (ms) |
| `limit-memory` | 容器内存限制 (MB) | 500 |
| `cpu-core` | 容器CPU核数 | 1 |

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
| `mode` | 初始化模式 (always/never) | always | "always"会每次启动执行schema.sql和data.sql，建议生产环境设为"never" |
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

### 12. 敏感数据加密配置 (Data Encryption)

配置前缀: `spring.ai.alibaba.data-agent`

系统支持对敏感信息（数据源密码、API密钥等）进行 AES-256-GCM 加密存储。

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `encrypt-key` | AES-256 加密密钥（Base64编码的32字节密钥） | 空（不加密） |

对应环境变量: `DATA_AGENT_ENCRYPT_KEY`

#### 受保护字段

| 数据库表 | 字段 | 说明 |
|----------|------|------|
| `datasource` | `password` | 数据库连接密码 |
| `datasource` | `connection_url` | 数据库连接URL（可能包含密码） |
| `model_config` | `api_key` | LLM API密钥 |
| `model_config` | `proxy_password` | 代理服务器密码 |

#### 使用方式

1. **生成加密密钥**
   ```bash
   # 方式一：使用系统内置工具生成（需要 Java 17+）
   java -cp data-agent-management/target/spring-ai-alibaba-data-agent-management-${revision}.jar.original com.alibaba.cloud.ai.dataagent.util.CryptoUtil

   # 方式二：使用 OpenSSL 生成（推荐）
   openssl rand -base64 32
   ```

   > 其中 `${revision}` 为项目版本号，当前为 `1.0.0-SNAPSHOT`，详见 `pom.xml` 中的 `<revision>` 属性。

2. **配置环境变量**
   ```bash
   # Linux/Mac
   export DATA_AGENT_ENCRYPT_KEY=<生成的Base64密钥>
   
   # Windows PowerShell
   $env:DATA_AGENT_ENCRYPT_KEY="<生成的Base64密钥>"
   ```

3. **Docker 部署**
   ```yaml
   # docker-compose.yml
   services:
     data-agent:
       environment:
         - DATA_AGENT_ENCRYPT_KEY=<生成的Base64密钥>
   ```

#### 历史数据兼容性

系统采用**透明兼容**策略，支持历史未加密数据与新加密数据共存：

| 场景 | 行为 |
|------|------|
| 读取已加密数据 | 正常解密 |
| 读取未加密数据（历史） | 原样返回，不解密 |
| 写入新数据 | 自动加密（需配置密钥） |
| 未配置密钥 | 所有数据保持明文（向后兼容） |

> 💡 **无需数据迁移**：配置加密密钥后，系统会自动处理新数据加密，历史数据保持原样可正常使用。随着数据更新，历史数据会逐步被加密覆盖。

#### 注意事项

- ⚠️ **生产环境必须配置加密密钥**，否则敏感信息将以明文存储
- 🔑 **密钥一旦配置不要轻易更换**，否则已加密的数据将无法解密
- 🔄 **密钥丢失后无法恢复数据**，请妥善备份密钥
- 📝 **开发环境**（H2）使用内置测试密钥，无需额外配置
- 🛡️ 加密算法: AES-256-GCM（带完整性校验），每次加密生成随机IV
- 🔄 **平滑升级**：历史未加密数据可正常读取，无需停机迁移

## 📚 学习资源

### 官方文档

- [Spring AI Alibaba 文档](https://springdoc.cn/spring-ai/)
- [Spring Boot 文档](https://spring.io/projects/spring-boot)
- [React 文档](https://react.dev/)
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
