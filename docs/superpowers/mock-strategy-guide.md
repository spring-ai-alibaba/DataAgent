# DataAgent Testing Mock Strategy Guide

**Date:** 2026-03-30
**Purpose:** Document correct mock strategies for testing workflow nodes and services

---

## Overview

This document provides the correct mock strategies for testing DataAgent workflow nodes. Based on analysis of the codebase, all nodes follow consistent patterns that can be tested with proper mocking.

**Key Insight:** The reason previous tests had 1.2% coverage was NOT constant naming issues - it was improper mock configuration. The actual constants in `Constant.java` match the state keys correctly.

---

## State Management

### OverAllState Usage

`OverAllState` is used to pass data between workflow nodes. Key methods for testing:

```java
// Register keys before use (CRITICAL)
state.registerKeyAndStrategy(SQL_GENERATE_OUTPUT, new ReplaceStrategy());

// Set state values
state.updateState(Map.of(
    SQL_GENERATE_OUTPUT, "SELECT * FROM users",
    SQL_GENERATE_COUNT, 0
));

// Get state values (via StateUtil)
String sql = StateUtil.getStringValue(state, SQL_GENERATE_OUTPUT);
SchemaDTO schema = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
```

**Important:** Always register state keys before using them in tests.

### State Constants (from Constant.java)

All state keys are UPPERCASE with underscores. No lowercase variants exist.

| Constant Key | Purpose | Type |
|-------------|---------|------|
| `SQL_GENERATE_OUTPUT` | Generated SQL string | String |
| `SQL_GENERATE_COUNT` | Retry count | Integer |
| `SQL_REGENERATE_REASON` | Retry reason | SqlRetryDto |
| `SQL_EXECUTE_NODE_OUTPUT` | Execution results | Map<String, String> |
| `SQL_RESULT_LIST_MEMORY` | Result data | List<Map<String, String>> |
| `PLANNER_NODE_OUTPUT` | Plan JSON | String |
| `PLAN_CURRENT_STEP` | Current step number | Integer |
| `PLAN_VALIDATION_ERROR` | Validation error | String |
| `AGENT_ID` | Agent ID | String |
| `DB_DIALECT_TYPE` | Database type | String |
| `TABLE_RELATION_OUTPUT` | Schema info | SchemaDTO |
| `EVIDENCE` | Evidence content | String |
| `QUERY_ENHANCE_NODE_OUTPUT` | Query enhancement | QueryEnhanceOutputDTO |
| `PYTHON_GENERATE_NODE_OUTPUT` | Python code | String |
| `PYTHON_EXECUTE_NODE_OUTPUT` | Python result | String |
| `PYTHON_IS_SUCCESS` | Success flag | Boolean |
| `PYTHON_TRIES_COUNT` | Retry count | Integer |
| `PYTHON_FALLBACK_MODE` | Fallback flag | Boolean |
| `HUMAN_REVIEW_ENABLED` | Human review | Boolean |

---

## Node Mock Strategies

### SqlGenerateNode

**Dependencies:**
- `Nl2SqlService nl2SqlService` - Main service for SQL generation
- `DataAgentProperties properties` - Configuration (max retry count)

