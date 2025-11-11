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

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.prompt.PromptConstant;
import com.alibaba.cloud.ai.service.llm.LlmService;
import com.alibaba.cloud.ai.service.processing.QueryProcessingService;
import com.alibaba.cloud.ai.util.JsonUtil;
import com.alibaba.cloud.ai.util.StateUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.constant.Constant.EVIDENCES;
import static com.alibaba.cloud.ai.constant.Constant.INPUT_KEY;

@Slf4j
@Component
public class EvidenceRecallNode implements NodeAction {

	private final LlmService llmService;

	private final QueryProcessingService queryProcessingService;

	@Autowired
	public EvidenceRecallNode(LlmService llmService, QueryProcessingService queryProcessingService) {
		this.llmService = llmService;
		this.queryProcessingService = queryProcessingService;
	}

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
		log.debug("Built evidence keyword extraction prompt");

		// 调用LLM提取关键词
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt);

		// 收集响应结果
		StringBuilder resultBuilder = new StringBuilder();
		responseFlux.doOnNext(response -> {
			String text = response.getResult().getOutput().getText();
			resultBuilder.append(text);
		}).blockLast();

		// 解析关键词列表
		List<String> keywords;
		try {
			String content = resultBuilder.toString().trim();
			keywords = JsonUtil.getObjectMapper().readValue(content, new TypeReference<List<String>>() {
			});
			log.info("For getting evidence keyword,extracted {} keywords: {}", keywords != null ? keywords.size() : 0,
					keywords);
		}
		catch (Exception e) {
			log.error("Failed to parse keywords from LLM response", e);
			keywords = List.of(); // 使用空列表作为默认值
		}

		if (null == keywords || keywords.isEmpty())
			return Map.of(EVIDENCES, List.of());

		// 将关键词列表用空格拼接成字符串
		String keywordsString = String.join(" ", keywords);
		log.debug("Joined keywords string: {}", keywordsString);

		// 调用QueryProcessingService获取evidence
		List<String> evidences = queryProcessingService.extractEvidences(keywordsString, agentId);
		log.info("Retrieved {} evidences", evidences != null ? evidences.size() : 0);

		if (null == evidences)
			return Map.of(EVIDENCES, List.of());
		// 返回结果
		return Map.of(EVIDENCES, evidences);
	}

}
