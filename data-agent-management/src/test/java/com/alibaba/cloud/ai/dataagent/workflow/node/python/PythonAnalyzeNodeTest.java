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
import static com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.execute;
import static com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.executeForError;
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

import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.NodeErrorExecution;
import com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.NodeExecution;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.PythonAnalyzeNode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
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

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("销售总额为15000元，平均销售额3000元")));

		NodeExecution execution = execute(pythonAnalyzeNode.apply(state), PYTHON_ANALYSIS_NODE_OUTPUT);

		assertEquals("销售总额为15000元，平均销售额3000元", analysisResults(execution).get("step_1_analysis"));
		assertEquals(2, execution.finalResult().get(PLAN_CURRENT_STEP));
	}

	@Test
	void apply_llmFailure_throwsException() {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callUser(anyString())).thenThrow(new RuntimeException("LLM service unavailable"));

		RuntimeException exception = assertThrowsExactly(RuntimeException.class, () -> pythonAnalyzeNode.apply(state));
		assertEquals("LLM service unavailable", exception.getMessage());
	}

	@Test
	void apply_fallbackMode_returnsStaticMessage() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(PYTHON_FALLBACK_MODE, true));

		NodeExecution execution = execute(pythonAnalyzeNode.apply(state), PYTHON_ANALYSIS_NODE_OUTPUT);

		assertEquals("Python 高级分析功能暂时不可用，出现错误", analysisResults(execution).get("step_1_analysis"));
		assertEquals(2, execution.finalResult().get(PLAN_CURRENT_STEP));
		verifyNoInteractions(llmService);
	}

	@Test
	void apply_emptyPythonOutput_returnsMinimalAnalysis() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PYTHON_EXECUTE_NODE_OUTPUT, "", PLAN_CURRENT_STEP, 1, QUERY_ENHANCE_NODE_OUTPUT,
				TEST_QUERY_ENHANCE, PLANNER_NODE_OUTPUT, TEST_PLAN_JSON));

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("Python输出为空，无法进行深入分析")));

		NodeExecution execution = execute(pythonAnalyzeNode.apply(state), PYTHON_ANALYSIS_NODE_OUTPUT);

		assertEquals("Python输出为空，无法进行深入分析", analysisResults(execution).get("step_1_analysis"));
		assertEquals(2, execution.finalResult().get(PLAN_CURRENT_STEP));
	}

	@Test
	void apply_updatesExecutionResults_correctly() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		Map<String, String> existingResults = new HashMap<>();
		existingResults.put("step_1", "{\"data\": []}");
		state.updateState(Map.of(SQL_EXECUTE_NODE_OUTPUT, existingResults));

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("分析完成：数据为空")));

		NodeExecution execution = execute(pythonAnalyzeNode.apply(state), PYTHON_ANALYSIS_NODE_OUTPUT);
		Map<String, String> updatedResults = analysisResults(execution);

		assertEquals("{\"data\": []}", updatedResults.get("step_1"));
		assertEquals("分析完成：数据为空", updatedResults.get("step_1_analysis"));
		assertEquals(2, execution.finalResult().get(PLAN_CURRENT_STEP));
	}

	@Test
	void apply_invalidOutput_passesRawOutputToAnalysisPrompt() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PYTHON_EXECUTE_NODE_OUTPUT, "{{{{invalid json garbage}}}}", PLAN_CURRENT_STEP, 1,
				QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE, PLANNER_NODE_OUTPUT, TEST_PLAN_JSON));

		when(llmService.callUser(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("无法解析Python输出")));

		NodeExecution execution = execute(pythonAnalyzeNode.apply(state), PYTHON_ANALYSIS_NODE_OUTPUT);

		assertEquals("无法解析Python输出", analysisResults(execution).get("step_1_analysis"));
		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(llmService).callSystem(prompt.capture());
		assertTrue(prompt.getValue().contains("{{{{invalid json garbage}}}}"));
	}

	@Test
	void apply_timeoutInLlmAnalysis_emitsGraphError() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.callUser(anyString())).thenReturn(Flux.error(new RuntimeException("LLM analysis timeout")));

		NodeErrorExecution execution = executeForError(pythonAnalyzeNode.apply(state), PYTHON_ANALYSIS_NODE_OUTPUT);

		assertInstanceOf(RuntimeException.class, execution.error());
		assertEquals("LLM analysis timeout", execution.error().getMessage());
		assertTrue(execution.streamedText().contains("正在分析代码运行结果"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> analysisResults(NodeExecution execution) {
		return (Map<String, String>) execution.finalResult().get(SQL_EXECUTE_NODE_OUTPUT);
	}

}
