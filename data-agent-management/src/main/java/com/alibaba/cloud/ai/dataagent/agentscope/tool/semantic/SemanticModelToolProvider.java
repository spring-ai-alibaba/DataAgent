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
package com.alibaba.cloud.ai.dataagent.agentscope.tool.semantic;

import com.alibaba.cloud.ai.dataagent.agentscope.tool.AgentScopedToolProvider;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class SemanticModelToolProvider implements AgentScopedToolProvider {

	private static final String TOOL_NAME = "semantic_model.search";

	private static final String DESCRIPTION = """
			Supplemental semantic hint tool for table/column understanding only.
			Use this tool when the user asks what a table/column means, asks for a business-friendly name, asks for enum meaning, asks for field usage notes, or asks for relation hints that are not explicitly stored in the physical schema.
			Typical examples: "token名称类型", "status字段什么意思", "这个字段有哪些别名", "这两个表可能怎么关联".
			Use datasource explorer first for physical schema, column lists, data types already in the database, previews, and readonly SQL.
			Do not use this tool for SQL execution, schema discovery already covered by datasource explorer, or business definitions/metric rules/SOPs that belong to domain_business_knowledge.search.
			""";

	private final SemanticModelToolSupport toolSupport;

	@Override
	public Map<String, ToolCallback> getToolCallbacks(String agentId) {
		if (!StringUtils.hasText(agentId)) {
			return Map.of();
		}
		return Map.of(TOOL_NAME, toolSupport.createSearchToolCallback(agentId, TOOL_NAME, DESCRIPTION));
	}

}
