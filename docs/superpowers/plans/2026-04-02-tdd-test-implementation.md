# DataAgent TDD Test Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reach 80%+ backend test coverage through a bottom-up phased approach: cleanup fake tests, restore deleted real tests, then add new tests layer by layer.

**Architecture:** 7-phase bottom-up approach. Phase 0 cleans up 12 fake test files and restores ~20 real test files from main. Phases 1-6 add new tests layer by layer: utilities → dispatchers → nodes → services → controllers/connectors → integration. Each test file uses Mockito with LENIENT strictness and real `OverAllState` objects.

**Tech Stack:** JUnit 5, Mockito, reactor-test, Testcontainers (MySQL 8.0), JaCoCo, Spring WebFluxTest

---

## File Structure Overview

### Files to DELETE (Phase 0)
```
src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/sql/
  SqlGenerateNodeErrorTest.java          # assertTrue(true) stubs
  SqlGenerateNodeCornerCaseTest.java     # data-only, never calls prod code
  SqlGenerateNodeAdditionalTest.java     # duplicate of SqlGenerateNodeTest
  SqlExecuteNodeErrorTest.java           # manually throws exceptions
  SqlExecuteNodeCornerCaseTest.java      # data-only
src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/python/
  PythonExecuteNodeErrorTest.java        # manually throws exceptions
  PythonExecuteNodeCornerCaseTest.java   # data-only
  PythonAnalyzeNodeErrorTest.java        # assertTrue(true) stubs
src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/rag/
  EvidenceRecallNodeErrorTest.java       # assertTrue(true) stubs
src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/orchestration/
  PlannerNodeErrorTest.java              # assertTrue(true) stubs
src/test/java/com/alibaba/cloud/ai/dataagent/integration/
  TextToSqlWorkflowIntegrationTest.java  # assertTrue(true) stubs
  PythonWorkflowIntegrationTest.java     # assertTrue(true) stub + package mismatch
```

### Files to KEEP (Phase 0)
```
src/test/java/com/alibaba/cloud/ai/dataagent/util/StateUtilSimpleTest.java
src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/sql/SqlGenerateNodeTest.java
src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/sql/SqlExecuteNodeTest.java
src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/PlanExecutorNodeTest.java
src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/IntentRecognitionNodeTest.java
```

### Files to CREATE (Phases 0-6)
```
src/test/java/com/alibaba/cloud/ai/dataagent/
├── common/
│   └── TestFixtures.java
├── util/
│   ├── PlanProcessUtilTest.java
│   ├── SqlUtilTest.java
│   ├── ApiKeyUtilTest.java
│   └── ChatResponseUtilTest.java
├── workflow/dispatcher/
│   ├── FeasibilityAssessmentDispatcherTest.java
│   ├── HumanFeedbackDispatcherTest.java
│   ├── IntentRecognitionDispatcherTest.java
│   ├── PlanExecutorDispatcherTest.java
│   ├── PythonExecutorDispatcherTest.java
│   ├── QueryEnhanceDispatcherTest.java
│   ├── SQLExecutorDispatcherTest.java
│   ├── SchemaRecallDispatcherTest.java
│   ├── SemanticConsistenceDispatcherTest.java
│   ├── SqlGenerateDispatcherTest.java
│   └── TableRelationDispatcherTest.java
└── (later phases: node tests, service tests, controller tests, integration tests)
```

---

## Task 1: Delete Fake Test Files

**Files:**
- Delete: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/sql/SqlGenerateNodeErrorTest.java`
- Delete: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/sql/SqlGenerateNodeCornerCaseTest.java`
- Delete: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/sql/SqlGenerateNodeAdditionalTest.java`
- Delete: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/sql/SqlExecuteNodeErrorTest.java`
- Delete: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/sql/SqlExecuteNodeCornerCaseTest.java`
- Delete: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/python/PythonExecuteNodeErrorTest.java`
- Delete: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/python/PythonExecuteNodeCornerCaseTest.java`
- Delete: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/python/PythonAnalyzeNodeErrorTest.java`
- Delete: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/rag/EvidenceRecallNodeErrorTest.java`
- Delete: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/orchestration/PlannerNodeErrorTest.java`
- Delete: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/integration/TextToSqlWorkflowIntegrationTest.java`
- Delete: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/integration/PythonWorkflowIntegrationTest.java`

- [ ] **Step 1: Delete all 12 fake test files**

```bash
cd data-agent-management
rm -f src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/sql/SqlGenerateNodeErrorTest.java
rm -f src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/sql/SqlGenerateNodeCornerCaseTest.java
rm -f src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/sql/SqlGenerateNodeAdditionalTest.java
rm -f src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/sql/SqlExecuteNodeErrorTest.java
rm -f src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/sql/SqlExecuteNodeCornerCaseTest.java
rm -f src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/python/PythonExecuteNodeErrorTest.java
rm -f src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/python/PythonExecuteNodeCornerCaseTest.java
rm -f src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/python/PythonAnalyzeNodeErrorTest.java
rm -f src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/rag/EvidenceRecallNodeErrorTest.java
rm -f src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/orchestration/PlannerNodeErrorTest.java
rm -f src/test/java/com/alibaba/cloud/ai/dataagent/integration/TextToSqlWorkflowIntegrationTest.java
rm -f src/test/java/com/alibaba/cloud/ai/dataagent/integration/PythonWorkflowIntegrationTest.java
```

