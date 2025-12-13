/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.dataagent.workflow.node;

import com.alibaba.cloud.ai.dataagent.connector.config.DbConfig;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import com.alibaba.cloud.ai.dataagent.common.enums.TextType;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.common.util.DatabaseUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticModelService;
import com.alibaba.cloud.ai.dataagent.common.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.common.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.common.util.StateUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dataagent.common.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.prompt.PromptHelper.buildSemanticModelPrompt;

/**
 * Table relationship inference node that automatically completes complex structures like
 * JOINs and foreign keys.
 *
 * This node is responsible for: - Inferring relationships between tables and fields -
 * Building initial schema from documents - Processing schema selection based on input and
 * evidence - Handling schema advice for missing information
 *
 * @author zhangshenghang
 */
@Slf4j
@Component
@AllArgsConstructor
public class TableRelationNode implements NodeAction {

	private final SchemaService schemaService;

	private final Nl2SqlService nl2SqlService;

	private final SemanticModelService semanticModelService;

	private final DatabaseUtil databaseUtil;

	private final DatasourceService datasourceService;

	private final AgentDatasourceService agentDatasourceService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// Get necessary input parameters
		String canonicalQuery = StateUtil.getCanonicalQuery(state);

		String evidence = StateUtil.getStringValue(state, EVIDENCE);
		List<Document> tableDocuments = StateUtil.getDocumentList(state, TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT);
		List<Document> columnDocuments = StateUtil.getDocumentList(state, COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT);
		String agentIdStr = StateUtil.getStringValue(state, AGENT_ID);

		// Execute business logic first - get final result immediately
		DbConfig agentDbConfig = databaseUtil.getAgentDbConfig(Integer.valueOf(agentIdStr));

		List<String> logicalForeignKeys = getLogicalForeignKeys(Integer.valueOf(agentIdStr), tableDocuments);
		log.info("Found {} logical foreign keys for agent: {}", logicalForeignKeys.size(), agentIdStr);

		SchemaDTO initialSchema = buildInitialSchema(agentIdStr, columnDocuments, tableDocuments, agentDbConfig,
				logicalForeignKeys);

		Map<String, Object> resultMap = new HashMap<>();

		Flux<ChatResponse> schemaFlux = processSchemaSelection(initialSchema, canonicalQuery, evidence, state,
				agentDbConfig, result -> {
					log.info("[{}] Schema processing result: {}", this.getClass().getSimpleName(), result);
					resultMap.put(TABLE_RELATION_OUTPUT, result);

					// 从最终的SchemaDTO中获取表名列表
					List<String> tableNames = result.getTable().stream().map(TableDTO::getName).toList();

					// 根据agentId和表名列表获取语义模型
					List<SemanticModel> semanticModels = semanticModelService
						.getByAgentIdAndTableNames(Integer.valueOf(agentIdStr), tableNames);

					// 构建语义模型提示并存储到resultMap中
					String semanticModelPrompt = buildSemanticModelPrompt(semanticModels);
					resultMap.put(GENEGRATED_SEMANTIC_MODEL_PROMPT, semanticModelPrompt);
				});

		// Create display stream for user experience only
		Flux<ChatResponse> preFlux = Flux.create(emitter -> {
			emitter.next(ChatResponseUtil.createResponse("开始构建初始Schema..."));
			emitter.next(ChatResponseUtil.createResponse("初始Schema构建完成."));
			emitter.complete();
		});
		Flux<ChatResponse> displayFlux = preFlux.concatWith(schemaFlux).concatWith(Flux.create(emitter -> {
			emitter.next(ChatResponseUtil.createResponse("开始处理Schema选择..."));
			emitter.next(ChatResponseUtil.createResponse("Schema选择处理完成."));
			emitter.complete();
		}));

