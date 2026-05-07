# DataAgent TDD Test Comprehensive Design v2

**Date:** 2026-04-02
**Supersedes:** 2026-03-28-tdd-test-comprehensive-design.md
**Status:** Draft
**Version:** 2.0

---

## Context

The DataAgent project's test suite is in critical condition:

- **56% of test methods are fake** -- 41 of 73 methods are `assertTrue(true)` stubs or data-only tests that never call production code
- **~120 real tests were deleted** from main branch in commit `3354ce3` and replaced with stubs
- **0/11 dispatchers tested**, 0/15 controllers tested, 0/25+ services tested, 0/19 connector components tested
- Only 6 of 16 workflow nodes have any meaningful test coverage
- Estimated real coverage: ~10-15%

This spec defines a bottom-up, phased approach to reach 80%+ backend test coverage.

**Why:** Production stability, regression prevention, enable safe refactoring, CI gate enforcement.

**How to apply:** Execute phases 0-6 sequentially. Each phase builds on tested lower layers.

---

## Scope

### In Scope

| Layer | Count | Current Tests | Target Coverage |
|-------|-------|--------------|-----------------|
| Fake test cleanup | 12 files | 41 fake methods | Delete all |
| Restore deleted tests | ~20 files | ~120 methods | Recover & adapt |
| Utilities | 18 classes | 1 partial (StateUtil) | 80%+ |
| Dispatchers | 11 classes | 0 | 80%+ |
| Workflow Nodes | 16 classes | 6 partial | 80%+ |
| Core Services | ~25 interfaces, ~55 impls | 0 | 80%+ critical, 60%+ CRUD |
| Controllers | 15 classes | 0 | Key endpoints |
| Connectors | 19 classes | 0 | H2 integration + unit |
| Integration | 2 stubs | 0 real | End-to-end workflows |

### Out of Scope

- Frontend tests (Vue 3) -- separate spec
- Performance/load testing
- Security penetration testing

---

## Test Directory Structure

```
data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/
├── common/
│   ├── TestFixtures.java                    # Shared test data factories
│   ├── ChatResponseUtil.java                # (exists in main, verify accessible)
│   └── MySqlContainerConfiguration.java     # Testcontainers config (restore from main)
├── util/
│   ├── StateUtilTest.java                   # Expand existing
│   ├── PlanProcessUtilTest.java
│   ├── JsonParseUtilTest.java
│   ├── SqlUtilTest.java
│   ├── DateTimeUtilTest.java               # Restore from main + expand
│   ├── FluxUtilTest.java
│   ├── MarkdownParserUtilTest.java          # Restore from main
│   ├── MdTableGeneratorUtilTest.java
│   └── ApiKeyUtilTest.java
├── workflow/
│   ├── dispatcher/
│   │   ├── FeasibilityAssessmentDispatcherTest.java
│   │   ├── HumanFeedbackDispatcherTest.java     # Restore from main
│   │   ├── IntentRecognitionDispatcherTest.java
│   │   ├── PlanExecutorDispatcherTest.java
│   │   ├── PythonExecutorDispatcherTest.java
│   │   ├── QueryEnhanceDispatcherTest.java
│   │   ├── SQLExecutorDispatcherTest.java
│   │   ├── SchemaRecallDispatcherTest.java
│   │   ├── SemanticConsistenceDispatcherTest.java
│   │   ├── SqlGenerateDispatcherTest.java
│   │   └── TableRelationDispatcherTest.java     # Restore from main
│   └── node/
│       ├── IntentRecognitionNodeTest.java       # Fix compilation
│       ├── PlanExecutorNodeTest.java            # Keep (16 methods)
│       ├── SqlGenerateNodeTest.java             # Expand error + corner
│       ├── SqlExecuteNodeTest.java              # Expand error + corner
│       ├── QueryEnhanceNodeTest.java            # NEW
│       ├── SchemaRecallNodeTest.java            # NEW
│       ├── TableRelationNodeTest.java           # NEW
│       ├── FeasibilityAssessmentNodeTest.java   # NEW
│       ├── HumanFeedbackNodeTest.java           # Restore from main + expand
│       ├── SemanticConsistencyNodeTest.java     # NEW
│       ├── ReportGeneratorNodeTest.java         # NEW
│       ├── PlannerNodeTest.java                 # NEW (replace stub)
│       ├── EvidenceRecallNodeTest.java          # NEW (replace stub)
│       ├── PythonGenerateNodeTest.java          # NEW
│       ├── PythonExecuteNodeTest.java           # NEW (replace stub)
│       └── PythonAnalyzeNodeTest.java           # NEW (replace stub)
├── service/
│   ├── graph/
│   │   ├── GraphServiceImplTest.java
│   │   └── MultiTurnContextManagerTest.java
│   ├── nl2sql/
│   │   └── Nl2SqlServiceImplTest.java
│   ├── vectorstore/
│   │   ├── AgentVectorStoreServiceImplTest.java
│   │   └── DynamicFilterServiceTest.java
│   ├── code/
│   │   ├── DockerCodePoolExecutorServiceTest.java   # Restore from main
│   │   └── LocalCodePoolExecutorServiceTest.java    # Restore from main
│   ├── llm/
│   │   ├── BlockLlmServiceTest.java
│   │   └── StreamLlmServiceTest.java
│   └── hybrid/
│       ├── RrfFusionStrategyTest.java               # Restore from main
│       └── AbstractHybridRetrievalStrategyTest.java  # Restore from main
├── controller/
│   ├── GraphControllerTest.java
│   ├── AgentControllerTest.java
│   ├── DatasourceControllerTest.java
│   ├── ChatControllerTest.java
│   └── ModelConfigControllerTest.java
├── connector/
│   ├── H2AccessorIntegrationTest.java           # Restore from main
│   ├── SqlExecutorTest.java
│   └── AbstractAccessorTest.java
├── mapper/
│   ├── AgentDatasourceMapperTest.java           # Restore from main
│   ├── DatasourceMapperTest.java                # Restore from main
│   ├── MappersTest.java                         # Restore from main
│   └── UserPromptConfigMapperTest.java          # Restore from main
└── integration/
    ├── TextToSqlWorkflowIntegrationTest.java    # NEW (replace stub)
    ├── PythonWorkflowIntegrationTest.java       # NEW (replace stub)
    └── HumanFeedbackIntegrationTest.java        # NEW
```

---

## Test Infrastructure

