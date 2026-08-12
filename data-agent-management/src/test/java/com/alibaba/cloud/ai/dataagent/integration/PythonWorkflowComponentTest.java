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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.service.code.PythonCodeExecutorService;
import com.alibaba.cloud.ai.dataagent.service.code.sandbox.dependency.PythonDependencyMetadata;
import com.alibaba.cloud.ai.dataagent.service.code.sandbox.dependency.PythonDependencyMetadataParser;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.NodeExecution;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.PythonAnalyzeNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.PythonExecuteNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.PythonGenerateNode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.PLAN_CURRENT_STEP;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.PLANNER_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.PYTHON_ANALYSIS_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.PYTHON_EXECUTE_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.PYTHON_FALLBACK_MODE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.PYTHON_GENERATE_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.PYTHON_IS_SUCCESS;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.PYTHON_TRIES_COUNT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.QUERY_ENHANCE_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_EXECUTE_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TABLE_RELATION_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.execute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PythonWorkflowComponentTest {

	@Mock
	private LlmService llmService;

	@Mock
	private PythonCodeExecutorService pythonCodeExecutor;

	@Mock
	private PythonDependencyMetadataParser dependencyMetadataParser;

	@Mock
	private JsonParseUtil jsonParseUtil;

	@Mock
	private CodeExecutorProperties codeExecutorProperties;

	private PythonGenerateNode pythonGenerateNode;

	private PythonExecuteNode pythonExecuteNode;

	private PythonAnalyzeNode pythonAnalyzeNode;

	@BeforeEach
	void setUp() {
		pythonGenerateNode = new PythonGenerateNode(codeExecutorProperties, llmService);
		pythonExecuteNode = new PythonExecuteNode(pythonCodeExecutor, dependencyMetadataParser, jsonParseUtil,
				codeExecutorProperties);
		pythonAnalyzeNode = new PythonAnalyzeNode(llmService);
	}

	@Test
	void generatedCodeReceivesOrderedSqlRowsAndItsAnalysisIsStoredOnThePythonStep() throws Exception {
		OverAllState state = workflowState();
		when(codeExecutorProperties.getLimitMemory()).thenReturn(500L);
		when(codeExecutorProperties.getCodeTimeout()).thenReturn(Duration.ofSeconds(60));
		String generatedCode = "import json\nprint(json.dumps({'count': len(data[0])}))";
		when(llmService.call(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse(generatedCode)));

		NodeExecution generation = execute(pythonGenerateNode.apply(state), PYTHON_GENERATE_NODE_OUTPUT);

		assertThat(generation.finalResult()).containsEntry(PYTHON_GENERATE_NODE_OUTPUT, generatedCode)
			.containsEntry(PYTHON_TRIES_COUNT, 1);
		state.updateState(generation.finalResult());
		ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
		verify(llmService).call(systemPrompt.capture(), userPrompt.capture());
		assertThat(systemPrompt.getValue()).contains("Alice", "Bob", "amount", "分析用户金额");
		assertThat(userPrompt.getValue()).contains("分析用户数据分布");

		when(dependencyMetadataParser.parse(generatedCode)).thenReturn(PythonDependencyMetadata.empty());
		String executionOutput = "{\"count\":2,\"mean\":150.0}";
		when(pythonCodeExecutor.runTask(any()))
			.thenReturn(PythonCodeExecutorService.TaskResponse.success(executionOutput));
		when(jsonParseUtil.tryConvertToObject(executionOutput, Object.class))
			.thenReturn(Map.of("count", 2, "mean", 150.0));

		NodeExecution execution = execute(pythonExecuteNode.apply(state), PYTHON_EXECUTE_NODE_OUTPUT);

		assertThat(execution.finalResult()).containsEntry(PYTHON_IS_SUCCESS, true);
		assertThat(JsonUtil.getObjectMapper()
			.readValue((String) execution.finalResult().get(PYTHON_EXECUTE_NODE_OUTPUT),
					new TypeReference<Map<String, Object>>() {
					}))
			.containsEntry("count", 2)
			.containsEntry("mean", 150.0);
		state.updateState(execution.finalResult());

		ArgumentCaptor<PythonCodeExecutorService.TaskRequest> task = ArgumentCaptor
			.forClass(PythonCodeExecutorService.TaskRequest.class);
		verify(pythonCodeExecutor).runTask(task.capture());
		assertThat(task.getValue().code()).isEqualTo(generatedCode);
		assertThat(task.getValue().dependencies()).isEmpty();
		List<List<Map<String, String>>> pythonInput = JsonUtil.getObjectMapper()
			.readValue(task.getValue().input(), new TypeReference<>() {
			});
		assertThat(pythonInput)
			.containsExactly(List.of(Map.of("name", "Alice", "amount", "100"), Map.of("name", "Bob", "amount", "200")));

		String analysis = "共2条记录，平均金额150.0元";
		when(llmService.callSystem(anyString())).thenReturn(Flux.just(ChatResponseUtil.createPureResponse(analysis)));

		NodeExecution analyzed = execute(pythonAnalyzeNode.apply(state), PYTHON_ANALYSIS_NODE_OUTPUT);

		assertThat(analyzed.finalResult()).containsEntry(PLAN_CURRENT_STEP, 3);
		@SuppressWarnings("unchecked")
		Map<String, String> storedResults = (Map<String, String>) analyzed.finalResult().get(SQL_EXECUTE_NODE_OUTPUT);
		assertThat(storedResults).containsEntry("step_2_analysis", analysis).containsKey("step_1");
		ArgumentCaptor<String> analysisPrompt = ArgumentCaptor.forClass(String.class);
		verify(llmService).callSystem(analysisPrompt.capture());
		assertThat(analysisPrompt.getValue()).contains("分析用户数据分布", "\"count\":2", "\"mean\":150.0");
	}

	private OverAllState workflowState() throws Exception {
		OverAllState state = new OverAllState();
		String[] keys = { TABLE_RELATION_OUTPUT, PYTHON_IS_SUCCESS, PYTHON_TRIES_COUNT, PYTHON_GENERATE_NODE_OUTPUT,
				PYTHON_EXECUTE_NODE_OUTPUT, PYTHON_ANALYSIS_NODE_OUTPUT, PYTHON_FALLBACK_MODE,
				QUERY_ENHANCE_NODE_OUTPUT, PLANNER_NODE_OUTPUT, PLAN_CURRENT_STEP, SQL_EXECUTE_NODE_OUTPUT };
		for (String key : keys) {
			state.registerKeyAndStrategy(key, new ReplaceStrategy());
		}

		String planJson = TestFixtures.planToJson(TestFixtures.createPlan("Analyze data",
				TestFixtures.createSqlStep(1, "查询用户金额"), TestFixtures.createPythonStep(2, "分析用户金额")));
		ResultSetBO sqlResult = new ResultSetBO();
		sqlResult.setColumn(List.of("name", "amount"));
		sqlResult.setData(List.of(Map.of("name", "Alice", "amount", "100"), Map.of("name", "Bob", "amount", "200")));
		state.updateState(Map.of(TABLE_RELATION_OUTPUT, TestFixtures.createSchemaMap("test_db", "users"),
				SQL_EXECUTE_NODE_OUTPUT, Map.of("step_1", JsonUtil.getObjectMapper().writeValueAsString(sqlResult)),
				PYTHON_IS_SUCCESS, true, PYTHON_TRIES_COUNT, 0, QUERY_ENHANCE_NODE_OUTPUT,
				TestFixtures.createQueryEnhanceMap("分析用户数据分布"), PLANNER_NODE_OUTPUT, planJson, PLAN_CURRENT_STEP, 2));
		return state;
	}

}
