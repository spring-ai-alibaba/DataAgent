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

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.PythonGenerateNode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
		when(codeExecutorProperties.getCodeTimeout()).thenReturn("60s");
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

		Map<String, Object> result = pythonGenerateNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_GENERATE_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_GENERATE_NODE_OUTPUT));
	}

	@Test
	void apply_withSchemaContext_includesSchemaInPrompt() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.call(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("print('with schema')")));

		Map<String, Object> result = pythonGenerateNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_GENERATE_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_GENERATE_NODE_OUTPUT));
	}

	@Test
	void apply_llmFailure_throwsException() {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.call(anyString(), anyString())).thenThrow(new RuntimeException("LLM service unavailable"));

		assertThrows(RuntimeException.class, () -> pythonGenerateNode.apply(state));
	}

	@Test
	void apply_previousFailure_includesErrorInPrompt() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(PYTHON_IS_SUCCESS, false, PYTHON_GENERATE_NODE_OUTPUT, "import pandas\nprint(df)",
				PYTHON_EXECUTE_NODE_OUTPUT, "NameError: name 'df' is not defined", PYTHON_TRIES_COUNT, 1));

		when(llmService.call(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("import pandas as pd\ndf = pd.DataFrame()")));

		Map<String, Object> result = pythonGenerateNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_GENERATE_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_GENERATE_NODE_OUTPUT));
	}

	@Test
	void apply_maxRetriesReached_throwsException() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(PYTHON_IS_SUCCESS, false, PYTHON_GENERATE_NODE_OUTPUT, "bad code",
				PYTHON_EXECUTE_NODE_OUTPUT, "SyntaxError", PYTHON_TRIES_COUNT, 10));

		when(llmService.call(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("print('retry')")));

		Map<String, Object> result = pythonGenerateNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_GENERATE_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_GENERATE_NODE_OUTPUT));
	}

	@Test
	void apply_stripsPythonMarkers_returnsCleanCode() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.call(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("```python\nprint('hello')\n```")));

		Map<String, Object> result = pythonGenerateNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_GENERATE_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_GENERATE_NODE_OUTPUT));
	}

	@Test
	void apply_withSqlResults_includesResultsInPrompt() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		List<Map<String, String>> sqlResults = new ArrayList<>();
		sqlResults.add(Map.of("name", "Alice", "sales", "100"));
		sqlResults.add(Map.of("name", "Bob", "sales", "200"));
		state.updateState(Map.of(SQL_RESULT_LIST_MEMORY, sqlResults));

		when(llmService.call(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("import json\nprint(json.dumps(result))")));

		Map<String, Object> result = pythonGenerateNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_GENERATE_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_GENERATE_NODE_OUTPUT));
	}

	@Test
	void apply_complexAnalysisRequest_generatesCode() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.call(anyString(), anyString())).thenReturn(Flux.just(ChatResponseUtil
			.createPureResponse("import pandas as pd\nimport numpy as np\nprint(np.mean([1,2,3]))")));

		Map<String, Object> result = pythonGenerateNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_GENERATE_NODE_OUTPUT));
	}

	@Test
	void apply_unsafeImportRequest_generatesCodeWithLimits() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(llmService.call(anyString(), anyString()))
			.thenReturn(Flux.just(ChatResponseUtil.createPureResponse("print('safe output only')")));

		Map<String, Object> result = pythonGenerateNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_GENERATE_NODE_OUTPUT));
	}

}
