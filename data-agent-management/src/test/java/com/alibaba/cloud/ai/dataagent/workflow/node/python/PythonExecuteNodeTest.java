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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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
import com.alibaba.cloud.ai.dataagent.service.code.CodePoolExecutorService;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.PythonExecuteNode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PythonExecuteNodeTest {

	@Mock
	private CodePoolExecutorService codePoolExecutor;

	@Mock
	private JsonParseUtil jsonParseUtil;

	@Mock
	private CodeExecutorProperties codeExecutorProperties;

	private PythonExecuteNode pythonExecuteNode;

	@BeforeEach
	void setUp() {
		when(codeExecutorProperties.getPythonMaxTriesCount()).thenReturn(5);
		pythonExecuteNode = new PythonExecuteNode(codePoolExecutor, jsonParseUtil, codeExecutorProperties);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(PYTHON_GENERATE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_EXECUTE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_IS_SUCCESS, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_TRIES_COUNT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_FALLBACK_MODE, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_RESULT_LIST_MEMORY, new ReplaceStrategy());
		return state;
	}

	private void setupBasicState(OverAllState state) {
		state.updateState(Map.of(PYTHON_GENERATE_NODE_OUTPUT, "print('hello world')"));
	}

	@Test
	void apply_validCode_executesSuccessfully() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(codePoolExecutor.runTask(any())).thenReturn(CodePoolExecutorService.TaskResponse.success("hello world"));
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(null);

		Map<String, Object> result = pythonExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_EXECUTE_NODE_OUTPUT));
	}

	@Test
	void apply_jsonOutput_parsesCorrectly() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PYTHON_GENERATE_NODE_OUTPUT, "import json\nprint(json.dumps({'key': 'value'}))"));

		String jsonOutput = "{\"key\": \"value\"}";
		when(codePoolExecutor.runTask(any())).thenReturn(CodePoolExecutorService.TaskResponse.success(jsonOutput));

		Map<String, Object> parsed = Map.of("key", "value");
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(parsed);

		Map<String, Object> result = pythonExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_EXECUTE_NODE_OUTPUT));
	}

	@Test
	void apply_executionError_setsFailureState() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(PYTHON_TRIES_COUNT, 1));

		when(codePoolExecutor.runTask(any()))
			.thenReturn(CodePoolExecutorService.TaskResponse.failure("", "NameError: name 'x' is not defined"));

		Map<String, Object> result = pythonExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_EXECUTE_NODE_OUTPUT));
	}

	@Test
	void apply_maxRetryExceeded_setsFallbackMode() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(PYTHON_TRIES_COUNT, 6));

		when(codePoolExecutor.runTask(any()))
			.thenReturn(CodePoolExecutorService.TaskResponse.failure("", "SyntaxError"));

		Map<String, Object> result = pythonExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_EXECUTE_NODE_OUTPUT));
	}

	@Test
	void apply_jsonParseFailure_usesRawOutput() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		String rawOutput = "not json content";
		when(codePoolExecutor.runTask(any())).thenReturn(CodePoolExecutorService.TaskResponse.success(rawOutput));
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(null);

		Map<String, Object> result = pythonExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_EXECUTE_NODE_OUTPUT));
	}

	@Test
	void apply_unicodeInOutput_handlesCorrectly() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PYTHON_GENERATE_NODE_OUTPUT, "print('你好世界')"));

		String unicodeOutput = "{\"message\": \"\\u4f60\\u597d\\u4e16\\u754c\"}";
		when(codePoolExecutor.runTask(any())).thenReturn(CodePoolExecutorService.TaskResponse.success(unicodeOutput));

		Map<String, Object> parsed = Map.of("message", "你好世界");
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(parsed);

		Map<String, Object> result = pythonExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_EXECUTE_NODE_OUTPUT));
	}

	@Test
	void apply_emptyOutput_setsEmptyResult() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(codePoolExecutor.runTask(any())).thenReturn(CodePoolExecutorService.TaskResponse.success(""));
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(null);

		Map<String, Object> result = pythonExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_EXECUTE_NODE_OUTPUT));
	}

	@Test
	void apply_withSqlResultsAsCSV_passesCorrectly() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PYTHON_GENERATE_NODE_OUTPUT, "print('processed')"));
		List<Map<String, String>> sqlResults = new ArrayList<>();
		sqlResults.add(Map.of("name", "Alice", "amount", "100"));
		sqlResults.add(Map.of("name", "Bob", "amount", "200"));
		state.updateState(Map.of(SQL_RESULT_LIST_MEMORY, sqlResults));

		when(codePoolExecutor.runTask(any())).thenReturn(CodePoolExecutorService.TaskResponse.success("processed"));
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(null);

		Map<String, Object> result = pythonExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_EXECUTE_NODE_OUTPUT));
	}

	@Test
	void apply_largeOutput_handlesMemoryPressure() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		StringBuilder largeOutput = new StringBuilder();
		for (int i = 0; i < 10000; i++) {
			largeOutput.append("line ").append(i).append(": data_value_").append(i).append("\n");
		}

		when(codePoolExecutor.runTask(any()))
			.thenReturn(CodePoolExecutorService.TaskResponse.success(largeOutput.toString()));
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(null);

		Map<String, Object> result = pythonExecuteNode.apply(state);
		assertNotNull(result);
		assertTrue(result.containsKey(PYTHON_EXECUTE_NODE_OUTPUT));
		assertNotNull(result.get(PYTHON_EXECUTE_NODE_OUTPUT));
	}

}
