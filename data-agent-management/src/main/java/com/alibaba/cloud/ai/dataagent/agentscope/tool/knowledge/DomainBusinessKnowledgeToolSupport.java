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

import com.alibaba.cloud.ai.dataagent.agentscope.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.agentscope.runtime.ToolContextRequestResolver;
import com.alibaba.cloud.ai.dataagent.service.knowledge.DomainKnowledgeSearchService;
import com.alibaba.cloud.ai.dataagent.service.knowledge.DomainKnowledgeSearchService.DomainKnowledgeSearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class DomainBusinessKnowledgeToolSupport {

	public static final String INPUT_SCHEMA = """
			{
			  "type": "object",
			  "properties": {
			    "query": {
			      "type": "string",
			      "description": "必填。需要检索的业务问题、指标名、术语、SOP 主题或案例主题。"
			    },
			    "knowledgeTypes": {
			      "type": "array",
			      "description": "可选。限定知识范围。支持 businessTerm、agentKnowledge、document、qa、faq、all。",
			      "items": {
			        "type": "string"
			      }
			    },
			    "topK": {
			      "type": "integer",
			      "description": "可选。返回条数，默认 5，最大 8。"
			    },
			    "similarityThreshold": {
			      "type": "number",
			      "description": "可选。相似度阈值，范围 0 到 1，默认 0.2。"
			    }
			  },
			  "required": ["query"]
			}
			""";

	private final ObjectMapper objectMapper;

	private final DomainKnowledgeSearchService domainKnowledgeSearchService;

	public ToolCallback createSearchToolCallback(String agentId, String toolName, String description) {
		ToolDefinition toolDefinition = ToolDefinition.builder()
			.name(toolName)
			.description(description)
			.inputSchema(INPUT_SCHEMA)
			.build();
		return new DomainBusinessKnowledgeSearchToolCallback(toolDefinition, objectMapper, agentId,
				domainKnowledgeSearchService);
	}

	private static final class DomainBusinessKnowledgeSearchToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		private final ObjectMapper objectMapper;

		private final String agentId;

		private final DomainKnowledgeSearchService domainKnowledgeSearchService;

		private DomainBusinessKnowledgeSearchToolCallback(ToolDefinition toolDefinition, ObjectMapper objectMapper,
				String agentId, DomainKnowledgeSearchService domainKnowledgeSearchService) {
			this.toolDefinition = toolDefinition;
			this.objectMapper = objectMapper;
			this.agentId = agentId;
			this.domainKnowledgeSearchService = domainKnowledgeSearchService;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			return call(toolInput, null);
		}

		@Override
		public String call(String toolInput, ToolContext toolContext) {
			try {
				JsonNode jsonNode = StringUtils.hasText(toolInput) ? objectMapper.readTree(toolInput)
						: objectMapper.createObjectNode();
				String query = jsonNode.path("query").asText("");
				List<String> knowledgeTypes = new ArrayList<>();
				JsonNode knowledgeTypesNode = jsonNode.path("knowledgeTypes");
				if (knowledgeTypesNode.isArray()) {
					for (JsonNode node : knowledgeTypesNode) {
						if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
							knowledgeTypes.add(node.asText());
						}
					}
				}
				Integer topK = jsonNode.has("topK") && jsonNode.get("topK").canConvertToInt()
						? jsonNode.get("topK").asInt() : null;
				Double similarityThreshold = jsonNode.has("similarityThreshold")
						&& jsonNode.get("similarityThreshold").isNumber()
								? jsonNode.get("similarityThreshold").asDouble() : null;

				DomainKnowledgeSearchRequest request = new DomainKnowledgeSearchRequest(query,
						knowledgeTypes.isEmpty() ? null : List.copyOf(knowledgeTypes), topK, similarityThreshold);
				GraphRequest graphRequest = ToolContextRequestResolver.resolveGraphRequest(toolContext);
				return objectMapper.writeValueAsString(domainKnowledgeSearchService.search(agentId, request, graphRequest));
			}
			catch (Exception ex) {
				throw new IllegalStateException("Failed to search domain business knowledge: " + ex.getMessage(), ex);
			}
		}

	}

}
