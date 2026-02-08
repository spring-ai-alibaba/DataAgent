/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.tools;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.util.DatabaseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.function.BiFunction;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

/**
 * SQL执行工具，用于执行SQL查询并返回结果
 * <p>
 * 参考了 SqlExecuteNode.java 中的 SQL 执行逻辑
 * </p>
 */
@Slf4j
@AllArgsConstructor
@Component
public class SqlExecuteTool implements BiFunction<SqlExecuteTool.Request, ToolContext, String> {

	private final DatabaseUtil databaseUtil;

	@Override
	public String apply(Request request, ToolContext toolContext) {
		log.info("========== SQL Execute Tool Start ==========");

		try {
			// 验证输入
			Long agentId = null;
			if (toolContext.getContext().get("agent_id") != null) {
				agentId = Long.parseLong((String) toolContext.getContext().get("agent_id"));
			}

			if (agentId == null) {
				log.error("agent_id is null, please check the context");
				return "Error listing tables: agent_id is null, please check the context.";
			}

			if (request.sql() == null || request.sql().isEmpty()) {
				return "SQL statement is required";
			}

			// 获取数据库配置
			DbConfigBO dbConfig = databaseUtil.getAgentDbConfig(agentId);
			if (dbConfig == null) {
				return "Database configuration not found for agent ID: " + agentId;
			}

			// 创建查询参数
			DbQueryParameter dbQueryParameter = new DbQueryParameter();
			dbQueryParameter.setSql(request.sql());
			dbQueryParameter.setSchema(dbConfig.getSchema());

			// 获取数据库访问器
			Accessor dbAccessor = databaseUtil.getAgentAccessor(agentId);
			if (dbAccessor == null) {
				return "Database accessor not found for agent ID: " + agentId;
			}

			// 执行SQL查询
			ResultSetBO resultSetBO = dbAccessor.executeSqlAndReturnObject(dbConfig, dbQueryParameter);

			// 生成执行结果
			String result = generateExecutionResult(request.sql(), resultSetBO);

			log.info("========== SQL Execute Tool End ==========");
			return result;
		}
		catch (Exception e) {
			log.error("Error executing SQL: {}", e.getMessage(), e);
			return "Error executing SQL: " + e.getMessage();
		}
	}

	/**
	 * 生成SQL执行结果
	 */
	private String generateExecutionResult(String sql, ResultSetBO resultSetBO) throws Exception {
		StringBuilder result = new StringBuilder();

		result.append("# SQL执行结果\n\n");
		result.append("## 执行的SQL\n");
		result.append("```sql\n").append(sql).append("\n```\n\n");

		if (resultSetBO != null) {
			result.append("## 执行结果\n");
			result.append("- 行数: ")
				.append(resultSetBO.getData() != null ? resultSetBO.getData().size() : 0)
				.append("\n\n");

			if (resultSetBO.getData() != null && !resultSetBO.getData().isEmpty()) {
				result.append("## 数据预览\n");
				String resultJson = JsonUtil.getObjectMapper().writeValueAsString(resultSetBO.getData());
				result.append("```json\n").append(resultJson).append("\n```\n");
			}
		}
		else {
			result.append("## 执行结果\n");
			result.append("- 无数据返回\n");
		}

		return result.toString();
	}

	/**
	 * SQL执行工具请求参数
	 */
	public record Request(@JsonProperty(value = "sql",
			required = true) @JsonPropertyDescription("SQL statement to execute") String sql) {

	}

	/**
	 * 获取工具回调
	 */
	public ToolCallback toolCallback() {
		return FunctionToolCallback.builder("execute_sql", this).description("""
				# SQL执行工具
				用于执行SQL查询并返回结果。
				## 何时使用
				- 当需要执行SQL查询以获取数据时
				- 当需要验证SQL语句的正确性时
				- 当需要获取数据库中的数据进行分析时
				## 输入参数
				- `sql`: 要执行的SQL语句（必填）
				- `agentId`: 代理ID，用于获取数据库配置（必填）
				- `schema`: 数据库schema（可选）
				## 输出
				- SQL执行结果，包括执行的SQL语句、列数、行数和数据预览
				- 如果执行失败，返回错误信息
				## 注意事项
				- 请确保SQL语句是有效的，否则会返回错误信息
				- 请确保agentId是有效的，否则会返回错误信息
				- 执行结果中的数据预览可能会被截断，仅用于参考
				""").inputType(Request.class).build();
	}
}
