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
			    "query": {
			      "type": "string",
			      "description": "必填。用户原始问题。"
			    },
			    "sql": {
			      "type": "string",
			      "description": "必填。当前准备执行或准备返回给用户的候选 SQL。"
			    },
			    "tableSchemas": {
			      "type": "object",
			      "description": "可选。把 datasource explorer 的 schema 结果原样传入，帮助识别时间列、维度列与表关系。"
			    },
			    "semanticHits": {
			      "type": "object",
			      "description": "可选。把 semantic_model.search 的结果原样传入。"
			    },
			    "businessKnowledgeHits": {
			      "type": "object",
			      "description": "可选。把 domain_business_knowledge.search 的结果原样传入。"
			    }
			  },
			  "required": ["query", "sql"]
			}
			""";

	private static final String DESCRIPTION = """
			Single SQL verification tool for SQL-backed answers.
			Check whether the candidate SQL really matches the user's intent before execution or final answer.
			If verification fails, read isAligned=false plus problems, ruleChecks and fixSuggestions, then rewrite SQL yourself and call sql_guard.check again.
			Each problem explains why it is wrong, what was expected, what was actually detected and how to repair it.
			Always pass a fresh top-level query and sql. Do not pass previous sql_guard.check output back into the tool.
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
		return Map.of(TOOL_NAME, new SqlGuardToolCallback(toolDefinition, objectMapper, sqlVerifyExplainService));
	}

	private static final class SqlGuardToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		private final ObjectMapper objectMapper;

		private final SqlVerifyExplainService sqlVerifyExplainService;

		private SqlGuardToolCallback(ToolDefinition toolDefinition, ObjectMapper objectMapper,
				SqlVerifyExplainService sqlVerifyExplainService) {
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
			try {
				SqlGuardCheckRequest request = StringUtils.hasText(toolInput)
						? objectMapper.readValue(toolInput, SqlGuardCheckRequest.class) : new SqlGuardCheckRequest();
				return objectMapper.writeValueAsString(sqlVerifyExplainService.explain(request));
			}
			catch (Exception ex) {
				throw new IllegalStateException("Failed to execute sql_guard.check: " + ex.getMessage(), ex);
			}
		}

		@Override
		public String call(String toolInput, ToolContext toolContext) {
			return call(toolInput);
		}

	}

}