**Mock Pattern:**

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SqlGenerateNodeTest {

    @Mock
    private Nl2SqlService nl2SqlService;

    @Mock
    private DataAgentProperties properties;

    private SqlGenerateNode sqlGenerateNode;

    @BeforeEach
    void setUp() {
        sqlGenerateNode = new SqlGenerateNode(nl2SqlService, properties);
    }

    @Test
    void testSqlGeneration() throws Exception {
        // Setup state
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(SQL_GENERATE_OUTPUT, new ReplaceStrategy());
        state.registerKeyAndStrategy(SQL_GENERATE_COUNT, new ReplaceStrategy());
        state.updateState(Map.of(
            SQL_GENERATE_COUNT, 0,
            TABLE_RELATION_OUTPUT, testSchema,
            EVIDENCE, "test evidence",
            DB_DIALECT_TYPE, "mysql",
            PLANNER_NODE_OUTPUT, TEST_PLAN_JSON,
            PLAN_CURRENT_STEP, 1,
            QUERY_ENHANCE_NODE_OUTPUT, testQueryEnhance
        ));

        // Mock Nl2SqlService - return Flux of SQL strings
        when(properties.getMaxSqlRetryCount()).thenReturn(10);
        when(nl2SqlService.generateSql(any(SqlGenerationDTO.class)))
            .thenReturn(Flux.just("SELECT * FROM users"));

        // Execute
        Map<String, Object> result = sqlGenerateNode.apply(state);

        // Verify
        assertNotNull(result);
        assertTrue(result.containsKey(SQL_GENERATE_OUTPUT));
        verify(nl2SqlService).generateSql(any(SqlGenerationDTO.class));
    }
}
```

**Test Data Setup:**

```java
// SchemaDTO for testing
private static final SchemaDTO TEST_SCHEMA = SchemaDTO.builder()
    .name("test_schema")
    .description("Test schema")
    .tableCount(1)
    .table(List.of(TableDTO.builder()
        .name("users")
        .description("Users table")
        .column(new ArrayList<>())
        .primaryKeys(new ArrayList<>())
        .build()))
    .foreignKeys(new ArrayList<>())
    .build();

// QueryEnhanceOutputDTO for testing
private static final QueryEnhanceOutputDTO TEST_QUERY_ENHANCE =
    QueryEnhanceOutputDTO.builder()
        .canonicalQuery("查询所有用户")
        .expandedQueries(new ArrayList<>())
        .build();
```

---

### SqlExecuteNode

**Dependencies:**
- `DatabaseUtil databaseUtil` - Database access
- `Nl2SqlService nl2SqlService` - SQL trimming
- `LlmService llmService` - Chart config generation (optional)
- `DataAgentProperties properties` - Configuration
- `JsonParseUtil jsonParseUtil` - JSON parsing

**Mock Pattern:**

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SqlExecuteNodeTest {

    @Mock
    private DatabaseUtil databaseUtil;

    @Mock
    private Nl2SqlService nl2SqlService;

    @Mock
    private LlmService llmService;

    @Mock
    private DataAgentProperties properties;

    @Mock
    private JsonParseUtil jsonParseUtil;

    @Mock
    private Accessor accessor;  // Internal database accessor

    private SqlExecuteNode sqlExecuteNode;

    @BeforeEach
    void setUp() {
        sqlExecuteNode = new SqlExecuteNode(
            databaseUtil, nl2SqlService, llmService, properties, jsonParseUtil);
    }

    @Test
    void testSqlExecution() throws Exception {
        OverAllState state = createTestState();
        state.updateState(Map.of(
            SQL_GENERATE_OUTPUT, "SELECT * FROM users",
            AGENT_ID, "1",
            PLANNER_NODE_OUTPUT, TEST_PLAN_JSON,
            PLAN_CURRENT_STEP, 1
        ));

        // Setup mocks
        DbConfigBO dbConfig = new DbConfigBO();
        dbConfig.setSchema("test_schema");

        ResultSetBO resultSetBO = new ResultSetBO();
        resultSetBO.setData(new ArrayList<>());

        when(nl2SqlService.sqlTrim(any())).thenReturn("SELECT * FROM users");
        when(databaseUtil.getAgentDbConfig(1L)).thenReturn(dbConfig);
        when(databaseUtil.getAgentAccessor(1L)).thenReturn(accessor);
        when(accessor.executeSqlAndReturnObject(any(), any()))
            .thenReturn(resultSetBO);

        // Execute
        Map<String, Object> result = sqlExecuteNode.apply(state);

        // Verify
        assertNotNull(result);
        assertTrue(result.containsKey(SQL_EXECUTE_NODE_OUTPUT));
    }
}
```

---

### PlannerNode

**Dependencies:**
- `LlmService llmService` - LLM calls

