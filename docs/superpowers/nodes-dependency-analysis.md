# DataAgent Workflow Nodes Dependency Analysis

**Date:** 2026-03-30
**Purpose:** Document all 16 workflow nodes with their dependencies and test requirements

---

## Node Dependency Matrix

| Node | Dependencies | State Keys Required | State Keys Produced | Mock Complexity |
|-------|-------------|-------------------|---------------------|-----------------|
| SqlGenerateNode | Nl2SqlService, DataAgentProperties | TABLE_RELATION_OUTPUT, EVIDENCE, DB_DIALECT_TYPE, PLANNER_NODE_OUTPUT, PLAN_CURRENT_STEP, SQL_GENERATE_COUNT, SQL_REGENERATE_REASON, QUERY_ENHANCE_NODE_OUTPUT | SQL_GENERATE_OUTPUT, SQL_GENERATE_COUNT, SQL_REGENERATE_REASON | Medium |
| SqlExecuteNode | DatabaseUtil, Nl2SqlService, LlmService, DataAgentProperties, JsonParseUtil | SQL_GENERATE_OUTPUT, AGENT_ID, PLANNER_NODE_OUTPUT, PLAN_CURRENT_STEP | SQL_EXECUTE_NODE_OUTPUT, SQL_REGENERATE_REASON, SQL_RESULT_LIST_MEMORY, PLAN_CURRENT_STEP, SQL_GENERATE_COUNT | High |
| PlannerNode | LlmService | TABLE_RELATION_OUTPUT, EVIDENCE, GENEGRATED_SEMANTIC_MODEL_PROMPT, PLAN_VALIDATION_ERROR, IS_ONLY_NL2SQL, QUERY_ENHANCE_NODE_OUTPUT | PLANNER_NODE_OUTPUT | Medium |
| EvidenceRecallNode | LlmService, AgentVectorStoreService, JsonParseUtil, AgentKnowledgeMapper | INPUT_KEY, AGENT_ID, MULTI_TURN_CONTEXT | EVIDENCE | High |
| PythonGenerateNode | CodeExecutorProperties, LlmService | TABLE_RELATION_OUTPUT, SQL_RESULT_LIST_MEMORY, PYTHON_IS_SUCCESS, PYTHON_TRIES_COUNT, QUERY_ENHANCE_NODE_OUTPUT | PYTHON_GENERATE_NODE_OUTPUT, PYTHON_TRIES_COUNT | Medium |
| PythonExecuteNode | CodePoolExecutorService, JsonParseUtil, CodeExecutorProperties | PYTHON_GENERATE_NODE_OUTPUT, SQL_RESULT_LIST_MEMORY, PYTHON_TRIES_COUNT | PYTHON_EXECUTE_NODE_OUTPUT, PYTHON_IS_SUCCESS, PYTHON_FALLBACK_MODE | Medium |
| PythonAnalyzeNode | LlmService | PYTHON_EXECUTE_NODE_OUTPUT, SQL_EXECUTE_NODE_OUTPUT, PLAN_CURRENT_STEP, PYTHON_FALLBACK_MODE, QUERY_ENHANCE_NODE_OUTPUT | SQL_EXECUTE_NODE_OUTPUT, PLAN_CURRENT_STEP, PYTHON_ANALYSIS_NODE_OUTPUT | Low |
| SemanticConsistencyNode | Nl2SqlService | EVIDENCE, TABLE_RELATION_OUTPUT, DB_DIALECT_TYPE, SQL_GENERATE_OUTPUT, QUERY_ENHANCE_NODE_OUTPUT | SEMANTIC_CONSISTENCY_NODE_OUTPUT, SQL_REGENERATE_REASON | Low |
| PlanExecutorNode | None | PLANNER_NODE_OUTPUT, PLAN_CURRENT_STEP, IS_ONLY_NL2SQL, PLAN_REPAIR_COUNT, HUMAN_REVIEW_ENABLED | PLAN_VALIDATION_STATUS, PLAN_VALIDATION_ERROR, PLAN_NEXT_NODE, PLAN_CURRENT_STEP, PLAN_REPAIR_COUNT | Very Low |
| ReportGeneratorNode | LlmService | SQL_EXECUTE_NODE_OUTPUT, TABLE_RELATION_OUTPUT, EVIDENCE, INPUT_KEY | REPORT_GENERATOR_NODE_OUTPUT | Medium |
| IntentRecognitionNode | LlmService | INPUT_KEY | INTENT_RECOGNITION_NODE_OUTPUT | Low |
| QueryEnhanceNode | LlmService, AgentVectorStoreService | INPUT_KEY, MULTI_TURN_CONTEXT | QUERY_ENHANCE_NODE_OUTPUT | Medium |
| FeasibilityAssessmentNode | LlmService | INPUT_KEY, TABLE_RELATION_OUTPUT, EVIDENCE | FEASIBILITY_ASSESSMENT_NODE_OUTPUT | Low |
| SchemaRecallNode | AgentKnowledgeMapper | AGENT_ID | TABLE_RELATION_OUTPUT | Medium |
| TableRelationNode | LlmService, AgentKnowledgeMapper | AGENT_ID, TABLE_RELATION_RETRY_COUNT | TABLE_RELATION_OUTPUT, TABLE_RELATION_EXCEPTION_OUTPUT, TABLE_RELATION_RETRY_COUNT | Medium |
| HumanFeedbackNode | None | HUMAN_FEEDBACK_DATA | None | Very Low |

