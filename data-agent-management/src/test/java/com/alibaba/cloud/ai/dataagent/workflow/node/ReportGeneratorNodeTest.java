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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.prompt.UserPromptService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportGeneratorNodeTest {

	@Mock
	private LlmService llmService;

	@Mock
	private UserPromptService promptConfigService;

	private ReportGeneratorNode reportGeneratorNode;

	@BeforeEach
	void setUp() {
		reportGeneratorNode = new ReportGeneratorNode(llmService, promptConfigService);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(PLANNER_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(QUERY_ENHANCE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLAN_CURRENT_STEP, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_EXECUTE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(AGENT_ID, new ReplaceStrategy());
		state.registerKeyAndStrategy(RESULT, new ReplaceStrategy());
		return state;
	}

	private void setupBasicState(OverAllState state) {
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询用户数据");
		String planJson = TestFixtures.planToJson(TestFixtures.createPlan("分析用户数据",
				TestFixtures.createSqlStep(1, "查询用户"), TestFixtures.createReportStep(2, "生成用户数据分析报告")));

		HashMap<String, String> executionResults = new HashMap<>();
		executionResults.put("step_1", "[{\"id\":1,\"name\":\"张三\"},{\"id\":2,\"name\":\"李四\"}]");

		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planJson, QUERY_ENHANCE_NODE_OUTPUT, dto, PLAN_CURRENT_STEP, 2,
				SQL_EXECUTE_NODE_OUTPUT, executionResults, AGENT_ID, "1"));
	}

	@Test
	void apply_validData_returnsResultGenerator() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(promptConfigService.getOptimizationConfigs(eq("report-generator"), eq(1L)))
			.thenReturn(Collections.emptyList());
		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("<h1>用户数据分析报告</h1>")));

		Map<String, Object> result = reportGeneratorNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(RESULT));
		verify(llmService).callUser(anyString());
	}

	@Test
	void apply_emptyExecutionResults_returnsGenerator() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询数据");
		String planJson = TestFixtures
			.planToJson(TestFixtures.createPlan("分析", TestFixtures.createReportStep(1, "生成报告")));

		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planJson, QUERY_ENHANCE_NODE_OUTPUT, dto, PLAN_CURRENT_STEP, 1,
				SQL_EXECUTE_NODE_OUTPUT, new HashMap<>(), AGENT_ID, "2"));

		when(promptConfigService.getOptimizationConfigs(eq("report-generator"), eq(2L)))
			.thenReturn(Collections.emptyList());
		when(llmService.callUser(anyString())).thenReturn(Flux.just(ChatResponseUtil.createPureResponse("暂无数据可分析")));

		Map<String, Object> result = reportGeneratorNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(RESULT));
	}

	@Test
	void apply_withMultipleSteps_includesAllStepData() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("分析销售数据");
		String planJson = TestFixtures
			.planToJson(TestFixtures.createPlan("多步骤分析", TestFixtures.createSqlStep(1, "查询销售"),
					TestFixtures.createSqlStep(2, "查询客户"), TestFixtures.createReportStep(3, "综合分析报告")));

		HashMap<String, String> executionResults = new HashMap<>();
		executionResults.put("step_1", "[{\"total\":1000}]");
		executionResults.put("step_2", "[{\"customers\":50}]");

		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planJson, QUERY_ENHANCE_NODE_OUTPUT, dto, PLAN_CURRENT_STEP, 3,
				SQL_EXECUTE_NODE_OUTPUT, executionResults, AGENT_ID, "3"));

		when(promptConfigService.getOptimizationConfigs(eq("report-generator"), eq(3L)))
			.thenReturn(Collections.emptyList());
		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("<h1>综合报告</h1>")));

		Map<String, Object> result = reportGeneratorNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(RESULT));
	}

	@Test
	void apply_llmFailure_throwsException() {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(promptConfigService.getOptimizationConfigs(eq("report-generator"), eq(1L)))
			.thenReturn(Collections.emptyList());
		when(llmService.callUser(anyString())).thenThrow(new RuntimeException("LLM unavailable"));

		assertThrows(RuntimeException.class, () -> reportGeneratorNode.apply(state));
	}

	@Test
	void apply_invalidAgentId_usesNullForConfigs() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询");
		String planJson = TestFixtures
			.planToJson(TestFixtures.createPlan("分析", TestFixtures.createReportStep(1, "报告")));

		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planJson, QUERY_ENHANCE_NODE_OUTPUT, dto, PLAN_CURRENT_STEP, 1,
				SQL_EXECUTE_NODE_OUTPUT, new HashMap<>(), AGENT_ID, "not-a-number"));

		when(promptConfigService.getOptimizationConfigs(eq("report-generator"), isNull()))
			.thenReturn(Collections.emptyList());
		when(llmService.callUser(anyString())).thenReturn(Flux.just(ChatResponseUtil.createPureResponse("report")));

		Map<String, Object> result = reportGeneratorNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(RESULT));
	}

	@Test
	void apply_missingPlanOutput_throwsException() {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询");
		state.updateState(
				Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto, PLAN_CURRENT_STEP, 1, SQL_EXECUTE_NODE_OUTPUT, new HashMap<>()));

		assertThrows(IllegalStateException.class, () -> reportGeneratorNode.apply(state));
	}

	@Test
	void apply_stepIndexOutOfRange_throwsException() {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("查询");
		String planJson = TestFixtures.planToJson(TestFixtures.createPlan("分析", TestFixtures.createSqlStep(1, "查询")));

		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planJson, QUERY_ENHANCE_NODE_OUTPUT, dto, PLAN_CURRENT_STEP, 5,
				SQL_EXECUTE_NODE_OUTPUT, new HashMap<>(), AGENT_ID, "1"));

		assertThrows(IllegalStateException.class, () -> reportGeneratorNode.apply(state));
	}

	@Test
	void apply_withAnalysisResults_includesAnalysis() throws Exception {
		OverAllState state = createTestState();
		QueryEnhanceOutputDTO dto = TestFixtures.createQueryEnhanceDTO("分析");
		String planJson = TestFixtures.planToJson(TestFixtures.createPlan("分析", TestFixtures.createSqlStep(1, "查询"),
				TestFixtures.createReportStep(2, "报告")));

		HashMap<String, String> executionResults = new HashMap<>();
		executionResults.put("step_1", "[{\"count\":100}]");
		executionResults.put("step_1_analysis", "数据趋势上升");

		state.updateState(Map.of(PLANNER_NODE_OUTPUT, planJson, QUERY_ENHANCE_NODE_OUTPUT, dto, PLAN_CURRENT_STEP, 2,
				SQL_EXECUTE_NODE_OUTPUT, executionResults, AGENT_ID, "4"));

		when(promptConfigService.getOptimizationConfigs(eq("report-generator"), eq(4L)))
			.thenReturn(Collections.emptyList());
		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("<p>分析完成</p>")));

		Map<String, Object> result = reportGeneratorNode.apply(state);

		assertNotNull(result);
		assertTrue(result.containsKey(RESULT));
	}

}
