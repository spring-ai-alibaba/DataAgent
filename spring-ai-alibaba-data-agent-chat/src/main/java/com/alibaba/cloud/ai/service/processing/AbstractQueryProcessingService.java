/*
 * Copyright 2024-2025 the original author or authors.
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

package com.alibaba.cloud.ai.service.processing;

import com.alibaba.cloud.ai.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.entity.Datasource;
import com.alibaba.cloud.ai.enums.TextType;
import com.alibaba.cloud.ai.prompt.PromptConstant;
import com.alibaba.cloud.ai.prompt.PromptHelper;
import com.alibaba.cloud.ai.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.service.llm.LlmService;
import com.alibaba.cloud.ai.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.service.schema.SchemaService;
import com.alibaba.cloud.ai.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.util.ChatResponseUtil;
import com.alibaba.cloud.ai.util.FluxUtil;
import com.alibaba.cloud.ai.util.JsonUtil;
import com.alibaba.cloud.ai.util.SchemaProcessorUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
public abstract class AbstractQueryProcessingService implements QueryProcessingService {

	private final LlmService llmService;

	private final DatasourceService datasourceService;

	protected abstract AgentVectorStoreService getVectorStoreService();

	protected abstract SchemaService getSchemaService();

	protected abstract Nl2SqlService getNl2SqlService();

	@Override
	public List<String> extractEvidences(String query, String agentId) {
		log.debug("Extracting evidences for query: {} with agentId: {}", query, agentId);
		Assert.notNull(agentId, "AgentId cannot be null");
		List<Document> evidenceDocuments = getVectorStoreService().getDocumentsForAgent(agentId, query, "evidence");

		List<String> evidences = evidenceDocuments.stream().map(Document::getText).collect(Collectors.toList());
		log.debug("Extracted {} evidences: {}", evidences.size(), evidences);
		return evidences;
	}

	@Override
	public Flux<ChatResponse> extractKeywords(String query, List<String> evidenceList,
			Consumer<List<String>> resultConsumer) {
		log.debug("Extracting keywords from query: {} with {} evidences", query, evidenceList.size());
		StringBuilder queryBuilder = new StringBuilder(query);
		for (String evidence : evidenceList) {
			queryBuilder.append(evidence).append("。");
		}
		query = queryBuilder.toString();

		String prompt = PromptHelper.buildQueryToKeywordsPrompt(query);
		log.debug("Calling LLM for keyword extraction");
		StringBuilder sb = new StringBuilder();
		return llmService.callUser(prompt).doOnNext(response -> {
			String text = response.getResult().getOutput().getText();
			sb.append(text);
		}).doOnComplete(() -> {
			String content = sb.toString();
			List<String> keywords;
			try {
				keywords = JsonUtil.getObjectMapper().readValue(content, new TypeReference<>() {
				});
			}
			catch (JsonProcessingException e) {
				throw new RuntimeException(e);
			}
			log.debug("Extracted {} keywords: {}", keywords != null ? keywords.size() : 0, keywords);
			resultConsumer.accept(keywords);
		});
	}

	@Override
	public Flux<ChatResponse> expandQuestion(String query, Consumer<List<String>> resultConsumer) {
		log.info("Starting question expansion for query: {}", query);
		try {
			// Build question expansion prompt
			Map<String, Object> params = new HashMap<>();
			params.put("question", query);
			String prompt = PromptConstant.getQuestionExpansionPromptTemplate().render(params);

			// Call LLM to get expanded questions
			log.debug("Calling LLM for question expansion");
			StringBuilder sb = new StringBuilder();
			Flux<ChatResponse> responseFlux = llmService.callUser(prompt);
			return responseFlux.doOnNext(response -> {
				String text = response.getResult().getOutput().getText();
				sb.append(text);
			}).doOnComplete(() -> {
				String content = sb.toString();
				try {
					// Parse JSON response
					List<String> expandedQuestions = JsonUtil.getObjectMapper()
						.readValue(content, new TypeReference<>() {
						});

					if (expandedQuestions == null || expandedQuestions.isEmpty()) {
						log.warn("No expanded questions generated, returning original query");
						expandedQuestions = Collections.singletonList(query);
					}

					log.info("Question expansion completed successfully: {} questions generated",
							expandedQuestions.size());
					log.debug("Expanded questions: {}", expandedQuestions);
					resultConsumer.accept(expandedQuestions);
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		}
		catch (Exception e) {
			log.warn("Question expansion failed, returning original query: {}", e.getMessage());
			return Flux.error(e);
		}
	}

	@Override
	public Flux<ChatResponse> rewriteStream(String query, String agentId) {
		return FluxUtil.<ChatResponse, String>cascadeFlux(processTimeExpressions(query), timeRewrittenQuery -> {

			log.debug("Time rewritten query: {} -> {}", query, timeRewrittenQuery);

			List<String> evidences = extractEvidences(timeRewrittenQuery, agentId);
			log.debug("Extracted {} evidences for rewriteStream, they are {}", evidences.size(), evidences);

			AtomicReference<SchemaDTO> schemaDTORef = new AtomicReference<>(null);
			return FluxUtil.<ChatResponse, String>cascadeFlux(
					select(timeRewrittenQuery, evidences, agentId, schemaDTORef::set), r -> {
						SchemaDTO schemaDTO = schemaDTORef.get();
						log.debug("SchemaDTO is {}", schemaDTO);
						String prompt = PromptHelper.buildRewritePrompt(timeRewrittenQuery, schemaDTO, evidences);
						log.debug("Built rewrite prompt for streaming, prompt is as follows \n {}", prompt);
						return llmService.callUser(prompt);
					}, flux -> Mono.just(""),
					Flux.just(ChatResponseUtil.createResponse("正在选择合适的数据表...\n"),
							ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())),
					Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
							ChatResponseUtil.createResponse("\n\n选择数据表完成！")),
					Flux.empty());
		}, flux -> flux.map(r -> Optional.ofNullable(r.getResult().getOutput().getText()).orElse(""))
			.collect(StringBuilder::new, StringBuilder::append)
			.map(StringBuilder::toString), Flux.just(ChatResponseUtil.createResponse("正在替换问题中的时间表达式...\n")),
				Flux.just(ChatResponseUtil.createResponse("\n\n重写时间表达式完成！\n正在提取用户问题关键词...")), Flux.empty())
			.doOnSubscribe(s -> log.info("Starting rewriteStream for query: {} with agentId: {}", query, agentId))
			.doOnError(e -> log.error("RewriteStream failed for query: {}", e.getMessage()));
	}

	/**
	 * 处理查询中的时间表达式，将相对时间转换为具体时间
	 * @param query 原始查询
	 * @return 处理后的查询
	 */
	private Flux<ChatResponse> processTimeExpressions(String query) {
		log.debug("Processing time expressions in query: {}", query);

		// 使用统一管理的提示词构建时间转换提示
		String timeConversionPrompt = PromptHelper.buildTimeConversionPrompt(query);

		// 调用模型进行时间转换
		return llmService.callUser(timeConversionPrompt);
	}

	private Flux<ChatResponse> select(String query, List<String> evidenceList, String agentId,
			Consumer<SchemaDTO> dtoConsumer) {
		Assert.notNull(agentId, "AgentId cannot be null");
		log.debug("Starting schema selection for query: {} with {} evidences and agentId: {}", query,
				evidenceList.size(), agentId);
		AtomicReference<SchemaDTO> oldSchemaDTORef = new AtomicReference<>(null);
		return FluxUtil.<ChatResponse, String>cascadeFlux(extractKeywords(query, evidenceList, keywords -> {
			log.debug("Using {} keywords for schema selection", keywords != null ? keywords.size() : 0);

			Datasource datasource = datasourceService.getActiveDatasourceByAgentId(Integer.valueOf(agentId));
			if (datasource == null) {
				throw new RuntimeException("No active datasource found for agentId: " + agentId);
			}
			SchemaDTO schemaDTO = getSchemaService().mixRagForAgent(agentId, query, keywords,
					SchemaProcessorUtil.createDbConfigFromDatasource(datasource));
			oldSchemaDTORef.set(schemaDTO);

			log.debug("Retrieved schema with {} tables",
					schemaDTO.getTable() != null ? schemaDTO.getTable().size() : 0);
		}), r -> {
			Flux<ChatResponse> flux = fineSelect(oldSchemaDTORef.get(), query, evidenceList, result -> {
				log.debug("Fine selection completed, final schema has {} tables",
						result.getTable() != null ? result.getTable().size() : 0);
				dtoConsumer.accept(result);
			});
			return Flux.concat(Flux.just(ChatResponseUtil.createResponse("")), flux);
		}, flux -> Mono.just(""));
	}

	private Flux<ChatResponse> fineSelect(SchemaDTO schemaDTO, String query, List<String> evidenceList,
			Consumer<SchemaDTO> dtoConsumer) {
		return getNl2SqlService().fineSelect(schemaDTO, query, evidenceList, null, null, dtoConsumer);
	}

}