### Standard Test Annotations

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
```

### State Setup Pattern

```java
private OverAllState createTestState(String... keys) {
    OverAllState state = new OverAllState();
    for (String key : keys) {
        state.registerKeyAndStrategy(key, new ReplaceStrategy());
    }
    return state;
}
```

### Mock Strategy by Layer

| Layer | Mock Approach | Tool |
|-------|--------------|------|
| Utilities | No mocks (pure functions). Exception: `JsonParseUtil` mocks `LlmService` | JUnit 5 |
| Dispatchers | No mocks (9/11). `SqlGenerateDispatcher` mocks `DataAgentProperties`, `PythonExecutorDispatcher` mocks `CodeExecutorProperties` | Mockito |
| Nodes | Mock all injected services. Use real `OverAllState` | Mockito |
| Services | Mock dependencies (VectorStore, Mappers, ChatModel) | Mockito |
| Controllers | `@WebFluxTest` with `@MockBean` for service layer | Spring Test |
| Connectors | H2 in-memory DB for integration tests | H2 + JDBC |
| Integration | Testcontainers MySQL + mocked LLM responses | Testcontainers |

### Shared Test Fixtures (TestFixtures.java)

```java
public final class TestFixtures {

    // Schema fixtures
    public static SchemaDTO createTestSchema(String name, String... tableNames) {
        List<TableDTO> tables = Arrays.stream(tableNames)
            .map(t -> TableDTO.builder()
                .name(t)
                .description(t + " table")
                .column(new ArrayList<>())
                .primaryKeys(new ArrayList<>())
                .build())
            .toList();
        return SchemaDTO.builder()
            .name(name)
            .description("Test schema")
            .tableCount(tables.size())
            .table(tables)
            .foreignKeys(new ArrayList<>())
            .build();
    }

    // Query enhance fixtures
    public static QueryEnhanceOutputDTO createTestQueryEnhance(String query) {
        return QueryEnhanceOutputDTO.builder()
            .canonicalQuery(query)
            .expandedQueries(new ArrayList<>())
            .build();
    }

    // Plan fixtures
    public static String createTestPlanJson(String toolToUse, String instruction) {
        return """
            {
                "thought_process": "Test plan",
                "execution_plan": [{
                    "step": 1,
                    "tool_to_use": "%s",
                    "tool_parameters": {"instruction": "%s"}
                }]
            }
            """.formatted(toolToUse, instruction);
    }

    public static String createMultiStepPlanJson(Map<Integer, String> stepTools) {
        StringBuilder steps = new StringBuilder();
        stepTools.forEach((step, tool) -> {
            if (steps.length() > 0) steps.append(",");
            steps.append("""
                {"step": %d, "tool_to_use": "%s", "tool_parameters": {"instruction": "Step %d"}}
                """.formatted(step, tool, step));
        });
        return """
            {"thought_process": "Multi-step plan", "execution_plan": [%s]}
            """.formatted(steps);
    }

    // State factory
    public static OverAllState createStateWith(Map<String, Object> values) {
        OverAllState state = new OverAllState();
        values.keySet().forEach(key ->
            state.registerKeyAndStrategy(key, new ReplaceStrategy()));
        state.updateState(values);
        return state;
    }
}
```

### Dependencies (already in POM)

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
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Phase 0: Cleanup & Infrastructure

### Goal
Remove all fake tests, restore deleted real tests, establish baseline.

### Files to DELETE (12 files, 41 fake methods)

| File | Reason |
|------|--------|
| `SqlGenerateNodeErrorTest.java` | All `assertTrue(true)` |
| `SqlGenerateNodeCornerCaseTest.java` | Data-only, never calls SqlGenerateNode |
| `SqlGenerateNodeAdditionalTest.java` | Duplicate of SqlGenerateNodeTest with incomplete state |
| `SqlExecuteNodeErrorTest.java` | Manually throws exceptions in assertThrows |
| `SqlExecuteNodeCornerCaseTest.java` | Data-only, never calls SqlExecuteNode |
| `PythonExecuteNodeErrorTest.java` | Manually throws exceptions in assertThrows |
| `PythonExecuteNodeCornerCaseTest.java` | Data-only, never calls PythonExecuteNode |
| `PythonAnalyzeNodeErrorTest.java` | All `assertTrue(true)` |
| `EvidenceRecallNodeErrorTest.java` | All `assertTrue(true)` |
| `PlannerNodeErrorTest.java` | All `assertTrue(true)` |
| `TextToSqlWorkflowIntegrationTest.java` | All `assertTrue(true)` stubs |
| `PythonWorkflowIntegrationTest.java` | `assertTrue(true)` stub, package mismatch |

### Files to KEEP (5 files)

| File | Methods | Action |
|------|---------|--------|
| `StateUtilSimpleTest.java` | 7 | Expand in Phase 1 |
| `SqlGenerateNodeTest.java` | 4 | Expand in Phase 3 |
| `SqlExecuteNodeTest.java` | 2 | Expand in Phase 3 |
| `PlanExecutorNodeTest.java` | 16 | Keep as-is (excellent quality) |
| `IntentRecognitionNodeTest.java` | 7 | Fix compilation issues in Phase 3 |

### Files to RESTORE from main (~20 files)

| File | Original Methods | Adaptation Needed |
|------|-----------------|-------------------|
| `MySqlContainerConfiguration.java` (x2) | N/A | Verify Testcontainers version |
| `TestApplication.java` | N/A | Verify Spring Boot config |
| `HumanFeedbackDispatcherTest.java` | 4 | None expected |
| `TableRelationDispatcherTest.java` | 10 | None expected |
| `AgentDatasourceMapperTest.java` | 8 | Check schema changes |
| `DatasourceMapperTest.java` | 8 | Check schema changes |
| `MappersTest.java` | 5 | Check schema changes |
| `UserPromptConfigMapperTest.java` | 10 | Check schema changes |
| `HumanFeedbackNodeTest.java` | 6 | None expected |
| `H2AccessorIntegrationTest.java` | 6 | Verify H2 schema |
| `H2DatabaseIntegrationTest.java` | 4 | Verify H2 schema |
| `CodeTestConstant.java` | N/A | None |
| `DockerCodePoolExecutorServiceTest.java` | ~5 | Check CodePoolExecutorService changes |
| `LocalCodePoolExecutorServiceTest.java` | ~5 | Check concurrent bug fix |
| `RrfFusionStrategyTest.java` | ~8 | None expected |
| `AbstractHybridRetrievalStrategyTest.java` | ~5 | None expected |
| `DateTimeUtilTest.java` | ~15 | None expected |
| `MarkdownParserUtilTest.java` | ~20 | None expected |

### New Files in Phase 0

| File | Purpose |
|------|---------|
| `TestFixtures.java` | Shared test data factory |

### Verification

```bash
./mvnw test                         # All tests pass
./mvnw test jacoco:report           # Coverage baseline established
```

### Target: ~25-30% coverage baseline

---

## Phase 1: Utilities (Foundation Layer)

### Goal
Test all utility classes that are dependencies of upper layers.

### 1.1 StateUtilTest.java (expand from StateUtilSimpleTest)

**Constructor:** N/A (static utility)

**Existing tests (7):** getStringValue, hasValue, getObjectValue, default values, missing key exception

**New tests to add:**

```java
// List operations
@Test void getListValue_withValidList_returnsTypedList()
@Test void getListValue_withEmptyList_returnsEmptyList()
@Test void getListValue_whenKeyMissing_returnsEmptyList()

