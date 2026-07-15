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
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.CLARIFICATION_NODE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.FEASIBILITY_ASSESSMENT_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.PLANNER_NODE;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

@Slf4j
public class FeasibilityAssessmentDispatcher implements EdgeAction {

	@Override
	public String apply(OverAllState state) throws Exception {
		FeasibilityAssessmentOutputDTO result = StateUtil.getObjectValue(state, FEASIBILITY_ASSESSMENT_NODE_OUTPUT,
				FeasibilityAssessmentOutputDTO.class, (FeasibilityAssessmentOutputDTO) null);
		if (result == null || result.getRequestType() == null || result.getRequestType().trim().isEmpty()) {
			log.warn("Feasibility assessment result is null or empty, defaulting to END");
			return END;
		}

		String requestType = result.getRequestType().trim();
		if (FeasibilityAssessmentOutputDTO.DATA_ANALYSIS.equalsIgnoreCase(requestType)) {
			log.info("[FeasibilityAssessmentDispatcher] requestType=DATA_ANALYSIS, routing to PlannerNode");
			return PLANNER_NODE;
		}
		if (FeasibilityAssessmentOutputDTO.NEED_CLARIFICATION.equalsIgnoreCase(requestType)) {
			log.info("[FeasibilityAssessmentDispatcher] requestType=NEED_CLARIFICATION, routing to ClarificationNode");
			return CLARIFICATION_NODE;
		}

		log.info("[FeasibilityAssessmentDispatcher] requestType={}, routing to END", requestType);
		return END;
	}

}