		// Use utility class to create generator, directly return business logic computed
		// result
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, v -> resultMap, displayFlux);

		// need to reset retry count and exception
		return Map.of(TABLE_RELATION_OUTPUT, generator, TABLE_RELATION_RETRY_COUNT, 0, TABLE_RELATION_EXCEPTION_OUTPUT,
				"");

	}

	/**
	 * Builds initial schema from column and table documents.
	 */
	private SchemaDTO buildInitialSchema(String agentId, List<Document> columnDocuments, List<Document> tableDocuments,
			DbConfig agentDbConfig, List<String> logicalForeignKeys) {
		SchemaDTO schemaDTO = new SchemaDTO();

		schemaService.extractDatabaseName(schemaDTO, agentDbConfig);
		schemaService.buildSchemaFromDocuments(agentId, columnDocuments, tableDocuments, schemaDTO);

		// 将逻辑外键信息合并到 schemaDTO 的 foreignKeys 字段
		if (logicalForeignKeys != null && !logicalForeignKeys.isEmpty()) {
			List<String> existingForeignKeys = schemaDTO.getForeignKeys();
			if (existingForeignKeys == null || existingForeignKeys.isEmpty()) {
				// 如果没有现有外键，直接设置
				schemaDTO.setForeignKeys(logicalForeignKeys);
			}
			else {
				// 合并现有外键和逻辑外键
				List<String> allForeignKeys = new ArrayList<>(existingForeignKeys);
				allForeignKeys.addAll(logicalForeignKeys);
				schemaDTO.setForeignKeys(allForeignKeys);
			}
			log.info("Merged {} logical foreign keys into schema for agent: {}", logicalForeignKeys.size(), agentId);
		}

		return schemaDTO;
	}

	/**
	 * Processes schema selection based on input, evidence, and optional advice.
	 */
	private Flux<ChatResponse> processSchemaSelection(SchemaDTO schemaDTO, String input, String evidence,
			OverAllState state, DbConfig agentDbConfig, Consumer<SchemaDTO> dtoConsumer) {
		String schemaAdvice = StateUtil.getStringValue(state, SQL_GENERATE_SCHEMA_MISSING_ADVICE, null);

		Flux<ChatResponse> schemaFlux;
		if (schemaAdvice != null) {
			log.info("[{}] Processing with schema supplement advice: {}", this.getClass().getSimpleName(),
					schemaAdvice);
			schemaFlux = nl2SqlService.fineSelect(schemaDTO, input, evidence, schemaAdvice, agentDbConfig, dtoConsumer);
		}
		else {
			log.info("[{}] Executing regular schema selection", this.getClass().getSimpleName());
			schemaFlux = nl2SqlService.fineSelect(schemaDTO, input, evidence, null, agentDbConfig, dtoConsumer);
		}
		return Flux
			.just(ChatResponseUtil.createResponse("正在选择合适的数据表...\n"),
					ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()))
			.concatWith(schemaFlux)
			.concatWith(Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
					ChatResponseUtil.createResponse("\n\n选择数据表完成。")));
	}

	/**
	 * 获取逻辑外键信息，并过滤只保留与当前召回表相关的外键
	 */
	private List<String> getLogicalForeignKeys(Integer agentId, List<Document> tableDocuments) {
		try {
			// 获取当前 agent 激活的数据源
			AgentDatasource agentDatasource = agentDatasourceService.getCurrentAgentDatasource(agentId);
			if (agentDatasource == null || agentDatasource.getDatasourceId() == null) {
				log.warn("No active datasource found for agent: {}", agentId);
				return Collections.emptyList();
			}

			Integer datasourceId = agentDatasource.getDatasourceId();

			// 从 tableDocuments 提取表名列表
			Set<String> recalledTableNames = tableDocuments.stream()
				.map(doc -> (String) doc.getMetadata().get("name"))
				.filter(name -> name != null && !name.isEmpty())
				.collect(Collectors.toSet());

			log.info("Recalled table names for agent {}: {}", agentId, recalledTableNames);

			// 查询该数据源的所有逻辑外键
			List<LogicalRelation> allLogicalRelations = datasourceService.getLogicalRelations(datasourceId);
			log.info("Found {} logical relations in datasource: {}", allLogicalRelations.size(), datasourceId);

			// 过滤只保留与召回表相关的外键（源表或目标表在召回列表中）
			List<String> formattedForeignKeys = allLogicalRelations.stream()
				.filter(lr -> recalledTableNames.contains(lr.getSourceTableName())
						|| recalledTableNames.contains(lr.getTargetTableName()))
				.map(lr -> String.format("%s.%s=%s.%s", lr.getSourceTableName(), lr.getSourceColumnName(),
						lr.getTargetTableName(), lr.getTargetColumnName()))
				.distinct()
				.collect(Collectors.toList());

			log.info("Filtered {} relevant logical relations for recalled tables", formattedForeignKeys.size());
			return formattedForeignKeys;
		}
		catch (Exception e) {
			log.error("Error fetching logical foreign keys for agent: {}", agentId, e);
			return Collections.emptyList();
		}
	}

}