**Mock Pattern:**

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlannerNodeTest {

    @Mock
    private LlmService llmService;

    private PlannerNode plannerNode;

    @BeforeEach
    void setUp() {
        plannerNode = new PlannerNode(llmService);
    }

    @Test
    void testPlanGeneration() throws Exception {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(PLANNER_NODE_OUTPUT, new ReplaceStrategy());
        state.updateState(Map.of(
            TABLE_RELATION_OUTPUT, TEST_SCHEMA,
            EVIDENCE, "test evidence",
            IS_ONLY_NL2SQL, false,
            QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE
        ));

        // Mock LLM response - return Plan JSON wrapped in markers
        String planJson = """
            {
                "thought_process": "Test plan",
                "execution_plan": [
                    {
                        "step": 1,
                        "tool_to_use": "sql_generate_node",
                        "tool_parameters": {"instruction": "Test"}
                    }
                ]
            }
            """;

        when(llmService.callUser(any()))
            .thenReturn(Flux.just(
                ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()),
                ChatResponseUtil.createPureResponse(planJson),
                ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign())
            ));

        // Execute
        Map<String, Object> result = plannerNode.apply(state);

        // Verify
        assertNotNull(result);
        assertTrue(result.containsKey(PLANNER_NODE_OUTPUT));
    }
}
```

---

### EvidenceRecallNode

**Dependencies:**
- `LlmService llmService` - Query rewrite
- `AgentVectorStoreService vectorStoreService` - Document retrieval
- `JsonParseUtil jsonParseUtil` - JSON parsing
- `AgentKnowledgeMapper agentKnowledgeMapper` - Database access

**Mock Pattern:**

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvidenceRecallNodeTest {

    @Mock
    private LlmService llmService;

    @Mock
    private AgentVectorStoreService vectorStoreService;

    @Mock
    private JsonParseUtil jsonParseUtil;

    @Mock
    private AgentKnowledgeMapper agentKnowledgeMapper;

    private EvidenceRecallNode evidenceRecallNode;

    @BeforeEach
    void setUp() {
        evidenceRecallNode = new EvidenceRecallNode(
            llmService, vectorStoreService, jsonParseUtil, agentKnowledgeMapper);
    }

    @Test
    void testEvidenceRecall() throws Exception {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(EVIDENCE, new ReplaceStrategy());
        state.updateState(Map.of(
            INPUT_KEY, "查询用户信息",
            AGENT_ID, "1"
        ));

        // Mock LLM query rewrite response
        String queryRewriteJson = """
            {
                "standalone_query": "查询用户信息"
            }
            """;
        when(llmService.callUser(any()))
            .thenReturn(Flux.just(
                ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()),
                ChatResponseUtil.createPureResponse(queryRewriteJson),
                ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign())
            ));

        // Mock vector store - return empty (no evidence case)
        when(vectorStoreService.getDocumentsForAgent(anyString(), anyString(), anyString()))
            .thenReturn(Flux.empty());

        // Mock JSON parse
        EvidenceQueryRewriteDTO rewriteDto = EvidenceQueryRewriteDTO.builder()
            .standaloneQuery("查询用户信息")
            .build();
        when(jsonParseUtil.tryConvertToObject(any(), any()))
            .thenReturn(rewriteDto);

        // Execute
        Map<String, Object> result = evidenceRecallNode.apply(state);

        // Verify
        assertNotNull(result);
        assertTrue(result.containsKey(EVIDENCE));
    }
}
```

---

### PythonGenerateNode

**Dependencies:**
- `CodeExecutorProperties codeExecutorProperties` - Config (timeout, memory)
- `LlmService llmService` - LLM calls

