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
package com.alibaba.cloud.ai.dataagent.workflow.node;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.INPUT_KEY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.QUERY_ENHANCE_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SCHEMA_RECALL_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Schema recall node that retrieves relevant database schema information based
 * on keywords and intent.
 *
 * This node is responsible for: - Recalling relevant tables based on user input
 * - Retrieving column documents based on extracted keywords - Organizing schema
 * information for subsequent processing - Providing streaming feedback during
 * recall process
 *
 * @author zhangshenghang
 */
@Slf4j
@Component
@AllArgsConstructor
public class SchemaRecallNode implements NodeAction {

	private static final int MAX_DISPLAY_TABLES = 10;

	private final SchemaService schemaService;

	private final AgentDatasourceMapper agentDatasourceMapper;

	private final AgentDatasourceService agentDatasourceService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		String canonicalQuery = resolveCanonicalQuery(state);
		Long agentId = parseAgentId(StateUtil.getStringValue(state, AGENT_ID));
		if (agentId == null) {
			return buildEarlyExitResult(state, """
					
 系统未能识别当前智能体标识，无法继续检索 Schema。
					请刷新页面后重试；若仍失败，请联系管理员排查 Agent 配置。
					流程已终止。
					""");
		}

		Integer datasourceId = agentDatasourceMapper.selectActiveDatasourceIdByAgentId(agentId);
		if (datasourceId == null) {
			log.warn("Agent {} has no active datasource", agentId);
			return buildEarlyExitResult(state, """
					
 该智能体没有激活的数据源。

					这可能是因为：
					1. 数据源尚未配置或关联。
					2. 所有数据源都已被禁用。
					3. 请先配置并激活数据源。
					流程已终止。
					""");
		}

