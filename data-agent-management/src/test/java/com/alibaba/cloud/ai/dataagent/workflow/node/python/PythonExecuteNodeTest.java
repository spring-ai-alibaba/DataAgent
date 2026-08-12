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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.service.code.PythonCodeExecutorService;
import com.alibaba.cloud.ai.dataagent.service.code.sandbox.dependency.PythonDependencyMetadataParser;
import com.alibaba.cloud.ai.dataagent.service.code.sandbox.dependency.PythonDependencyPolicy;
import com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport.NodeExecution;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.PythonExecuteNode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

@ExtendWith(MockitoExtension.class)
class PythonExecuteNodeTest {

	@Mock
	private PythonCodeExecutorService pythonCodeExecutor;

	private PythonDependencyMetadataParser dependencyMetadataParser;

	@Mock
	private JsonParseUtil jsonParseUtil;

	private CodeExecutorProperties codeExecutorProperties;

	private PythonExecuteNode pythonExecuteNode;

	@BeforeEach
	void setUp() {
		codeExecutorProperties = new CodeExecutorProperties();
		dependencyMetadataParser = new PythonDependencyMetadataParser(
				new PythonDependencyPolicy(codeExecutorProperties));
		pythonExecuteNode = new PythonExecuteNode(pythonCodeExecutor, dependencyMetadataParser, jsonParseUtil,
				codeExecutorProperties);
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(PYTHON_GENERATE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_EXECUTE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_IS_SUCCESS, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_TRIES_COUNT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_FALLBACK_MODE, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_RESULT_LIST_MEMORY, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_EXECUTE_NODE_OUTPUT, new ReplaceStrategy());
		return state;
	}

	private void setupBasicState(OverAllState state) {
		state.updateState(Map.of(PYTHON_GENERATE_NODE_OUTPUT, "print('hello world')"));
	}

	@Test
	void apply_validCode_executesSuccessfully() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(pythonCodeExecutor.runTask(any()))
			.thenReturn(PythonCodeExecutorService.TaskResponse.success("hello world"));
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(null);