**Mock Pattern:**

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PythonGenerateNodeTest {

    @Mock
    private CodeExecutorProperties codeExecutorProperties;

    @Mock
    private LlmService llmService;

    private PythonGenerateNode pythonGenerateNode;

    @BeforeEach
    void setUp() {
        pythonGenerateNode = new PythonGenerateNode(codeExecutorProperties, llmService);
    }

    @Test
    void testPythonCodeGeneration() throws Exception {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(PYTHON_GENERATE_NODE_OUTPUT, new ReplaceStrategy());
        state.registerKeyAndStrategy(PYTHON_TRIES_COUNT, new ReplaceStrategy());
        state.updateState(Map.of(
            TABLE_RELATION_OUTPUT, TEST_SCHEMA,
            SQL_RESULT_LIST_MEMORY, new ArrayList<>(),
            PYTHON_IS_SUCCESS, true,
            PYTHON_TRIES_COUNT, 0,
            QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE
        ));

        // Mock properties
        when(codeExecutorProperties.getLimitMemory()).thenReturn(512);
        when(codeExecutorProperties.getCodeTimeout()).thenReturn("60s");

        // Mock LLM response - return Python code wrapped in markers
        String pythonCode = "import pandas as pd\ndata = pd.DataFrame()\nprint(data)";
        when(llmService.call(anyString(), anyString()))
            .thenReturn(Flux.just(
                ChatResponseUtil.createPureResponse(TextType.PYTHON.getStartSign()),
                ChatResponseUtil.createPureResponse(pythonCode),
                ChatResponseUtil.createPureResponse(TextType.PYTHON.getEndSign())
            ));

        // Execute
        Map<String, Object> result = pythonGenerateNode.apply(state);

        // Verify
        assertNotNull(result);
        assertTrue(result.containsKey(PYTHON_GENERATE_NODE_OUTPUT));
    }
}
```

---

### PythonExecuteNode

**Dependencies:**
- `CodePoolExecutorService codePoolExecutor` - Code execution
- `JsonParseUtil jsonParseUtil` - JSON parsing
- `CodeExecutorProperties codeExecutorProperties` - Config

**Mock Pattern:**

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PythonExecuteNodeTest {

    @Mock
    private CodePoolExecutorService codePoolExecutor;

    @Mock
    private JsonParseUtil jsonParseUtil;

    @Mock
    private CodeExecutorProperties codeExecutorProperties;

    private PythonExecuteNode pythonExecuteNode;

    @BeforeEach
    void setUp() {
        pythonExecuteNode = new PythonExecuteNode(
            codePoolExecutor, jsonParseUtil, codeExecutorProperties);
    }

    @Test
    void testPythonExecution() throws Exception {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(PYTHON_EXECUTE_NODE_OUTPUT, new ReplaceStrategy());
        state.registerKeyAndStrategy(PYTHON_IS_SUCCESS, new ReplaceStrategy());
        state.registerKeyAndStrategy(PYTHON_TRIES_COUNT, new ReplaceStrategy());
        state.updateState(Map.of(
            PYTHON_GENERATE_NODE_OUTPUT, "print('hello')",
            SQL_RESULT_LIST_MEMORY, new ArrayList<>(),
            PYTHON_TRIES_COUNT, 0
        ));

        // Mock code pool executor - successful execution
        String jsonOutput = "{\"result\": \"hello\"}";
        CodePoolExecutorService.TaskResponse taskResponse =
            new CodePoolExecutorService.TaskResponse(true, jsonOutput, "", "");
        when(codePoolExecutor.runTask(any())).thenReturn(taskResponse);
        when(codeExecutorProperties.getPythonMaxTriesCount()).thenReturn(3);
        when(jsonParseUtil.tryConvertToObject(any(), any())).thenReturn(Map.of("result", "hello"));

        // Execute
        Map<String, Object> result = pythonExecuteNode.apply(state);

        // Verify
        assertNotNull(result);
        assertTrue(result.containsKey(PYTHON_EXECUTE_NODE_OUTPUT));
    }
}
```

---

### PythonAnalyzeNode

**Dependencies:**
- `LlmService llmService` - LLM calls