// Document list
@Test void getDocumentList_withDocuments_returnsDocumentList()
@Test void getDocumentList_whenEmpty_returnsEmptyList()

// Canonical query extraction
@Test void getCanonicalQuery_withQueryEnhanceDTO_returnsQuery()
@Test void getCanonicalQuery_withRawString_returnsFallback()

// Deserialization edge cases
@Test void deserializeIfNeeded_withHashMap_convertsToTargetType()
@Test void deserializeIfNeeded_withAlreadyCorrectType_returnsAsIs()
@Test void deserializeIfNeeded_withNull_returnsNull()
```

### 1.2 PlanProcessUtilTest.java (NEW)

**Constructor:** N/A (static utility, uses `BeanOutputConverter<Plan>`)

```java
// Plan parsing
@Test void getPlan_withValidJson_returnsParsedPlan()
@Test void getPlan_withMalformedJson_throwsException()
@Test void getPlan_withEmptyPlannerOutput_throwsException()

// Step navigation
@Test void getCurrentExecutionStep_step1_returnsFirstStep()
@Test void getCurrentExecutionStep_lastStep_returnsLastStep()
@Test void getCurrentExecutionStep_beyondRange_throwsException()
@Test void getCurrentStepNumber_validState_returnsStepNumber()

// Step instruction
@Test void getCurrentExecutionStepInstruction_validStep_returnsInstruction()
@Test void getCurrentExecutionStepInstruction_nullParameters_returnsEmpty()

// Step results
@Test void addStepResult_newResult_addsToMap()
@Test void addStepResult_existingResults_appendsToMap()
@Test void addStepResult_nullExisting_createsNewMap()
```

### 1.3 JsonParseUtilTest.java (NEW)

**Constructor:** `JsonParseUtil(LlmService llmService)`

```java
// Successful parsing
@Test void tryConvertToObject_validJson_returnsObject()
@Test void tryConvertToObject_withTypeReference_returnsTypedObject()

// Retry with LLM fix
@Test void tryConvertToObject_malformedJson_retriesWithLlmFix()
@Test void tryConvertToObject_maxRetriesExceeded_throwsException()

// Think tag removal
@Test void removeThinkTags_withThinkBlock_removesBlock()
@Test void removeThinkTags_noThinkBlock_returnsOriginal()
@Test void removeThinkTags_multipleBlocks_removesAll()

// Edge cases
@Test void tryConvertToObject_emptyString_throwsException()
@Test void tryConvertToObject_nullInput_throwsException()
@Test void tryConvertToObject_jsonWithExtraFields_ignoresExtras()
```

### 1.4 SqlUtilTest.java (NEW)

**Constructor:** N/A (Lombok `@UtilityClass`)

```java
// MySQL dialect
@Test void buildSelectSql_mysql_appendsLimit()

// PostgreSQL dialect
@Test void buildSelectSql_postgresql_appendsLimit()

// Oracle dialect
@Test void buildSelectSql_oracle_appendsFetchFirst()

// SQL Server dialect
@Test void buildSelectSql_sqlserver_prependsTop()

// Edge cases
@Test void buildSelectSql_unknownDialect_defaultsToLimit()
@Test void buildSelectSql_zeroLimit_noLimitClause()
@Test void buildSelectSql_withSpecialColumnNames_escapesCorrectly()
```

### 1.5 DateTimeUtilTest.java (restore from main + expand)

**Constructor:** N/A (static utility)

Restore ~15 tests from main. Add:

```java
// Chinese relative date expressions
@Test void buildDateExpressions_今天_resolvesToToday()
@Test void buildDateExpressions_昨天_resolvesToYesterday()
@Test void buildDateExpressions_上周_resolvesToLastWeek()
@Test void buildDateExpressions_本月_resolvesToCurrentMonth()
@Test void buildDateExpressions_上个季度_resolvesToLastQuarter()
@Test void buildDateExpressions_今年_resolvesToCurrentYear()

// Year calculations
@Test void getYearEx_去年_returnsLastYear()
@Test void getYearEx_前年_returnsTwoYearsAgo()

// Quarter calculations
@Test void getQuarterEx_本季度_returnsCurrentQuarterRange()
@Test void getQuarterEx_上个季度_returnsLastQuarterRange()

// Week calculations
@Test void getWeekDayEx_上周一_returnsCorrectDate()
@Test void getWeekEx_本周_returnsCurrentWeekRange()

// Edge cases
@Test void buildDateExpressions_emptyList_returnsEmptyList()
@Test void buildDateExpressions_unknownExpression_returnsOriginal()
@Test void buildDateTimeComment_multipleExpressions_formatsCorrectly()
```

### 1.6 FluxUtilTest.java (NEW)

**Constructor:** N/A (static utility)

```java
// Cascade flux
@Test void cascadeFlux_withPreMiddleEnd_chainsCorrectly()
@Test void cascadeFlux_withEmptyOrigin_returnsPreEndOnly()
@Test void cascadeFlux_errorInOrigin_propagatesError()

// Streaming generator
@Test void createStreamingGenerator_validResponse_streamsContent()
@Test void createStreamingGenerator_emptyResponse_completesEmpty()
@Test void createStreamingGeneratorWithMessages_addsStartEndMessages()

