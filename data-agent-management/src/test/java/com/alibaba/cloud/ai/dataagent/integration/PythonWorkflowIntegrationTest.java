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
package com.alibaba.cloud.ai.dataagent.integration;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.service.code.CodePoolExecutorService;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.PythonAnalyzeNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.PythonExecuteNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.PythonGenerateNode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PythonWorkflowIntegrationTest {

	@Mock
	private LlmService llmService;

	@Mock
	private CodePoolExecutorService codePoolExecutor;

	@Mock
	private JsonParseUtil jsonParseUtil;

	@Mock
	private CodeExecutorProperties codeExecutorProperties;

	private PythonGenerateNode pythonGenerateNode;

	private PythonExecuteNode pythonExecuteNode;

	private PythonAnalyzeNode pythonAnalyzeNode;

	@BeforeEach
	void setUp() {
		when(codeExecutorProperties.getLimitMemory()).thenReturn(500L);
		when(codeExecutorProperties.getCodeTimeout()).thenReturn("60s");
		when(codeExecutorProperties.getPythonMaxTriesCount()).thenReturn(5);

		pythonGenerateNode = new PythonGenerateNode(codeExecutorProperties, llmService);
		pythonExecuteNode = new PythonExecuteNode(codePoolExecutor, jsonParseUtil, codeExecutorProperties);
		pythonAnalyzeNode = new PythonAnalyzeNode(llmService);
	}

	private OverAllState createWorkflowState() {
		OverAllState state = new OverAllState();
		String[] keys = { TABLE_RELATION_OUTPUT, SQL_RESULT_LIST_MEMORY, PYTHON_IS_SUCCESS, PYTHON_TRIES_COUNT,
				PYTHON_GENERATE_NODE_OUTPUT, PYTHON_EXECUTE_NODE_OUTPUT, PYTHON_ANALYSIS_NODE_OUTPUT,
				PYTHON_FALLBACK_MODE, QUERY_ENHANCE_NODE_OUTPUT, PLANNER_NODE_OUTPUT, PLAN_CURRENT_STEP,
				SQL_EXECUTE_NODE_OUTPUT, TRACE_THREAD_ID };
		for (String key : keys) {
			state.registerKeyAndStrategy(key, new ReplaceStrategy());
		}
		return state;
	}

	private void setupWorkflowState(OverAllState state) {
		String planJson = TestFixtures
			.planToJson(TestFixtures.createPlan("Analyze data", TestFixtures.createPythonStep(1, "分析用户数据分布")));

		Map<String, Object> schema = TestFixtures.createSchemaMap("test_db", "users");
		Map<String, Object> queryEnhance = TestFixtures.createQueryEnhanceMap("分析用户数据分布");

		List<Map<String, String>> sqlResults = new ArrayList<>();
		sqlResults.add(Map.of("name", "Alice", "amount", "100"));
		sqlResults.add(Map.of("name", "Bob", "amount", "200"));
		sqlResults.add(Map.of("name", "Charlie", "amount", "300"));

		state.updateState(Map.of(TABLE_RELATION_OUTPUT, schema, SQL_RESULT_LIST_MEMORY, sqlResults, PYTHON_IS_SUCCESS,
				true, PYTHON_TRIES_COUNT, 0, QUERY_ENHANCE_NODE_OUTPUT, queryEnhance, PLANNER_NODE_OUTPUT, planJson,
				PLAN_CURRENT_STEP, 1, SQL_EXECUTE_NODE_OUTPUT, new HashMap<String, String>()));
	}

	@Test
	void endToEnd_pythonAnalysis_generatesExecutesAnalyzes_returnsResults() throws Exception {
		OverAllState state = createWorkflowState();
		setupWorkflowState(state);

		String generatedCode = "import pandas as pd\ndf = pd.DataFrame(data)\nprint(df.describe().to_json())";
		when(llmService.call(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(generatedCode)));

		Map<String, Object> generateResult = pythonGenerateNode.apply(state);
		assertNotNull(generateResult);
		assertTrue(generateResult.containsKey(PYTHON_GENERATE_NODE_OUTPUT));

		state.updateState(Map.of(PYTHON_GENERATE_NODE_OUTPUT, generatedCode, PYTHON_TRIES_COUNT, 1));

		String executionOutput = "{\"count\": 3, \"mean\": 200.0}";
		when(codePoolExecutor.runTask(any()))
			.thenReturn(CodePoolExecutorService.TaskResponse.success(executionOutput));
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(null);

		Map<String, Object> executeResult = pythonExecuteNode.apply(state);
		assertNotNull(executeResult);
		assertTrue(executeResult.containsKey(PYTHON_EXECUTE_NODE_OUTPUT));

		state.updateState(Map.of(PYTHON_EXECUTE_NODE_OUTPUT, executionOutput, PYTHON_IS_SUCCESS, true));

		String analysisResponse = "数据分析结果：共3条记录，平均金额200.0元";
		when(llmService.callSystem(anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(analysisResponse)));

		Map<String, Object> analyzeResult = pythonAnalyzeNode.apply(state);
		assertNotNull(analyzeResult);
		assertTrue(analyzeResult.containsKey(PYTHON_ANALYSIS_NODE_OUTPUT));

		verify(llmService).call(anyString(), anyString());
		verify(codePoolExecutor).runTask(any());
		verify(llmService).callSystem(anyString());
	}

}
