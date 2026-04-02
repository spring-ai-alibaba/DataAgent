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
package com.alibaba.cloud.ai.dataagent.workflow.node;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanExecutorNodeTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private PlanExecutorNode planExecutorNode;

	@BeforeEach
	void setUp() {
		planExecutorNode = new PlanExecutorNode();
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(PLANNER_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLAN_NEXT_NODE, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLAN_VALIDATION_STATUS, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLAN_VALIDATION_ERROR, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLAN_CURRENT_STEP, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLAN_REPAIR_COUNT, new ReplaceStrategy());
		state.registerKeyAndStrategy(HUMAN_REVIEW_ENABLED, new ReplaceStrategy());
		state.registerKeyAndStrategy(IS_ONLY_NL2SQL, new ReplaceStrategy());
		return state;
	}

	private String planToJson(Plan plan) {
		try {
			return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(plan);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to serialize plan", e);
		}
	}

	private Plan createValidPlan() {
		ExecutionStep step1 = new ExecutionStep();
		step1.setStep(1);
		step1.setToolToUse(SQL_GENERATE_NODE);
		ExecutionStep.ToolParameters params1 = new ExecutionStep.ToolParameters();
		params1.setInstruction("查询所有用户");
		step1.setToolParameters(params1);

		ExecutionStep step2 = new ExecutionStep();
		step2.setStep(2);
		step2.setToolToUse(PYTHON_GENERATE_NODE);
		ExecutionStep.ToolParameters params2 = new ExecutionStep.ToolParameters();
		params2.setInstruction("分析数据");
		step2.setToolParameters(params2);

		Plan plan = new Plan();
		plan.setThoughtProcess("根据问题生成SQL");
		plan.setExecutionPlan(List.of(step1, step2));
		return plan;
	}

	// ==================== Phase 1: Happy Paths ====================

	@Test
	void validPlan_currentStep1_routesToSqlGenerateNode() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(createValidPlan()), PLAN_CURRENT_STEP, 1,
				IS_ONLY_NL2SQL, false));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertTrue((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertEquals(SQL_GENERATE_NODE, result.get(PLAN_NEXT_NODE));
	}

	@Test
	void validPlan_currentStep2_routesToPythonGenerateNode() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(createValidPlan()), PLAN_CURRENT_STEP, 2,
				IS_ONLY_NL2SQL, false));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertTrue((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertEquals(PYTHON_GENERATE_NODE, result.get(PLAN_NEXT_NODE));
	}

	@Test
	void validPlan_stepExceedsSteps_routesToReportGeneratorNode() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(createValidPlan()), PLAN_CURRENT_STEP, 3,
				IS_ONLY_NL2SQL, false));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertTrue((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertEquals(REPORT_GENERATOR_NODE, result.get(PLAN_NEXT_NODE));
	}

	@Test
	void nl2sqlMode_routesToSqlGenerateNodeForCurrentStep() throws Exception {
		OverAllState state = createTestState();

		ExecutionStep step = new ExecutionStep();
		step.setStep(1);
		step.setToolToUse(SQL_GENERATE_NODE);
		ExecutionStep.ToolParameters params = new ExecutionStep.ToolParameters();
		params.setInstruction("SQL生成");
		step.setToolParameters(params);
		Plan plan = new Plan();
		plan.setThoughtProcess("根据问题生成SQL");
		plan.setExecutionPlan(List.of(step));

		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(plan), PLAN_CURRENT_STEP, 1, IS_ONLY_NL2SQL, true));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertTrue((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertEquals(SQL_GENERATE_NODE, result.get(PLAN_NEXT_NODE));
	}

	// ==================== Phase 2: Error Paths ====================

	@Test
	void emptyExecutionPlan_returnsValidationError() throws Exception {
		OverAllState state = createTestState();
		Plan emptyPlan = new Plan();
		emptyPlan.setThoughtProcess("测试");
		emptyPlan.setExecutionPlan(new ArrayList<>());
		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(emptyPlan), PLAN_CURRENT_STEP, 1));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertFalse((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertTrue(result.containsKey(PLAN_VALIDATION_ERROR));
		assertTrue(((String) result.get(PLAN_VALIDATION_ERROR)).contains("no execution steps"));
	}

	@Test
	void missingToolParameters_returnsValidationError() throws Exception {
		OverAllState state = createTestState();
		ExecutionStep step = new ExecutionStep();
		step.setStep(1);
		step.setToolToUse(SQL_GENERATE_NODE);
		step.setToolParameters(null);
		Plan plan = new Plan();
		plan.setThoughtProcess("测试");
		plan.setExecutionPlan(List.of(step));
		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(plan), PLAN_CURRENT_STEP, 1));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertFalse((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertTrue(result.containsKey(PLAN_VALIDATION_ERROR));
		assertTrue(((String) result.get(PLAN_VALIDATION_ERROR)).contains("Tool parameters are missing"));
	}

	@Test
	void missingInstructionForSqlGenerateNode_returnsValidationError() throws Exception {
		OverAllState state = createTestState();
		ExecutionStep step = new ExecutionStep();
		step.setStep(1);
		step.setToolToUse(SQL_GENERATE_NODE);
		step.setToolParameters(new ExecutionStep.ToolParameters());
		Plan plan = new Plan();
		plan.setThoughtProcess("测试");
		plan.setExecutionPlan(List.of(step));
		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(plan), PLAN_CURRENT_STEP, 1));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertFalse((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertTrue(result.containsKey(PLAN_VALIDATION_ERROR));
		assertTrue(((String) result.get(PLAN_VALIDATION_ERROR)).contains("SQL generation node is missing description"));
	}

	@Test
	void missingInstructionForPythonGenerateNode_returnsValidationError() throws Exception {
		OverAllState state = createTestState();
		ExecutionStep step = new ExecutionStep();
		step.setStep(1);
		step.setToolToUse(PYTHON_GENERATE_NODE);
		step.setToolParameters(new ExecutionStep.ToolParameters());
		Plan plan = new Plan();
		plan.setThoughtProcess("测试");
		plan.setExecutionPlan(List.of(step));
		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(plan), PLAN_CURRENT_STEP, 1));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertFalse((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertTrue(result.containsKey(PLAN_VALIDATION_ERROR));
		assertTrue(
				((String) result.get(PLAN_VALIDATION_ERROR)).contains("Python generation node is missing instruction"));
	}

	@Test
	void unsupportedNodeType_returnsValidationError() throws Exception {
		OverAllState state = createTestState();

		// Build plan with unsupported node type directly using correct uppercase constant
		// This avoids BeanOutputConverter deserialization issues
		ExecutionStep step = new ExecutionStep();
		step.setStep(1);
		step.setToolToUse("unknown_node"); // Direct uppercase string
		ExecutionStep.ToolParameters params = new ExecutionStep.ToolParameters();
		params.setInstruction("测试");
		step.setToolParameters(params);
		Plan plan = new Plan();
		plan.setThoughtProcess("测试");
		plan.setExecutionPlan(List.of(step));

		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(plan), PLAN_CURRENT_STEP, 1));

		Map<String, Object> result = planExecutorNode.apply(state);

		// Validation should fail for unsupported node type
		assertFalse((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertTrue(result.containsKey(PLAN_VALIDATION_ERROR));
	}

	@Test
	void missingSummaryForReportNode_returnsValidationError() throws Exception {
		OverAllState state = createTestState();
		ExecutionStep step = new ExecutionStep();
		step.setStep(1);
		step.setToolToUse(REPORT_GENERATOR_NODE);
		step.setToolParameters(new ExecutionStep.ToolParameters());
		Plan plan = new Plan();
		plan.setThoughtProcess("测试");
		plan.setExecutionPlan(List.of(step));
		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(plan), PLAN_CURRENT_STEP, 1));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertFalse((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertTrue(result.containsKey(PLAN_VALIDATION_ERROR));
		assertTrue(((String) result.get(PLAN_VALIDATION_ERROR)).contains("missing summary_and_recommendations"));
	}

	@Test
	void validationFailure_incrementsRepairCount() throws Exception {
		OverAllState state = createTestState();
		Plan emptyPlan = new Plan();
		emptyPlan.setThoughtProcess("测试");
		emptyPlan.setExecutionPlan(new ArrayList<>());
		state.updateState(
				Map.of(PLANNER_NODE_OUTPUT, planToJson(emptyPlan), PLAN_CURRENT_STEP, 1, PLAN_REPAIR_COUNT, 2));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertFalse((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertEquals(3, result.get(PLAN_REPAIR_COUNT));
	}

	// ==================== Phase 3: Corner Cases ====================

	@Test
	void humanReviewEnabled_routesToHumanFeedbackNode() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(createValidPlan()), PLAN_CURRENT_STEP, 1,
				HUMAN_REVIEW_ENABLED, true));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertTrue((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertEquals(HUMAN_FEEDBACK_NODE, result.get(PLAN_NEXT_NODE));
	}

	@Test
	void planAlreadyCompleted_resetsStepTo1AndRoutesToReportGeneratorNode() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(createValidPlan()), PLAN_CURRENT_STEP, 3));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertTrue((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertEquals(REPORT_GENERATOR_NODE, result.get(PLAN_NEXT_NODE));
		assertEquals(1, result.get(PLAN_CURRENT_STEP));
	}

	@Test
	void planCompletedWithNl2sql_resetsTo1AndEndsWorkflow() throws Exception {
		OverAllState state = createTestState();

		// Build NL2SQL plan directly with correct uppercase constant values
		// This avoids BeanOutputConverter deserialization issues
		ExecutionStep step = new ExecutionStep();
		step.setStep(1);
		step.setToolToUse(SQL_GENERATE_NODE); // Use uppercase constant
		ExecutionStep.ToolParameters params = new ExecutionStep.ToolParameters();
		params.setInstruction("SQL生成");
		step.setToolParameters(params);
		Plan plan = new Plan();
		plan.setThoughtProcess("根据问题生成SQL");
		plan.setExecutionPlan(List.of(step));

		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(plan), PLAN_CURRENT_STEP, 3, IS_ONLY_NL2SQL, true));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertTrue((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertEquals(StateGraph.END, result.get(PLAN_NEXT_NODE));
		assertEquals(1, result.get(PLAN_CURRENT_STEP));
	}

	@Test
	void multipleValidationErrors_eachIncrementsRepairCount() throws Exception {
		OverAllState state = createTestState();
		Plan emptyPlan = new Plan();
		emptyPlan.setThoughtProcess("测试");
		emptyPlan.setExecutionPlan(new ArrayList<>());
		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(emptyPlan), PLAN_CURRENT_STEP, 1));

		// First validation error
		Map<String, Object> result1 = planExecutorNode.apply(state);
		assertEquals(1, result1.get(PLAN_REPAIR_COUNT));

		// Second validation error (simulate state carryover)
		state.updateState(Map.of(PLAN_REPAIR_COUNT, 1));
		Map<String, Object> result2 = planExecutorNode.apply(state);
		assertEquals(2, result2.get(PLAN_REPAIR_COUNT));

		// Third validation error
		state.updateState(Map.of(PLAN_REPAIR_COUNT, 2));
		Map<String, Object> result3 = planExecutorNode.apply(state);
		assertEquals(3, result3.get(PLAN_REPAIR_COUNT));
	}

	@Test
	void currentStepBoundary_handling_firstStep() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(createValidPlan()), PLAN_CURRENT_STEP, 1));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertTrue((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertEquals(SQL_GENERATE_NODE, result.get(PLAN_NEXT_NODE));
	}

	@Test
	void currentStepBoundary_handling_lastValidStep() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(createValidPlan()), PLAN_CURRENT_STEP, 2));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertTrue((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertEquals(PYTHON_GENERATE_NODE, result.get(PLAN_NEXT_NODE));
	}

	@Test
	void currentStepBoundary_handlingBeyondSteps() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planToJson(createValidPlan()), PLAN_CURRENT_STEP, 3));

		Map<String, Object> result = planExecutorNode.apply(state);

		assertTrue((Boolean) result.get(PLAN_VALIDATION_STATUS));
		assertEquals(REPORT_GENERATOR_NODE, result.get(PLAN_NEXT_NODE));
	}

}