- [ ] **Step 2: Run remaining tests to verify nothing broke**

Run: `./mvnw test -pl data-agent-management`
Expected: All 5 remaining test files pass (StateUtilSimpleTest, SqlGenerateNodeTest, SqlExecuteNodeTest, PlanExecutorNodeTest, IntentRecognitionNodeTest)

- [ ] **Step 3: Commit**

```bash
git add -A data-agent-management/src/test/
git commit -m "chore: delete 12 fake test files (41 fake methods)

Remove assertTrue(true) stubs, data-only tests, and duplicate tests
that provided zero coverage of production code."
```

---

## Task 2: Fix IntentRecognitionNodeTest Compilation

**Files:**
- Modify: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/IntentRecognitionNodeTest.java`

- [ ] **Step 1: Add missing imports**

The file is missing imports for `@Mock`, `when()`, `any()`, `verify()`, and `TextType`. Add these imports after the existing import block:

```java
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
```

- [ ] **Step 2: Verify the test compiles and runs**

Run: `./mvnw test -Dtest=IntentRecognitionNodeTest -pl data-agent-management`
Expected: All 7 tests pass. If `IntentRecognitionNode` constructor doesn't accept `JsonParseUtil`, adjust the constructor call to match the actual signature.

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/node/IntentRecognitionNodeTest.java
git commit -m "fix: add missing imports to IntentRecognitionNodeTest"
```

---

