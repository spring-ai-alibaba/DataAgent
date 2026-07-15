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

import com.alibaba.cloud.ai.dataagent.dto.prompt.FeasibilityAssessmentOutputDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.CLARIFICATION_NODE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.FEASIBILITY_ASSESSMENT_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.PLANNER_NODE;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FeasibilityAssessmentDispatcherTest {

	private FeasibilityAssessmentDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new FeasibilityAssessmentDispatcher();
	}

	@Test
	void apply_dataAnalysisOutput_routesToPlannerNode() throws Exception {
		OverAllState state = new OverAllState();
		FeasibilityAssessmentOutputDTO dto = new FeasibilityAssessmentOutputDTO();
		dto.setRequestType(FeasibilityAssessmentOutputDTO.DATA_ANALYSIS);
		dto.setContent("查询销售额");
		state.updateState(Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, dto));

		assertEquals(PLANNER_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_needClarification_routesToClarificationNode() throws Exception {
		OverAllState state = new OverAllState();
		FeasibilityAssessmentOutputDTO dto = new FeasibilityAssessmentOutputDTO();
		dto.setRequestType(FeasibilityAssessmentOutputDTO.NEED_CLARIFICATION);
		dto.setContent("请问你想看哪个时间范围？");
		state.updateState(Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, dto));

		assertEquals(CLARIFICATION_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_chatOutput_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		FeasibilityAssessmentOutputDTO dto = new FeasibilityAssessmentOutputDTO();
		dto.setRequestType(FeasibilityAssessmentOutputDTO.CHIT_CHAT);
		dto.setContent("你好");
		state.updateState(Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, dto));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_missingOutput_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();

		assertEquals(END, dispatcher.apply(state));
	}

}
