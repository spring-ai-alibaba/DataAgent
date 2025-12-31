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

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `spring.ai.alibaba.data-agent.llm-service-type` | LLM服务类型 (STREAM/BLOCK) | STREAM |
| `spring.ai.alibaba.data-agent.max-sql-retry-count` | SQL执行失败重试次数 | 10 |
| `spring.ai.alibaba.data-agent.max-sql-optimize-count` | SQL优化最多次数 | 10 |
| `spring.ai.alibaba.data-agent.sql-score-threshold` | SQL优化分数阈值 | 0.95 |
| `spring.ai.alibaba.data-agent.maxturnhistory` | 最多保留的对话轮数 | 5 |
| `spring.ai.alibaba.data-agent.maxplanlength` | 单次规划最大长度限制 | 2000 |
| `spring.ai.alibaba.data-agent.max-columns-per-table` | 每张表的最大预估列数 | 50 |
| `spring.ai.alibaba.data-agent.fusion-strategy` | 多路召回结果融合策略 | rrf |

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

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `chunk-size` | 默认分块大小 (基于token) | 1000 |
| `min-chunk-size-chars` | 最小分块字符数 | 400 |
| `min-chunk-length-to-embed` | 嵌入最小分块长度 | 10 |
| `max-num-chunks` | 最大分块数量 | 5000 |
| `keep-separator` | 是否保留分隔符 | true |
| `separators` | 自定义分隔符列表 | null (使用默认) |


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