**Mock Pattern:**

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PythonAnalyzeNodeTest {

    @Mock
    private LlmService llmService;

    private PythonAnalyzeNode pythonAnalyzeNode;

    @BeforeEach
    void setUp() {
        pythonAnalyzeNode = new PythonAnalyzeNode(llmService);
    }

    @Test
    void testPythonAnalysis() throws Exception {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(PYTHON_ANALYSIS_NODE_OUTPUT, new ReplaceStrategy());
        state.registerKeyAndStrategy(SQL_EXECUTE_NODE_OUTPUT, new ReplaceStrategy());
        state.registerKeyAndStrategy(PLAN_CURRENT_STEP, new ReplaceStrategy());
        state.registerKeyAndStrategy(PYTHON_FALLBACK_MODE, new ReplaceStrategy());
        state.updateState(Map.of(
            PYTHON_EXECUTE_NODE_OUTPUT, "{\"result\": \"data\"}",
            PLAN_CURRENT_STEP, 1,
            PYTHON_FALLBACK_MODE, false,
            QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE
        ));

        // Mock LLM analysis response
        String analysis = "分析完成：数据包含10条记录";
        when(llmService.callSystem(anyString()))
            .thenReturn(Flux.just(ChatResponseUtil.createResponse(analysis)));

        // Execute
        Map<String, Object> result = pythonAnalyzeNode.apply(state);

        // Verify
        assertNotNull(result);
        assertTrue(result.containsKey(PYTHON_ANALYSIS_NODE_OUTPUT));
    }
}
```

---

### SemanticConsistencyNode

**Dependencies:**
- `Nl2SqlService nl2SqlService` - Semantic validation

**Mock Pattern:**

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SemanticConsistencyNodeTest {

    @Mock
    private Nl2SqlService nl2SqlService;

    private SemanticConsistencyNode semanticConsistencyNode;

    @BeforeEach
    void setUp() {
        semanticConsistencyNode = new SemanticConsistencyNode(nl2SqlService);
    }

    @Test
    void testSemanticValidation() throws Exception {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(SEMANTIC_CONSISTENCY_NODE_OUTPUT, new ReplaceStrategy());
        state.updateState(Map.of(
            EVIDENCE, "test evidence",
            TABLE_RELATION_OUTPUT, TEST_SCHEMA,
            DB_DIALECT_TYPE, "mysql",
            SQL_GENERATE_OUTPUT, "SELECT * FROM users",
            QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE
        ));

        // Mock validation - pass case (no "不通过" in result)
        when(nl2SqlService.performSemanticConsistency(any(SemanticConsistencyDTO.class)))
            .thenReturn(Flux.just(
                ChatResponseUtil.createResponse("语义一致性校验通过")
            ));

        // Execute
        Map<String, Object> result = semanticConsistencyNode.apply(state);

        // Verify - should pass
        assertNotNull(result);
        assertTrue(result.containsKey(SEMANTIC_CONSISTENCY_NODE_OUTPUT));
    }
}
```

---

### PlanExecutorNode

**Dependencies:**
- None (pure logic node)

**Mock Pattern:**

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanExecutorNodeTest {

    private PlanExecutorNode planExecutorNode;

    @BeforeEach
    void setUp() {
        planExecutorNode = new PlanExecutorNode();
    }

    @Test
    void testPlanExecution() throws Exception {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(PLAN_NEXT_NODE, new ReplaceStrategy());
        state.registerKeyAndStrategy(PLAN_VALIDATION_STATUS, new ReplaceStrategy());
        state.registerKeyAndStrategy(PLAN_CURRENT_STEP, new ReplaceStrategy());
        state.registerKeyAndStrategy(PLAN_VALIDATION_ERROR, new ReplaceStrategy());

        // Set plan JSON in state
        String planJson = """
            {
                "thought_process": "Test plan",
                "execution_plan": [
                    {
                        "step": 1,
                        "tool_to_use": "sql_generate_node",
                        "tool_parameters": {"instruction": "Test"}
                    }
                ]
            }
            """;

        state.updateState(Map.of(
            PLANNER_NODE_OUTPUT, planJson,
            PLAN_CURRENT_STEP, 1,
            IS_ONLY_NL2SQL, false
        ));

        // Execute
        Map<String, Object> result = planExecutorNode.apply(state);

        // Verify - should route to sql_generate_node
        assertNotNull(result);
        assertEquals("sql_generate_node", result.get(PLAN_NEXT_NODE));
        assertTrue((Boolean) result.get(PLAN_VALIDATION_STATUS));
    }
}
```

---

## Common Test Utilities

### ChatResponse Creation

```java
// Always use ChatResponseUtil for creating mock responses
ChatResponse response = ChatResponseUtil.createResponse("message");
ChatResponse pureResponse = ChatResponseUtil.createPureResponse("pure message");

