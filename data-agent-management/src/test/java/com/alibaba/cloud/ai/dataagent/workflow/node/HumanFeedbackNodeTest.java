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
package com.alibaba.cloud.ai.dataagent.workflow.node;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HumanFeedbackNodeTest {

	private HumanFeedbackNode humanFeedbackNode;

	@BeforeEach
	void setUp() {
		humanFeedbackNode = new HumanFeedbackNode();
	}

	private OverAllState createTestState() {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(PLAN_REPAIR_COUNT, new ReplaceStrategy());
		state.registerKeyAndStrategy(HUMAN_FEEDBACK_DATA, new ReplaceStrategy());
		state.registerKeyAndStrategy(HUMAN_REVIEW_ENABLED, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLAN_CURRENT_STEP, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLAN_VALIDATION_ERROR, new ReplaceStrategy());
		state.registerKeyAndStrategy(PLANNER_NODE_OUTPUT, new ReplaceStrategy());
		return state;
	}

	@Test
	void apply_approvedFeedback_routesToPlanExecutor() throws Exception {
		OverAllState state = createTestState();
		Map<String, Object> feedbackData = new HashMap<>();
		feedbackData.put("feedback", true);
		state.updateState(Map.of(HUMAN_FEEDBACK_DATA, feedbackData, PLAN_REPAIR_COUNT, 0));

		Map<String, Object> result = humanFeedbackNode.apply(state);

		assertEquals(PLAN_EXECUTOR_NODE, result.get("human_next_node"));
		assertEquals(false, result.get(HUMAN_REVIEW_ENABLED));
	}

	@Test
	void apply_rejectedFeedback_routesToPlanner() throws Exception {
		OverAllState state = createTestState();
		Map<String, Object> feedbackData = new HashMap<>();
		feedbackData.put("feedback", false);
		feedbackData.put("feedback_content", "需要增加过滤条件");
		state.updateState(Map.of(HUMAN_FEEDBACK_DATA, feedbackData, PLAN_REPAIR_COUNT, 0));

		Map<String, Object> result = humanFeedbackNode.apply(state);

		assertEquals(PLANNER_NODE, result.get("human_next_node"));
		assertEquals(1, result.get(PLAN_REPAIR_COUNT));
		assertEquals(1, result.get(PLAN_CURRENT_STEP));
		assertEquals(true, result.get(HUMAN_REVIEW_ENABLED));
		assertEquals("需要增加过滤条件", result.get(PLAN_VALIDATION_ERROR));
		assertEquals("", result.get(PLANNER_NODE_OUTPUT));
	}

	@Test
	void apply_emptyFeedback_waitsForFeedback() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PLAN_REPAIR_COUNT, 0));

		Map<String, Object> result = humanFeedbackNode.apply(state);

		assertEquals("WAIT_FOR_FEEDBACK", result.get("human_next_node"));
	}

	@Test
	void apply_maxRepairExceeded_routesToEnd() throws Exception {
		OverAllState state = createTestState();
		state.updateState(Map.of(PLAN_REPAIR_COUNT, 3));

		Map<String, Object> result = humanFeedbackNode.apply(state);

		assertEquals("END", result.get("human_next_node"));
	}

	@Test
	void apply_repairCountIncrement_incrementsCorrectly() throws Exception {
		OverAllState state = createTestState();
		Map<String, Object> feedbackData = new HashMap<>();
		feedbackData.put("feedback", false);
		state.updateState(Map.of(HUMAN_FEEDBACK_DATA, feedbackData, PLAN_REPAIR_COUNT, 1));

		Map<String, Object> result = humanFeedbackNode.apply(state);

		assertEquals(PLANNER_NODE, result.get("human_next_node"));
		assertEquals(2, result.get(PLAN_REPAIR_COUNT));
	}

	@Test
	void apply_rejectedWithoutContent_usesDefaultMessage() throws Exception {
		OverAllState state = createTestState();
		Map<String, Object> feedbackData = new HashMap<>();
		feedbackData.put("feedback", false);
		state.updateState(Map.of(HUMAN_FEEDBACK_DATA, feedbackData, PLAN_REPAIR_COUNT, 0));

		Map<String, Object> result = humanFeedbackNode.apply(state);

		assertEquals("Plan rejected by user", result.get(PLAN_VALIDATION_ERROR));
	}

	@Test
	void apply_feedbackAsStringTrue_treatedAsApproved() throws Exception {
		OverAllState state = createTestState();
		Map<String, Object> feedbackData = new HashMap<>();
		feedbackData.put("feedback", "true");
		state.updateState(Map.of(HUMAN_FEEDBACK_DATA, feedbackData, PLAN_REPAIR_COUNT, 0));

		Map<String, Object> result = humanFeedbackNode.apply(state);

		assertEquals(PLAN_EXECUTOR_NODE, result.get("human_next_node"));
	}

	@Test
	void apply_repairCountAtBoundary_routesToEnd() throws Exception {
		OverAllState state = createTestState();
		Map<String, Object> feedbackData = new HashMap<>();
		feedbackData.put("feedback", false);
		state.updateState(Map.of(HUMAN_FEEDBACK_DATA, feedbackData, PLAN_REPAIR_COUNT, 3));

		Map<String, Object> result = humanFeedbackNode.apply(state);

		assertEquals("END", result.get("human_next_node"));
	}

}