		NodeExecution execution = execute(pythonExecuteNode.apply(state), PYTHON_EXECUTE_NODE_OUTPUT);
		assertEquals("hello world", execution.finalResult().get(PYTHON_EXECUTE_NODE_OUTPUT));
		assertEquals(true, execution.finalResult().get(PYTHON_IS_SUCCESS));
	}

	@Test
	void apply_jsonOutput_parsesCorrectly() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PYTHON_GENERATE_NODE_OUTPUT, "import json\nprint(json.dumps({'key': 'value'}))"));

		String jsonOutput = "{\"key\": \"value\"}";
		when(pythonCodeExecutor.runTask(any())).thenReturn(PythonCodeExecutorService.TaskResponse.success(jsonOutput));

		Map<String, Object> parsed = Map.of("key", "value");
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(parsed);

		NodeExecution execution = execute(pythonExecuteNode.apply(state), PYTHON_EXECUTE_NODE_OUTPUT);
		assertEquals("{\"key\":\"value\"}", execution.finalResult().get(PYTHON_EXECUTE_NODE_OUTPUT));
		assertEquals(true, execution.finalResult().get(PYTHON_IS_SUCCESS));
	}

	@Test
	void apply_executionError_setsFailureState() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(PYTHON_TRIES_COUNT, 1));

		when(pythonCodeExecutor.runTask(any()))
			.thenReturn(PythonCodeExecutorService.TaskResponse.failure("", "NameError: name 'x' is not defined"));

		NodeExecution execution = execute(pythonExecuteNode.apply(state), PYTHON_EXECUTE_NODE_OUTPUT);
		assertEquals(false, execution.finalResult().get(PYTHON_IS_SUCCESS));
		assertTrue(execution.finalResult().get(PYTHON_EXECUTE_NODE_OUTPUT).toString().contains("NameError"));
	}

	@Test
	void apply_maxRetryExceeded_setsFallbackMode() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);
		state.updateState(Map.of(PYTHON_TRIES_COUNT, 6));

		when(pythonCodeExecutor.runTask(any()))
			.thenReturn(PythonCodeExecutorService.TaskResponse.failure("", "SyntaxError"));

		NodeExecution execution = execute(pythonExecuteNode.apply(state), PYTHON_EXECUTE_NODE_OUTPUT);
		assertEquals("{}", execution.finalResult().get(PYTHON_EXECUTE_NODE_OUTPUT));
		assertEquals(false, execution.finalResult().get(PYTHON_IS_SUCCESS));
		assertEquals(true, execution.finalResult().get(PYTHON_FALLBACK_MODE));
	}

	@Test
	void apply_jsonParseFailure_usesRawOutput() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		String rawOutput = "not json content";
		when(pythonCodeExecutor.runTask(any())).thenReturn(PythonCodeExecutorService.TaskResponse.success(rawOutput));
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(null);

		NodeExecution execution = execute(pythonExecuteNode.apply(state), PYTHON_EXECUTE_NODE_OUTPUT);
		assertEquals(rawOutput, execution.finalResult().get(PYTHON_EXECUTE_NODE_OUTPUT));
		assertEquals(true, execution.finalResult().get(PYTHON_IS_SUCCESS));
	}

	@Test
	void apply_unicodeInOutput_handlesCorrectly() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PYTHON_GENERATE_NODE_OUTPUT, "print('你好世界')"));

		String unicodeOutput = "{\"message\": \"\\u4f60\\u597d\\u4e16\\u754c\"}";
		when(pythonCodeExecutor.runTask(any()))
			.thenReturn(PythonCodeExecutorService.TaskResponse.success(unicodeOutput));

		Map<String, Object> parsed = Map.of("message", "你好世界");
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(parsed);

		NodeExecution execution = execute(pythonExecuteNode.apply(state), PYTHON_EXECUTE_NODE_OUTPUT);
		assertEquals("{\"message\":\"你好世界\"}", execution.finalResult().get(PYTHON_EXECUTE_NODE_OUTPUT));
		assertEquals(true, execution.finalResult().get(PYTHON_IS_SUCCESS));
	}

	@Test
	void apply_emptyOutput_setsEmptyResult() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		when(pythonCodeExecutor.runTask(any())).thenReturn(PythonCodeExecutorService.TaskResponse.success(""));
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(null);

		NodeExecution execution = execute(pythonExecuteNode.apply(state), PYTHON_EXECUTE_NODE_OUTPUT);
		assertEquals("", execution.finalResult().get(PYTHON_EXECUTE_NODE_OUTPUT));
		assertEquals(true, execution.finalResult().get(PYTHON_IS_SUCCESS));
	}

	@Test
	void apply_withMultipleSqlResults_passesOrderedResultSetsToPython() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PYTHON_GENERATE_NODE_OUTPUT, "print('processed')"));
		state.updateState(Map.of(SQL_RESULT_LIST_MEMORY,
				List.of(Map.of("department", "engineering", "headcount", "20")), SQL_EXECUTE_NODE_OUTPUT,
				Map.of("step_2", "{\"data\":[{\"department\":\"engineering\",\"headcount\":\"20\"}]}", "step_1",
						"{\"data\":[{\"department\":\"sales\",\"revenue\":\"100\"}]}")));

		when(pythonCodeExecutor.runTask(any())).thenReturn(PythonCodeExecutorService.TaskResponse.success("processed"));
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(null);

		NodeExecution execution = execute(pythonExecuteNode.apply(state), PYTHON_EXECUTE_NODE_OUTPUT);
		assertEquals("processed", execution.finalResult().get(PYTHON_EXECUTE_NODE_OUTPUT));

		ArgumentCaptor<PythonCodeExecutorService.TaskRequest> request = ArgumentCaptor
			.forClass(PythonCodeExecutorService.TaskRequest.class);
		verify(pythonCodeExecutor).runTask(request.capture());
		String input = request.getValue().input();
		String message = "Expected ordered sales and engineering result sets, but Python received: " + input;
		assertTrue(input.startsWith("[[{"), message);
		assertTrue(input.indexOf("sales") < input.indexOf("engineering"), message);
		assertTrue(input.endsWith("}]]"), message);
	}

	@Test
	void apply_largeOutput_handlesMemoryPressure() throws Exception {
		OverAllState state = createTestState();
		setupBasicState(state);

		StringBuilder largeOutput = new StringBuilder();
		for (int i = 0; i < 10000; i++) {
			largeOutput.append("line ").append(i).append(": data_value_").append(i).append("\n");
		}

		when(pythonCodeExecutor.runTask(any()))
			.thenReturn(PythonCodeExecutorService.TaskResponse.success(largeOutput.toString()));
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(null);

		NodeExecution execution = execute(pythonExecuteNode.apply(state), PYTHON_EXECUTE_NODE_OUTPUT);
		assertEquals(largeOutput.toString(), execution.finalResult().get(PYTHON_EXECUTE_NODE_OUTPUT));
		assertEquals(true, execution.finalResult().get(PYTHON_IS_SUCCESS));
	}

	@Test
	void apply_pep723Dependencies_passesStructuredDependenciesToSandbox() throws Exception {
		OverAllState state = createTestState();
		String code = """
				# /// script
				# dependencies = ["pandas>=2,<3"]
				# ///
				print("{}")
				""";
		state.updateState(Map.of(PYTHON_GENERATE_NODE_OUTPUT, code));
		when(pythonCodeExecutor.runTask(any())).thenReturn(PythonCodeExecutorService.TaskResponse.success("{}"));
		when(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).thenReturn(null);

		NodeExecution execution = execute(pythonExecuteNode.apply(state), PYTHON_EXECUTE_NODE_OUTPUT);
		assertEquals("{}", execution.finalResult().get(PYTHON_EXECUTE_NODE_OUTPUT));

		ArgumentCaptor<PythonCodeExecutorService.TaskRequest> requestCaptor = ArgumentCaptor
			.forClass(PythonCodeExecutorService.TaskRequest.class);
		org.mockito.Mockito.verify(pythonCodeExecutor).runTask(requestCaptor.capture());
		assertEquals(List.of("pandas>=2,<3"), requestCaptor.getValue().dependencies());
	}

}
