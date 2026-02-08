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

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@AllArgsConstructor
@Slf4j
@Component
public class ListTableSchemaTool implements BiFunction<ListTableSchemaTool.Request, ToolContext, String> {

	private final SchemaService schemaService;

	private final AgentDatasourceMapper agentDatasourceMapper;

	@Override
	public String apply(Request request, ToolContext toolContext) {
		log.info("========== List Table schema Tool Start ==========");

		Long agentId = null;
		if (toolContext.getContext().get("agent_id") != null) {
			agentId = Long.parseLong((String) toolContext.getContext().get("agent_id"));
		}

		if (agentId == null) {
			log.error("agent_id is null, please check the context");
			return "Error listing table schemas: agent_id is null, please check the context.";
		}
		try {
			// 查询 Agent 的激活数据源
			Integer datasourceId = agentDatasourceMapper.selectActiveDatasourceIdByAgentId(agentId);

			List<Document> tableDocuments = new ArrayList<>(
					schemaService.getTableDocumentsByDatasource(datasourceId, request.query()));
			// extract table names
			List<String> recalledTableNames = extractTableName(tableDocuments);
			List<Document> columnDocuments = schemaService.getColumnDocumentsByTableName(datasourceId,
					recalledTableNames);
			if (recalledTableNames.isEmpty()) {
				log.info("No tables found in the database");
				return "No tables found in the database.";
			}
			StringBuilder sb = new StringBuilder();
			int index = 1;
			for (String tableName : recalledTableNames) {
				sb.append("## result").append(index).append(": \n");
				sb.append(tableName).append("\n");
				List<Document> columns = columnDocuments.stream()
					.filter(p -> p.getMetadata().get(DocumentMetadataConstant.TABLE_NAME) != null
							&& p.getMetadata().get(DocumentMetadataConstant.TABLE_NAME).equals(tableName))
					.toList();
				if (!columns.isEmpty()) {
					sb.append("  ---- Columns ----\n");
				}
				for (Document column : columns) {
					String columnName = (String) column.getMetadata().get("name");
					String columnComment = (String) column.getMetadata().get("description");
					sb.append("      ").append(columnName);
					if (StringUtils.hasText(columnComment)) {
						sb.append("  comment: ").append(columnComment);
					}
					sb.append("\n");
				}
				index++;
			}
			log.info("Found {} tables: {}", recalledTableNames.size(), sb);
			log.info("========== List Table schema Tool End ==========");
			return sb.toString();
		}
		catch (Exception e) {
			log.error("Error listing tables", e);
			return "Error listing tables: " + e.getMessage();
		}
	}

	public ToolCallback toolCallback() {
		return FunctionToolCallback.builder("list_table_schema", this)
			.description(
					"""
							# Semantic/vector search tool for retrieving relevant database table Schema from knowledge base based on business intent
							This tool uses embeddings to understand the user's business-related query and find semantically relevant database table structure information, including table details and column specifications, to provide a foundation for subsequent SQL statement generation.
							## Purpose
							Designed for retrieving database table Schema information aligned with user business needs, specifically to:
							- Obtain table names and their Chinese names associated with the target business scenario
							- Retrieve detailed column information including column names, data types, and Chinese names
							- Provide accurate structural references to guide correct SQL statement drafting
							- Identify all relevant tables and columns required to fulfill the user's data retrieval or analysis needs via SQL
							The tool searches by business intent and data relevance rather than exact keyword matching, ensuring it captures all tables/columns related to the user's data request context.
							## What the Tool Does NOT Do
							- Does NOT perform literal keyword-only matching without considering business contextual relevance
							- Does NOT return actual business data records (only table and column structure metadata)
							- Should NOT be used for conceptual explanations unrelated to database Schema
							- Should NOT process queries that do not involve data retrieval or SQL generation requirements
							- Does NOT generate SQL statements directly (only provides Schema information to support subsequent SQL creation)
							## Required Input Behavior
							"query" must contain **well-formed semantic questions or intent statements** that clearly express the business data need and corresponding Schema retrieval requirement.
							Each query should represent a **business data scenario, data analysis requirement, or specific data element need** that points to the database table structure needed, such as:
							- Business process-related data scenarios (e.g., order creation, user payment)
							- Specific data element tracking needs (e.g., user membership points, product inventory levels)
							- Data analysis scope requirements (e.g., tables for monthly sales performance analysis)
							- Contextual data association needs (e.g., tables linked to user delivery information)
							Avoid:
							- Isolated keyword lists without business context
							- Raw unprocessed user messages that don't clarify Schema retrieval intent
							- Full paragraphs of business logic description without explicit Schema focus
							- Queries centered on business logic explanations rather than table structure needs
							## Examples of valid query shapes (not content):
							- "Which tables store user order related data and their column structures?"
							- "Retrieve Schema information of tables associated with user payment records"
							- "What tables contain user delivery address fields, including column details?"
							- "Get table structures for product inventory management scenarios"
							- "Which tables are needed to analyze sales performance, along with their column specs?"
							## Parameters
							- query (required): questions or intent statements.
							  These should reflect the business data context and the specific Schema information you aim to retrieve.
							## Output
							Returns chunks ranked by semantic relevance to the business intent, reranked when applicable.
							Results contain structured database Schema information including:
							- Table name
							- Table Chinese name
							- Column name
							- Column data type
							- Column Chinese name
							The results are intended to provide clear, context-aligned structural references for subsequent SQL statement generation.
							""")
			.inputType(Request.class)
			.build();
	}

	@JsonClassDescription("Request to list matched database tables and schema")
	public record Request(@JsonProperty(value = "query",
			required = true) @JsonPropertyDescription("The search query to find relevant documentation. "
					+ "Be specific and include key terms related to your question.") String query) {

	}

	private static List<String> extractTableName(List<Document> tableDocuments) {
		List<String> tableNames = new ArrayList<>();
		// metadata中的name字段
		for (Document document : tableDocuments) {
			String name = (String) document.getMetadata().get("name");
			if (name != null && !name.isEmpty()) {
				tableNames.add(name);
			}
		}
		log.info("At this SchemaRecallNode, Recall tables are: {}", tableNames);
		return tableNames;
	}

}
