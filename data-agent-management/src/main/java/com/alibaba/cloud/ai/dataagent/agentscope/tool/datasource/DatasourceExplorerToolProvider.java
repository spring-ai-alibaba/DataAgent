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

import com.alibaba.cloud.ai.dataagent.agentscope.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.agentscope.runtime.ToolContextRequestResolver;
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
			      "description": "探索动作。默认顺序：LIST_TABLES/FIND_TABLES -> GET_TABLE_SCHEMA -> GET_RELATED_TABLES -> SEARCH。PREVIEW_ROWS 仅在用户明确要求看样例行，或关键字段语义仍不确定且会影响 SQL 写法时才使用"
			    },
			    "query": {
			      "type": "string",
			      "description": "用于 FIND_TABLES 的检索关键词"
			    },
			    "tableName": {
			      "type": "string",
			      "description": "目标表名。GET_TABLE_SCHEMA / GET_RELATED_TABLES / PREVIEW_ROWS 必填。不要把 PREVIEW_ROWS 当作默认前置动作"
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
		String visibleTables = selectedTables.isEmpty()
				? "当前未显式选表，将回退到数据源全部可见表"
				: "当前显式选表 %d 个：%s"
					.formatted(selectedTables.size(), String.join(", ", selectedTables.stream().limit(8).toList()));
		return """
				数据源'%s'（%s）的统一探索工具。
				可用于查看表列表、查看表结构、查看统一关系、按需预览样例数据，以及执行只读 SQL 查询。
				约束说明：
				1. 只能访问当前 Agent 的活动数据源。
				2. SEARCH 仅允许执行只读 SQL。
				3. GET_TABLE_SCHEMA 和 GET_RELATED_TABLES 返回的 relations 字段，会合并数据库物理外键与已配置的逻辑关系。
				4. 做表关系推断和 Join 规划时，应优先使用 relations 字段。
				5. 表元数据里的 foreignKeys 字段仅为兼容保留，Agent 推理时优先使用 relations。
				6. 默认调用顺序：LIST_TABLES/FIND_TABLES -> GET_TABLE_SCHEMA -> GET_RELATED_TABLES -> SEARCH。
				7. PREVIEW_ROWS 只是条件动作，不是默认前置步骤。只有在用户明确要求看样例行，或 schema、列名和现有语义仍不足以判断关键字段实际语义，且这种不确定性会影响 SQL 写法时，才调用 PREVIEW_ROWS。
				8. 如果 schema、relations、列名和已知语义已经足够支持写 SQL，就直接进入 SQL_VERIFY/SEARCH，不要为了“先确认数据质量”再额外预览样例。
				9. 不要根据可见值推断隐藏字段。例如不要从邮箱前缀、ID、编码或别名推断用户名或真实姓名。
				10. %s
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
			return call(toolInput, null);
		}

		@Override
		public String call(String toolInput, ToolContext toolContext) {
			try {
				DatasourceExplorerRequest request = objectMapper.readValue(toolInput, DatasourceExplorerRequest.class);
				GraphRequest graphRequest = ToolContextRequestResolver.resolveGraphRequest(toolContext);
				return objectMapper.writeValueAsString(datasourceExplorerService.execute(agentId, request, graphRequest));
			}
			catch (Exception ex) {
				throw new IllegalStateException("Datasource explorer tool failed: " + ex.getMessage(), ex);
			}
		}

	}

}
