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
package com.alibaba.cloud.ai.dataagent.workflow.node.orchestration;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.execute;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.NodeExecution;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.PlannerNode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class PlannerNodeTest {

	private static final String VALID_PLAN_JSON = """
			{
			    "thought_process": "分析用户查询，需要查询用户数据",
			    "execution_plan": [
			        {
			            "step": 1,
			            "tool_to_use": "SQL_GENERATE_NODE",
			            "tool_parameters": {
			                "instruction": "查询所有用户信息"
			            }
			        }
			    ]
			}
			""";

	private static final String MULTI_STEP_PLAN_JSON = """
			{
			    "thought_process": "需要先查SQL再用Python分析",
			    "execution_plan": [
			        {
			            "step": 1,
			            "tool_to_use": "SQL_GENERATE_NODE",
			            "tool_parameters": {
			                "instruction": "查询销售数据"
			            }
			        },
			        {
			            "step": 2,
			            "tool_to_use": "PYTHON_GENERATE_NODE",
			            "tool_parameters": {
			                "instruction": "分析销售趋势"
			            }
			        }
			    ]
			}
			""";

	private static final Map<String, Object> TEST_QUERY_ENHANCE;

	private static final Map<String, Object> TEST_SCHEMA;

	static {
		Map<String, Object> table = new HashMap<>();
		table.put("name", "users");
		table.put("description", "用户表");
		table.put("column", new ArrayList<>());
		table.put("primaryKeys", new ArrayList<>());

		Map<String, Object> schema = new HashMap<>();
		schema.put("name", "test_schema");
		schema.put("description", "测试schema");
		schema.put("tableCount", 1);
		schema.put("table", new ArrayList<>(List.of(table)));
		schema.put("foreignKeys", new ArrayList<>());

		Map<String, Object> queryEnhance = new HashMap<>();
		queryEnhance.put("canonical_query", "查询所有用户信息");
		queryEnhance.put("expanded_queries", new ArrayList<>(List.of("查询用户")));

		TEST_SCHEMA = schema;
		TEST_QUERY_ENHANCE = queryEnhance;
	}

	@Mock
	private LlmService llmService;

	private PlannerNode plannerNode;

	@BeforeEach
	void setUp() {
		plannerNode = new PlannerNode(llmService);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(PLANNER_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(QUERY_ENHANCE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(TABLE_RELATION_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(EVIDENCE, new ReplaceStrategy());
		state.registerKeyAndStrategy(GENEGRATED_SEMANTIC_MODEL_PROMPT, new ReplaceStrategy());
		state.registerKeyAndStrategy(IS_ONLY_NL2SQL, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLAN_VALIDATION_ERROR, new ReplaceStrategy());
		return state;
	}

	private void setupBasicState(OverAllState state) {
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE, TABLE_RELATION_OUTPUT, TEST_SCHEMA,
				EVIDENCE, "业务知识：用户表包含所有注册用户", GENEGRATED_SEMANTIC_MODEL_PROMPT, ""));
	}

	@Test
	void apply_validQuery_generatesPlan() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(VALID_PLAN_JSON)));

		NodeExecution execution = execute(plannerNode.apply(state), PLANNER_NODE_OUTPUT);
		Plan plan = parsePlan(execution);

		assertEquals("分析用户查询，需要查询用户数据", plan.getThoughtProcess());
		assertEquals(1, plan.getExecutionPlan().size());
		assertEquals(SQL_GENERATE_NODE, plan.getExecutionPlan().get(0).getToolToUse());
		assertEquals("查询所有用户信息", plan.getExecutionPlan().get(0).getToolParameters().getInstruction());
		assertTrue(execution.streamedText().contains(VALID_PLAN_JSON.trim()));
	}

	@Test
	void apply_multiStepQuery_generatesMultiStepPlan() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(MULTI_STEP_PLAN_JSON)));

		Plan plan = parsePlan(execute(plannerNode.apply(state), PLANNER_NODE_OUTPUT));

		assertEquals(2, plan.getExecutionPlan().size());
		assertEquals(SQL_GENERATE_NODE, plan.getExecutionPlan().get(0).getToolToUse());
		assertEquals(PYTHON_GENERATE_NODE, plan.getExecutionPlan().get(1).getToolToUse());
		assertEquals("分析销售趋势", plan.getExecutionPlan().get(1).getToolParameters().getInstruction());
	}

	@Test
	void apply_nl2SqlOnly_returnsFixedPlan() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(IS_ONLY_NL2SQL, true));

		Plan plan = parsePlan(execute(plannerNode.apply(state), PLANNER_NODE_OUTPUT));

		assertEquals("根据问题生成SQL", plan.getThoughtProcess());
		assertEquals(SQL_GENERATE_NODE, plan.getExecutionPlan().get(0).getToolToUse());
		assertEquals("查询所有用户信息", plan.getExecutionPlan().get(0).getToolParameters().getInstruction());
		verifyNoInteractions(llmService);
	}

	@Test
	void apply_llmFailure_throwsException() {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callUser(anyString())).thenThrow(new RuntimeException("LLM service unavailable"));

		RuntimeException exception = assertThrowsExactly(RuntimeException.class, () -> plannerNode.apply(state));
		assertEquals("LLM service unavailable", exception.getMessage());
	}

	@Test
	void apply_invalidPlanJson_emitsRawOutputForDownstreamValidation() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("this is not valid json at all")));

		NodeExecution execution = execute(plannerNode.apply(state), PLANNER_NODE_OUTPUT);

		assertEquals("this is not valid json at all", execution.finalResult().get(PLANNER_NODE_OUTPUT));
		assertTrue(execution.streamedText().contains("this is not valid json at all"));
	}

	@Test
	void apply_withValidationError_regeneratesPlan() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(PLAN_VALIDATION_ERROR, "请不要使用Python分析，直接用SQL", PLANNER_NODE_OUTPUT, VALID_PLAN_JSON));

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(VALID_PLAN_JSON)));

		Plan plan = parsePlan(execute(plannerNode.apply(state), PLANNER_NODE_OUTPUT));
		ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
		verify(llmService).callUser(promptCaptor.capture());

		assertEquals("查询所有用户信息", plan.getExecutionPlan().get(0).getToolParameters().getInstruction());
		assertTrue(promptCaptor.getValue().contains("请不要使用Python分析，直接用SQL"));
		assertTrue(promptCaptor.getValue().contains(VALID_PLAN_JSON.trim()));
	}

	@Test
	void apply_withSemanticModel_includesInPrompt() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(GENEGRATED_SEMANTIC_MODEL_PROMPT, "语义模型定义：PV表示页面浏览量"));

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(VALID_PLAN_JSON)));

		parsePlan(execute(plannerNode.apply(state), PLANNER_NODE_OUTPUT));
		ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
		verify(llmService).callUser(promptCaptor.capture());

		assertTrue(promptCaptor.getValue().contains("语义模型定义：PV表示页面浏览量"));
	}

	@Test
	void apply_emptyEvidence_generatesWithoutEvidence() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE, TABLE_RELATION_OUTPUT, TEST_SCHEMA,
				EVIDENCE, "", GENEGRATED_SEMANTIC_MODEL_PROMPT, ""));

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(VALID_PLAN_JSON)));

		parsePlan(execute(plannerNode.apply(state), PLANNER_NODE_OUTPUT));
		ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
		verify(llmService).callUser(promptCaptor.capture());

		assertFalse(promptCaptor.getValue().contains("业务知识：用户表包含所有注册用户"));
	}

	private Plan parsePlan(NodeExecution execution) throws Exception {
		String planJson = (String) execution.finalResult().get(PLANNER_NODE_OUTPUT);
		assertNotNull(planJson);
		return JsonUtil.getObjectMapper().readValue(planJson, Plan.class);
	}

}
