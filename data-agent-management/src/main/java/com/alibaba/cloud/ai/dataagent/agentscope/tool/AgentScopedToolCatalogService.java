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
package com.alibaba.cloud.ai.dataagent.agentscope.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentScopedToolCatalogService {

	private final List<AgentScopedToolProvider> providers;

	public Map<String, ToolCallback> getToolCallbacks(String agentId) {
		if (!StringUtils.hasText(agentId)) {
			return Collections.emptyMap();
		}
		Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
		for (AgentScopedToolProvider provider : providers) {
			Map<String, ToolCallback> providedCallbacks = provider.getToolCallbacks(agentId);
			if (providedCallbacks == null || providedCallbacks.isEmpty()) {
				continue;
			}
			providedCallbacks.forEach((name, toolCallback) -> register(callbacks, name, toolCallback, provider));
		}
		return Collections.unmodifiableMap(callbacks);
	}

	private void register(Map<String, ToolCallback> callbacks, String name, ToolCallback toolCallback,
			AgentScopedToolProvider provider) {
		if (!StringUtils.hasText(name) || toolCallback == null) {
			return;
		}
		ToolCallback previous = callbacks.putIfAbsent(name, toolCallback);
		if (previous != null && previous != toolCallback) {
			log.warn("Duplicate agent-scoped tool name detected, keep first one. agentTool={}, provider={}", name,
					provider.getClass().getName());
		}
	}

}