// For text type markers
ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign());
ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign());
ChatResponseUtil.createPureResponse(TextType.PYTHON.getStartSign());
ChatResponseUtil.createPureResponse(TextType.PYTHON.getEndSign());
ChatResponseUtil.createPureResponse(TextType.SQL.getStartSign());
ChatResponseUtil.createPureResponse(TextType.SQL.getEndSign());
```

### Flux Response Patterns

```java
// Single response
when(service.call(anyString(), anyString()))
    .thenReturn(Flux.just(ChatResponseUtil.createResponse("result")));

// Multiple responses (with markers)
when(service.call(anyString(), anyString()))
    .thenReturn(Flux.just(
        ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()),
        ChatResponseUtil.createPureResponse(jsonContent),
        ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign())
    ));

// Empty response
when(service.call(anyString()))
    .thenReturn(Flux.empty());
```

### JSON Parsing Mocks

```java
// Successful parse
when(jsonParseUtil.tryConvertToObject(jsonString, TargetClass.class))
    .thenReturn(targetObject);

// Parse failure
when(jsonParseUtil.tryConvertToObject(jsonString, TargetClass.class))
    .thenReturn(null);

// Parse with exception handling
when(jsonParseUtil.tryConvertToObject(any(), any()))
    .thenThrow(new JsonProcessingException("Invalid JSON"));
```

---

## Test Configuration

### Required Settings

```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-test</artifactId>
    <scope>test</scope>
</dependency>
```

### Test Class Pattern

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class YourNodeTest {

    @Mock
    private Service1 service1;

    @Mock
    private Service2 service2;

    private YourNode node;

    @BeforeEach
    void setUp() {
        node = new YourNode(service1, service2);
    }

    private OverAllState createTestState() {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(KEY1, new ReplaceStrategy());
        state.registerKeyAndStrategy(KEY2, new ReplaceStrategy());
        // ... more keys
        return state;
    }

    @Test
    void testMethod() throws Exception {
        OverAllState state = createTestState();
        state.updateState(Map.of(...));

        // Mock setup
        when(service1.method(any())).thenReturn(...);

        // Execute
        Map<String, Object> result = node.apply(state);

        // Assertions
        assertNotNull(result);
        assertTrue(result.containsKey(OUTPUT_KEY));
    }
}
```

---

## Why Previous Tests Failed (Debugging Notes)

1. **Missing State Registration**: Not calling `registerKeyAndStrategy()` before setting values
2. **Incorrect Constant Usage**: Using lowercase instead of UPPERCASE constants
3. **Improper Flux Mocking**: Not wrapping responses in `Flux.just()` properly
4. **Missing Mock Strictness**: Not using `@MockitoSettings(strictness = Strictness.LENIENT)` to allow unused stubs

**None of these issues exist in the actual code** - constants are correctly defined as UPPERCASE.

---

## Summary

Key principles for effective testing:

1. **Always register state keys** before using them
2. **Use correct constants** from `Constant.java` (all UPPERCASE)
3. **Mock Flux responses** using `Flux.just()` or `Flux.empty()`
4. **Use ChatResponseUtil** for creating mock chat responses
5. **Use Strictness.LENIENT** to allow flexible mocking
6. **Verify state outputs** after node execution
7. **Mock external services** to isolate node logic

Follow these patterns and tests will execute actual code paths, achieving meaningful coverage.