// Token extraction
@Test void extractAndAccumulateTokens_validResponse_accumulatesContent()
```

### 1.7 Other Utilities (NEW)

**MarkdownParserUtilTest.java** (restore from main ~20 tests)

**MdTableGeneratorUtilTest.java:**
```java
@Test void generateTable_withData_returnsMarkdownTable()
@Test void generateTable_emptyData_returnsEmptyString()
@Test void generateTable_specialCharacters_escapesCorrectly()
```

**ApiKeyUtilTest.java:**
```java
@Test void generateApiKey_returnsValidFormat()
@Test void generateApiKey_uniqueEachCall()
@Test void validateApiKey_validKey_returnsTrue()
@Test void validateApiKey_invalidKey_returnsFalse()
```

### Phase 1 Target: ~40% overall coverage

---

## Phase 2: Dispatchers (Routing Layer)

### Goal
Test all 11 dispatchers. These are pure routing logic -- the fastest path to high-value coverage.

### 2.1 FeasibilityAssessmentDispatcherTest.java (NEW)

**Constructor:** none

```java
@Test void apply_dataAnalysisOutput_routesToPlannerNode()
@Test void apply_nonDataAnalysisOutput_routesToEnd()
@Test void apply_nullOutput_routesToEnd()
@Test void apply_emptyOutput_routesToEnd()
```

### 2.2 HumanFeedbackDispatcherTest.java (restore from main + expand)

**Constructor:** none

Restore 4 tests from main:
```java
@Test void apply_waitForFeedback_routesToEnd()
@Test void apply_normalNodeName_routesToSpecifiedNode()
@Test void apply_missingKey_routesToEnd()
@Test void apply_rejectionNode_routesToPlannerNode()
```

### 2.3 IntentRecognitionDispatcherTest.java (NEW)

**Constructor:** none

```java
@Test void apply_dataQueryIntent_routesToEvidenceRecall()
@Test void apply_chatIntent_routesToEnd()
@Test void apply_nullIntent_routesToEnd()
@Test void apply_deserializationFailure_routesToEnd()
```

### 2.4 PlanExecutorDispatcherTest.java (NEW)

**Constructor:** none (uses constant `MAX_REPAIR_ATTEMPTS = 2`)

```java
@Test void apply_validationPassed_routesToPlanNextNode()
@Test void apply_validationFailed_underMaxRepair_routesToPlanner()
@Test void apply_validationFailed_atMaxRepair_routesToEnd()
@Test void apply_missingValidationStatus_routesToEnd()
@Test void apply_nullPlanNextNode_routesToEnd()
```

### 2.5 PythonExecutorDispatcherTest.java (NEW)

**Constructor:** `PythonExecutorDispatcher(CodeExecutorProperties codeExecutorProperties)`

```java
@Test void apply_successTrue_routesToPythonAnalyze()
@Test void apply_fallbackMode_routesToEnd()
@Test void apply_failedUnderMaxRetry_routesToPythonGenerate()
@Test void apply_failedAtMaxRetry_routesToEnd()
@Test void apply_missingSuccessFlag_defaultsToRetry()
```

### 2.6 QueryEnhanceDispatcherTest.java (NEW)

**Constructor:** none

```java
@Test void apply_validCanonicalQuery_routesToSchemaRecall()
@Test void apply_emptyCanonicalQuery_routesToEnd()
@Test void apply_nullExpandedQueries_routesToSchemaRecall()
@Test void apply_deserializationFailure_routesToEnd()
```

### 2.7 SQLExecutorDispatcherTest.java (NEW)

**Constructor:** none

```java
@Test void apply_retryReasonPresent_routesToSqlGenerate()
@Test void apply_noRetryReason_routesToPlanExecutor()
@Test void apply_nullRetryDto_routesToPlanExecutor()
```

### 2.8 SchemaRecallDispatcherTest.java (NEW)

**Constructor:** none

```java
@Test void apply_tablesExist_routesToTableRelation()
@Test void apply_noTables_routesToEnd()
@Test void apply_emptyTableList_routesToEnd()
```

### 2.9 SemanticConsistenceDispatcherTest.java (NEW)

**Constructor:** none

```java
@Test void apply_consistencyPassed_routesToSqlExecute()
@Test void apply_consistencyFailed_routesToSqlGenerate()
@Test void apply_nullResult_routesToSqlGenerate()
```

### 2.10 SqlGenerateDispatcherTest.java (NEW)

**Constructor:** `SqlGenerateDispatcher(DataAgentProperties properties)`

```java
@Test void apply_validSqlOutput_routesToSemanticConsistency()
@Test void apply_emptySqlOutput_underMaxRetry_routesToSqlGenerate()
@Test void apply_emptySqlOutput_atMaxRetry_routesToEnd()
@Test void apply_endMarkerInSql_routesToEnd()
@Test void apply_nullSqlOutput_routesToSqlGenerate()
```

### 2.11 TableRelationDispatcherTest.java (restore from main + expand)

**Constructor:** none (uses constant `MAX_RETRY_COUNT = 3`)

Restore 10 tests from main:
```java
@Test void apply_noError_routesToFeasibilityAssessment()
@Test void apply_retryableError_underMaxRetry_routesToTableRelation()
@Test void apply_retryableError_atMaxRetry_routesToEnd()
@Test void apply_nonRetryableError_routesToFeasibilityAssessment()
@Test void apply_nullRetryCount_defaultsToZero()
@Test void apply_nullErrorOutput_routesToFeasibilityAssessment()
@Test void isRetryableError_connectionError_returnsTrue()
@Test void isRetryableError_syntaxError_returnsFalse()
@Test void apply_boundaryRetryCount_routesCorrectly()
@Test void apply_emptyErrorOutput_routesToFeasibilityAssessment()
```

### Phase 2 Target: ~50% overall coverage

---

## Phase 3: Workflow Nodes (Business Logic Layer)

### Goal
Add tests for all 16 workflow nodes. Replace all stubs with real tests.

### 3a: New Node Tests (7 nodes with zero coverage)

#### 3a.1 QueryEnhanceNodeTest.java (NEW)

**Constructor:** `QueryEnhanceNode(LlmService llmService, JsonParseUtil jsonParseUtil)`

```java
// Happy paths
@Test void apply_validQuery_returnsEnhancedQuery()
@Test void apply_withMultiTurnContext_includesContextInPrompt()

// Error paths
@Test void apply_llmFailure_throwsException()
@Test void apply_emptyInput_throwsValidationError()
@Test void apply_jsonParseFailure_returnsFallbackQuery()

// Edge cases
@Test void apply_veryLongQuery_truncatesAppropriately()
```

#### 3a.2 SchemaRecallNodeTest.java (NEW)

**Constructor:** `SchemaRecallNode(SchemaService schemaService, AgentDatasourceMapper agentDatasourceMapper)`

```java
// Happy paths
@Test void apply_withDatasource_returnsTableSchema()
@Test void apply_multipleDatasources_mergesSchemas()