---

## Detailed Node Analysis

### SQL Workflow Nodes

#### SqlGenerateNode
- **Purpose**: Generate SQL queries with retry logic
- **Dependencies**: 2
  - `Nl2SqlService.generateSql(SqlGenerationDTO)` - Returns `Flux<String>`
  - `DataAgentProperties.getMaxSqlRetryCount()` - Returns `int`
- **Logic Flow**:
  1. Check if `SQL_GENERATE_COUNT >= maxRetryCount`
  2. Get current step instruction from plan
  3. Check `SQL_REGENERATE_REASON` for retry scenario
  4. Call `nl2SqlService.generateSql()` with appropriate DTO
  5. Trim SQL output using `nl2SqlService.sqlTrim()`
  6. Return streaming generator with SQL markers
- **Test Scenarios**:
  - Initial SQL generation (no retry)
  - SQL regeneration after execution error
  - SQL regeneration after semantic validation failure
  - Max retry count exceeded

#### SqlExecuteNode
- **Purpose**: Execute SQL against database
- **Dependencies**: 5
  - `DatabaseUtil.getAgentDbConfig(Long)` - Returns `DbConfigBO`
  - `DatabaseUtil.getAgentAccessor(Long)` - Returns `Accessor`
  - `Accessor.executeSqlAndReturnObject(DbConfigBO, DbQueryParameter)` - Returns `ResultSetBO`
  - `Nl2SqlService.sqlTrim(String)` - Returns trimmed SQL
  - `LlmService.callSystem(String)` (optional) - Returns `Flux<ChatResponse>`
  - `JsonParseUtil.tryConvertToObject()` - For chart config
- **Logic Flow**:
  1. Get `SQL_GENERATE_OUTPUT` and trim
  2. Validate `AGENT_ID` present
  3. Get database config and accessor
  4. Execute SQL and get results
  5. Optionally enrich with chart config via LLM
  6. Return streaming generator with result markers
- **Test Scenarios**:
  - Successful SQL execution
  - SQL execution error (retry trigger)
  - Empty result set
  - Large result set
  - Chart config generation success/failure
  - Connection failure

### Python Workflow Nodes

#### PythonGenerateNode
- **Purpose**: Generate Python code for data analysis
- **Dependencies**: 2
  - `LlmService.call(String, String)` - Returns `Flux<ChatResponse>`
  - `CodeExecutorProperties` - Config properties (limitMemory, codeTimeout)
