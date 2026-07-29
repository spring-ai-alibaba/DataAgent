[中文](./DEVELOPER_GUIDE.md) | English

# Developer Guide

Welcome to participate in the development of the DataAgent project! This document will help you understand how to contribute to the project.

## Development Environment Setup

### Prerequisites

- **JDK**: 17 or higher
- **Maven**: 3.6 or higher
- **Node.js**: 22
- **pnpm**: 11
- **MySQL**: 5.7 or higher
- **Docker**: Required only when running or verifying Python workflows
- **Git**: Version control tool
- **IDE**: IntelliJ IDEA or Eclipse (IntelliJ IDEA recommended)

### Clone Project

```bash
git clone https://github.com/spring-ai-alibaba/DataAgent.git
cd DataAgent
```

### Backend Development Environment

1. **Import Project into IDE**
   - Open the project root directory with IntelliJ IDEA
   - IDE will automatically recognize it as a Maven project and download dependencies

2. **Configure Database**
   - Create a MySQL database
   - Modify the database configuration in `data-agent-management/src/main/resources/application.yml`

3. **Start Backend Service**
   ```bash
   ./mvnw -pl data-agent-management spring-boot:run
   ```

### Frontend Development Environment

1. **Install Dependencies**
   ```bash
   cd data-agent-frontend-nuxt
   pnpm install
   ```

2. **Start Development Server**
   ```bash
   pnpm dev
   ```

3. **Access Application**
   - Open browser and visit http://localhost:3000



## Core Module Description

### 1. StateGraph Workflow Engine

The workflow is based on Spring AI Alibaba's StateGraph implementation. Core nodes include:

- **IntentRecognitionNode**: Intent recognition
- **EvidenceRecallNode**: Evidence recall
- **PlannerNode**: Plan generation
- **SqlGenerateNode**: SQL generation
- **PythonGenerateNode**: Python code generation
- **PythonExecuteNode**: Parse PEP 723 metadata and dispatch SAA sandbox execution
- **PythonAnalyzeNode**: Analyze Python output and update step state
- **ReportGeneratorNode**: Report generation

### 2. Multi-Model Scheduling

Multi-model management and hot-swapping is implemented through `AiModelRegistry`:

```java
@Service
public class AiModelRegistry {
    private ChatModel currentChatModel;
    private EmbeddingModel currentEmbeddingModel;

    public void refreshChatModel(ModelConfig config) {
        // Dynamically create and switch Chat model
    }

    public void refreshEmbeddingModel(ModelConfig config) {
        // Dynamically create and switch Embedding model
    }
}
```

### 3. Vector Retrieval Service

`AgentVectorStoreService` provides a unified vector retrieval interface:

```java
@Service
public class AgentVectorStoreService {
    public List<Document> retrieve(String query,
                                   String agentId,
                                   VectorType vectorType) {
        // Vector retrieval logic
    }
}
```

## Coding Standards

### Java Coding Standards

1. **Naming Conventions**
   - Class names: PascalCase
   - Method names: camelCase
   - Constants: UPPER_SNAKE_CASE

2. **Comment Standards**
   - All public classes and methods must have JavaDoc comments
   - Complex logic requires inline comments

3. **Code Format**
   - Use 4 spaces for indentation
   - Each line of code should not exceed 120 characters
   - Use Google Java Style Guide

### TypeScript Coding Standards

1. **Naming Conventions**
   - Component names: PascalCase
   - Variables/functions: camelCase
   - Interfaces: I prefix + PascalCase

2. **Type Definitions**
   - Prefer interface over type
   - Avoid using any type
   - Add types for all function parameters and return values

3. **Code Format**
   - Use 2 spaces for indentation
   - Use Prettier for code formatting
   - Use ESLint for code quality checking

## Development Configuration Manual

All configuration items in this project are under the `spring.ai.alibaba.data-agent` prefix.

### 1. General Configuration