// Error paths
@Test void apply_noDatasource_returnsEmptySchema()
@Test void apply_schemaServiceFailure_throwsException()

// Edge cases
@Test void apply_emptyTableDocuments_returnsEmptyTableList()
@Test void extractTableName_validDocuments_extractsNames()
@Test void extractTableName_emptyDocuments_returnsEmptyList()
```

#### 3a.3 TableRelationNodeTest.java (NEW)

**Constructor:** `TableRelationNode(SchemaService, Nl2SqlService, SemanticModelService, DatabaseUtil, DatasourceService, AgentDatasourceService)` -- 6 dependencies

```java
// Happy paths
@Test void apply_validSchema_buildsSchemaWithRelations()
@Test void apply_withLogicalForeignKeys_includesRelations()
@Test void apply_withSchemaSelection_filtersSchema()

// Error paths
@Test void apply_databaseConfigMissing_setsErrorOutput()
@Test void apply_schemaSelectionFailure_setsRetryCount()

// Edge cases
@Test void apply_noForeignKeys_returnsSchemaWithoutRelations()
@Test void apply_emptyColumnDocuments_returnsMinimalSchema()
@Test void getLogicalForeignKeys_noRelations_returnsEmptyList()
```

#### 3a.4 FeasibilityAssessmentNodeTest.java (NEW)

**Constructor:** `FeasibilityAssessmentNode(LlmService llmService)`

```java
// Happy paths
@Test void apply_feasibleQuery_returnsFeasibleAssessment()
@Test void apply_infeasibleQuery_returnsInfeasibleAssessment()

// Error paths
@Test void apply_llmFailure_throwsException()
@Test void apply_emptySchema_returnsInfeasible()

// Edge cases
@Test void apply_ambiguousQuery_returnsAssessmentWithCaveats()
```

#### 3a.5 HumanFeedbackNodeTest.java (restore from main + expand)

**Constructor:** none

Restore 6 tests from main:
```java
@Test void apply_approvedFeedback_setsApprovalState()
@Test void apply_rejectedFeedback_setsRejectionState()
@Test void apply_waitForFeedback_setsWaitState()
@Test void apply_emptyFeedback_setsDefaultState()
@Test void apply_maxRepairExceeded_routesToEnd()
@Test void apply_repairCountIncrement_incrementsCorrectly()
```

#### 3a.6 SemanticConsistencyNodeTest.java (NEW)

**Constructor:** `SemanticConsistencyNode(Nl2SqlService nl2SqlService)`

```java
// Happy paths
@Test void apply_validSql_passesValidation()
@Test void apply_invalidSql_failsValidation()

// Error paths
@Test void apply_nl2SqlServiceFailure_throwsException()
@Test void apply_missingStateKeys_throwsException()

// Edge cases
@Test void apply_containsNotPassKeyword_failsValidation()  // "不通过"
@Test void buildValidationResult_passed_returnsPassResult()
@Test void buildValidationResult_failed_returnsFailWithReason()
```

#### 3a.7 ReportGeneratorNodeTest.java (NEW)

**Constructor:** `ReportGeneratorNode(LlmService llmService, UserPromptService promptConfigService)`

```java
// Happy paths
@Test void apply_validData_generatesHtmlReport()
@Test void apply_withCustomPrompt_usesCustomTemplate()

// Error paths
@Test void apply_llmFailure_throwsException()
@Test void apply_missingExecutionResults_generatesPartialReport()

// Edge cases
@Test void apply_emptyResults_generatesMinimalReport()
@Test void getCurrentExecutionStep_validPlan_returnsStep()
@Test void buildUserRequirementsAndPlan_validInput_formatsCorrectly()
@Test void buildAnalysisStepsAndData_multipleSteps_formatsAll()
```

### 3b: Expand/Replace Existing Node Tests

#### 3b.1 SqlGenerateNodeTest.java (expand)

**Existing (4 tests):** simple SELECT, WHERE, JOIN, max retry

**Add:**

```java
// Error paths
@Test void apply_nl2SqlServiceFailure_throwsException()
@Test void apply_missingSchemaState_throwsException()
@Test void apply_nullPlannerOutput_throwsException()

// Retry scenarios
@Test void apply_withRegenerateReason_includesReasonInPrompt()
@Test void apply_semanticFailureReason_includesValidationFeedback()

// Corner cases
@Test void apply_emptyEvidenceString_generatesWithoutEvidence()
@Test void apply_sqlTrimRemovesMarkdown_returnsCleanSql()
```

#### 3b.2 SqlExecuteNodeTest.java (expand)

**Existing (2 tests):** valid SELECT, multi-column

**Add:**

```java
// Error paths
@Test void apply_sqlExecutionError_setsRetryReason()
@Test void apply_connectionFailure_throwsException()
@Test void apply_missingAgentId_throwsException()

// Edge cases
@Test void apply_emptyResultSet_returnsEmptyResults()
@Test void apply_nullResultSet_handlesGracefully()
@Test void apply_withChartConfigEnabled_generatesChartConfig()
@Test void apply_chartConfigLlmFailure_continuesWithoutChart()

// Large results
@Test void apply_largeResultSet_truncatesAppropriately()
```

#### 3b.3 PlannerNodeTest.java (NEW -- replaces stub)

**Constructor:** `PlannerNode(LlmService llmService)`

```java
// Happy paths
@Test void apply_validQuery_generatesPlan()
@Test void apply_multiStepQuery_generatesMultiStepPlan()
@Test void apply_nl2SqlOnly_returnsFixedPlan()

// Error paths
@Test void apply_llmFailure_throwsException()
@Test void apply_invalidPlanJson_throwsParseException()

// Retry/repair
@Test void apply_withValidationError_regeneratesPlan()
@Test void apply_withSemanticModel_includesInPrompt()

// Edge cases
@Test void apply_emptyEvidence_generatesWithoutEvidence()
@Test void handleNl2SqlOnly_returnsStandardPlan()
```

#### 3b.4 EvidenceRecallNodeTest.java (NEW -- replaces stub)

**Constructor:** `EvidenceRecallNode(LlmService, AgentVectorStoreService, JsonParseUtil, AgentKnowledgeMapper)`

```java
// Happy paths
@Test void apply_validQuery_returnsEvidence()
@Test void apply_multipleDocTypes_formatsAllEvidence()
@Test void apply_faqKnowledge_formatsAsFaq()
@Test void apply_documentKnowledge_formatsAsDocument()

