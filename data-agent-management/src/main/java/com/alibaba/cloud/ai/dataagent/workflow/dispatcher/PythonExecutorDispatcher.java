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
 * @author vlsmb
 * @since 2025/7/29
 */
@Slf4j
public class PythonExecutorDispatcher implements EdgeAction {

	@Override
	public String apply(OverAllState state) throws Exception {
		// Determine if failed
		boolean isSuccess = StateUtil.getObjectValue(state, PYTHON_IS_SUCCESS, Boolean.class, false);
		if (!isSuccess) {
			String message = StateUtil.getStringValue(state, PYTHON_EXECUTE_NODE_OUTPUT);
			log.error("Python Executor Node Error: {}", message);
			int tries = StateUtil.getObjectValue(state, PYTHON_TRIES_COUNT, Integer.class, 0);
			if (tries <= 0) {
				log.warn("Python Executor Node Error: Exceeding the maximum number of iterations");
				return END;
			}
			else {
				// Regenerate code for testing
				return PYTHON_GENERATE_NODE;
			}
		}
		// Go to code execution result analysis node
		return PYTHON_ANALYZE_NODE;
	}

}
