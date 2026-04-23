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
package com.alibaba.cloud.ai.dataagent.agentscope.tool.datasource;

import com.alibaba.cloud.ai.dataagent.agentscope.tool.AgentScopedToolProvider;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatasourceExplorerToolProvider implements AgentScopedToolProvider {

	private static final String INPUT_SCHEMA = """
			{
			  "type": "object",
			  "properties": {
			    "action": {
			      "type": "string",
			      "enum": [
			        "LIST_TABLES",
			        "FIND_TABLES",
			        "GET_TABLE_SCHEMA",
			        "GET_RELATED_TABLES",
			        "PREVIEW_ROWS",
			        "SEARCH"
			      ],
			      "description": "探索动作。推荐顺序：LIST_TABLES -> GET_TABLE_SCHEMA -> PREVIEW_ROWS/SEARCH"
			    },
			    "query": {
			      "type": "string",
			      "description": "用于 FIND_TABLES 的检索关键词"
			    },
			    "tableName": {
			      "type": "string",
			      "description": "目标表名。GET_TABLE_SCHEMA / GET_RELATED_TABLES / PREVIEW_ROWS 必填"
			    },
			    "tableNames": {
			      "type": "array",
			      "items": {
			        "type": "string"
			      },
			      "description": "可选表名列表。当前版本主要使用单表"
			    },
			    "sql": {
			      "type": "string",
			      "description": "SEARCH 动作需要的只读 SQL。仅允许 SELECT/WITH"
			    },
			    "limit": {
			      "type": "integer",
			      "description": "返回行数上限，默认 20，最大 200"
			    }
			  },
			  "required": [
			    "action"
			  ]
			}
			""";

	private final AgentDatasourceService agentDatasourceService;

	private final DatasourceExplorerService datasourceExplorerService;

	private final ObjectMapper objectMapper;

	@Override
	public Map<String, ToolCallback> getToolCallbacks(String agentId) {
		AgentDatasource agentDatasource = resolveActiveDatasource(agentId);
		if (agentDatasource == null || agentDatasource.getDatasource() == null) {
			return Map.of();
		}
		Datasource datasource = agentDatasource.getDatasource();
		String toolName = "datasource.%s.search".formatted(buildDatasourceSlug(datasource));
		String description = buildDescription(datasource, agentDatasource);
		ToolDefinition toolDefinition = ToolDefinition.builder()
			.name(toolName)
			.description(description)
			.inputSchema(INPUT_SCHEMA)
			.build();
		return Map.of(toolName, new AgentBoundDatasourceExplorerToolCallback(agentId, toolDefinition,
				datasourceExplorerService, objectMapper));
	}

	private AgentDatasource resolveActiveDatasource(String agentId) {
		if (!StringUtils.isNumeric(agentId)) {
			return null;
		}
		try {
			return agentDatasourceService.getCurrentAgentDatasource(Long.valueOf(agentId));
		}
		catch (Exception ex) {
			return null;
		}
	}

	private String buildDatasourceSlug(Datasource datasource) {
		String source = StringUtils.defaultIfBlank(datasource.getName(), "datasource");
		String slug = source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
		slug = slug.replaceAll("^_+|_+$", "");
		if (StringUtils.isBlank(slug)) {
			return "datasource_" + datasource.getId();
		}
		return slug;
	}

	private String buildDescription(Datasource datasource, AgentDatasource agentDatasource) {
		List<String> selectedTables = agentDatasource.getSelectTables() == null ? List.of()
				: agentDatasource.getSelectTables();
		String visibleTables = selectedTables.isEmpty() ? "当前未显式选表，将回退到数据源全部可见表" : "当前显式选表 %d 个：%s"
			.formatted(selectedTables.size(), String.join(", ", selectedTables.stream().limit(8).toList()));
		return """
				Unified explorer for datasource '%s' (%s).
				Use this tool to inspect tables, inspect schema, inspect unified table relations, preview rows, and execute readonly SQL search.
				Constraints:
				1. Only the current agent's active datasource is visible.
				2. Only readonly SQL is allowed for SEARCH.
				3. GET_TABLE_SCHEMA and GET_RELATED_TABLES return a unified relations field that combines physical foreign keys discovered from the database and configured logical relations.
				4. Treat the unified relations field as the primary source for table-to-table relationship reasoning and join planning.
				5. The foreignKeys field inside table metadata is kept only for compatibility; prefer relations for agent reasoning.
				6. Recommended call order: LIST_TABLES -> GET_TABLE_SCHEMA -> GET_RELATED_TABLES -> PREVIEW_ROWS -> SEARCH.
				7. Never infer hidden fields from visible values. For example, do not derive a username or person name from an email local-part, ID, code, or alias.
				8. %s
				""".formatted(datasource.getName(), datasource.getType(), visibleTables);
	}

	private static final class AgentBoundDatasourceExplorerToolCallback implements ToolCallback {

		private final String agentId;

		private final ToolDefinition toolDefinition;

		private final DatasourceExplorerService datasourceExplorerService;

		private final ObjectMapper objectMapper;

		private AgentBoundDatasourceExplorerToolCallback(String agentId, ToolDefinition toolDefinition,
				DatasourceExplorerService datasourceExplorerService, ObjectMapper objectMapper) {
			this.agentId = agentId;
			this.toolDefinition = toolDefinition;
			this.datasourceExplorerService = datasourceExplorerService;
			this.objectMapper = objectMapper;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			try {
				DatasourceExplorerRequest request = objectMapper.readValue(toolInput, DatasourceExplorerRequest.class);
				return objectMapper.writeValueAsString(datasourceExplorerService.execute(agentId, request));
			}
			catch (Exception ex) {
				throw new IllegalStateException("Datasource explorer tool failed: " + ex.getMessage(), ex);
			}
		}

		@Override
		public String call(String toolInput, ToolContext toolContext) {
			return call(toolInput);
		}

	}

}