// Error paths
@Test void apply_llmRewriteFailure_usesOriginalQuery()
@Test void apply_vectorStoreFailure_returnsEmptyEvidence()
@Test void apply_jsonParseFailure_usesOriginalQuery()

// Edge cases
@Test void apply_noEvidenceFound_returnsEmptyString()
@Test void apply_withMultiTurnContext_includesContext()
```

#### 3b.5 PythonGenerateNodeTest.java (NEW -- replaces stub)

**Constructor:** `PythonGenerateNode(CodeExecutorProperties, LlmService)`

```java
// Happy paths
@Test void apply_validRequest_generatesPythonCode()
@Test void apply_withSchemaContext_includesSchemaInPrompt()

// Error paths
@Test void apply_llmFailure_throwsException()

// Retry
@Test void apply_previousFailure_includesErrorInPrompt()
@Test void apply_maxRetriesReached_throwsException()

// Edge cases
@Test void apply_stripsPythonMarkers_returnsCleanCode()
@Test void apply_withSqlResults_includesResultsInPrompt()
```

#### 3b.6 PythonExecuteNodeTest.java (NEW -- replaces stub)

**Constructor:** `PythonExecuteNode(CodePoolExecutorService, JsonParseUtil, CodeExecutorProperties)`

```java
// Happy paths
@Test void apply_validCode_executesSuccessfully()
@Test void apply_jsonOutput_parsesCorrectly()

// Error paths
@Test void apply_executionError_setsFailureState()
@Test void apply_maxRetryExceeded_setsFallbackMode()
@Test void apply_jsonParseFailure_usesRawOutput()

// Edge cases
@Test void apply_unicodeInOutput_handlesCorrectly()
@Test void apply_emptyOutput_setsEmptyResult()
@Test void apply_withSqlResultsAsCSV_passesCorrectly()
```

#### 3b.7 PythonAnalyzeNodeTest.java (NEW -- replaces stub)

**Constructor:** `PythonAnalyzeNode(LlmService llmService)`

```java
// Happy paths
@Test void apply_validOutput_returnsAnalysis()

// Error paths
@Test void apply_llmFailure_throwsException()

// Fallback mode
@Test void apply_fallbackMode_returnsStaticMessage()

// Edge cases
@Test void apply_emptyPythonOutput_returnsMinimalAnalysis()
@Test void apply_updatesExecutionResults_correctly()
```

#### 3b.8 IntentRecognitionNodeTest.java (FIX)

Fix compilation issues:
- Add missing imports for `@Mock`, `when()`, `any()`, `verify()`
- Verify `TextType` and `ChatResponseUtil` references resolve
- Run and verify all 7 tests pass

### Phase 3 Target: ~65% overall coverage

---

## Phase 4: Core Services

### Goal
Test critical service implementations.

### 4.1 Nl2SqlServiceImplTest.java (NEW)

**Constructor:** `Nl2SqlServiceImpl(LlmService llmService, JsonParseUtil jsonParseUtil)`

```java
// SQL generation
@Test void generateSql_validDto_returnsSqlFlux()
@Test void generateSql_withSemanticModel_includesModelInPrompt()
@Test void generateSql_emptySchema_throwsException()

// Semantic consistency
@Test void performSemanticConsistency_validDto_returnsValidationFlux()
@Test void performSemanticConsistency_missingFields_throwsException()

// Fine select (schema refinement)
@Test void fineSelect_validSchema_returnsRefinedFlux()
@Test void fineSelect_withMissingAdvice_includesAdvice()

// SQL trimming
@Test void sqlTrim_withMarkdownMarkers_returnsCleanSql()
@Test void sqlTrim_cleanSql_returnsUnchanged()
```

### 4.2 AgentVectorStoreServiceImplTest.java (NEW)

**Constructor:** `AgentVectorStoreServiceImpl(VectorStore, Optional<HybridRetrievalStrategy>, DataAgentProperties, DynamicFilterService)`

```java
// Search
@Test void search_validRequest_returnsDocuments()
@Test void search_withHybridStrategy_usesHybridSearch()
@Test void search_noHybridStrategy_usesVectorOnly()

// Add/delete
@Test void addDocuments_validDocuments_addsToStore()
@Test void deleteDocumentsByVectorType_validType_deletesMatching()
@Test void deleteDocumentsByMetadata_validMetadata_deletesMatching()

// Get documents
@Test void getDocumentsForAgent_validRequest_returnsFiltered()
@Test void getDocumentsForAgent_withTopK_limitsResults()
@Test void hasDocuments_agentWithDocs_returnsTrue()
@Test void hasDocuments_agentWithoutDocs_returnsFalse()
```

### 4.3 DynamicFilterServiceTest.java (NEW)

**Constructor:** `DynamicFilterService(AgentKnowledgeMapper, BusinessKnowledgeMapper)`

```java
// Filter building
@Test void buildDynamicFilter_validAgentId_returnsFilterExpression()
@Test void combineWithAnd_multipleConditions_returnsAndExpression()
@Test void combineWithAnd_singleCondition_returnsSingleExpression()
@Test void combineWithAnd_emptyList_returnsNull()

// String escaping
@Test void escapeStringLiteral_withQuotes_escapesCorrectly()
@Test void escapeStringLiteral_noSpecialChars_returnsOriginal()

// Table/column filters
@Test void buildFilterExpressionForSearchTables_validInput_returnsFilter()
@Test void buildFilterExpressionForSearchColumns_validInput_returnsFilter()
@Test void buildFilterExpressionString_validMap_returnsExpression()
```

### 4.4 MultiTurnContextManagerTest.java (NEW)

**Constructor:** `MultiTurnContextManager(DataAgentProperties properties)`

```java
// Turn management
@Test void beginTurn_newThread_createsPending()
@Test void finishTurn_completesAndStoresInHistory()
@Test void discardPending_removesPendingTurn()
@Test void restartLastTurn_restartsFromLastTurn()

// Context building
@Test void buildContext_withHistory_formatsCorrectly()
@Test void buildContext_noHistory_returnsEmpty()
@Test void buildContext_exceedsMaxTurns_truncatesOldest()

// Concurrency
@Test void multipleThreads_isolateContextCorrectly()

// Chunk appending
@Test void appendPlannerChunk_validThread_appendsToBuilder()
@Test void appendPlannerChunk_noPending_ignores()
```

### 4.5 GraphServiceImplTest.java (NEW)

**Constructor:** `GraphServiceImpl(StateGraph, ExecutorService, MultiTurnContextManager, LangfuseService)`

```java
// NL2SQL
@Test void nl2sql_validQuery_returnsResult()

