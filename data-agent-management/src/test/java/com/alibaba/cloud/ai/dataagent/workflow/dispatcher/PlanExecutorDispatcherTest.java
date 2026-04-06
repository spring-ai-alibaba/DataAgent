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

class PlanExecutorDispatcherTest {

	private PlanExecutorDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new PlanExecutorDispatcher();
	}

	@Test
	void apply_validationPassed_routesToNextNode() {
		OverAllState state = TestFixtures
			.createStateWith(Map.of(PLAN_VALIDATION_STATUS, true, PLAN_NEXT_NODE, SQL_GENERATE_NODE));

		assertEquals(SQL_GENERATE_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_validationPassed_endNode_routesToEnd() {
		OverAllState state = TestFixtures.createStateWith(Map.of(PLAN_VALIDATION_STATUS, true, PLAN_NEXT_NODE, "END"));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_validationFailed_underMaxRepair_routesToPlanner() {
		OverAllState state = TestFixtures.createStateWith(Map.of(PLAN_VALIDATION_STATUS, false, PLAN_REPAIR_COUNT, 1));

		assertEquals(PLANNER_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_validationFailed_atMaxRepair_routesToPlanner() {
		OverAllState state = TestFixtures.createStateWith(Map.of(PLAN_VALIDATION_STATUS, false, PLAN_REPAIR_COUNT, 2));

		assertEquals(PLANNER_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_validationFailed_exceedsMaxRepair_routesToEnd() {
		OverAllState state = TestFixtures.createStateWith(Map.of(PLAN_VALIDATION_STATUS, false, PLAN_REPAIR_COUNT, 3));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_missingValidationStatus_defaultsFalse_routesToPlanner() {
		OverAllState state = TestFixtures.createStateWith(Map.of(PLAN_REPAIR_COUNT, 0));

		assertEquals(PLANNER_NODE, dispatcher.apply(state));
	}

}
