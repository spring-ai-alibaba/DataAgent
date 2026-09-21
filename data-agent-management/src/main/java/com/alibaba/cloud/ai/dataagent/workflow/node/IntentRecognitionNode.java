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

import com.alibaba.cloud.ai.dataagent.dto.prompt.IntentRecognitionOutputDTO;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.MarkdownCodeBlockCleaner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.HashMap;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 意图识别节点，用于识别用户输入是闲聊还是数据分析请求
 */
@Slf4j
@Component
@AllArgsConstructor
public class IntentRecognitionNode implements NodeAction {

	private static final BeanOutputConverter<IntentRecognitionOutputDTO> OUTPUT_CONVERTER = new BeanOutputConverter<>(
			IntentRecognitionOutputDTO.class);

	private static final MarkdownCodeBlockCleaner MARKDOWN_CLEANER = new MarkdownCodeBlockCleaner();

	private static final ObjectReader JSON_READER = new ObjectMapper().reader()
		.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

	private static final String THINK_END_TAG = "</think>";

	private final LlmService llmService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// 获取用户输入
		String userInput = StateUtil.getStringValue(state, INPUT_KEY);
		log.debug("User input for intent recognition: {}", userInput);

		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

		// 构建意图识别提示
		String prompt = PromptHelper.buildIntentRecognitionPrompt(multiTurn, userInput);
		log.debug("Built intent recognition prompt as follows \n {} \n", prompt);

		// 调用LLM进行意图识别
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt, IntentRecognitionOutputDTO.class);

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGenerator(this.getClass(), state,
				responseFlux,
				Flux.just(ChatResponseUtil.createResponse("正在进行意图识别..."),
						ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())),
				Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
						ChatResponseUtil.createResponse("\n意图识别完成！")),
				result -> {
					IntentRecognitionOutputDTO intent = OUTPUT_CONVERTER.convert(removeThinkingPrefix(result));
					Map<String, Object> output = new HashMap<>();
					output.put(INTENT_RECOGNITION_NODE_OUTPUT, intent);
					if ("《闲聊或无关指令》".equals(intent.getClassification())
							&& org.springframework.util.StringUtils.hasText(intent.getResponse())) {
						output.put(FINAL_ANSWER, intent.getResponse().trim());
					}
					return output;
				});
		return Map.of(INTENT_RECOGNITION_NODE_OUTPUT, generator);
	}

	private static String removeThinkingPrefix(String response) {
		for (int index = response.lastIndexOf(THINK_END_TAG); index >= 0; index = response.lastIndexOf(THINK_END_TAG,
				index - 1)) {
			String candidate = MARKDOWN_CLEANER.clean(response.substring(index + THINK_END_TAG.length()));
			if (!candidate.startsWith("{")) {
				continue;
			}
			try {
				JsonNode json = JSON_READER.readTree(candidate);
				if (json != null && json.isObject()) {
					return candidate;
				}
			}
			catch (JsonProcessingException ex) {
				// A closing tag may be quoted inside the JSON or the reasoning text.
				// Remove a prefix only when the remaining response is a complete object.
			}
		}
		return response;
	}

}