- **Logic Flow**:
  1. Check `PYTHON_IS_SUCCESS` flag
  2. If failed, include previous code and error in prompt
  3. Get schema and SQL results
  4. Call LLM with system prompt and user prompt
  5. Strip Python markers from response
  6. Return streaming generator with code markers
- **Test Scenarios**:
  - Initial code generation
  - Code regeneration after execution failure
  - Code regeneration with schema context
  - Timeout handling

#### PythonExecuteNode
- **Purpose**: Execute Python code via code pool
- **Dependencies**: 3
  - `CodePoolExecutorService.runTask(TaskRequest)` - Returns `TaskResponse`
  - `JsonParseUtil.tryConvertToObject()` - For parsing JSON output
  - `CodeExecutorProperties.getPythonMaxTriesCount()` - Max retries
- **Logic Flow**:
  1. Get code from `PYTHON_GENERATE_NODE_OUTPUT`
  2. Get SQL results from `SQL_RESULT_LIST_MEMORY`
  3. Check retry count
  4. Execute via code pool
  5. Parse JSON output (handle Unicode escape)
  6. Return streaming generator with output markers
- **Test Scenarios**:
  - Successful execution
  - Execution error (retry)
  - Max retry exceeded (fallback)
  - JSON parsing success/failure
  - Unicode character handling

#### PythonAnalyzeNode
- **Purpose**: Analyze Python execution results
- **Dependencies**: 1
  - `LlmService.callSystem(String)` - Returns `Flux<ChatResponse>`
- **Logic Flow**:
  1. Check `PYTHON_FALLBACK_MODE` flag
  2. If fallback, return static message
  3. Otherwise, call LLM to analyze output
  4. Update `SQL_EXECUTE_NODE_OUTPUT` with analysis
  5. Return streaming generator
- **Test Scenarios**:
  - Normal analysis mode
  - Fallback mode (max retries exceeded)
  - Empty Python output
  - Large Python output

### Orchestration Nodes

#### PlannerNode
- **Purpose**: Generate execution plan from query
- **Dependencies**: 1
  - `LlmService.callUser(String)` - Returns `Flux<ChatResponse>`
- **Logic Flow**:
  1. Check `IS_ONLY_NL2SQL` flag
  2. If true, return fixed NL2SQL plan
  3. Get plan from `QUERY_ENHANCE_NODE_OUTPUT`
  4. Check for `PLAN_VALIDATION_ERROR` (regeneration)
  5. Get schema from `TABLE_RELATION_OUTPUT`
  6. Get evidence from `EVIDENCE`
  7. Build prompt with template
  8. Call LLM to generate plan
  9. Return streaming generator with JSON markers
- **Test Scenarios**:
  - Initial plan generation
  - Plan regeneration with validation error
  - NL2SQL only mode
  - Multi-step plan
  - Single-step plan

#### PlanExecutorNode
- **Purpose**: Execute plan step by step
- **Dependencies**: 0 (pure logic)
- **Logic Flow**:
  1. Parse plan from state
  2. Validate plan structure
  3. Validate each execution step
  4. Check `HUMAN_REVIEW_ENABLED` flag
  5. Determine current step
  6. Check if plan completed
  7. Route to next node based on tool_to_use
- **Test Scenarios**:
  - Valid plan routing
  - Plan validation failure
  - Step completion detection
  - Human review routing
  - Unsupported node type

### RAG Nodes

#### EvidenceRecallNode
- **Purpose**: Retrieve evidence via RAG
- **Dependencies**: 4
  - `LlmService.callUser(String)` - Query rewrite
  - `AgentVectorStoreService.getDocumentsForAgent()` - Document retrieval
  - `JsonParseUtil.tryConvertToObject()` - Parse rewrite result
  - `AgentKnowledgeMapper.selectById()` - Knowledge lookup
