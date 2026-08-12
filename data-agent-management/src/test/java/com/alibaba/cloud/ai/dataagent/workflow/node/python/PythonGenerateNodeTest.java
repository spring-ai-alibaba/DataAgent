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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.NodeExecution;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.PythonGenerateNode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class PythonGenerateNodeTest {

	private static final String TEST_PLAN_JSON = """
			{
			    "thought_process": "需要Python分析",
			    "execution_plan": [
			        {
			            "step": 1,
			            "tool_to_use": "PYTHON_GENERATE_NODE",
			            "tool_parameters": {
			                "instruction": "使用Python分析销售趋势"
			            }
			        }
			    ]
			}
			""";

	private static final Map<String, Object> TEST_QUERY_ENHANCE;

	private static final Map<String, Object> TEST_SCHEMA;

	static {
		Map<String, Object> table = new HashMap<>();
		table.put("name", "sales");
		table.put("description", "销售表");
		table.put("column", new ArrayList<>());
		table.put("primaryKeys", new ArrayList<>());

		Map<String, Object> schema = new HashMap<>();
		schema.put("name", "test_schema");
		schema.put("description", "测试schema");
		schema.put("tableCount", 1);
		schema.put("table", new ArrayList<>(List.of(table)));
		schema.put("foreignKeys", new ArrayList<>());

		Map<String, Object> queryEnhance = new HashMap<>();
		queryEnhance.put("canonical_query", "分析销售趋势");
		queryEnhance.put("expanded_queries", new ArrayList<>(List.of("销售分析")));

		TEST_SCHEMA = schema;
		TEST_QUERY_ENHANCE = queryEnhance;
	}

	@Mock
	private CodeExecutorProperties codeExecutorProperties;

	@Mock
	private LlmService llmService;

	private PythonGenerateNode pythonGenerateNode;

	@BeforeEach
	void setUp() {
		when(codeExecutorProperties.getLimitMemory()).thenReturn(500L);
		when(codeExecutorProperties.getCodeTimeout()).thenReturn(Duration.ofSeconds(60));
		pythonGenerateNode = new PythonGenerateNode(codeExecutorProperties, llmService);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(PYTHON_GENERATE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_EXECUTE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_IS_SUCCESS, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_TRIES_COUNT, new ReplaceStrategy());
		state.registerKeyAndStrategy(TABLE_RELATION_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_RESULT_LIST_MEMORY, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_EXECUTE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(QUERY_ENHANCE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLANNER_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLAN_CURRENT_STEP, new ReplaceStrategy());
		return state;
	}

	private void setupBasicState(OverAllState state) {
		state.updateState(Map.of(TABLE_RELATION_OUTPUT, TEST_SCHEMA, QUERY_ENHANCE_NODE_OUTPUT, TEST_QUERY_ENHANCE,
				PLANNER_NODE_OUTPUT, TEST_PLAN_JSON, PLAN_CURRENT_STEP, 1));
	}

	@Test
	void apply_validRequest_generatesPythonCode() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.call(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("import pandas as pd\nprint('hello')")));

		NodeExecution execution = execute(pythonGenerateNode.apply(state), PYTHON_GENERATE_NODE_OUTPUT);

		assertEquals("import pandas as pd\nprint('hello')", execution.finalResult().get(PYTHON_GENERATE_NODE_OUTPUT));
		assertEquals(1, execution.finalResult().get(PYTHON_TRIES_COUNT));
	}

	@Test
	void apply_withSchemaContext_includesSchemaInPrompt() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.call(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("print('with schema')")));

		NodeExecution execution = execute(pythonGenerateNode.apply(state), PYTHON_GENERATE_NODE_OUTPUT);

		assertEquals("print('with schema')", execution.finalResult().get(PYTHON_GENERATE_NODE_OUTPUT));
		ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
		verify(llmService).call(systemPrompt.capture(), anyString());
		assertTrue(systemPrompt.getValue().contains("test_schema"));
		assertTrue(systemPrompt.getValue().contains("sales"));
	}

	@Test
	void apply_llmFailure_throwsException() {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.call(anyString(), anyString())).thenThrow(new RuntimeException("LLM service unavailable"));

		RuntimeException exception = assertThrowsExactly(RuntimeException.class, () -> pythonGenerateNode.apply(state));
		assertEquals("LLM service unavailable", exception.getMessage());
	}

	@Test
	void apply_previousFailure_includesErrorInPrompt() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(PYTHON_IS_SUCCESS, false, PYTHON_GENERATE_NODE_OUTPUT, "import pandas\nprint(df)",
				PYTHON_EXECUTE_NODE_OUTPUT, "NameError: name 'df' is not defined", PYTHON_TRIES_COUNT, 1));

		when(llmService.call(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("import pandas as pd\ndf = pd.DataFrame()")));

		NodeExecution execution = execute(pythonGenerateNode.apply(state), PYTHON_GENERATE_NODE_OUTPUT);

		assertEquals("import pandas as pd\ndf = pd.DataFrame()",
				execution.finalResult().get(PYTHON_GENERATE_NODE_OUTPUT));
		assertEquals(2, execution.finalResult().get(PYTHON_TRIES_COUNT));
		ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
		verify(llmService).call(anyString(), userPrompt.capture());
		assertTrue(userPrompt.getValue().contains("import pandas\nprint(df)"));
		assertTrue(userPrompt.getValue().contains("NameError: name 'df' is not defined"));
	}

	@Test
	void apply_existingRetryCount_incrementsWithoutLocalLimit() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(PYTHON_IS_SUCCESS, false, PYTHON_GENERATE_NODE_OUTPUT, "bad code",
				PYTHON_EXECUTE_NODE_OUTPUT, "SyntaxError", PYTHON_TRIES_COUNT, 10));

		when(llmService.call(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("print('retry')")));

		NodeExecution execution = execute(pythonGenerateNode.apply(state), PYTHON_GENERATE_NODE_OUTPUT);

		assertEquals("print('retry')", execution.finalResult().get(PYTHON_GENERATE_NODE_OUTPUT));
		assertEquals(11, execution.finalResult().get(PYTHON_TRIES_COUNT));
	}

	@Test
	void apply_stripsPythonMarkers_returnsCleanCode() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.call(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("```python\nprint('hello')\n```")));

		NodeExecution execution = execute(pythonGenerateNode.apply(state), PYTHON_GENERATE_NODE_OUTPUT);

		assertEquals("print('hello')", execution.finalResult().get(PYTHON_GENERATE_NODE_OUTPUT));
		assertEquals(1, execution.finalResult().get(PYTHON_TRIES_COUNT));
	}

	@Test
	void apply_withMultipleSqlResults_includesOrderedStepSamplesInPrompt() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(SQL_EXECUTE_NODE_OUTPUT,
				Map.of("step_2", "{\"data\":[{\"department\":\"engineering\",\"headcount\":\"20\"}]}", "step_1",
						"{\"data\":[{\"department\":\"sales\",\"revenue\":\"100\"}]}")));

		when(llmService.call(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("import json\nprint(json.dumps(result))")));

		NodeExecution execution = execute(pythonGenerateNode.apply(state), PYTHON_GENERATE_NODE_OUTPUT);
		assertEquals("import json\nprint(json.dumps(result))",
				execution.finalResult().get(PYTHON_GENERATE_NODE_OUTPUT));

		ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
		verify(llmService).call(systemPrompt.capture(), anyString());
		String prompt = systemPrompt.getValue();
		assertTrue(prompt.contains("[[{"));
		assertTrue(prompt.indexOf("sales") < prompt.indexOf("engineering"));
		assertTrue(prompt.contains("revenue"));
		assertTrue(prompt.contains("headcount"));
	}

	@Test
	void apply_complexAnalysisRequest_generatesCode() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.call(anyString(), anyString())).thenReturn(Flux.just(ChatResponseUtil
			.createPureResponse("import pandas as pd\nimport numpy as np\nprint(np.mean([1,2,3]))")));

		NodeExecution execution = execute(pythonGenerateNode.apply(state), PYTHON_GENERATE_NODE_OUTPUT);

		assertEquals("import pandas as pd\nimport numpy as np\nprint(np.mean([1,2,3]))",
				execution.finalResult().get(PYTHON_GENERATE_NODE_OUTPUT));
	}

	@Test
	void apply_promptIncludesConfiguredResourceLimits() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.call(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("print('safe output only')")));

		NodeExecution execution = execute(pythonGenerateNode.apply(state), PYTHON_GENERATE_NODE_OUTPUT);

		assertEquals("print('safe output only')", execution.finalResult().get(PYTHON_GENERATE_NODE_OUTPUT));
		ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
		verify(llmService).call(systemPrompt.capture(), anyString());
		assertTrue(systemPrompt.getValue().contains("500"));
		assertTrue(systemPrompt.getValue().contains("60s"));
	}

}
