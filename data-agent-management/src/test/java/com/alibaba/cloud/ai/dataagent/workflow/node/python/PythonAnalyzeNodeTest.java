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
package com.alibaba.cloud.ai.dataagent.workflow.node.python;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.PythonAnalyzeNode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PythonAnalyzeNodeTest {

	private static final String TEST_PLAN_JSON = """
			{
			    "thought_process": "需要Python分析",
			    "execution_plan": [
			        {
			            "step": 1,
			            "tool_to_use": "PYTHON_ANALYZE_NODE",
			            "tool_parameters": {
			                "instruction": "分析Python输出"
			            }
			        }
			    ]
			}
			""";

	private static final Map<String, Object> TEST_QUERY_ENHANCE;

	static {
		Map<String, Object> queryEnhance = new HashMap<>();
		queryEnhance.put("canonical_query", "分析销售趋势");
		queryEnhance.put("expanded_queries", new ArrayList<>(List.of("销售分析")));
		TEST_QUERY_ENHANCE = queryEnhance;
	}

	@Mock
	private LlmService llmService;

	private PythonAnalyzeNode pythonAnalyzeNode;

	@BeforeEach
	void setUp() {
		pythonAnalyzeNode = new PythonAnalyzeNode(llmService);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(PYTHON_ANALYSIS_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_EXECUTE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_FALLBACK_MODE, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_EXECUTE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLAN_CURRENT_STEP, new ReplaceStrategy());
		state.registerKeyAndStrategy(QUERY_ENHANCE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLANNER_NODE_OUTPUT, new ReplaceStrategy());
		return state;
	}

	private void setupBasicState(OverAllState state) {
		state.updateState(
				Map.of(PYTHON_EXECUTE_NODE_OUTPUT, "{\"total_sales\": 15000, \"avg_sales\": 3000}", PLAN_CURRENT_STEP,
						1, QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE, PLANNER_NODE_OUTPUT, TEST_PLAN_JSON));
	}

	@Test
	void apply_validOutput_returnsAnalysis() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callSystem(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("销售总额为15000元，平均销售额3000元")));

		Map<String, Object> result = pythonAnalyzeNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_ANALYSIS_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_ANALYSIS_NODE_OUTPUT));
	}

	@Test
	void apply_llmFailure_throwsException() {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callSystem(anyString())).thenThrow(new RuntimeException("LLM service unavailable"));

		assertThrows(RuntimeException.class, () -> pythonAnalyzeNode.apply(state));
	}

	@Test
	void apply_fallbackMode_returnsStaticMessage() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(PYTHON_FALLBACK_MODE, true));

		Map<String, Object> result = pythonAnalyzeNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_ANALYSIS_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_ANALYSIS_NODE_OUTPUT));
	}

	@Test
	void apply_emptyPythonOutput_returnsMinimalAnalysis() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PYTHON_EXECUTE_NODE_OUTPUT, "", PLAN_CURRENT_STEP, 1, QUERY_ENHANCE_NODE_OUTPUT,
				TEST_QUERY_ENHANCE, PLANNER_NODE_OUTPUT, TEST_PLAN_JSON));

		when(llmService.callSystem(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("Python输出为空，无法进行深入分析")));

		Map<String, Object> result = pythonAnalyzeNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_ANALYSIS_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_ANALYSIS_NODE_OUTPUT));
	}

	@Test
	void apply_updatesExecutionResults_correctly() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		Map<String, String> existingResults = new HashMap<>();
		existingResults.put("step_1", "{\"data\": []}");
		state.updateState(Map.of(SQL_EXECUTE_NODE_OUTPUT, existingResults));

		when(llmService.callSystem(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("分析完成：数据为空")));

		Map<String, Object> result = pythonAnalyzeNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_ANALYSIS_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_ANALYSIS_NODE_OUTPUT));
	}

	@Test
	void apply_invalidOutput_throwsOrHandlesGracefully() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PYTHON_EXECUTE_NODE_OUTPUT, "{{{{invalid json garbage}}}}", PLAN_CURRENT_STEP, 1,
				QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE, PLANNER_NODE_OUTPUT, TEST_PLAN_JSON));

		when(llmService.callSystem(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("无法解析Python输出")));

		Map<String, Object> result = pythonAnalyzeNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_ANALYSIS_NODE_OUTPUT));
	}

	@Test
	void apply_timeoutInLlmAnalysis_returnsResultWithGenerator() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callSystem(anyString())).thenReturn(Flux.error(new RuntimeException("LLM analysis timeout")));

		Map<String, Object> result = pythonAnalyzeNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_ANALYSIS_NODE_OUTPUT));
	}

}