| Configuration Item | Description | Default Value |
|-------------------|-------------|---------------|
| `spring.ai.alibaba.data-agent.llm-service-type` | LLM service type (STREAM/BLOCK) | STREAM |
| `spring.ai.alibaba.data-agent.max-sql-retry-count` | SQL execution failure retry count | 10 |
| `spring.ai.alibaba.data-agent.max-sql-optimize-count` | Maximum SQL optimization attempts | 10 |
| `spring.ai.alibaba.data-agent.sql-score-threshold` | SQL optimization score threshold | 0.95 |
| `spring.ai.alibaba.data-agent.max-columns-per-table` | Maximum estimated columns per table | 50 |
| `spring.ai.alibaba.data-agent.fusion-strategy` | Multi-channel recall result fusion strategy | rrf |
| `spring.ai.alibaba.data-agent.enable-sql-result-chart` | Enable SQL result chart judgment | true |
| `spring.ai.alibaba.data-agent.enrich-sql-result-timeout` | SQL result chart generation timeout (ms) | 3000 |

### 2. Conversation Memory

Configuration prefix: `spring.ai.alibaba.data-agent.memory`

| Configuration Item | Description | Default Value |
|-------------------|-------------|---------------|
| `recent-turns` | Recent successful turns read verbatim from the relational source of truth | 3 |
| `max-summary-length` | Maximum length of the rebuildable rolling summary | 4000 |
| `max-result-summary-length` | Maximum result-summary length per turn | 2000 |
| `long-term-top-k` | Maximum confirmed long-term memories injected per request | 5 |
| `episodic-top-k` | Maximum cross-session turns recalled for a trusted owner | 3 |
| `user-scope-enabled` | Enable personal memory; identity is inherited from a verified source turn and cannot be supplied by the API | false |
| `vector-index-enabled` | Enable the optional semantic index; MySQL remains authoritative | false |
| `vector-similarity-threshold` | Memory vector recall threshold | 0.6 |
| `outbox-batch-size` | Projection events processed per batch | 20 |
| `outbox-max-attempts` | Maximum projection attempts | 5 |

For an existing MySQL installation, apply
`data-agent-management/src/main/resources/sql/migration/V20260729_01__create_durable_memory.sql`
before deployment. New installations continue to use
`data-agent-management/src/main/resources/sql/schema.sql`. Graph requests do not fall back to the legacy memory
implementation when the new tables are absent.

### 3. Embedding Batch Configuration

Configuration prefix: `spring.ai.alibaba.data-agent.embedding-batch`

| Configuration Item | Description | Default Value |
|-------------------|-------------|---------------|
| `encoding-type` | Text encoding type (refer to com.knuddels.jtokkit.api.EncodingType) | cl100k_base |
| `max-token-count` | Maximum tokens per batch. Recommended: 2000-8000 | 8000 |
| `reserve-percentage` | Reserve percentage (for buffer space) | 0.2 |
| `max-text-count` | Maximum texts per batch (DashScope limit is 10) | 10 |

### 4. Vector Store Configuration

Configuration prefix: `spring.ai.alibaba.data-agent.vector-store`

| Configuration Item | Description | Default Value |
|-------------------|-------------|---------------|
| `default-similarity-threshold` | Global default similarity threshold | 0.4 |
| `table-similarity-threshold` | Table recall similarity threshold | 0.2 |
| `batch-del-topk-limit` | Maximum documents for batch deletion | 5000 |
| `default-topk-limit` | Global default max documents returned (currently only used by business knowledge and agent knowledge) | 8 |
| `table-topk-limit` | Maximum documents for table recall | 10 |
| `enable-hybrid-search` | Enable hybrid search | false |
| `elasticsearch-min-score` | ES keyword search minimum score threshold | 0.5 |

#### Vector Store Dependency Extension

The project uses in-memory vector store (`SimpleVectorStore`) by default. To use persistent vector stores (like PGVector, Milvus, etc.), follow these steps:

1. **Add Dependency**: Add the corresponding Spring AI Starter to `pom.xml`.

   ```xml
   <!-- Example: Import PGvector -->
   <dependency>
       <groupId>org.springframework.ai</groupId>
       <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
   </dependency>
   ```

