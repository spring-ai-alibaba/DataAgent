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
import com.alibaba.cloud.ai.dataagent.util.StateUtil;

import java.util.Optional;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

public class TableRelationDispatcher implements EdgeAction {

	private static final int MAX_RETRY_COUNT = 3;

	@Override
	public String apply(OverAllState state) throws Exception {

		String errorFlag = StateUtil.getStringValue(state, TABLE_RELATION_EXCEPTION_OUTPUT, null);
		Integer retryCount = StateUtil.getObjectValue(state, TABLE_RELATION_RETRY_COUNT, Integer.class, 0);

		if (errorFlag != null && !errorFlag.isEmpty()) {
			if (isRetryableError(errorFlag) && retryCount < MAX_RETRY_COUNT) {
				return TABLE_RELATION_NODE;
			}
			else {
				return END;
			}
		}

		Optional<String> tableRelationOutput = state.value(TABLE_RELATION_OUTPUT);
		if (tableRelationOutput.isPresent()) {
			return FEASIBILITY_ASSESSMENT_NODE;
		}

		// no output, end
		return END;
	}

	private boolean isRetryableError(String errorMessage) {
		return errorMessage.startsWith("RETRYABLE:");
	}

}