		List<Document> tableDocuments = new ArrayList<>(
				schemaService.getTableDocumentsByDatasource(datasourceId, canonicalQuery));
		List<String> recalledTableNames = extractTableNames(tableDocuments);
		List<Document> columnDocuments = schemaService.getColumnDocumentsByTableName(datasourceId, recalledTableNames);

		Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
			emitter.next(ChatResponseUtil.createResponse("开始初步召回 Schema 信息..."));
			emitter.next(ChatResponseUtil.createResponse(
					"初步表信息召回完成，数量: " + tableDocuments.size() + "，表名: " + String.join(", ", recalledTableNames)));
			if (tableDocuments.isEmpty()) {
				List<String> availableTables = getAvailableTables(agentId);
				String fallbackMessage = buildFallbackMessage(canonicalQuery, datasourceId, availableTables);
				emitter.next(ChatResponseUtil.createResponse(fallbackMessage));
			}
			emitter.next(ChatResponseUtil.createResponse("初步 Schema 信息召回完成。"));
			emitter.complete();
		});

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(
				this.getClass(),
				state,
				currentState -> Map.of(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, tableDocuments,
						COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, columnDocuments),
				displayFlux);

		return Map.of(SCHEMA_RECALL_NODE_OUTPUT, generator);
	}

	private String resolveCanonicalQuery(OverAllState state) {
		QueryEnhanceOutputDTO queryEnhanceOutputDTO = StateUtil.getObjectValue(state, QUERY_ENHANCE_NODE_OUTPUT,
				QueryEnhanceOutputDTO.class, (QueryEnhanceOutputDTO) null);
		if (queryEnhanceOutputDTO != null && StringUtils.hasText(queryEnhanceOutputDTO.getCanonicalQuery())) {
			return queryEnhanceOutputDTO.getCanonicalQuery().trim();
		}

		String rawInput = StateUtil.getStringValue(state, INPUT_KEY, "");
		if (StringUtils.hasText(rawInput)) {
			return rawInput.trim();
		}
		return "（用户问题为空）";
	}

	private Long parseAgentId(String rawAgentId) {
		if (!StringUtils.hasText(rawAgentId)) {
			log.warn("Agent id is empty in workflow state");
			return null;
		}
		try {
			return Long.valueOf(rawAgentId.trim());
		}
		catch (NumberFormatException ex) {
			log.warn("Invalid agent id in workflow state: {}", rawAgentId, ex);
			return null;
		}
	}

	private Map<String, Object> buildEarlyExitResult(OverAllState state, String message) {
		Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
			emitter.next(ChatResponseUtil.createResponse(message));
			emitter.complete();
		});

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(
				this.getClass(),
				state,
				currentState -> Map.of(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, Collections.emptyList(),
						COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, Collections.emptyList()),
				displayFlux);
		return Map.of(SCHEMA_RECALL_NODE_OUTPUT, generator);
	}

	private static List<String> extractTableNames(List<Document> tableDocuments) {
		List<String> tableNames = new ArrayList<>();
		for (Document document : tableDocuments) {
			Object nameObject = document.getMetadata().get("name");
			if (nameObject instanceof String name && StringUtils.hasText(name)) {
				tableNames.add(name.trim());
			}
		}
		log.info("At SchemaRecallNode, recalled tables are: {}", tableNames);
		return tableNames;
	}

	private List<String> getAvailableTables(Long agentId) {
		try {
			AgentDatasource currentDatasource = agentDatasourceService.getCurrentAgentDatasource(agentId);
			if (currentDatasource == null || currentDatasource.getSelectTables() == null
					|| currentDatasource.getSelectTables().isEmpty()) {
				return List.of();
			}

			Set<String> uniqueTables = new LinkedHashSet<>();
			for (String tableName : currentDatasource.getSelectTables()) {
				if (StringUtils.hasText(tableName)) {
					uniqueTables.add(tableName.trim());
				}
			}
			return List.copyOf(uniqueTables);
		}
		catch (Exception e) {
			log.warn("Failed to load selected tables for agent {}", agentId, e);
			return List.of();
		}
	}

	private String buildFallbackMessage(String userQuery, Integer datasourceId, List<String> availableTables) {
		String formattedTables = formatAvailableTables(availableTables);
		List<String> suggestedQuestions = buildSuggestedQuestions(availableTables);

		return ("""
				
 未检索到与当前问题相关的数据表。

				当前问题：
				%s

				当前可用表：
				%s

				建议你可以这样提问：
				1. %s
				2. %s
				3. %s

				下一步操作：
				1. 确认已执行“初始化数据源”，并且初始化使用的是当前 Embedding 模型。
				2. 若刚切换过 Embedding 模型，请重新初始化该数据源。
				3. 在问题中补充业务关键词或表字段关键词（例如：订单、用户、金额、日期）。
				4. 若是业务口径词（例如“人均 GDP”），建议在知识库补充“术语-字段映射”。
				5. 如需排查，请检查数据源 ID：%s。
				流程已终止。
				""").formatted(userQuery, formattedTables, suggestedQuestions.get(0), suggestedQuestions.get(1),
				suggestedQuestions.get(2), datasourceId);
	}

	private String formatAvailableTables(List<String> availableTables) {
		if (availableTables.isEmpty()) {
			return "暂无（当前智能体还没有配置已选表）";
		}
		List<String> displayTables = availableTables.stream().limit(MAX_DISPLAY_TABLES).toList();
		if (availableTables.size() > MAX_DISPLAY_TABLES) {
			return String.join(", ", displayTables) + " ...（共 " + availableTables.size() + " 张）";
		}
		return String.join(", ", displayTables);
	}

	private List<String> buildSuggestedQuestions(List<String> availableTables) {
		if (availableTables.isEmpty()) {
			return List.of("查询最近30天核心业务指标趋势", "按地区统计核心指标分布", "查询核心对象 Top10 及占比");
		}

		int size = availableTables.size();
		String first = availableTables.get(0);
		String second = size > 1 ? availableTables.get(1) : null;
		String third = size > 2 ? availableTables.get(2) : null;

		String q1 = "查询 " + first + " 最近30天的数量趋势";
		String q2 = second != null
				? "按维度统计 " + second + " 的分布情况"
				: "按维度统计 " + first + " 的分布情况";
		String q3 = (second != null && third != null)
				? "关联 " + second + " 与 " + third + " 分析核心指标"
				: second != null
						? "关联 " + first + " 与 " + second + " 分析核心指标"
						: "在问题中补充 " + first + " 的关键字段后重试";

		return List.of(q1, q2, q3);
	}

}