2. **Configure Properties**: Add the corresponding vector store connection configuration in `application.yml`. For specific parameters, refer to [Spring AI Official Documentation](https://springdoc.cn/spring-ai/api/vectordbs.html).

2. **Configure `spring.ai.vectorstore.type`**. You can find the specific value after importing the vector store starter above by searching for `VectorStoreAutoConfiguration` auto-configuration class. For example, for `es` it's `ElasticsearchVectorStoreAutoConfiguration`, and you can see that `spring.ai.vectorstore.type` expects `elasticsearch`.


#### ES Schema Configuration Example
Below is the Elasticsearch Schema structure. Other vector stores (like Milvus, PGVector) can reference this structure to create their Schema, paying special attention to the data types of fields in `metadata`.

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

### 5. Text Splitter Configuration

Configuration prefix: `spring.ai.alibaba.data-agent.text-splitter`

| Configuration Item | Description | Default Value |
|-------------------|-------------|---------------|
| `chunk-size` | Default chunk size (token-based) | 1000 |
| `min-chunk-size-chars` | Minimum chunk character count | 400 |
| `min-chunk-length-to-embed` | Minimum chunk length for embedding | 10 |
| `max-num-chunks` | Maximum number of chunks | 5000 |
| `keep-separator` | Keep separator | true |
| `separators` | Custom separator list | null (use default) |


### 6. Code Executor Configuration

Configuration prefix: `spring.ai.alibaba.data-agent.code-executor`

| Configuration Item | Description | Default Value |
|-------------------|-------------|---------------|
| `code-timeout` | Python code execution timeout | 60s |
| `limit-memory` | Container memory limit (MB) | 500 |
| `cpu-core` | Container CPU cores | 1 |
| `python-max-tries-count` | Maximum Python execution retries | 5 |
| `sandbox.docker-host` | Docker Engine endpoint | `unix:///var/run/docker.sock` |
| `sandbox.image-name` | SAA base image | `agentscope-registry.ap-southeast-1.cr.aliyuncs.com/agentscope/runtime-sandbox-base:latest` |
| `sandbox.container-prefix` | Task container name prefix | `dataagent-sandbox-` |
| `sandbox.max-concurrency` | Maximum concurrent sandboxes | 4 |
| `sandbox.queue-capacity` | Bounded wait queue size | 10 |
| `sandbox.max-code-bytes` | Python source UTF-8 byte limit | 262144 (256 KiB) |
| `sandbox.max-input-bytes` | stdin JSON UTF-8 byte limit | 10485760 (10 MiB) |
| `sandbox.max-output-bytes` | stdout UTF-8 byte limit | 1048576 (1 MiB) |
| `sandbox.max-error-bytes` | stderr UTF-8 byte limit | 262144 (256 KiB) |
| `sandbox.max-metadata-bytes` | PEP 723 metadata UTF-8 byte limit | 8192 (8 KiB) |
| `sandbox.max-dependencies` | Maximum number of direct dependencies | 20 |
| `sandbox.package-index-url` | Dynamic package index | `https://pypi.org/simple` |
| `sandbox.dependency-install-timeout` | Dependency installation timeout | 3m |
| `sandbox.max-connections` | Container `nofile` limit | 4096 |

Third-party dependencies must be declared in the generated script's PEP 723 `dependencies`.
Host-local, legacy Docker pool, and AI Simulation executors are no longer available.

Common environment variables:

| Environment Variable | Configuration | Purpose |
|---|---|---|
| `DATAAGENT_SANDBOX_DOCKER_HOST` | `sandbox.docker-host` | Point to a local or remote Docker Engine |
| `DATAAGENT_SANDBOX_IMAGE` | `sandbox.image-name` | Pin the runtime image; use a digest in production |
| `DATAAGENT_PYPI_INDEX_URL` | `sandbox.package-index-url` | Point to an enterprise private PyPI proxy |

Each Python task creates a separate `BaseSandbox`, installs dependencies and executes code in the same
container, then stops and removes that container. The service-side wait timeout is the dependency
installation timeout plus the code timeout plus a 30-second transport margin. `requires-python` is
currently parsed and retained, but it does not select or validate the sandbox Python version.

See [Advanced Features - Python Execution Environment Configuration](ADVANCED_FEATURES-en.md#python-execution-environment-configuration)
for dependency syntax, security restrictions, runtime verification, and troubleshooting. See the
[SAA 1.1.2.2 Python Sandbox Integration Design](superpowers/specs/2026-07-28-saa-python-sandbox-integration-design.md)
for implementation boundaries.

### 7. File Storage Configuration

Configuration prefix: `spring.ai.alibaba.data-agent.file`

| Configuration Item | Description | Default Value |
|-------------------|-------------|---------------|
| `type` | Storage type (LOCAL/OSS) | LOCAL |
| `path` | Local upload directory path | ./uploads |
| `url-prefix` | External access URL prefix | /uploads |
| `image-size` | Image size limit (bytes) | 2097152 (2MB) |
| `path-prefix` | Object storage path prefix | "" |

### 8. Alibaba Cloud OSS Configuration

Configuration prefix: `spring.ai.alibaba.data-agent.file.oss`

| Configuration Item | Description | Default Value |
|-------------------|-------------|---------------|
| `access-key-id` | OSS Access Key ID | - |
| `access-key-secret` | OSS Access Key Secret | - |
| `endpoint` | OSS endpoint address | - |
| `bucket-name` | OSS bucket name | - |
| `custom-domain` | Custom domain | - |


### 9. Database Initialization

Configuration prefix: `spring.sql.init`

| Configuration Item | Description | Default Value | Notes |
|-------------------|-------------|---------------|-------|
| `mode` | Initialization mode (always/never) | never | Set to `always` only when initialization is explicitly required |
| `schema-locations` | Table structure script path | classpath:sql/schema.sql | |
| `data-locations` | Data script path | classpath:sql/data.sql | |

### 10. Dependency Extension

If you choose not to use Spring AI Alibaba Starter and instead manually import OpenAI or other vendor Starters:
- Please ensure you remove the default Starter dependency to avoid conflicts.
- You may need to manually configure `ChatClient`, `ChatModel`, and `EmbeddingModel` Beans.

### 11. Report Resources Configuration

Configuration prefix: `spring.ai.alibaba.data-agent.report-template`

| Configuration Item | Description | Default Value |
|-------------------|-------------|---------------|
| `marked-url` | Marked.js path (Markdown rendering library) | https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/marked/12.0.0/marked.min.js |
| `echarts-url` | ECharts path (chart library) | https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/echarts/5.5.0/echarts.min.js |

### 12. Langfuse Observability Configuration

Configuration prefix: `spring.ai.alibaba.data-agent.langfuse`

| Configuration Item | Description | Default Value |
|-------------------|-------------|---------------|
| `enabled` | Enable Langfuse observability | true |
| `host` | Langfuse service URL (e.g. `https://cloud.langfuse.com` or self-hosted) | - |
| `public-key` | Langfuse project Public Key | - |
| `secret-key` | Langfuse project Secret Key | - |

Environment variables: `LANGFUSE_ENABLED`, `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY`

> For detailed usage, refer to [Advanced Features - Langfuse Observability](ADVANCED_FEATURES-en.md#langfuse-observability).

## Python Sandbox Verification

Unit tests that do not require Docker:

```bash
./mvnw -pl data-agent-management \
  -Dtest='PythonDependencyMetadataParserTest,PythonSandboxBootstrapBuilderTest,SandboxExecutionResultParserTest,SaaSandboxPythonCodeExecutorServiceTest,SaaSandboxRuntimeTest,PythonExecuteNodeTest,PythonWorkflowIntegrationTest' \
  test
```

Run the real SAA integration test while Docker is available:

```bash
docker info
./mvnw -pl data-agent-management -Dtest=SaaSandboxTaskRunnerIT test
```

Run the CI-equivalent checks before submission:

```bash
make format-check
make checkstyle-check
make test
```

A real end-to-end acceptance check must go beyond HTTP 200: the browser timeline must show dependency
installation and Python execution output, a final report, and SSE `event:complete`. The command
`docker ps -a --filter name=dataagent-sandbox-` must not show leftover task containers.

## Learning Resources

### Official Documentation

- [Spring AI Alibaba Documentation](https://springdoc.cn/spring-ai/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Nuxt Documentation](https://nuxt.com/docs)
- [Vue Documentation](https://vuejs.org/guide/)
- [TypeScript Documentation](https://www.typescriptlang.org/)

### Related Technologies

- StateGraph Workflow Engine
- MyBatis Data Access Framework
- Vector Store
- Server-Sent Events (SSE)

## Contribution Guide

For detailed contribution guidelines, see [CONTRIBUTING-en.md](../CONTRIBUTING-en.md).

### Contribution Types

- Report Bugs
- Suggest New Features
- Improve Documentation
- Submit Code Fixes
- Develop New Features

### Code of Conduct

- Respect all contributors
- Stay friendly and professional
- Accept constructive criticism
- Focus on project goals


---

Thank you for contributing to the DataAgent project!