// Stream processing
@Test void graphStreamProcess_validRequest_streamsResponses()
@Test void graphStreamProcess_newProcess_initializesState()
@Test void graphStreamProcess_humanFeedback_resumesExecution()

// Error handling
@Test void graphStreamProcess_graphError_emitsErrorEvent()
@Test void stopStreamProcessing_validThread_stopsProcessing()

// State management
@Test void handleNodeOutput_validOutput_emitsToSink()
```

### 4.6 LlmService Impls (NEW)

**BlockLlmServiceTest.java:**
```java
@Test void callUser_validPrompt_returnsChatResponse()
@Test void callSystem_validPrompt_returnsSystemResponse()
@Test void toStringFlux_validFlux_extractsText()
```

**StreamLlmServiceTest.java:**
```java
@Test void callUser_validPrompt_returnsStreamFlux()
@Test void callSystem_validPrompt_returnsStreamFlux()
@Test void call_validPrompts_returnsStreamFlux()
```

### 4.7 Restore from main

- `DockerCodePoolExecutorServiceTest.java` (~5 tests)
- `LocalCodePoolExecutorServiceTest.java` (~5 tests)
- `RrfFusionStrategyTest.java` (~8 tests)
- `AbstractHybridRetrievalStrategyTest.java` (~5 tests)

### Phase 4 Target: ~75% overall coverage

---

## Phase 5: Controllers & Connectors

### Goal
Add tests for key REST endpoints and database connectors.

### 5.1 Controllers (WebFluxTest)

**GraphControllerTest.java:**
```java
@WebFluxTest(GraphController.class)
// Uses @MockBean for GraphService
@Test void streamSearch_validRequest_returnsSSEStream()
@Test void streamSearch_invalidRequest_returns400()
@Test void streamSearch_humanFeedback_resumesStream()
```

**AgentControllerTest.java:**
```java
@WebFluxTest(AgentController.class)
@Test void createAgent_validRequest_returnsCreatedAgent()
@Test void getAgent_existingId_returnsAgent()
@Test void deleteAgent_existingId_returns200()
@Test void publishAgent_validAgent_updatesStatus()
@Test void generateApiKey_validAgent_returnsKey()
```

**DatasourceControllerTest.java:**
```java
@WebFluxTest(DatasourceController.class)
@Test void createDatasource_validRequest_returnsCreated()
@Test void testConnection_validDatasource_returnsSuccess()
@Test void getTableColumns_validTable_returnsColumns()
@Test void getLogicalRelations_validDatasource_returnsRelations()
```

**ChatControllerTest.java:**
```java
@WebFluxTest(ChatController.class)
@Test void createSession_validRequest_returnsSession()
@Test void getMessages_validSession_returnsMessages()
@Test void deleteSession_existingId_returns200()
@Test void downloadReport_validSession_returnsHtml()
```

**ModelConfigControllerTest.java:**
```java
@WebFluxTest(ModelConfigController.class)
@Test void createModelConfig_validRequest_returnsCreated()
@Test void activateModel_validId_activatesModel()
@Test void testConnection_validConfig_returnsSuccess()
@Test void checkReady_allConfigured_returnsReady()
```

### 5.2 Connectors

**H2AccessorIntegrationTest.java** (restore from main + expand):
```java
// Restore 6 tests: showTables, fetchTables, showColumns, showForeignKeys, sampleColumn, executeSql
```

**SqlExecutorTest.java (NEW):**
```java
@Test void executeSql_validSelect_returnsResults()
@Test void executeSql_invalidSql_throwsException()
@Test void executeSql_parameterizedQuery_executesCorrectly()
```

**AbstractAccessorTest.java (NEW):**
```java
@Test void executeSqlAndReturnObject_validQuery_returnsResultSetBO()
@Test void executeSqlAndReturnObject_emptyResult_returnsEmptyBO()
```

### 5.3 Restore Mapper Tests from main

- `AgentDatasourceMapperTest.java` (8 tests)
- `DatasourceMapperTest.java` (8 tests)
- `MappersTest.java` (5 tests)
- `UserPromptConfigMapperTest.java` (10 tests)

### Phase 5 Target: ~78% overall coverage

---

## Phase 6: Integration & Finalization

### Goal
End-to-end workflow tests with Testcontainers MySQL and mocked LLM.

### 6.1 TextToSqlWorkflowIntegrationTest.java (NEW -- replaces stub)

```java
@SpringBootTest
@Testcontainers
class TextToSqlWorkflowIntegrationTest {

    @Test void endToEnd_simpleQuery_generatesAndExecutesSql()
    @Test void endToEnd_queryWithJoin_handlesMultiTableQuery()
    @Test void endToEnd_semanticValidationFails_retriesSqlGeneration()
    @Test void endToEnd_sqlExecutionError_retriesSqlGeneration()
    @Test void endToEnd_infeasibleQuery_returnsInfeasibleResult()
}
```

### 6.2 PythonWorkflowIntegrationTest.java (NEW -- replaces stub)

```java
@SpringBootTest
@Testcontainers
class PythonWorkflowIntegrationTest {

    @Test void endToEnd_dataAnalysis_generatesPythonAndAnalyzes()
    @Test void endToEnd_pythonExecutionError_retriesGeneration()
    @Test void endToEnd_maxRetryExceeded_fallbackMode()
}
```

### 6.3 HumanFeedbackIntegrationTest.java (NEW)

```java
@SpringBootTest
@Testcontainers
class HumanFeedbackIntegrationTest {

    @Test void endToEnd_humanApproval_continuesExecution()
    @Test void endToEnd_humanRejection_regeneratesPlan()
}
```

### Phase 6 Target: 80%+ overall coverage

---

## Coverage Goals

| Component | Target | Phase 0 | Phase 1 | Phase 2 | Phase 3 | Phase 4 | Phase 5 | Phase 6 |
|-----------|--------|---------|---------|---------|---------|---------|---------|---------|
| Utilities | 80% | -- | 80% | -- | -- | -- | -- | -- |
| Dispatchers | 80% | 10% | -- | 80% | -- | -- | -- | -- |
| SQL Nodes | 80% | 20% | -- | -- | 80% | -- | -- | 85% |
| Python Nodes | 80% | 0% | -- | -- | 80% | -- | -- | 85% |
| Orchestration | 80% | 15% | -- | -- | 80% | -- | -- | 85% |
| RAG | 80% | 0% | -- | -- | 80% | -- | -- | 85% |
| Core Services | 80% | 0% | -- | -- | -- | 80% | -- | 85% |
| Controllers | 60% | 0% | -- | -- | -- | -- | 60% | 65% |
| Connectors | 60% | 0% | -- | -- | -- | -- | 60% | 65% |
| **Overall** | **80%** | **~25%** | **~40%** | **~50%** | **~65%** | **~75%** | **~78%** | **80%+** |

---

## Test Execution & CI

### Running Tests

```bash
# All tests
./mvnw test

