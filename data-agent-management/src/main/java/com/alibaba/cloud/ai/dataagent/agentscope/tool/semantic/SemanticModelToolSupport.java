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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class SemanticModelToolSupport {

	public static final String INPUT_SCHEMA = """
			{
			  "type": "object",
			  "properties": {
			    "query": {
			      "type": "string",
			      "description": "必填。用于补充理解表/列含义的关键词、字段名、别名或枚举说明。"
			    },
			    "tableNames": {
			      "type": "array",
			      "description": "可选。将检索范围限制在这些表内；如果 datasource explorer 已能定位表结构，则不必传该工具。",
			      "items": {
			        "type": "string"
			      }
			    }
			  },
			  "required": ["query"]
			}
			""";

	private final ObjectMapper objectMapper;

	private final SemanticModelSearchService semanticModelSearchService;

	public ToolCallback createSearchToolCallback(String agentId, String toolName, String description) {
		ToolDefinition toolDefinition = ToolDefinition.builder()
			.name(toolName)
			.description(description)
			.inputSchema(INPUT_SCHEMA)
			.build();
		return new SemanticModelSearchToolCallback(toolDefinition, objectMapper, agentId, semanticModelSearchService);
	}

	private static final class SemanticModelSearchToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		private final ObjectMapper objectMapper;

		private final String agentId;

		private final SemanticModelSearchService semanticModelSearchService;

		private SemanticModelSearchToolCallback(ToolDefinition toolDefinition, ObjectMapper objectMapper, String agentId,
				SemanticModelSearchService semanticModelSearchService) {
			this.toolDefinition = toolDefinition;
			this.objectMapper = objectMapper;
			this.agentId = agentId;
			this.semanticModelSearchService = semanticModelSearchService;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			try {
				SemanticModelSearchRequest request = StringUtils.hasText(toolInput)
						? objectMapper.readValue(toolInput, SemanticModelSearchRequest.class)
						: new SemanticModelSearchRequest();
				return objectMapper.writeValueAsString(semanticModelSearchService.search(agentId, request));
			}
			catch (Exception ex) {
				throw new IllegalStateException("Failed to search semantic model hints: " + ex.getMessage(), ex);
			}
		}

		@Override
		public String call(String toolInput, ToolContext toolContext) {
			return call(toolInput);
		}

	}

}
