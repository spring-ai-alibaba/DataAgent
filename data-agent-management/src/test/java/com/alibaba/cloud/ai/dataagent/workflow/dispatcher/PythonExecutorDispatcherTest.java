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
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PythonExecutorDispatcherTest {

	@Mock
	private CodeExecutorProperties codeExecutorProperties;

	private PythonExecutorDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new PythonExecutorDispatcher(codeExecutorProperties);
	}

	@Test
	void apply_successTrue_routesToPythonAnalyze() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(PYTHON_IS_SUCCESS, true, PYTHON_FALLBACK_MODE, false));

		assertEquals(PYTHON_ANALYZE_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_fallbackMode_routesToPythonAnalyze() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(PYTHON_FALLBACK_MODE, true, PYTHON_IS_SUCCESS, false));

		assertEquals(PYTHON_ANALYZE_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_failedUnderMaxRetry_routesToPythonGenerate() throws Exception {
		when(codeExecutorProperties.getPythonMaxTriesCount()).thenReturn(3);
		OverAllState state = TestFixtures.createStateWith(Map.of(PYTHON_IS_SUCCESS, false, PYTHON_FALLBACK_MODE, false,
				PYTHON_EXECUTE_NODE_OUTPUT, "RuntimeError", PYTHON_TRIES_COUNT, 1));

		assertEquals(PYTHON_GENERATE_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_failedAtMaxRetry_routesToEnd() throws Exception {
		when(codeExecutorProperties.getPythonMaxTriesCount()).thenReturn(3);
		OverAllState state = TestFixtures.createStateWith(Map.of(PYTHON_IS_SUCCESS, false, PYTHON_FALLBACK_MODE, false,
				PYTHON_EXECUTE_NODE_OUTPUT, "RuntimeError", PYTHON_TRIES_COUNT, 3));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_failedAboveMaxRetry_routesToEnd() throws Exception {
		when(codeExecutorProperties.getPythonMaxTriesCount()).thenReturn(3);
		OverAllState state = TestFixtures.createStateWith(Map.of(PYTHON_IS_SUCCESS, false, PYTHON_FALLBACK_MODE, false,
				PYTHON_EXECUTE_NODE_OUTPUT, "RuntimeError", PYTHON_TRIES_COUNT, 5));

		assertEquals(END, dispatcher.apply(state));
	}

}
