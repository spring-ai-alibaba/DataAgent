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
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanProcessUtilTest {

	@Test
	void getPlan_withValidJson_returnsParsedPlan() {
		String planJson = TestFixtures.createSingleSqlPlanJson();
		OverAllState state = TestFixtures.createStateWith(Map.of(PLANNER_NODE_OUTPUT, planJson, PLAN_CURRENT_STEP, 1));

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
		OverAllState state = TestFixtures.createStateWith(Map.of(PLANNER_NODE_OUTPUT, planJson, PLAN_CURRENT_STEP, 1));

		ExecutionStep step = PlanProcessUtil.getCurrentExecutionStep(state);

		assertEquals(1, step.getStep());
		assertEquals(SQL_GENERATE_NODE, step.getToolToUse());
	}

	@Test
	void getCurrentExecutionStep_step2_returnsSecondStep() {
		String planJson = TestFixtures.createMultiStepPlanJson();
		OverAllState state = TestFixtures.createStateWith(Map.of(PLANNER_NODE_OUTPUT, planJson, PLAN_CURRENT_STEP, 2));

		ExecutionStep step = PlanProcessUtil.getCurrentExecutionStep(state);

		assertEquals(2, step.getStep());
		assertEquals(PYTHON_GENERATE_NODE, step.getToolToUse());
	}

	@Test
	void getCurrentExecutionStep_beyondRange_throwsException() {
		String planJson = TestFixtures.createSingleSqlPlanJson();
		OverAllState state = TestFixtures.createStateWith(Map.of(PLANNER_NODE_OUTPUT, planJson, PLAN_CURRENT_STEP, 5));

		assertThrows(IllegalStateException.class, () -> PlanProcessUtil.getCurrentExecutionStep(state));
	}

	@Test
	void getCurrentExecutionStep_emptyPlan_throwsException() {
		Plan emptyPlan = new Plan();
		emptyPlan.setThoughtProcess("test");
		emptyPlan.setExecutionPlan(java.util.List.of());

		assertThrows(IllegalStateException.class, () -> PlanProcessUtil.getCurrentExecutionStep(emptyPlan, 1));
	}

	@Test
	void getCurrentStepNumber_validState_returnsStepNumber() {
		OverAllState state = TestFixtures.createStateWith(Map.of(PLAN_CURRENT_STEP, 3));

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
		OverAllState state = TestFixtures.createStateWith(Map.of(PLANNER_NODE_OUTPUT, planJson, PLAN_CURRENT_STEP, 1));

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