- **Logic Flow**:
  1. Get question and agent ID
  2. Build query rewrite prompt
  3. Call LLM to rewrite query
  4. Parse standalone query
  5. Retrieve business term documents
  6. Retrieve agent knowledge documents
  7. Build formatted evidence
  8. Return streaming generator
- **Test Scenarios**:
  - Successful evidence retrieval
  - No evidence found
  - LLM rewrite failure
  - Vector store failure
  - Multiple document types (FAQ, QA, Document)

### Validation Nodes

#### SemanticConsistencyNode
- **Purpose**: Validate SQL semantic consistency
- **Dependencies**: 1
  - `Nl2SqlService.performSemanticConsistency()` - Returns `Flux<ChatResponse>`
- **Logic Flow**:
  1. Get evidence, schema, SQL, user query
  2. Build semantic consistency DTO
  3. Call NL2SQL service for validation
  4. Check if result contains "不通过" (fail)
  5. Return appropriate state update
- **Test Scenarios**:
  - Validation pass
  - Validation fail (semantic issue)
  - Empty schema
  - Missing required state keys

---

## Testing Priority Order

### Phase 1: Happy Paths (Target 40%)

1. **SqlGenerateNode** - High business value, 3 main paths
2. **SqlExecuteNode** - High business value, 4 main paths
3. **PlannerNode** - High business value, 3 main paths
4. **PythonGenerateNode** - Medium business value, 2 main paths
5. **PythonExecuteNode** - Medium business value, 3 main paths
6. **EvidenceRecallNode** - Medium complexity, 3 main paths

### Phase 2: Error Paths (Target 60%)

7. **SqlGenerateNode** - Max retry, null state
8. **SqlExecuteNode** - SQL error, connection fail, empty result
9. **PythonExecuteNode** - Execution error, max retry, parse error
10. **SemanticConsistencyNode** - Validation fail, missing data
11. **EvidenceRecallNode** - No evidence, LLM failure

### Phase 3: Corner Cases (Target 80%)

12. **PlanExecutorNode** - Edge cases in routing
13. **SqlExecuteNode** - Large results, null values
14. **PythonExecuteNode** - Unicode, large output
15. **Integration tests** - End-to-end workflows

---

## Common Test Patterns

### OverAllState Setup Pattern

```java
private OverAllState createTestState() {
    OverAllState state = new OverAllState();
    state.registerKeyAndStrategy(OUTPUT_KEY, new ReplaceStrategy());
    state.registerKeyAndStrategy(INPUT_KEY_1, new ReplaceStrategy());
    // ... more keys
    return state;
}
```

### Mock Service Pattern

```java
// Always use LENIENT to avoid unnecessary stubbing errors
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
```

### Flux Mock Pattern

```java
// For services returning Flux<ChatResponse>
when(service.call(anyString()))
    .thenReturn(Flux.just(
        ChatResponseUtil.createResponse("message")
    ));

// For services returning Flux<String>
when(service.generateSql(any()))
    .thenReturn(Flux.just("SELECT * FROM users"));

// Empty response
when(service.getDocumentsForAgent(anyString(), anyString(), anyString()))
    .thenReturn(Flux.empty());
```

---

## Complexity Assessment

| Complexity Level | Nodes | Strategy |
|----------------|-------|----------|
| Very Low | PlanExecutorNode, HumanFeedbackNode | Pure logic, no external deps |
| Low | IntentRecognitionNode, FeasibilityAssessmentNode, PythonAnalyzeNode, SemanticConsistencyNode | Single LLM dependency |
| Medium | SqlGenerateNode, PlannerNode, PythonGenerateNode, QueryEnhanceNode, SchemaRecallNode, TableRelationNode, ReportGeneratorNode | 2-3 dependencies |
| High | SqlExecuteNode | 5 dependencies, DB mocking |
| Very High | EvidenceRecallNode, PythonExecuteNode | Multiple external services |

**Testing Strategy**: Start with Very Low → Low complexity nodes first, build confidence, then tackle Medium → High complexity nodes.
