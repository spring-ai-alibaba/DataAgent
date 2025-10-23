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

import com.alibaba.cloud.ai.pojo.KeywordExtractionResult;
import com.alibaba.cloud.ai.enums.StreamResponseType;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.service.processing.QueryProcessingService;
import com.alibaba.cloud.ai.util.ChatResponseUtil;
import com.alibaba.cloud.ai.util.FluxUtil;
import com.alibaba.cloud.ai.util.StateUtil;
import com.alibaba.cloud.ai.util.StreamingChatGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.alibaba.cloud.ai.constant.Constant.*;

/**
 * Keyword, entity, and temporal information extraction node to prepare for subsequent
 * schema recall.
 *
 * This node is responsible for: - Extracting evidences from user input - Extracting
 * keywords based on evidences - Preparing structured information for schema recall -
 * Providing streaming feedback during extraction process
 *
 * @author zhangshenghang
 */
@Component
public class KeywordExtractNode implements NodeAction {

	private static final Logger logger = LoggerFactory.getLogger(KeywordExtractNode.class);

	private final QueryProcessingService queryProcessingService;

	public KeywordExtractNode(QueryProcessingService queryProcessingService) {
		this.queryProcessingService = queryProcessingService;
	}

	/**
	 * Process multiple question variants, extract keywords and merge results Use parallel
	 * stream processing to improve multi-question processing efficiency
	 * @param questions list of question variants
	 * @return list of extraction results
	 */
	private Flux<ChatResponse> processMultipleQuestions(List<String> questions, String agentId,
			Consumer<List<KeywordExtractionResult>> resultConsumer) {
		List<KeywordExtractionResult> resultList = new ArrayList<>(questions.size());
		AtomicReference<Flux<ChatResponse>> fluxRef = new AtomicReference<>(Flux.empty());
		questions.forEach(question -> {
			List<String> evidences = queryProcessingService.extractEvidences(question, agentId);
			fluxRef
				.set(fluxRef.get().concatWith(queryProcessingService.extractKeywords(question, evidences, keywords -> {
					logger.info("成功从问题变体提取关键词: 问题=\"{}\", 关键词={}", question, keywords);
					resultList.add(new KeywordExtractionResult(question, evidences, keywords));
				}).doOnError(e -> {
					logger.warn("从问题变体提取关键词失败: 问题={}", question, e);
					resultList.add(new KeywordExtractionResult(question, false));
				})));
		});
		return fluxRef.get().doOnComplete(() -> resultConsumer.accept(resultList));
	}

	/**
	 * Merge keywords from multiple question variants, deduplicate and keep original
	 * question keywords priority
	 * @param extractionResults list of extraction results
	 * @param originalQuestion original question
	 * @return merged keyword list
	 */
	private List<String> mergeKeywords(List<KeywordExtractionResult> extractionResults, String originalQuestion) {
		if (extractionResults.isEmpty()) {
			return List.of();
		}

		Set<String> mergedKeywords = new LinkedHashSet<>();

		extractionResults.stream()
			.filter(result -> result.isSuccessful() && result.getQuestion().equals(originalQuestion))
			.findFirst()
			.ifPresent(result -> mergedKeywords.addAll(result.getKeywords()));

		extractionResults.stream()
			.filter(result -> result.isSuccessful() && !result.getQuestion().equals(originalQuestion))
			.forEach(result -> mergedKeywords.addAll(result.getKeywords()));

		return new ArrayList<>(mergedKeywords);
	}

	/**
	 * Merge evidences from multiple question variants, deduplicate
	 * @param extractionResults list of extraction results
	 * @return merged evidence list
	 */
	private List<String> mergeEvidences(List<KeywordExtractionResult> extractionResults) {
		Set<String> mergedEvidences = new HashSet<>();

		extractionResults.stream()
			.filter(KeywordExtractionResult::isSuccessful)
			.forEach(result -> mergedEvidences.addAll(result.getEvidences()));

		return new ArrayList<>(mergedEvidences);
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		logger.info("Entering {} node", this.getClass().getSimpleName());

		String input = StateUtil.getStringValue(state, QUERY_REWRITE_NODE_OUTPUT,
				StateUtil.getStringValue(state, INPUT_KEY));

		try {
			logger.info("开始增强关键词提取处理...");

			Map<String, Object> resultMap = new HashMap<>();

			AtomicReference<List<String>> expandedQuestionsRef = new AtomicReference<>(null);
			Sinks.Many<ChatResponse> sink = Sinks.many().multicast().onBackpressureBuffer();
			Flux<ChatResponse> displayFlux = FluxUtil
				.<ChatResponse, String>cascadeFlux(queryProcessingService.expandQuestion(input, expandedQuestions -> {
					logger.info("问题扩展结果: {}", expandedQuestions);
					expandedQuestionsRef.set(expandedQuestions);
				}), r -> processMultipleQuestions(expandedQuestionsRef.get(), StateUtil.getStringValue(state, AGENT_ID),
						extractionResults -> {
							List<String> mergedKeywords = mergeKeywords(extractionResults, input);
							List<String> mergedEvidences = mergeEvidences(extractionResults);

							logger.info("[{}] 增强提取结果 - 证据: {}, 关键词: {}", this.getClass().getSimpleName(),
									mergedEvidences, mergedKeywords);
							resultMap.putAll(Map.of(KEYWORD_EXTRACT_NODE_OUTPUT, mergedKeywords, EVIDENCES,
									mergedEvidences, RESULT, mergedKeywords));
							sink.tryEmitNext(ChatResponseUtil
								.createStatusResponse("合并后的证据: " + String.join(", ", mergedEvidences)));
							sink.tryEmitNext(ChatResponseUtil
								.createStatusResponse("合并后的关键词: " + String.join(", ", mergedKeywords)));
							sink.tryEmitNext(ChatResponseUtil.createStatusResponse("关键词提取完成."));
							sink.tryEmitComplete();
						}), flux -> Mono.just(""),
						Flux.just(ChatResponseUtil.createStatusResponse("开始增强关键词提取..."),
								ChatResponseUtil.createStatusResponse("正在扩展问题理解...")),
						Flux.defer(() -> Flux.just(
								ChatResponseUtil.createStatusResponse("\n问题扩展结果：" + expandedQuestionsRef.get()),
								ChatResponseUtil.createStatusResponse("合并多个问题变体的结果...\n"))),
						sink.asFlux());

			var generator = StreamingChatGeneratorUtil.createStreamingGeneratorWithMessages(this.getClass(), state,
					v -> resultMap, displayFlux, StreamResponseType.KEYWORD_EXTRACT);

			return Map.of(KEYWORD_EXTRACT_NODE_OUTPUT, generator);

		}
		catch (Exception e) {
			logger.error("增强关键词提取失败{}", e.getMessage());
			throw new RuntimeException(e);
		}
	}

}
