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
package com.alibaba.cloud.ai.dataagent.agentscope.tool.sqlguard;

import com.alibaba.cloud.ai.dataagent.agentscope.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.agentscope.runtime.ToolContextRequestResolver;
import com.alibaba.cloud.ai.dataagent.agentscope.tool.AgentScopedToolProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SqlGuardToolProvider implements AgentScopedToolProvider {

	private static final String TOOL_NAME = "sql_guard.check";

	private static final String INPUT_SCHEMA = """
			{
			  "type": "object",
			  "properties": {
			    "action": {
			      "type": "string",
			      "enum": ["SQL_VERIFY", "DATA_PROFILE"],
			      "description": "可选。默认 SQL_VERIFY。SQL_VERIFY 用于候选 SQL 的结构与意图校验；DATA_PROFILE 用于查看字段值域、空值率、distinct、top values 与样例。"
			    },
			    "query": {
			      "type": "string",
			      "description": "SQL_VERIFY 时必填。用户原始问题。"
			    },
			    "sql": {
			      "type": "string",
			      "description": "SQL_VERIFY 时必填。当前准备执行或准备返回给用户的候选 SQL。"
			    },
			    "tableName": {
			      "type": "string",
			      "description": "DATA_PROFILE 时必填。目标表名。"
			    },
			    "columnNames": {
			      "type": "array",
			      "items": {
			        "type": "string"
			      },
			      "description": "DATA_PROFILE 时可选。要分析的字段列表；不传时默认取该表前几个可见字段。"
			    },
			    "limit": {
			      "type": "integer",
			      "description": "DATA_PROFILE 时可选。样例值和 top values 的返回上限，默认 5，最大 20。"
			    },
			    "tableSchemas": {
			      "type": "object",
			      "description": "可选。把 datasource explorer 的 schema 结果原样传入，帮助 SQL 校验识别时间列、维度列与表关系。"
			    },
			    "semanticHits": {
			      "type": "object",
			      "description": "可选。把 semantic_model.search 的结果原样传入。"
			    },
			    "businessKnowledgeHits": {
			      "type": "object",
			      "description": "可选。把 domain_business_knowledge.search 的结果原样传入。"
			    }
			  }
			}
			""";

	private static final String DESCRIPTION = """
			Unified SQL guard tool for SQL-backed answers.
			Action SQL_VERIFY: check whether the candidate SQL really matches the user's intent before execution or final answer.
			Action DATA_PROFILE: inspect column value distribution only when a small set of candidate columns remain semantically ambiguous after schema inspection, and that ambiguity would materially change filters, grouping, ordering, time windows, or metric logic.
			Do not call DATA_PROFILE as a default preflight step for every query. Skip it when the user request and schema already make the relevant columns obvious.
			When using DATA_PROFILE, prefer focused columnNames instead of profiling an entire table.
			For SQL_VERIFY, if verification fails, read isAligned=false plus problems, ruleChecks and fixSuggestions, then rewrite SQL yourself and call sql_guard.check again.
			For DATA_PROFILE, use the returned columnProfiles to understand null ratio, distinct count, top values, samples, and whether a field looks categorical, numeric, or temporal.
			Always pass fresh top-level parameters for the current action. Do not pass previous sql_guard.check output back into the tool.
			""";

	private final ObjectMapper objectMapper;

	private final SqlVerifyExplainService sqlVerifyExplainService;

	public SqlGuardToolProvider(ObjectMapper objectMapper, SqlVerifyExplainService sqlVerifyExplainService) {
		this.objectMapper = objectMapper;
		this.sqlVerifyExplainService = sqlVerifyExplainService;
	}

	@Override
	public Map<String, ToolCallback> getToolCallbacks(String agentId) {
		ToolDefinition toolDefinition = ToolDefinition.builder()
			.name(TOOL_NAME)
			.description(DESCRIPTION)
			.inputSchema(INPUT_SCHEMA)
			.build();
		return Map.of(TOOL_NAME,
				new SqlGuardToolCallback(agentId, toolDefinition, objectMapper, sqlVerifyExplainService));
	}

	private static final class SqlGuardToolCallback implements ToolCallback {

		private final String agentId;

		private final ToolDefinition toolDefinition;

		private final ObjectMapper objectMapper;

		private final SqlVerifyExplainService sqlVerifyExplainService;

		private SqlGuardToolCallback(String agentId, ToolDefinition toolDefinition, ObjectMapper objectMapper,
				SqlVerifyExplainService sqlVerifyExplainService) {
			this.agentId = agentId;
			this.toolDefinition = toolDefinition;
			this.objectMapper = objectMapper;
			this.sqlVerifyExplainService = sqlVerifyExplainService;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			return execute(toolInput, null);
		}

		@Override
		public String call(String toolInput, ToolContext toolContext) {
			return execute(toolInput, toolContext);
		}

		private String execute(String toolInput, ToolContext toolContext) {
			try {
				SqlGuardCheckRequest request = StringUtils.hasText(toolInput)
						? objectMapper.readValue(toolInput, SqlGuardCheckRequest.class) : new SqlGuardCheckRequest();
				enrichRequestFromToolContext(request, toolContext);
				String action = request.normalizedAction();
				SqlGuardCheckResult result = switch (action) {
					case "DATA_PROFILE" -> sqlVerifyExplainService.inspectProfile(agentId, request);
					case "SQL_VERIFY" -> sqlVerifyExplainService.explain(request);
					default -> throw new IllegalArgumentException("Unsupported sql_guard.check action: " + action);
				};
				return objectMapper.writeValueAsString(result);
			}
			catch (Exception ex) {
				throw new IllegalStateException("Failed to execute sql_guard.check: " + ex.getMessage(), ex);
			}
		}

		private void enrichRequestFromToolContext(SqlGuardCheckRequest request, ToolContext toolContext) {
			if (request == null || StringUtils.hasText(request.getHumanFeedbackContent())) {
				return;
			}
			GraphRequest graphRequest = ToolContextRequestResolver.resolveGraphRequest(toolContext);
			if (graphRequest == null) {
				return;
			}
			request.setHumanFeedbackContent(graphRequest.getHumanFeedbackContent());
		}

	}

}
