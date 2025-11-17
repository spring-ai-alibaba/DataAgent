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

import com.alibaba.cloud.ai.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.enums.TextType;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.FluxConverter;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.prompt.PromptConstant;
import com.alibaba.cloud.ai.prompt.PromptHelper;
import com.alibaba.cloud.ai.service.llm.LlmService;
import com.alibaba.cloud.ai.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.constant.Constant.*;

@Slf4j
@Component
@AllArgsConstructor
public class EvidenceRecallNode implements NodeAction {

	private final LlmService llmService;

	private final AgentVectorStoreService vectorStoreService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// 从state中提取question和agentId
		String question = StateUtil.getStringValue(state, INPUT_KEY);
		String agentId = StateUtil.getStringValue(state, AGENT_ID);
		Assert.hasText(agentId, "Agent ID cannot be empty.");

		log.info("Extracting keywords before getting evidence in question: {}", question);
		log.debug("Agent ID: {}", agentId);

		// 构建关键词提取提示词
		String prompt = PromptConstant.getQuestionToKeywordsPromptTemplate().render(Map.of("question", question));
		log.debug("Built evidence keyword extraction prompt as follows \n {} \n", prompt);

		// 调用LLM提取关键词
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt);
		Sinks.Many<String> evidenceDisplaySink = Sinks.many().multicast().onBackpressureBuffer();

		final Map<String, Object> resultMap = new HashMap<>();
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGenerator(this.getClass(), state,
				responseFlux,
				Flux.just(ChatResponseUtil.createResponse("正在获取关键词..."),
						ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())),
				Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
						ChatResponseUtil.createResponse("\n关键词获取完成！")),
				result -> {
					resultMap.putAll(getEvidences(result, agentId, evidenceDisplaySink));
					return resultMap;
				});

		Flux<GraphResponse<StreamingOutput>> evidenceFlux = FluxConverter.builder()
			.startingNode(this.getClass().getSimpleName())
			.startingState(state)
			.mapResult(r -> resultMap)
			.build(evidenceDisplaySink.asFlux().map(ChatResponseUtil::createPureResponse));
		return Map.of(EVIDENCE, generator.concatWith(evidenceFlux));
	}

	private Map<String, Object> getEvidences(String llmOutput, String agentId, Sinks.Many<String> sink) {
		try {
			List<String> keywords = extractKeywords(llmOutput);

			if (null == keywords || keywords.isEmpty()) {
				log.debug("No keywords extracted from LLM output");
				sink.tryEmitNext("未找到关键词！\n");
				return Map.of(EVIDENCE, "无");
			}

			// 将关键词列表用空格拼接成字符串
			sink.tryEmitNext("关键词：\n");
			keywords.forEach(keyword -> sink.tryEmitNext(keyword + " "));
			sink.tryEmitNext("\n");
			String keywordsString = String.join(" ", keywords);
			log.debug("Joined keywords string: {}", keywordsString);
			sink.tryEmitNext("正在获取证据...");

			// 获取业务知识和智能体的知识
			List<Document> businessTermDocuments = vectorStoreService
				.getDocumentsForAgent(agentId, keywordsString, DocumentMetadataConstant.BUSINESS_TERM)
				.stream()
				.toList();

			// 检查是否有证据文档
			if (businessTermDocuments.isEmpty()) {
				log.debug("No evidence documents found for agent: {} with keywords: {}", agentId, keywordsString);
				sink.tryEmitNext("未找到证据！\n");
				return Map.of(EVIDENCE, "无");
			}

			// 构建业务知识提示
			String businessKnowledgePrompt = PromptHelper.buildBusinessKnowledgePrompt(
					businessTermDocuments.stream().map(Document::getText).collect(Collectors.joining(";\n")));
			// TODO 根据知识库模板渲染智能体的知识，然后拼接成EVIDENCE。 businessKnowledgePrompt + "\n\n" +
			// agentKnowledgePrompt;
			String evidence = businessKnowledgePrompt + "\n\n";

			// 输出证据内容
			sink.tryEmitNext("证据内容：\n");
			businessTermDocuments.forEach(e -> sink.tryEmitNext(e.getText() + "\n"));
			// TODO agentKnowledge.forEach

			// 返回结果
			return Map.of(EVIDENCE, evidence);
		}
		catch (Exception e) {
			log.error("Error occurred while getting evidences", e);
			sink.tryEmitError(e);
			return Map.of(EVIDENCE, "");
		}
		finally {
			sink.tryEmitComplete();
		}
	}

	@Nullable
	private static List<String> extractKeywords(String llmOutput) {
		// 解析关键词列表
		List<String> keywords;
		try {
			String content = MarkdownParserUtil.extractText(llmOutput.trim());
			keywords = JsonUtil.getObjectMapper().readValue(content, new TypeReference<List<String>>() {
			});
			log.info("For getting evidence keyword,extracted {} keywords: {}", keywords != null ? keywords.size() : 0,
					keywords);
		}
		catch (Exception e) {
			log.error("Failed to parse keywords from LLM response", e);
			keywords = List.of(); // 使用空列表作为默认值
		}
		return keywords;
	}

}
