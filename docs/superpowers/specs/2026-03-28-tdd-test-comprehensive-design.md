# DataAgent TDD Test Comprehensive Design

**Date:** 2026-03-28
**Status:** Approved
**Version:** 1.0

---

## Context

The DataAgent project currently has minimal test coverage (~12% backend, 0% frontend). This design establishes a comprehensive TDD-based testing strategy focusing on backend core logic (workflow nodes and services) with an 80% coverage target delivered in phases.

**Why:** Production stability, regression prevention, enable refactoring confidence.

**How to apply:** Execute phases sequentially; each phase adds tests before any implementation changes.

---

## Scope

**In Scope:**
- Backend workflow nodes (16 nodes)
- Backend core services (GraphService, VectorStore, CodePool)
- Test infrastructure (mocks, fixtures)
- Integration tests for critical flows

**Out of Scope:**
- Frontend tests (deferred)
- Controller endpoint tests (deferred)
- Performance/load tests (deferred)
- Security penetration tests (deferred)

---

## Architecture

### Test Directory Structure

```
data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/
├── node/
│   ├── sql/
│   │   ├── SqlGenerateNodeTest.java
│   │   ├── SqlExecuteNodeTest.java
│   │   └── SemanticConsistencyNodeTest.java
│   ├── python/
│   │   ├── PythonGenerateNodeTest.java
│   │   ├── PythonExecuteNodeTest.java
│   │   └── PythonAnalyzeNodeTest.java
│   ├── orchestration/
│   │   ├── PlannerNodeTest.java
│   │   └── PlanExecutorNodeTest.java
│   └── rag/
│       └── EvidenceRecallNodeTest.java
├── service/
│   ├── graph/
│   │   └── GraphServiceImplTest.java
│   ├── vectorstore/
│   │   ├── AgentVectorStoreServiceImplTest.java
│   │   └── DynamicFilterServiceTest.java
│   └── code/
│       └── CodePoolExecutorServiceTest.java  # enhance existing
├── integration/
│   ├── TextToSqlWorkflowIntegrationTest.java
│   └── PythonWorkflowIntegrationTest.java
└── mock/
    ├── MockLlmService.java
    ├── MockVectorStore.java
    └── TestFixtures.java
```

### Dependencies

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
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Phase 1: Critical Happy Paths (~40% Coverage)

### Text-to-SQL Tests

**SqlGenerateNodeTest.java**
```java
@Test
void simpleSelectQuery_validInput_generatesValidSql()
@Test
void queryWithWhereClause_validInput_generatesWhereCondition()
@Test
void queryWithJoin_validInput_generatesJoinClause()
```

**SqlExecuteNodeTest.java**
```java
@Test
void validSelectQuery_executesSuccessfully_returnsResults()
@Test
void queryWithMultipleColumns_executesSuccessfully_returnsAllColumns()
```

**SemanticConsistencyNodeTest.java**
```java
@Test
void validSql_noIssues_returnsPass()
@Test
void sqlWithKnownColumns_validatesSuccessfully()
```

### Python Execution Tests

**PythonGenerateNodeTest.java**
```java
@Test
void simpleDataAnalysis_validInput_generatesPythonCode()
@Test
void visualizationRequest_validInput_generatesPlottingCode()
```

**PythonExecuteNodeTest.java**
```java
@Test
void validPythonCode_executesSuccessfully_returnsOutput()
@Test
void codeWithPandas_imports_executesSuccessfully()
```

**PythonAnalyzeNodeTest.java**
```java
@Test
void validOutput_analyzesSuccessfully_returnsInterpretation()
```

### Orchestration Tests

**PlannerNodeTest.java**
```java
@Test
void simpleQuery_validInput_generatesValidPlan()
@Test
void queryWithMultipleSteps_generatesMultiStepPlan()
```

**PlanExecutorNodeTest.java**
```java
@Test
void validPlan_executesAllStepsSuccessfully()
@Test
void planWithSqlAndPython_steps_executesBoth()
```

**GraphServiceImplTest.java**
```java
@Test
void simpleQuery_validInput_completesWorkflowSuccessfully()
```

### RAG Tests

**EvidenceRecallNodeTest.java**
```java
@Test
void relevantQuery_validInput_returnsRelevantEvidence()
```

---

## Phase 2: Error Paths & Edge Cases (~60% Coverage)

### Text-to-SQL Error Handling

**SqlGenerateNodeTest.java** (add)
```java
@Test
void ambiguousQuery_throwsClarificationException()
@Test
void unknownTable_throwsSchemaException()
@Test
void invalidInput_throwsValidationException()
```

**SqlExecuteNodeTest.java** (add)
```java
@Test
void syntaxError_throwsSqlException_withMessage()
@Test
void emptyResult_returnsEmptyList_notError()
@Test
void connectionFailure_throwsConnectionException()
```

**SemanticConsistencyNodeTest.java** (add)
```java
@Test
void unknownColumn_throwsValidationException()
@Test
void invalidColumnType_throwsTypeException()
```

### Python Execution Error Handling

**PythonGenerateNodeTest.java** (add)
```java
@Test
void complexQuery_throwsComplexityException()
@Test
void unsafeRequest_throwsSecurityException()
```

