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

package com.alibaba.cloud.ai.node;

import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.dto.BusinessKnowledgeDTO;
import com.alibaba.cloud.ai.entity.Datasource;
import com.alibaba.cloud.ai.entity.SemanticModel;
import com.alibaba.cloud.ai.enums.StreamResponseType;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.service.business.BusinessKnowledgeService;
import com.alibaba.cloud.ai.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.service.schema.SchemaService;
import com.alibaba.cloud.ai.service.semantic.SemanticModelService;
import com.alibaba.cloud.ai.util.ChatResponseUtil;
import com.alibaba.cloud.ai.util.SchemaProcessorUtil;
import com.alibaba.cloud.ai.util.StateUtil;
import com.alibaba.cloud.ai.util.StreamingChatGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.constant.Constant.*;
import static com.alibaba.cloud.ai.prompt.PromptHelper.buildBusinessKnowledgePrompt;
import static com.alibaba.cloud.ai.prompt.PromptHelper.buildSemanticModelPrompt;

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
@Component
public class TableRelationNode implements NodeAction {

	private static final Logger logger = LoggerFactory.getLogger(TableRelationNode.class);

	private final SchemaService schemaService;

	private final Nl2SqlService nl2SqlService;

	private final BusinessKnowledgeService businessKnowledgeService;

	private final SemanticModelService semanticModelService;

	private final DatasourceService datasourceService;