## Task 3: Create TestFixtures

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/common/TestFixtures.java`

- [ ] **Step 1: Create the shared test fixtures class**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.common;

import com.alibaba.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;
import com.alibaba.cloud.ai.dataagent.dto.prompt.IntentRecognitionOutputDTO;
import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

public final class TestFixtures {

	private static final ObjectMapper OBJECT_MAPPER = JsonUtil.getObjectMapper();

	private TestFixtures() {
	}

	// --- State Factory ---

	public static OverAllState createStateWith(String... keys) {
		OverAllState state = new OverAllState();
		for (String key : keys) {
			state.registerKeyAndStrategy(key, new ReplaceStrategy());
		}
		return state;
	}

	public static OverAllState createStateWith(Map<String, Object> values) {
		OverAllState state = new OverAllState();
		values.keySet().forEach(key -> state.registerKeyAndStrategy(key, new ReplaceStrategy()));
		state.updateState(values);
		return state;
	}

	// --- Schema Fixtures (HashMap-based, matches how OverAllState deserializes) ---

	public static Map<String, Object> createSchemaMap(String name, String... tableNames) {
		List<Map<String, Object>> tables = new ArrayList<>();
		for (String tableName : tableNames) {
			Map<String, Object> table = new HashMap<>();
			table.put("name", tableName);
			table.put("description", tableName + " table");
			table.put("column", new ArrayList<>());
			table.put("primaryKeys", new ArrayList<>());
			tables.add(table);
		}
		Map<String, Object> schema = new HashMap<>();
		schema.put("name", name);
		schema.put("description", "Test schema");
		schema.put("tableCount", tables.size());
		schema.put("table", tables);
		schema.put("foreignKeys", new ArrayList<>());
		return schema;
	}

	// --- QueryEnhance Fixtures ---

	public static Map<String, Object> createQueryEnhanceMap(String query) {
		Map<String, Object> qe = new HashMap<>();
		qe.put("canonical_query", query);
		qe.put("expanded_queries", new ArrayList<>(List.of(query)));
		return qe;
	}

	public static QueryEnhanceOutputDTO createQueryEnhanceDTO(String query) {
		QueryEnhanceOutputDTO dto = new QueryEnhanceOutputDTO();
		dto.setCanonicalQuery(query);
		dto.setExpandedQueries(List.of(query));
		return dto;
	}

	// --- Intent Recognition Fixtures ---

	public static IntentRecognitionOutputDTO createIntentDTO(String classification) {
		IntentRecognitionOutputDTO dto = new IntentRecognitionOutputDTO();
		dto.setClassification(classification);
		return dto;
	}

	// --- Plan Fixtures ---

	public static Plan createPlan(String thoughtProcess, ExecutionStep... steps) {
		Plan plan = new Plan();
		plan.setThoughtProcess(thoughtProcess);
		plan.setExecutionPlan(List.of(steps));
		return plan;
	}

	public static ExecutionStep createSqlStep(int stepNum, String instruction) {
		ExecutionStep step = new ExecutionStep();
		step.setStep(stepNum);
		step.setToolToUse(SQL_GENERATE_NODE);
		ExecutionStep.ToolParameters params = new ExecutionStep.ToolParameters();
		params.setInstruction(instruction);
		step.setToolParameters(params);
		return step;
	}

	public static ExecutionStep createPythonStep(int stepNum, String instruction) {
		ExecutionStep step = new ExecutionStep();
		step.setStep(stepNum);
		step.setToolToUse(PYTHON_GENERATE_NODE);
		ExecutionStep.ToolParameters params = new ExecutionStep.ToolParameters();
		params.setInstruction(instruction);
		step.setToolParameters(params);
		return step;
	}

	public static ExecutionStep createReportStep(int stepNum, String summary) {
		ExecutionStep step = new ExecutionStep();
		step.setStep(stepNum);
		step.setToolToUse(REPORT_GENERATOR_NODE);
		ExecutionStep.ToolParameters params = new ExecutionStep.ToolParameters();
		params.setSummaryAndRecommendations(summary);
		step.setToolParameters(params);
		return step;
	}

	public static String planToJson(Plan plan) {
		try {
			return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(plan);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to serialize plan", e);
		}
	}

	// --- SqlRetryDto Fixtures ---

	public static SqlRetryDto createEmptyRetry() {
		return SqlRetryDto.empty();
	}

	public static SqlRetryDto createSemanticRetry(String reason) {
		return SqlRetryDto.semantic(reason);
	}

	public static SqlRetryDto createSqlExecuteRetry(String reason) {
		return SqlRetryDto.sqlExecute(reason);
	}

	// --- Common Plan JSON ---

	public static String createSingleSqlPlanJson() {
		return planToJson(createPlan("Generate SQL", createSqlStep(1, "Query all users")));
	}

	public static String createMultiStepPlanJson() {
		return planToJson(createPlan("Multi-step analysis",
				createSqlStep(1, "Query user data"),
				createPythonStep(2, "Analyze results"),
				createReportStep(3, "Generate report")));
	}

}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw compile -pl data-agent-management`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/common/TestFixtures.java
git commit -m "test: add shared TestFixtures for test data factories"
```

---

## Task 4: PlanProcessUtilTest

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/util/PlanProcessUtilTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanProcessUtilTest {

	@Test
	void getPlan_withValidJson_returnsParsedPlan() {
		String planJson = TestFixtures.createSingleSqlPlanJson();
		OverAllState state = TestFixtures.createStateWith(Map.of(
				PLANNER_NODE_OUTPUT, planJson,
				PLAN_CURRENT_STEP, 1));

		Plan plan = PlanProcessUtil.getPlan(state);

		assertNotNull(plan);
		assertNotNull(plan.getExecutionPlan());
		assertEquals(1, plan.getExecutionPlan().size());
		assertEquals(SQL_GENERATE_NODE, plan.getExecutionPlan().get(0).getToolToUse());
	}

	@Test
	void getPlan_withEmptyState_throwsException() {
		OverAllState state = TestFixtures.createStateWith(PLANNER_NODE_OUTPUT);

		assertThrows(IllegalStateException.class, () -> PlanProcessUtil.getPlan(state));
	}

	@Test
	void getCurrentExecutionStep_step1_returnsFirstStep() {
		String planJson = TestFixtures.createMultiStepPlanJson();
		OverAllState state = TestFixtures.createStateWith(Map.of(
				PLANNER_NODE_OUTPUT, planJson,
				PLAN_CURRENT_STEP, 1));

		ExecutionStep step = PlanProcessUtil.getCurrentExecutionStep(state);

		assertEquals(1, step.getStep());
		assertEquals(SQL_GENERATE_NODE, step.getToolToUse());
	}

	@Test
	void getCurrentExecutionStep_step2_returnsSecondStep() {
		String planJson = TestFixtures.createMultiStepPlanJson();
		OverAllState state = TestFixtures.createStateWith(Map.of(
				PLANNER_NODE_OUTPUT, planJson,
				PLAN_CURRENT_STEP, 2));

		ExecutionStep step = PlanProcessUtil.getCurrentExecutionStep(state);

		assertEquals(2, step.getStep());
		assertEquals(PYTHON_GENERATE_NODE, step.getToolToUse());
	}

	@Test
	void getCurrentExecutionStep_beyondRange_throwsException() {
		String planJson = TestFixtures.createSingleSqlPlanJson();
		OverAllState state = TestFixtures.createStateWith(Map.of(
				PLANNER_NODE_OUTPUT, planJson,
				PLAN_CURRENT_STEP, 5));

		assertThrows(IllegalStateException.class, () -> PlanProcessUtil.getCurrentExecutionStep(state));
	}

	@Test
	void getCurrentExecutionStep_emptyPlan_throwsException() {
		Plan emptyPlan = new Plan();
		emptyPlan.setThoughtProcess("test");
		emptyPlan.setExecutionPlan(java.util.List.of());

		assertThrows(IllegalStateException.class,
				() -> PlanProcessUtil.getCurrentExecutionStep(emptyPlan, 1));
	}

	@Test
	void getCurrentStepNumber_validState_returnsStepNumber() {
		OverAllState state = TestFixtures.createStateWith(Map.of(
				PLAN_CURRENT_STEP, 3));

		assertEquals(3, PlanProcessUtil.getCurrentStepNumber(state));
	}

	@Test
	void getCurrentStepNumber_missingKey_defaultsTo1() {
		OverAllState state = new OverAllState();

		assertEquals(1, PlanProcessUtil.getCurrentStepNumber(state));
	}

	@Test
	void getCurrentExecutionStepInstruction_validStep_returnsInstruction() {
		String planJson = TestFixtures.createSingleSqlPlanJson();
		OverAllState state = TestFixtures.createStateWith(Map.of(
				PLANNER_NODE_OUTPUT, planJson,
				PLAN_CURRENT_STEP, 1));

		String instruction = PlanProcessUtil.getCurrentExecutionStepInstruction(state);

		assertEquals("Query all users", instruction);
	}

	@Test
	void addStepResult_newResult_addsToMap() {
		Map<String, String> existing = new HashMap<>();

		Map<String, String> updated = PlanProcessUtil.addStepResult(existing, 1, "result data");

		assertEquals(1, updated.size());
		assertEquals("result data", updated.get("step_1"));
	}

	@Test
	void addStepResult_existingResults_appendsToMap() {
		Map<String, String> existing = new HashMap<>();
		existing.put("step_1", "first result");

		Map<String, String> updated = PlanProcessUtil.addStepResult(existing, 2, "second result");

		assertEquals(2, updated.size());
		assertEquals("first result", updated.get("step_1"));
		assertEquals("second result", updated.get("step_2"));
	}

	@Test
	void addStepResult_doesNotMutateOriginal() {
		Map<String, String> existing = new HashMap<>();
		existing.put("step_1", "first result");

		PlanProcessUtil.addStepResult(existing, 2, "second result");

		assertEquals(1, existing.size());
	}

}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=PlanProcessUtilTest -pl data-agent-management`
Expected: All 12 tests PASS

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/util/PlanProcessUtilTest.java
git commit -m "test: add PlanProcessUtilTest (12 tests)"
```

---

## Task 5: SqlUtilTest

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/util/SqlUtilTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlUtilTest {

	@Test
	void buildSelectSql_mysql_appendsLimit() {
		String sql = SqlUtil.buildSelectSql("mysql", "users", "id, name", 10);
		assertEquals("SELECT id, name FROM users LIMIT 10", sql);
	}

	@Test
	void buildSelectSql_postgresql_appendsLimit() {
		String sql = SqlUtil.buildSelectSql("postgresql", "users", "*", 5);
		assertEquals("SELECT * FROM users LIMIT 5", sql);
	}

	@Test
	void buildSelectSql_h2_appendsLimit() {
		String sql = SqlUtil.buildSelectSql("h2", "users", "*", 100);
		assertEquals("SELECT * FROM users LIMIT 100", sql);
	}

	@Test
	void buildSelectSql_sqlserver_prependsTop() {
		String sql = SqlUtil.buildSelectSql("sqlserver", "users", "id, name", 10);
		assertEquals("SELECT TOP 10 id, name FROM users", sql);
	}

	@Test
	void buildSelectSql_oracle_appendsFetchFirst() {
		String sql = SqlUtil.buildSelectSql("oracle", "users", "id, name", 10);
		assertEquals("SELECT id, name FROM users FETCH FIRST 10 ROWS ONLY", sql);
	}

	@Test
	void buildSelectSql_nullColumnNames_defaultsToStar() {
		String sql = SqlUtil.buildSelectSql("mysql", "users", null, 10);
		assertEquals("SELECT * FROM users LIMIT 10", sql);
	}

	@Test
	void buildSelectSql_emptyColumnNames_defaultsToStar() {
		String sql = SqlUtil.buildSelectSql("mysql", "users", "", 10);
		assertEquals("SELECT * FROM users LIMIT 10", sql);
	}

	@Test
	void buildSelectSql_nullTableName_throwsException() {
		assertThrows(IllegalArgumentException.class,
				() -> SqlUtil.buildSelectSql("mysql", null, "*", 10));
	}

	@Test
	void buildSelectSql_emptyTableName_throwsException() {
		assertThrows(IllegalArgumentException.class,
				() -> SqlUtil.buildSelectSql("mysql", "", "*", 10));
	}

}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=SqlUtilTest -pl data-agent-management`
Expected: All 9 tests PASS

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/util/SqlUtilTest.java
git commit -m "test: add SqlUtilTest (9 tests, all DB dialects)"
```

---

## Task 6: ApiKeyUtilTest

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/util/ApiKeyUtilTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyUtilTest {

	@Test
	void generate_returnsKeyWithPrefix() {
		String key = ApiKeyUtil.generate();
		assertTrue(key.startsWith("sk-"));
	}

	@Test
	void generate_returnsCorrectLength() {
		String key = ApiKeyUtil.generate();
		// "sk-" (3 chars) + 32 chars = 35
		assertEquals(35, key.length());
	}

	@Test
	void generate_uniqueEachCall() {
		String key1 = ApiKeyUtil.generate();
		String key2 = ApiKeyUtil.generate();
		assertNotEquals(key1, key2);
	}

	@Test
	void generate_containsOnlyValidChars() {
		String key = ApiKeyUtil.generate();
		String body = key.substring(3); // Remove "sk-" prefix
		assertTrue(body.matches("[a-zA-Z0-9]+"));
	}

	@Test
	void mask_validKey_masksCorrectly() {
		String key = "sk-abcdefghijklmnopqrstuvwxyz123456";
		String masked = ApiKeyUtil.mask(key);
		assertEquals("****3456", masked);
	}

	@Test
	void mask_shortKey_returnsFourStars() {
		String masked = ApiKeyUtil.mask("sk-abc");
		assertEquals("****", masked);
	}

	@Test
	void mask_nullKey_returnsFourStars() {
		String masked = ApiKeyUtil.mask(null);
		assertEquals("****", masked);
	}

}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=ApiKeyUtilTest -pl data-agent-management`
Expected: All 7 tests PASS

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/util/ApiKeyUtilTest.java
git commit -m "test: add ApiKeyUtilTest (7 tests)"
```

---

## Task 7: ChatResponseUtilTest

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/util/ChatResponseUtilTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.util;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;

import static org.junit.jupiter.api.Assertions.*;

class ChatResponseUtilTest {

	@Test
	void createResponse_addsNewline() {
		ChatResponse response = ChatResponseUtil.createResponse("hello");
		String text = ChatResponseUtil.getText(response);
		assertEquals("hello\n", text);
	}

	@Test
	void createPureResponse_noNewline() {
		ChatResponse response = ChatResponseUtil.createPureResponse("hello");
		String text = ChatResponseUtil.getText(response);
		assertEquals("hello", text);
	}

	@Test
	void getText_nullResult_returnsEmptyString() {
		ChatResponse response = new ChatResponse(java.util.List.of());
		String text = ChatResponseUtil.getText(response);
		assertEquals("", text);
	}

	@Test
	void getText_validResponse_returnsText() {
		ChatResponse response = ChatResponseUtil.createPureResponse("test message");
		assertEquals("test message", ChatResponseUtil.getText(response));
	}

}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=ChatResponseUtilTest -pl data-agent-management`
Expected: All 4 tests PASS

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/util/ChatResponseUtilTest.java
git commit -m "test: add ChatResponseUtilTest (4 tests)"
```

---

## Task 8: FeasibilityAssessmentDispatcherTest

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/FeasibilityAssessmentDispatcherTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FeasibilityAssessmentDispatcherTest {

	private FeasibilityAssessmentDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new FeasibilityAssessmentDispatcher();
	}

	@Test
	void apply_dataAnalysisOutput_routesToPlannerNode() throws Exception {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(
				FEASIBILITY_ASSESSMENT_NODE_OUTPUT, "【需求类型】：《数据分析》\n用户需要查询PV数据"));

		assertEquals(PLANNER_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_nonDataAnalysisOutput_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(
				FEASIBILITY_ASSESSMENT_NODE_OUTPUT, "【需求类型】：《闲聊》"));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_missingOutput_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_emptyOutput_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, ""));

		assertEquals(END, dispatcher.apply(state));
	}

}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=FeasibilityAssessmentDispatcherTest -pl data-agent-management`
Expected: All 4 tests PASS

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/FeasibilityAssessmentDispatcherTest.java
git commit -m "test: add FeasibilityAssessmentDispatcherTest (4 tests)"
```

---

## Task 9: HumanFeedbackDispatcherTest

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/HumanFeedbackDispatcherTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HumanFeedbackDispatcherTest {

	private HumanFeedbackDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new HumanFeedbackDispatcher();
	}

	@Test
	void apply_waitForFeedback_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		state.updateState(Map.of("human_next_node", "WAIT_FOR_FEEDBACK"));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_planExecutorNode_routesToPlanExecutor() throws Exception {
		OverAllState state = new OverAllState();
		state.updateState(Map.of("human_next_node", PLAN_EXECUTOR_NODE));

		assertEquals(PLAN_EXECUTOR_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_plannerNode_routesToPlanner() throws Exception {
		OverAllState state = new OverAllState();
		state.updateState(Map.of("human_next_node", PLANNER_NODE));

		assertEquals(PLANNER_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_missingKey_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();

		assertEquals(END, dispatcher.apply(state));
	}

}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=HumanFeedbackDispatcherTest -pl data-agent-management`
Expected: All 4 tests PASS

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/HumanFeedbackDispatcherTest.java
git commit -m "test: add HumanFeedbackDispatcherTest (4 tests)"
```

---

## Task 10: IntentRecognitionDispatcherTest

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/IntentRecognitionDispatcherTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.dto.prompt.IntentRecognitionOutputDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentRecognitionDispatcherTest {

	private IntentRecognitionDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new IntentRecognitionDispatcher();
	}

	@Test
	void apply_dataAnalysisIntent_routesToEvidenceRecall() throws Exception {
		OverAllState state = new OverAllState();
		IntentRecognitionOutputDTO dto = TestFixtures.createIntentDTO("《可能的数据分析请求》");
		state.updateState(Map.of(INTENT_RECOGNITION_NODE_OUTPUT, dto));

		assertEquals(EVIDENCE_RECALL_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_chatIntent_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		IntentRecognitionOutputDTO dto = TestFixtures.createIntentDTO("《闲聊或无关指令》");
		state.updateState(Map.of(INTENT_RECOGNITION_NODE_OUTPUT, dto));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_nullClassification_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		IntentRecognitionOutputDTO dto = new IntentRecognitionOutputDTO();
		dto.setClassification(null);
		state.updateState(Map.of(INTENT_RECOGNITION_NODE_OUTPUT, dto));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_emptyClassification_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		IntentRecognitionOutputDTO dto = TestFixtures.createIntentDTO("   ");
		state.updateState(Map.of(INTENT_RECOGNITION_NODE_OUTPUT, dto));

		assertEquals(END, dispatcher.apply(state));
	}

}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=IntentRecognitionDispatcherTest -pl data-agent-management`
Expected: All 4 tests PASS

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/IntentRecognitionDispatcherTest.java
git commit -m "test: add IntentRecognitionDispatcherTest (4 tests)"
```

---

## Task 11: PlanExecutorDispatcherTest

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/PlanExecutorDispatcherTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanExecutorDispatcherTest {

	private PlanExecutorDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new PlanExecutorDispatcher();
	}

	@Test
	void apply_validationPassed_routesToNextNode() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(
				PLAN_VALIDATION_STATUS, true,
				PLAN_NEXT_NODE, SQL_GENERATE_NODE));

		assertEquals(SQL_GENERATE_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_validationPassed_endNode_routesToEnd() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(
				PLAN_VALIDATION_STATUS, true,
				PLAN_NEXT_NODE, "END"));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_validationFailed_underMaxRepair_routesToPlanner() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(
				PLAN_VALIDATION_STATUS, false,
				PLAN_REPAIR_COUNT, 1));

		assertEquals(PLANNER_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_validationFailed_atMaxRepair_routesToEnd() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(
				PLAN_VALIDATION_STATUS, false,
				PLAN_REPAIR_COUNT, 3));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_missingValidationStatus_defaultsFalse_routesToPlanner() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(
				PLAN_REPAIR_COUNT, 0));

		assertEquals(PLANNER_NODE, dispatcher.apply(state));
	}

}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=PlanExecutorDispatcherTest -pl data-agent-management`
Expected: All 5 tests PASS

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/PlanExecutorDispatcherTest.java
git commit -m "test: add PlanExecutorDispatcherTest (5 tests)"
```

---

## Task 12: SemanticConsistenceDispatcherTest

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/SemanticConsistenceDispatcherTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SemanticConsistenceDispatcherTest {

	private SemanticConsistenceDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new SemanticConsistenceDispatcher();
	}

	@Test
	void apply_consistencyPassed_routesToSqlExecute() {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, true));

		assertEquals(SQL_EXECUTE_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_consistencyFailed_routesToSqlGenerate() {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, false));

		assertEquals(SQL_GENERATE_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_missingOutput_defaultsFalse_routesToSqlGenerate() {
		OverAllState state = new OverAllState();

		assertEquals(SQL_GENERATE_NODE, dispatcher.apply(state));
	}

}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=SemanticConsistenceDispatcherTest -pl data-agent-management`
Expected: All 3 tests PASS

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/SemanticConsistenceDispatcherTest.java
git commit -m "test: add SemanticConsistenceDispatcherTest (3 tests)"
```

---

## Task 13: SQLExecutorDispatcherTest

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/SQLExecutorDispatcherTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SQLExecutorDispatcherTest {

	private SQLExecutorDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new SQLExecutorDispatcher();
	}

	@Test
	void apply_sqlExecuteFailed_routesToSqlGenerate() {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(SQL_REGENERATE_REASON, SqlRetryDto.sqlExecute("syntax error")));

		assertEquals(SQL_GENERATE_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_noRetryReason_routesToPlanExecutor() {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(SQL_REGENERATE_REASON, SqlRetryDto.empty()));

		assertEquals(PLAN_EXECUTOR_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_semanticFail_notSqlExecuteFail_routesToPlanExecutor() {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(SQL_REGENERATE_REASON, SqlRetryDto.semantic("semantic issue")));

		assertEquals(PLAN_EXECUTOR_NODE, dispatcher.apply(state));
	}

}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=SQLExecutorDispatcherTest -pl data-agent-management`
Expected: All 3 tests PASS

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/SQLExecutorDispatcherTest.java
git commit -m "test: add SQLExecutorDispatcherTest (3 tests)"
```

---

## Task 14: SchemaRecallDispatcherTest

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/SchemaRecallDispatcherTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaRecallDispatcherTest {

	private SchemaRecallDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new SchemaRecallDispatcher();
	}

	@Test
	void apply_tablesExist_routesToTableRelation() throws Exception {
		OverAllState state = new OverAllState();
		List<Document> docs = List.of(new Document("users table"));
		state.updateState(Map.of(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, docs));

		assertEquals(TABLE_RELATION_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_emptyTableList_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, new ArrayList<>()));

		assertEquals(END, dispatcher.apply(state));
	}

}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=SchemaRecallDispatcherTest -pl data-agent-management`
Expected: All 2 tests PASS

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/SchemaRecallDispatcherTest.java
git commit -m "test: add SchemaRecallDispatcherTest (2 tests)"
```

---

## Task 15: QueryEnhanceDispatcherTest

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/QueryEnhanceDispatcherTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryEnhanceDispatcherTest {

	private QueryEnhanceDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new QueryEnhanceDispatcher();
	}

	@Test
	void apply_validQuery_routesToSchemaRecall() throws Exception {
		OverAllState state = new OverAllState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询用户数据");
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto));

		assertEquals(SCHEMA_RECALL_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_emptyCanonicalQuery_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		QueryEnhanceOutputDTO dto = new QueryEnhanceOutputDTO();
		dto.setCanonicalQuery("");
		dto.setExpandedQueries(List.of("query"));
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_emptyExpandedQueries_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		QueryEnhanceOutputDTO dto = new QueryEnhanceOutputDTO();
		dto.setCanonicalQuery("valid query");
		dto.setExpandedQueries(new ArrayList<>());
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_nullOutput_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();

		// StateUtil.getObjectValue will throw if key is missing, which causes null
		// The dispatcher catches this via null check
		assertEquals(END, dispatcher.apply(state));
	}

}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=QueryEnhanceDispatcherTest -pl data-agent-management`
Expected: All 4 tests PASS. If `apply_nullOutput_routesToEnd` fails because `StateUtil.getObjectValue` throws instead of returning null, the test should be adjusted to expect the exception instead.

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/QueryEnhanceDispatcherTest.java
git commit -m "test: add QueryEnhanceDispatcherTest (4 tests)"
```

---

## Task 16: SqlGenerateDispatcherTest

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/SqlGenerateDispatcherTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqlGenerateDispatcherTest {

	@Mock
	private DataAgentProperties properties;

	private SqlGenerateDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new SqlGenerateDispatcher(properties);
	}

	@Test
	void apply_validSqlOutput_routesToSemanticConsistency() {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(SQL_GENERATE_OUTPUT, "SELECT * FROM users"));

		assertEquals(SEMANTIC_CONSISTENCY_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_endMarkerInSql_routesToEnd() {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(SQL_GENERATE_OUTPUT, END));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_emptySqlOutput_underMaxRetry_routesToSqlGenerate() {
		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		OverAllState state = new OverAllState();
		state.updateState(Map.of(SQL_GENERATE_COUNT, 2));

		assertEquals(SQL_GENERATE_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_emptySqlOutput_atMaxRetry_routesToEnd() {
		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		OverAllState state = new OverAllState();
		state.updateState(Map.of(SQL_GENERATE_COUNT, 10));

		assertEquals(END, dispatcher.apply(state));
	}

}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=SqlGenerateDispatcherTest -pl data-agent-management`
Expected: All 4 tests PASS

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/SqlGenerateDispatcherTest.java
git commit -m "test: add SqlGenerateDispatcherTest (4 tests)"
```

---

## Task 17: TableRelationDispatcherTest

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/TableRelationDispatcherTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TableRelationDispatcherTest {

	private TableRelationDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new TableRelationDispatcher();
	}

	@Test
	void apply_noError_routesToFeasibilityAssessment() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(
				TABLE_RELATION_EXCEPTION_OUTPUT, "",
				TABLE_RELATION_RETRY_COUNT, 0,
				TABLE_RELATION_OUTPUT, "schema data"));

		assertEquals(FEASIBILITY_ASSESSMENT_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_retryableError_underMaxRetry_routesToTableRelation() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(
				TABLE_RELATION_EXCEPTION_OUTPUT, "RETRYABLE: connection timeout",
				TABLE_RELATION_RETRY_COUNT, 1));

		assertEquals(TABLE_RELATION_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_retryableError_atMaxRetry_routesToEnd() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(
				TABLE_RELATION_EXCEPTION_OUTPUT, "RETRYABLE: connection timeout",
				TABLE_RELATION_RETRY_COUNT, 3));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_nonRetryableError_routesToEnd() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(
				TABLE_RELATION_EXCEPTION_OUTPUT, "FATAL: schema not found",
				TABLE_RELATION_RETRY_COUNT, 0));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_noOutputNoError_routesToEnd() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(
				TABLE_RELATION_EXCEPTION_OUTPUT, "",
				TABLE_RELATION_RETRY_COUNT, 0));

		assertEquals(END, dispatcher.apply(state));
	}

}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=TableRelationDispatcherTest -pl data-agent-management`
Expected: All 5 tests PASS

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/TableRelationDispatcherTest.java
git commit -m "test: add TableRelationDispatcherTest (5 tests)"
```

---

## Task 18: PythonExecutorDispatcherTest

**Files:**
- Create: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/PythonExecutorDispatcherTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PythonExecutorDispatcherTest {

	@Mock
	private CodeExecutorProperties codeExecutorProperties;

	private PythonExecutorDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new PythonExecutorDispatcher(codeExecutorProperties);
	}

	@Test
	void apply_successTrue_routesToPythonAnalyze() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(
				PYTHON_IS_SUCCESS, true,
				PYTHON_FALLBACK_MODE, false,
				PYTHON_EXECUTE_NODE_OUTPUT, "result",
				PYTHON_TRIES_COUNT, 1));

		assertEquals(PYTHON_ANALYZE_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_fallbackMode_routesToPythonAnalyze() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(
				PYTHON_FALLBACK_MODE, true,
				PYTHON_IS_SUCCESS, false,
				PYTHON_EXECUTE_NODE_OUTPUT, "error",
				PYTHON_TRIES_COUNT, 3));

		assertEquals(PYTHON_ANALYZE_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_failedUnderMaxRetry_routesToPythonGenerate() throws Exception {
		when(codeExecutorProperties.getPythonMaxTriesCount()).thenReturn(3);
		OverAllState state = TestFixtures.createStateWith(Map.of(
				PYTHON_IS_SUCCESS, false,
				PYTHON_FALLBACK_MODE, false,
				PYTHON_EXECUTE_NODE_OUTPUT, "RuntimeError",
				PYTHON_TRIES_COUNT, 1));

		assertEquals(PYTHON_GENERATE_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_failedAtMaxRetry_routesToEnd() throws Exception {
		when(codeExecutorProperties.getPythonMaxTriesCount()).thenReturn(3);
		OverAllState state = TestFixtures.createStateWith(Map.of(
				PYTHON_IS_SUCCESS, false,
				PYTHON_FALLBACK_MODE, false,
				PYTHON_EXECUTE_NODE_OUTPUT, "RuntimeError",
				PYTHON_TRIES_COUNT, 3));

		assertEquals(END, dispatcher.apply(state));
	}

}
```

