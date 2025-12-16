/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.dataagent.common.util.StateUtil;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.dataagent.common.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * Dispatches to the next node based on the plan execution and validation status.
 *
 * @author zhangshenghang
 */
@Slf4j
public class PlanExecutorDispatcher implements EdgeAction {

	private static final int MAX_REPAIR_ATTEMPTS = 2;

	@Override
	public String apply(OverAllState state) {
		boolean validationPassed = StateUtil.getObjectValue(state, PLAN_VALIDATION_STATUS, Boolean.class, false);

		if (validationPassed) {
			log.info("Plan validation passed. Proceeding to next step.");
			String nextNode = state.value(PLAN_NEXT_NODE, END);
			// 如果返回的是"END"，直接返回END常量
			if ("END".equals(nextNode)) {
				log.info("Plan execution completed successfully.");
				return END;
			}
			return nextNode;
		}
		else {
			// Plan validation failed, check repair count and decide whether to retry or
			// end.
			int repairCount = StateUtil.getObjectValue(state, PLAN_REPAIR_COUNT, Integer.class, 0);

			if (repairCount > MAX_REPAIR_ATTEMPTS) {
				log.error("Plan repair attempts exceeded the limit of {}. Terminating execution.", MAX_REPAIR_ATTEMPTS);
				// The node is responsible for setting the final error message.
				return END;
			}

			log.warn("Plan validation failed. Routing back to PlannerNode for repair. Attempt count from state: {}.",
					repairCount);
			return PLANNER_NODE;
		}
	}

}
