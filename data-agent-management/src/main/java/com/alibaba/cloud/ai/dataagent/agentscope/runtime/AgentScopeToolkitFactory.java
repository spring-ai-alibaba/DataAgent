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
package com.alibaba.cloud.ai.dataagent.agentscope.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Toolkit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentScopeToolkitFactory {

	private final GenericApplicationContext applicationContext;

	private final ObjectMapper objectMapper;

	public Toolkit create() {
		Toolkit toolkit = new Toolkit();
		Map<String, ToolCallback> toolCallbacks = getToolCallbacks();
		toolCallbacks.values()
			.forEach(toolCallback -> toolkit.registerAgentTool(new SpringToolCallbackAgentAdapter(toolCallback,
					objectMapper)));
		log.debug("Mapped {} Spring AI tool callbacks into AgentScope toolkit", toolCallbacks.size());
		return toolkit;
	}

	public Map<String, ToolCallback> getToolCallbacks() {
		return Collections.unmodifiableMap(collectToolCallbacks());
	}

	private Map<String, ToolCallback> collectToolCallbacks() {
		Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
		for (String beanName : applicationContext.getBeanNamesForType(ToolCallback.class)) {
			register(callbacks, applicationContext.getBean(beanName, ToolCallback.class));
		}
		for (String beanName : applicationContext.getBeanNamesForType(ToolCallbackProvider.class)) {
			ToolCallbackProvider provider = applicationContext.getBean(beanName, ToolCallbackProvider.class);
			for (ToolCallback toolCallback : provider.getToolCallbacks()) {
				register(callbacks, toolCallback);
			}
		}
		return callbacks;
	}

	private void register(Map<String, ToolCallback> callbacks, ToolCallback toolCallback) {
		if (toolCallback == null || toolCallback.getToolDefinition() == null
				|| toolCallback.getToolDefinition().name() == null) {
			return;
		}
		callbacks.putIfAbsent(toolCallback.getToolDefinition().name(), toolCallback);
	}

}
