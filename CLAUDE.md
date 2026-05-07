# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common Commands

### Backend (Maven)

```bash
# Build project (skip tests)
make build
# or
./mvnw clean package -DskipTests=true

# Run tests
make test
# or
./mvnw test

# Run specific test class
./mvnw test -Dtest=YourTestClass

# Run backend server (use H2 for development)
cd data-agent-management
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2

# Code formatting
make format-fix
./mvnw spring-javaformat:apply

# Check format
make format-check
./mvnw spring-javaformat:validate

# Checkstyle
make checkstyle-check
./mvnw checkstyle:check
```

### Frontend (Vue 3 / Vite)

```bash
cd data-agent-frontend

# Install dependencies
npm install

# Start dev server
npm run dev

# Build for production
npm run build

# Lint
npm run lint

# Type check
npm run type-check
```

### Database Setup

```bash
# For MySQL (production)
mysql -u root -p < data-agent-management/src/main/resources/sql/schema.sql

# For H2 (development) - auto-initializes via application-h2.yml
# Access H2 console at: http://localhost:8065/h2-console
```

## Project Architecture

DataAgent is an enterprise-level intelligent data analysis agent built on **Spring AI Alibaba Graph**. It goes beyond traditional Text-to-SQL with Python deep analysis, multi-dimensional chart reports, and intelligent report generation.

### High-Level Structure

```
data-agent-management/     # Spring Boot backend (port 8065)
data-agent-frontend/        # Vue 3 frontend (port 3000)
docs/                      # Documentation
```

### Backend Core Architecture

The backend uses a **StateGraph workflow** pattern with 16+ nodes orchestrated by a dispatcher:

**Workflow Nodes** (`workflow/node/`):
- `IntentRecognitionNode` - Analyzes user intent
- `EvidenceRecallNode` - RAG retrieval with optional hybrid search
- `PlannerNode` - Generates execution plan
- `SqlGenerateNode` / `SqlExecuteNode` - Text-to-SQL execution
- `PythonGenerateNode` / `PythonExecuteNode` / `PythonAnalyzeNode` - Python code execution
- `ReportGeneratorNode` - Generates HTML/Markdown reports with ECharts
- `HumanFeedbackNode` - Human-in-the-loop intervention
- `SchemaRecallNode` / `TableRelationNode` - Schema management
- `SemanticConsistencyNode` - SQL validation
- `FeasibilityAssessmentNode` - Plan feasibility check
- `QueryEnhanceNode` - Query rewriting
- `PlanExecutorNode` - Orchestrates plan step-by-step

**Key Services** (`service/`):
- `graph/GraphServiceImpl` - Main graph orchestration via SSE streaming
- `llm/LlmService` - LLM abstraction (stream/block modes)
- `aimodelconfig/AiModelRegistry` - Dynamic model switching at runtime
- `vectorstore/AgentVectorStoreService` - Vector retrieval with hybrid strategy
- `code/CodePoolExecutorService` - Python execution (Docker/Local/AI-sim)
- `mcp/McpServerService` - MCP protocol server for external tools
- `langfuse/LangfuseService` - Observability integration

**Controllers**:
- `GraphController` - SSE endpoint for streaming analysis (`/api/graph/stream-search`)
- `AgentController` - Agent CRUD + API Key management
- `ModelConfigController` - LLM/Embedding model configuration
- `PromptConfigController` - Custom prompt templates with optimization

### StateGraph Flow

```
User Query
    → IntentRecognition
    → EvidenceRecall (RAG)
    → QueryEnhance
    → SchemaRecall + TableRelation
    → FeasibilityAssessment
    → Planner
    → [Optional] HumanFeedback
    → PlanExecutor (iterates steps)
        → SQL steps: Generate → SemanticCheck → Execute
        → Python steps: Generate → Execute → Analyze
    → ReportGenerator
```

### Configuration Prefix

All DataAgent configs use `spring.ai.alibaba.data-agent`:

- `llm-service-type` - STREAM or BLOCK mode
- `vector-store.*` - Retrieval thresholds, TopK limits
- `code-executor.*` - Python execution (docker/local/ai-sim)
- `file.*` - File storage (local/OSS)
- `langfuse.*` - Observability (enabled by default false)
- `embedding-batch.*` - Embedding batch processing
- `text-splitter.*` - Multiple splitter strategies (token/recursive/sentence/semantic/paragraph)

### Model Support

Uses OpenAI-compatible API interface. Supports:
- Chat models via `ChatClient`/`ChatModel`
- Embedding models via `EmbeddingModel`
- Dynamic hot-swapping via `AiModelRegistry`
- Transformers-based local embedding (optional)

### Vector Store Extension

Default: `SimpleVectorStore` (in-memory)

To use persistent stores:
1. Add dependency (e.g., `spring-ai-starter-vector-store-pgvector`)
2. Set `spring.ai.vectorstore.type` (e.g., `pgvector`)
3. Configure connection properties
4. Ensure metadata fields match: `agentId`, `vectorType`, `businessTermId`, `agentKnowledgeId`

### MCP Server

Native MCP protocol support for integration with Claude Desktop:
- Tools: `nl2SqlToolCallback`, `listAgentsToolCallback`
- Auto-discovery via `spring-ai-starter-mcp-server-webflux`

## Development Notes

### Environment Profiles

- `application.yml` - MySQL configuration (production)
- `application-h2.yml` - H2 in-memory database (development, auto-initializes)

### Testcontainers

Uses Testcontainers for integration tests. Database: `mysql:8.0`.

### Human Feedback Flow

1. Request with `humanFeedback=true` sets state flag
2. `PlanExecutorNode` pauses at `HumanFeedbackNode`
3. `CompiledGraph.interruptBefore(HUMAN_FEEDBACK_NODE)` waits
4. Resume via `/api/graph/stream-search` with feedback payload and `threadId`

### Multi-Turn Context

`MultiTurnContextManager` maintains conversation history (max turns configurable via `maxturnhistory`) injected into subsequent prompts.

### File Storage Types

- **LOCAL**: `path` relative to project root (e.g., `uploads/`)
- **OSS**: Alibaba Cloud OSS with custom domain support
