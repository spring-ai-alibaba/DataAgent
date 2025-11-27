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

package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.common.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

/**
 * JSON解析工具类，支持自动修复格式错误的JSON
 */
@Slf4j
@Component
@AllArgsConstructor
public class JsonParseUtil {

	private LlmService llmService;

	private static final int MAX_RETRY_COUNT = 3;

	private static final String JSON_FIX_PROMPT = """
			你是一个JSON格式修复专家。用户提供了一个格式错误的JSON字符串，请帮助修复它。

			要求：
			1. 只输出修复后的JSON，不要包含任何解释或其他内容
			2. 保持原始数据的语义不变
			3. 确保输出是有效的JSON格式
			4. 移除任何非JSON内容（如XML标签、推理过程等）

			错误的JSON字符串：
			{json_string}

			错误信息：
			{error_message}

			请输出修复后的JSON：
			""";

	public <T> T tryConvertToObject(String json, Class<T> clazz) {
		Assert.hasText(json, "Input JSON string cannot be null or empty");
		Assert.notNull(clazz, "Target class cannot be null");

		String currentJson = json;
		Exception lastException = null;
		ObjectMapper objectMapper = JsonUtil.getObjectMapper();

		try {
			return objectMapper.readValue(json, clazz);
		}
		catch (JsonProcessingException e) {
			log.warn("首次解析失败,准备调用LLM: {}", e.getMessage());
		}

		for (int i = 0; i < MAX_RETRY_COUNT; i++) {
			try {
				currentJson = callLlmToFix(currentJson,
						lastException != null ? lastException.getMessage() : "Unknown error");

				return objectMapper.readValue(currentJson, clazz);
			}
			catch (JsonProcessingException e) {
				lastException = e;
				log.warn("第 {} 次修复后仍然失败: {}", i + 1, e.getMessage());

				if (i == MAX_RETRY_COUNT - 1) {
					log.error("经过 {} 次修复尝试后最终失败", MAX_RETRY_COUNT);
					log.warn("最后一次修复结果: {}", currentJson);
				}
			}
		}

		throw new IllegalArgumentException(String.format("无法解析JSON，经过 %d 次LLM修复尝试后仍然失败", MAX_RETRY_COUNT),
				lastException);

	}

	private String callLlmToFix(String json, String errorMessage) {
		try {
			String prompt = JSON_FIX_PROMPT.replace("{json_string}", json).replace("{error_message}", errorMessage);

			Flux<ChatResponse> responseFlux = llmService.callUser(prompt);
			String fixedJson = llmService.toStringFlux(responseFlux)
				.collect(StringBuilder::new, StringBuilder::append)
				.map(StringBuilder::toString)
				.block();

			String cleanedJson = MarkdownParserUtil.extractRawText(fixedJson);

			return cleanedJson;
		}
		catch (Exception e) {
			log.error("调用 LLM 修复服务时发生异常", e);
			return json;
		}
	}

}
