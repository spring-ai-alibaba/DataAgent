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
package com.alibaba.cloud.ai.dataagent.agentscope.tool.knowledge;

import com.alibaba.cloud.ai.dataagent.agentscope.tool.AgentScopedToolProvider;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class DomainBusinessKnowledgeToolProvider implements AgentScopedToolProvider {

	private static final String TOOL_NAME = "domain_business_knowledge.search";

	private static final String DESCRIPTION = """
			Search the current agent's recalled business terms, FAQs, QA entries, and embedded documents on demand.
			Use this tool when you need internal business definitions, metric rules, SOPs, historical cases, or terminology clarification.
			Call it only when the answer depends on domain knowledge instead of general reasoning.
			""";

	private final DomainBusinessKnowledgeToolSupport toolSupport;

	@Override
	public Map<String, ToolCallback> getToolCallbacks(String agentId) {
		if (!StringUtils.hasText(agentId)) {
			return Map.of();
		}
		return Map.of(TOOL_NAME, toolSupport.createSearchToolCallback(agentId, TOOL_NAME, DESCRIPTION));
	}

}