# Specific phase by package
./mvnw test -Dtest="com.alibaba.cloud.ai.dataagent.util.*"           # Phase 1
./mvnw test -Dtest="com.alibaba.cloud.ai.dataagent.workflow.dispatcher.*"  # Phase 2
./mvnw test -Dtest="com.alibaba.cloud.ai.dataagent.workflow.node.*"  # Phase 3

# Coverage report
./mvnw test jacoco:report
# Report at: target/site/jacoco/index.html

# Coverage check (80% threshold)
./mvnw test jacoco:check
```

### JaCoCo Configuration

Already configured in POM:
- Line coverage threshold: 80% at PACKAGE level
- Excludes: `**/excel/**/*.class`
- Report generated to `target/site/jacoco/`

### CI Integration (GitHub Actions)

Existing `build-and-test.yml` pipeline:
```yaml
- name: Format check
  run: ./mvnw spring-javaformat:validate
- name: Checkstyle
  run: ./mvnw checkstyle:check
- name: Test
  run: ./mvnw test
- name: Build
  run: ./mvnw clean package -DskipTests=true
```

Add after tests:
```yaml
- name: Coverage check
  run: ./mvnw jacoco:check -Djacoco.minimum.coverage=0.80
```

---

## Estimated Test Counts

| Phase | New Tests | Restored Tests | Total Methods |
|-------|-----------|---------------|---------------|
| Phase 0 | ~5 (fixtures) | ~120 | ~125 |
| Phase 1 | ~65 | ~35 (DateTimeUtil, MarkdownParser) | ~100 |
| Phase 2 | ~45 | ~14 (HumanFeedback, TableRelation) | ~59 |
| Phase 3 | ~85 | ~6 (HumanFeedbackNode) | ~91 |
| Phase 4 | ~55 | ~23 (CodePool, Hybrid) | ~78 |
| Phase 5 | ~30 | ~37 (H2, Mappers) | ~67 |
| Phase 6 | ~10 | 0 | ~10 |
| **Total** | **~295** | **~235** | **~530** |

---

## Success Criteria

1. All tests pass consistently in CI
2. Overall line coverage reaches 80%+ (JaCoCo verified)
3. Zero fake/stub tests remain (`assertTrue(true)` is banned)
4. All 11 dispatchers have routing tests
5. All 16 workflow nodes have happy-path + error-path tests
6. Critical services (GraphService, Nl2SqlService, VectorStore) have 80%+ coverage
7. Integration tests cover Text-to-SQL and Python workflows end-to-end
8. Test execution time < 5 minutes for unit tests, < 10 minutes total

---

## Implementation Order Summary

| Phase | Focus | Deliverable | Coverage Target |
|-------|-------|------------|-----------------|
| 0 | Cleanup & Restore | Delete fakes, restore real tests, add fixtures | ~25% |
| 1 | Utilities | 10 utility test classes | ~40% |
| 2 | Dispatchers | 11 dispatcher test classes | ~50% |
| 3 | Workflow Nodes | 16 node test classes (7 new, 9 expand/replace) | ~65% |
| 4 | Core Services | 7+ service test classes | ~75% |
| 5 | Controllers & Connectors | 5 controller + 3 connector test classes | ~78% |
| 6 | Integration | 3 integration test classes | 80%+ |

---

## Appendix: Node Dependency Quick Reference

| Node | Constructor Dependencies | Mock Count |
|------|------------------------|------------|
| IntentRecognitionNode | LlmService | 1 |
| EvidenceRecallNode | LlmService, AgentVectorStoreService, JsonParseUtil, AgentKnowledgeMapper | 4 |
| QueryEnhanceNode | LlmService, JsonParseUtil | 2 |
| SchemaRecallNode | SchemaService, AgentDatasourceMapper | 2 |
| TableRelationNode | SchemaService, Nl2SqlService, SemanticModelService, DatabaseUtil, DatasourceService, AgentDatasourceService | 6 |
| FeasibilityAssessmentNode | LlmService | 1 |
| PlannerNode | LlmService | 1 |
| PlanExecutorNode | (none) | 0 |
| HumanFeedbackNode | (none) | 0 |
| SqlGenerateNode | Nl2SqlService, DataAgentProperties | 2 |
| SemanticConsistencyNode | Nl2SqlService | 1 |
| SqlExecuteNode | DatabaseUtil, Nl2SqlService, LlmService, DataAgentProperties, JsonParseUtil | 5 |
| PythonGenerateNode | CodeExecutorProperties, LlmService | 2 |
| PythonExecuteNode | CodePoolExecutorService, JsonParseUtil, CodeExecutorProperties | 3 |
| PythonAnalyzeNode | LlmService | 1 |
| ReportGeneratorNode | LlmService, UserPromptService | 2 |

## Appendix: Dispatcher Quick Reference

| Dispatcher | Constructor Dependencies | Routing Decisions |
|-----------|------------------------|-------------------|
| FeasibilityAssessmentDispatcher | (none) | PLANNER / END |
| HumanFeedbackDispatcher | (none) | WAIT→END / node name |
| IntentRecognitionDispatcher | (none) | EVIDENCE_RECALL / END |
| PlanExecutorDispatcher | (none) | PLAN_NEXT / PLANNER / END |
| PythonExecutorDispatcher | CodeExecutorProperties | ANALYZE / GENERATE / END |
| QueryEnhanceDispatcher | (none) | SCHEMA_RECALL / END |
| SQLExecutorDispatcher | (none) | SQL_GENERATE / PLAN_EXECUTOR |
| SchemaRecallDispatcher | (none) | TABLE_RELATION / END |
| SemanticConsistenceDispatcher | (none) | SQL_EXECUTE / SQL_GENERATE |
| SqlGenerateDispatcher | DataAgentProperties | SEMANTIC / SQL_GENERATE / END |
| TableRelationDispatcher | (none) | TABLE_RELATION / FEASIBILITY / END |
