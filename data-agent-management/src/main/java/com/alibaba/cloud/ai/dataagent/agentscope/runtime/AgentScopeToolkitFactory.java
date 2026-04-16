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

import com.alibaba.cloud.ai.dataagent.agentscope.tool.AgentScopedToolCatalogService;
import com.alibaba.cloud.ai.dataagent.util.McpServerToolUtil;
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

	private final AgentScopedToolCatalogService agentScopedToolCatalogService;

	private final GenericApplicationContext applicationContext;

	private final ObjectMapper objectMapper;

	public Toolkit create(String agentId) {
		Toolkit toolkit = new Toolkit();
		Map<String, ToolCallback> toolCallbacks = getToolCallbacks(agentId);
		toolCallbacks.values()
			.forEach(toolCallback -> toolkit
				.registerAgentTool(new SpringToolCallbackAgentAdapter(toolCallback, objectMapper)));
		log.debug("Mapped {} Spring AI tool callbacks into AgentScope toolkit, agentId={}", toolCallbacks.size(),
				agentId);
		return toolkit;
	}

	public Map<String, ToolCallback> getToolCallbacks(String agentId) {
		return Collections.unmodifiableMap(collectToolCallbacks(agentId));
	}

	private Map<String, ToolCallback> collectToolCallbacks(String agentId) {
		Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
		for (ToolCallback toolCallback : McpServerToolUtil.excludeMcpServerTool(applicationContext,
				ToolCallback.class)) {
			register(callbacks, toolCallback);
		}
		for (ToolCallbackProvider provider : McpServerToolUtil.excludeMcpServerTool(applicationContext,
				ToolCallbackProvider.class)) {
			for (ToolCallback toolCallback : provider.getToolCallbacks()) {
				register(callbacks, toolCallback);
			}
		}
		agentScopedToolCatalogService.getToolCallbacks(agentId)
			.forEach((toolName, toolCallback) -> register(callbacks, toolName, toolCallback));
		return callbacks;
	}

	private void register(Map<String, ToolCallback> callbacks, ToolCallback toolCallback) {
		if (toolCallback == null || toolCallback.getToolDefinition() == null
				|| toolCallback.getToolDefinition().name() == null) {
			return;
		}
		register(callbacks, toolCallback.getToolDefinition().name(), toolCallback);
	}

	private void register(Map<String, ToolCallback> callbacks, String toolName, ToolCallback toolCallback) {
		if (toolCallback == null || toolName == null) {
			return;
		}
		ToolCallback previous = callbacks.putIfAbsent(toolName, toolCallback);
		if (previous != null && previous != toolCallback) {
			log.warn("Duplicate Spring AI tool callback detected, keep first one. toolName={}", toolName);
		}
	}

}