	public TableRelationNode(SchemaService schemaService, Nl2SqlService nl2SqlService,
			BusinessKnowledgeService businessKnowledgeService, SemanticModelService semanticModelService,
			DatasourceService datasourceService) {
		this.schemaService = schemaService;
		this.nl2SqlService = nl2SqlService;
		this.businessKnowledgeService = businessKnowledgeService;
		this.semanticModelService = semanticModelService;
		this.datasourceService = datasourceService;
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		logger.info("Entering {} node", this.getClass().getSimpleName());

		int retryCount = StateUtil.getObjectValue(state, TABLE_RELATION_RETRY_COUNT, Integer.class, 0);

		// Get necessary input parameters
		String input = StateUtil.getStringValue(state, QUERY_REWRITE_NODE_OUTPUT,
				StateUtil.getStringValue(state, INPUT_KEY));
		List<String> evidenceList = StateUtil.getListValue(state, EVIDENCES);
		List<Document> tableDocuments = StateUtil.getDocumentList(state, TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT);
		List<List<Document>> columnDocumentsByKeywords = StateUtil.getDocumentListList(state,
				COLUMN_DOCUMENTS_BY_KEYWORDS_OUTPUT);
		String agentIdStr = StateUtil.getStringValue(state, AGENT_ID);
		if (!StringUtils.hasText(agentIdStr))
			throw new RuntimeException("Agent ID is empty.");

		// Execute business logic first - get final result immediately
		DbConfig agentDbConfig = getAgentDbConfig(Integer.valueOf(agentIdStr));
		SchemaDTO schemaDTO = buildInitialSchema(agentIdStr, columnDocumentsByKeywords, tableDocuments, agentDbConfig);
		SchemaDTO result = processSchemaSelection(schemaDTO, input, evidenceList, state, agentDbConfig);

		List<BusinessKnowledgeDTO> businessKnowledges;
		List<SemanticModel> semanticModel;
		try {
			// Extract business knowledge and semantic model
			businessKnowledges = businessKnowledgeService.getKnowledgeDtoRecalled(Long.parseLong(agentIdStr));
			semanticModel = semanticModelService.getEnabledByAgentId(Long.parseLong(agentIdStr));
		}
		catch (DataAccessException e) {
			logger.warn("Database query failed (attempt {}): {}", retryCount + 1, e.getMessage());

			String errorType = classifyDatabaseError(e);
			return Map.of(TABLE_RELATION_EXCEPTION_OUTPUT, errorType + ": " + e.getMessage(),
					TABLE_RELATION_RETRY_COUNT, retryCount + 1);
		}
		// load prompt template
		String businessKnowledgePrompt = buildBusinessKnowledgePrompt(businessKnowledges);
		String semanticModelPrompt = buildSemanticModelPrompt(semanticModel);

		logger.info("[{}] Schema processing result: {}", this.getClass().getSimpleName(), result);

		// Create display stream for user experience only
		Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
			emitter.next(ChatResponseUtil.createStatusResponse("开始构建初始Schema..."));
			emitter.next(ChatResponseUtil.createStatusResponse("初始Schema构建完成."));

			emitter.next(ChatResponseUtil.createStatusResponse("开始处理Schema选择..."));
			emitter.next(ChatResponseUtil.createStatusResponse("Schema选择处理完成."));
			emitter.complete();
		});

		// Use utility class to create generator, directly return business logic computed
		// result
		var generator = StreamingChatGeneratorUtil.createStreamingGeneratorWithMessages(
				this.getClass(), state, v -> Map.of(TABLE_RELATION_OUTPUT, result, BUSINESS_KNOWLEDGE,
						businessKnowledgePrompt, SEMANTIC_MODEL, semanticModelPrompt),
				displayFlux, StreamResponseType.SCHEMA_DEEP_RECALL);

		// need to reset retry count and exception
		return Map.of(TABLE_RELATION_OUTPUT, generator, BUSINESS_KNOWLEDGE, businessKnowledgePrompt, SEMANTIC_MODEL,
				semanticModelPrompt, TABLE_RELATION_RETRY_COUNT, 0, TABLE_RELATION_EXCEPTION_OUTPUT, "");

	}

	private String classifyDatabaseError(DataAccessException e) {
		String message = e.getMessage();
		if (message != null) {
			// timeout, connection, network can be retried
			if (message.contains("timeout") || message.contains("connection") || message.contains("network")) {
				return "RETRYABLE";
			}
		}
		return "NON_RETRYABLE";
	}

	/**
	 * Builds initial schema from column and table documents.
	 */
	private SchemaDTO buildInitialSchema(String agentId, List<List<Document>> columnDocumentsByKeywords,
			List<Document> tableDocuments, DbConfig agentDbConfig) {
		SchemaDTO schemaDTO = new SchemaDTO();

		schemaService.extractDatabaseName(schemaDTO, agentDbConfig);
		schemaService.buildSchemaFromDocuments(agentId, columnDocumentsByKeywords, tableDocuments, schemaDTO);
		return schemaDTO;
	}

	private DbConfig getAgentDbConfig(Integer agentId) {
		// Get the enabled data source for the agent
		Datasource agentDatasource = datasourceService.getActiveDatasourceByAgentId(agentId);
		if (agentDatasource == null)
			throw new RuntimeException("No active datasource found for agent " + agentId);

		// Convert to DbConfig
		DbConfig dbConfig = SchemaProcessorUtil.createDbConfigFromDatasource(agentDatasource);
		logger.debug("Successfully created DbConfig for agent {}: url={}, schema={}, type={}", agentId,
				dbConfig.getUrl(), dbConfig.getSchema(), dbConfig.getDialectType());

		return dbConfig;
	}

	/**
	 * Processes schema selection based on input, evidence, and optional advice.
	 */
	private SchemaDTO processSchemaSelection(SchemaDTO schemaDTO, String input, List<String> evidenceList,
			OverAllState state, DbConfig agentDbConfig) {
		String schemaAdvice = StateUtil.getStringValue(state, SQL_GENERATE_SCHEMA_MISSING_ADVICE, null);

		if (schemaAdvice != null) {
			logger.info("[{}] Processing with schema supplement advice: {}", this.getClass().getSimpleName(),
					schemaAdvice);
			return nl2SqlService.fineSelect(schemaDTO, input, evidenceList, schemaAdvice, agentDbConfig);
		}
		else {
			logger.info("[{}] Executing regular schema selection", this.getClass().getSimpleName());
			return nl2SqlService.fineSelect(schemaDTO, input, evidenceList, null, agentDbConfig);
		}
	}

}
