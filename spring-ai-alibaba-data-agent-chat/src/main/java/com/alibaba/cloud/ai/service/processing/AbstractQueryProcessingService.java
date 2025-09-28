package com.alibaba.cloud.ai.service.processing;

import com.alibaba.cloud.ai.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.prompt.PromptConstant;
import com.alibaba.cloud.ai.prompt.PromptHelper;
import com.alibaba.cloud.ai.service.LlmService;
import com.alibaba.cloud.ai.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.service.schema.SchemaService;
import com.alibaba.cloud.ai.service.vectorstore.VectorStoreService;
import com.alibaba.cloud.ai.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractQueryProcessingService implements QueryProcessingService {

	private static final Logger logger = LoggerFactory.getLogger(AbstractQueryProcessingService.class);

	private final LlmService aiService;

	public AbstractQueryProcessingService(LlmService aiService) {
		this.aiService = aiService;
	}

	protected abstract VectorStoreService getVectorStoreService();

	protected abstract SchemaService getSchemaService();

	protected abstract Nl2SqlService getNl2SqlService();

	@Override
	public List<String> extractEvidences(String query, String agentId) {
		logger.debug("Extracting evidences for query: {} with agentId: {}", query, agentId);
		List<Document> evidenceDocuments;
		if (agentId != null && !agentId.trim().isEmpty()) {
			evidenceDocuments = getVectorStoreService().getDocumentsForAgent(agentId, query, "evidence");
		}
		else {
			evidenceDocuments = getVectorStoreService().getDocuments(query, "evidence");
		}
		List<String> evidences = evidenceDocuments.stream().map(Document::getText).collect(Collectors.toList());
		logger.debug("Extracted {} evidences: {}", evidences.size(), evidences);
		return evidences;
	}

	@Override
	public List<String> extractKeywords(String query, List<String> evidenceList) {
		logger.debug("Extracting keywords from query: {} with {} evidences", query, evidenceList.size());
		StringBuilder queryBuilder = new StringBuilder(query);
		for (String evidence : evidenceList) {
			queryBuilder.append(evidence).append("。");
		}
		query = queryBuilder.toString();

		String prompt = PromptHelper.buildQueryToKeywordsPrompt(query);
		logger.debug("Calling LLM for keyword extraction");
		String content = aiService.call(prompt);

		List<String> keywords;
		try {
			keywords = JsonUtil.getObjectMapper().readValue(content, new TypeReference<List<String>>() {
			});
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
		logger.debug("Extracted {} keywords: {}", keywords != null ? keywords.size() : 0, keywords);
		return keywords;
	}

	@Override
	public List<String> expandQuestion(String query) {
		logger.info("Starting question expansion for query: {}", query);
		try {
			// Build question expansion prompt
			Map<String, Object> params = new HashMap<>();
			params.put("question", query);
			String prompt = PromptConstant.getQuestionExpansionPromptTemplate().render(params);

			// Call LLM to get expanded questions
			logger.debug("Calling LLM for question expansion");
			String content = aiService.call(prompt);

			// Parse JSON response
			List<String> expandedQuestions = JsonUtil.getObjectMapper().readValue(content, new TypeReference<>() {
			});

			if (expandedQuestions == null || expandedQuestions.isEmpty()) {
				logger.warn("No expanded questions generated, returning original query");
				return Collections.singletonList(query);
			}

			logger.info("Question expansion completed successfully: {} questions generated", expandedQuestions.size());
			logger.debug("Expanded questions: {}", expandedQuestions);
			return expandedQuestions;
		}
		catch (Exception e) {
			logger.warn("Question expansion failed, returning original query: {}", e.getMessage());
			return Collections.singletonList(query);
		}
	}

	@Override
	public Flux<ChatResponse> rewriteStream(String query, String agentId) throws Exception {
		logger.info("Starting rewriteStream for query: {} with agentId: {}", query, agentId);

		// 处理时间表达式 - 将相对时间转换为具体时间
		String timeRewrittenQuery = processTimeExpressions(query);
		logger.debug("Time rewritten query: {} -> {}", query, timeRewrittenQuery);

		List<String> evidences = extractEvidences(timeRewrittenQuery, agentId);
		logger.debug("Extracted {} evidences for rewriteStream", evidences.size());
		SchemaDTO schemaDTO = select(timeRewrittenQuery, evidences, agentId);
		String prompt = PromptHelper.buildRewritePrompt(timeRewrittenQuery, schemaDTO, evidences);
		logger.debug("Built rewrite prompt for streaming");
		Flux<ChatResponse> result = aiService.streamCall(prompt);
		logger.info("RewriteStream completed for query: {}", query);
		return result;
	}

	/**
	 * 处理查询中的时间表达式，将相对时间转换为具体时间
	 * @param query 原始查询
	 * @return 处理后的查询
	 */
	private String processTimeExpressions(String query) {
		try {
			logger.debug("Processing time expressions in query: {}", query);

			// 使用统一管理的提示词构建时间转换提示
			String timeConversionPrompt = PromptHelper.buildTimeConversionPrompt(query);

			// 调用模型进行时间转换
			String convertedQuery = aiService.call(timeConversionPrompt);

			if (!convertedQuery.equals(query)) {
				logger.info("Time expression conversion: {} -> {}", query, convertedQuery);
			}
			else {
				logger.debug("No time expressions found or converted in query: {}", query);
			}

			return convertedQuery;

		}
		catch (Exception e) {
			logger.warn("Failed to process time expressions using AI, using original query: {}", e.getMessage());
			return query;
		}
	}

	private SchemaDTO select(String query, List<String> evidenceList, String agentId) throws Exception {
		logger.debug("Starting schema selection for query: {} with {} evidences and agentId: {}", query,
				evidenceList.size(), agentId);
		List<String> keywords = extractKeywords(query, evidenceList);
		logger.debug("Using {} keywords for schema selection", keywords != null ? keywords.size() : 0);
		SchemaDTO schemaDTO;
		if (agentId != null) {
			schemaDTO = getSchemaService().mixRagForAgent(agentId, query, keywords);
		}
		else {
			schemaDTO = getSchemaService().mixRag(query, keywords);
		}
		logger.debug("Retrieved schema with {} tables", schemaDTO.getTable() != null ? schemaDTO.getTable().size() : 0);
		SchemaDTO result = fineSelect(schemaDTO, query, evidenceList);
		logger.debug("Fine selection completed, final schema has {} tables",
				result.getTable() != null ? result.getTable().size() : 0);
		return result;
	}

	private SchemaDTO fineSelect(SchemaDTO schemaDTO, String query, List<String> evidenceList) {
		return getNl2SqlService().fineSelect(schemaDTO, query, evidenceList, null, null);
	}

}
