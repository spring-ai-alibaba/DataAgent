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

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SemanticConsistenceDispatcherTest {

	private SemanticConsistenceDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new SemanticConsistenceDispatcher();
	}

	@Test
	void apply_consistencyPassed_routesToSqlExecute() {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, true));

		assertEquals(SQL_EXECUTE_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_consistencyFailed_routesToSqlGenerate() {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, false));

		assertEquals(SQL_GENERATE_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_missingOutput_defaultsFalse_routesToSqlGenerate() {
		OverAllState state = new OverAllState();

		assertEquals(SQL_GENERATE_NODE, dispatcher.apply(state));
	}

}
