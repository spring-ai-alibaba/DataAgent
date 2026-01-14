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
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * @author vlsmb
 * @since 2025/7/29
 */
@Slf4j
public class PythonExecutorDispatcher implements EdgeAction {

	private final CodeExecutorProperties codeExecutorProperties;

	public PythonExecutorDispatcher(CodeExecutorProperties codeExecutorProperties) {
		this.codeExecutorProperties = codeExecutorProperties;
	}

	@Override
	public String apply(OverAllState state) throws Exception {
		boolean isFallbackMode = StateUtil.getObjectValue(state, PYTHON_FALLBACK_MODE, Boolean.class, false);
		if (isFallbackMode) {
			log.warn("Python执行进入降级模式，跳过重试直接进入分析节点");
			return PYTHON_ANALYZE_NODE;
		}

		// Determine if failed
		boolean isSuccess = StateUtil.getObjectValue(state, PYTHON_IS_SUCCESS, Boolean.class, false);
		if (!isSuccess) {
			String message = StateUtil.getStringValue(state, PYTHON_EXECUTE_NODE_OUTPUT);
			log.error("Python Executor Node Error: {}", message);
			int tries = StateUtil.getObjectValue(state, PYTHON_TRIES_COUNT, Integer.class, 0);
			if (tries >= codeExecutorProperties.getPythonMaxTriesCount()) {
				log.error("Python执行失败且已超过最大重试次数（已尝试次数：{}），流程终止", tries);
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
