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
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TableRelationDispatcherTest {

	private TableRelationDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new TableRelationDispatcher();
	}

	@Test
	void apply_noError_withOutput_routesToFeasibilityAssessment() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(TABLE_RELATION_EXCEPTION_OUTPUT, "",
				TABLE_RELATION_RETRY_COUNT, 0, TABLE_RELATION_OUTPUT, "schema data"));

		assertEquals(FEASIBILITY_ASSESSMENT_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_retryableError_underMaxRetry_routesToTableRelation() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(TABLE_RELATION_EXCEPTION_OUTPUT,
				"RETRYABLE: connection timeout", TABLE_RELATION_RETRY_COUNT, 1));

		assertEquals(TABLE_RELATION_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_retryableError_atMaxRetry_routesToEnd() throws Exception {
		OverAllState state = TestFixtures.createStateWith(Map.of(TABLE_RELATION_EXCEPTION_OUTPUT,
				"RETRYABLE: connection timeout", TABLE_RELATION_RETRY_COUNT, 3));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_nonRetryableError_routesToEnd() throws Exception {
		OverAllState state = TestFixtures.createStateWith(
				Map.of(TABLE_RELATION_EXCEPTION_OUTPUT, "FATAL: schema not found", TABLE_RELATION_RETRY_COUNT, 0));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_noErrorNoOutput_routesToEnd() throws Exception {
		OverAllState state = TestFixtures
			.createStateWith(Map.of(TABLE_RELATION_EXCEPTION_OUTPUT, "", TABLE_RELATION_RETRY_COUNT, 0));

		assertEquals(END, dispatcher.apply(state));
	}

}
