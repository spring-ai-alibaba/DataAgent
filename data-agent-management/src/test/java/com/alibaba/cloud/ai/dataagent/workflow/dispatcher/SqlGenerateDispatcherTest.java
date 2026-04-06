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

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
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
class SqlGenerateDispatcherTest {

	@Mock
	private DataAgentProperties properties;

	private SqlGenerateDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new SqlGenerateDispatcher(properties);
	}

	@Test
	void apply_validSqlOutput_routesToSemanticConsistency() {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(SQL_GENERATE_OUTPUT, "SELECT * FROM users"));

		assertEquals(SEMANTIC_CONSISTENCY_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_endMarkerInSql_routesToEnd() {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(SQL_GENERATE_OUTPUT, END));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_emptySqlOutput_underMaxRetry_routesToSqlGenerate() {
		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		OverAllState state = new OverAllState();
		state.updateState(Map.of(SQL_GENERATE_COUNT, 2));

		assertEquals(SQL_GENERATE_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_emptySqlOutput_atMaxRetry_routesToEnd() {
		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		OverAllState state = new OverAllState();
		state.updateState(Map.of(SQL_GENERATE_COUNT, 10));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_emptySqlOutput_missingCount_defaultsToMax_routesToEnd() {
		when(properties.getMaxSqlRetryCount()).thenReturn(10);
		OverAllState state = new OverAllState();

		assertEquals(END, dispatcher.apply(state));
	}

}