**PythonExecuteNodeTest.java** (add)
```java
@Test
void codeTimeout_timesOut_throwsTimeoutException()
@Test
void syntaxError_throwsSyntaxException_withLineNumber()
@Test
void runtimeError_throwsExecutionException_withTraceback()
```

**PythonAnalyzeNodeTest.java** (add)
```java
@Test
void invalidOutput_throwsAnalysisException()
@Test
void timeoutInAnalysis_throwsTimeoutException()
```

### Orchestration Error Handling

**PlannerNodeTest.java** (add)
```java
@Test
void llmFailure_retriesThenThrowsException()
@Test
void invalidQuery_throwsValidationException()
```

**PlanExecutorNodeTest.java** (add)
```java
@Test
void stepFailure_returnsPartialResults_withFailureInfo()
@Test
void invalidState_throwsInvalidStateException()
```

**GraphServiceImplTest.java** (add)
```java
@Test
void llmApiFailure_retriesThenFailsGracefully()
@Test
void vectorStoreFailure_fallbackToDirectQuery()
```

### RAG Error Handling

**EvidenceRecallNodeTest.java** (add)
```java
@Test
void noRelevantEvidence_returnsEmptyList()
@Test
void vectorStoreFailure_fallbackToKeywordSearch()
```

---

## Phase 3: Corner Cases & Integration (~80% Coverage)

### Integration Tests

**TextToSqlWorkflowIntegrationTest.java**
```java
@Test
void endToEnd_sqlQuery_generatesExecutesValidates_returnsResults()
@Test
void errorInStep_returnsPartialResults_withErrorDetails()
```

**PythonWorkflowIntegrationTest.java**
```java
@Test
void endToEnd_pythonAnalysis_generatesExecutesAnalyzes_returnsResults()
```

### Corner Cases

**SqlGenerateNodeTest.java** (add)
```java
@Test
void extremelyLongQuery_truncatesOrSplits_generatesValidSql()
@Test
void nestedQueries_generatesSubquerySyntax()
@Test
void specialCharacters_inColumns_escapesProperly()
```

**SqlExecuteNodeTest.java** (add)
```java
@Test
void veryLargeResultSet_processesInChunks()
@Test
void nullValues_inColumns_handlesCorrectly()
```

**PythonExecuteNodeTest.java** (add)
```java
@Test
void largeOutput_handlesMemoryPressure()
@Test
void unicodeCharacters_inOutput_preservesCorrectly()
```

---

## Mock Strategy

| Component | Mock Approach | Tool |
|-----------|---------------|------|
| LLM Service | Mock ChatModel with predefined responses | Mockito |
| Vector Store | In-memory SimpleVectorStore with test data | Spring Test |
| Database | H2 for unit, Testcontainers MySQL for integration | Testcontainers |
| Code Pool | Mocked executor returning known results | Mockito |
| File Storage | In-memory storage for tests | Custom |

### Mock Examples

```java
@Mock
private ChatModel mockChatModel;

given(mockChatModel.call(any()))
    .willReturn(new ChatResponse(List.of(new Generation("mock sql"))));
```

---

## Test Fixtures

### TestFixtures.java

```java
public class TestFixtures {
    public static final String SAMPLE_SCHEMA = "CREATE TABLE users (id INT, name VARCHAR)";
    public static final String SAMPLE_QUERY = "find all users";
    public static final String EXPECTED_SQL = "SELECT * FROM users";

    public static AgentState createTestAgentState() { ... }
    public static Evidence createTestEvidence() { ... }
    public static ExecutionPlan createTestPlan() { ... }
}
```

---

## Verification

### Coverage Goals

| Component | Target | Phase 1 | Phase 2 | Phase 3 |
|-----------|--------|---------|---------|---------|
| SQL Nodes | 80% | 30% | 60% | 80% |
| Python Nodes | 80% | 30% | 60% | 80% |
| Orchestration | 80% | 30% | 60% | 80% |
| RAG | 80% | 30% | 60% | 80% |
| Vector Services | 80% | 30% | 60% | 80% |

### Running Tests

```bash
# All tests
./mvnw test

# Specific phase
./mvnw test -Dtest=*Node*Test -Dgroups=phase1

# Coverage report
./mvnw test jacoco:report
```

### CI Integration

```yaml
- name: Run Tests
  run: mvnw test

- name: Check Coverage
  run: mvnw jacoco:check -Djacoco.minimum.coverage=0.80
```

---

## Success Criteria

1. All tests pass consistently
2. Coverage reaches 80% for in-scope components
3. Test execution time < 2 minutes for unit tests
4. Tests provide clear failure messages
5. Integration tests use Testcontainers with MySQL

---

## Implementation Order

1. Set up test infrastructure (mocks, fixtures)
2. Phase 1 tests (happy paths) → verify fail
3. Implement/adjust code to pass Phase 1 tests
4. Phase 2 tests (error paths) → verify fail
5. Implement/adjust code to pass Phase 2 tests
6. Phase 3 tests (corner cases + integration) → verify fail
7. Implement/adjust code to pass Phase 3 tests
8. Verify 80% coverage
9. Refactor as needed (IMPROVE phase)