- [ ] **Step 2: Run the test**

Run: `./mvnw test -Dtest=PythonExecutorDispatcherTest -pl data-agent-management`
Expected: All 4 tests PASS

- [ ] **Step 3: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/workflow/dispatcher/PythonExecutorDispatcherTest.java
git commit -m "test: add PythonExecutorDispatcherTest (4 tests)"
```

---

## Task 19: Run All Tests and Verify Coverage Baseline

**Files:**
- No file changes

- [ ] **Step 1: Run all tests**

Run: `./mvnw test -pl data-agent-management`
Expected: All tests PASS

- [ ] **Step 2: Generate coverage report**

Run: `./mvnw test jacoco:report -pl data-agent-management`
Expected: Report generated at `data-agent-management/target/site/jacoco/index.html`

- [ ] **Step 3: Check coverage**

Open the JaCoCo report and note the coverage baseline. Expected: significantly higher than the ~10-15% starting point after dispatcher tests are added.

- [ ] **Step 4: Commit (tag milestone)**

```bash
git add -A
git commit -m "test: Phase 0-2 complete - cleanup, utilities, all 11 dispatchers

Deleted 12 fake test files (41 fake methods).
Added TestFixtures, PlanProcessUtilTest, SqlUtilTest, ApiKeyUtilTest,
ChatResponseUtilTest, and all 11 dispatcher tests.
Total new test methods: ~70"
```

---

## Remaining Phases (Tasks 20+)

The remaining tasks follow the same pattern. Each task creates one test file with complete code, runs tests, and commits. The spec at `docs/superpowers/specs/2026-04-02-tdd-test-comprehensive-design-v2.md` provides the detailed test method list for each remaining phase:

### Phase 3: Workflow Nodes (Tasks 20-35)
- Task 20: QueryEnhanceNodeTest (6 tests)
- Task 21: FeasibilityAssessmentNodeTest (5 tests)
- Task 22: HumanFeedbackNodeTest (6 tests, restore from main)
- Task 23: SemanticConsistencyNodeTest (7 tests)
- Task 24: ReportGeneratorNodeTest (8 tests)
- Task 25: PlannerNodeTest (9 tests, replace stub)
- Task 26: EvidenceRecallNodeTest (9 tests, replace stub)
- Task 27: PythonGenerateNodeTest (7 tests)
- Task 28: PythonExecuteNodeTest (8 tests, replace stub)
- Task 29: PythonAnalyzeNodeTest (5 tests, replace stub)
- Task 30: Expand SqlGenerateNodeTest (+7 error/corner tests)
- Task 31: Expand SqlExecuteNodeTest (+9 error/corner tests)
- Task 32: SchemaRecallNodeTest (7 tests)
- Task 33: TableRelationNodeTest (8 tests)

For each node test, follow the patterns established in the existing `SqlGenerateNodeTest` and `PlanExecutorNodeTest`:
1. `@ExtendWith(MockitoExtension.class)` + `@MockitoSettings(strictness = Strictness.LENIENT)`
2. Mock all constructor dependencies with `@Mock`
3. Create node in `@BeforeEach` using constructor injection
4. Use `TestFixtures.createStateWith()` for state setup
5. Mock service responses with `when(...).thenReturn(Flux.just(...))`
6. Call `node.apply(state)` and assert on the returned `Map<String, Object>`

### Phase 4: Core Services (Tasks 34-40)
- Task 34: Nl2SqlServiceImplTest (9 tests)
- Task 35: DynamicFilterServiceTest (9 tests)
- Task 36: MultiTurnContextManagerTest (9 tests)
- Task 37: GraphServiceImplTest (7 tests)
- Task 38: BlockLlmServiceTest (3 tests)
- Task 39: StreamLlmServiceTest (3 tests)
- Task 40: Restore CodePoolExecutor + HybridRetrieval tests from main

### Phase 5: Controllers & Connectors (Tasks 41-48)
- Tasks 41-45: Controller tests using `@WebFluxTest`
- Tasks 46-48: Connector tests (H2 integration, SqlExecutor, AbstractAccessor)
- Restore mapper tests from main

### Phase 6: Integration (Tasks 49-51)
- Task 49: TextToSqlWorkflowIntegrationTest
- Task 50: PythonWorkflowIntegrationTest
- Task 51: HumanFeedbackIntegrationTest

Each remaining task follows the exact same structure: write complete test file → run → commit. Refer to the spec for the detailed test method signatures and mock patterns for each class.

---

## Verification

After all tasks complete:

```bash
# Run all tests
./mvnw test -pl data-agent-management

# Generate coverage report
./mvnw test jacoco:report -pl data-agent-management

# Check 80% threshold
./mvnw test jacoco:check -pl data-agent-management

# View report
open data-agent-management/target/site/jacoco/index.html
```

Expected: 80%+ line coverage, all tests pass, < 5 minutes unit test execution time.
