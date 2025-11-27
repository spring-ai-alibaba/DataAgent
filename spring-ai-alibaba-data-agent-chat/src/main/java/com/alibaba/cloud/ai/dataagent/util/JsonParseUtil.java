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
import com.alibaba.cloud.ai.dataagent.prompt.PromptConstant;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * JSON解析工具类，支持自动修复格式错误的JSON
 */
@Slf4j
@Component
@AllArgsConstructor
public class JsonParseUtil {

	private LlmService llmService;

	private static final int MAX_RETRY_COUNT = 3;

	public <T> T tryConvertToObject(String json, Class<T> clazz) {
		Assert.hasText(json, "Input JSON string cannot be null or empty");
		Assert.notNull(clazz, "Target class cannot be null");

		return tryConvertToObjectInternal(json, (mapper, currentJson) -> mapper.readValue(currentJson, clazz));
	}

	/**
	 * 尝试将JSON字符串转换为指定类型，支持TypeReference（如List<String>等复杂类型）
	 * @param json JSON字符串
	 * @param typeReference 类型引用
	 * @return 转换后的对象
	 */
	public <T> T tryConvertToObject(String json, TypeReference<T> typeReference) {
		Assert.hasText(json, "Input JSON string cannot be null or empty");
		Assert.notNull(typeReference, "TypeReference cannot be null");

		return tryConvertToObjectInternal(json, (mapper, currentJson) -> mapper.readValue(currentJson, typeReference));
	}

	/**
	 * 内部通用方法，用于JSON解析和修复
	 * @param json JSON字符串
	 * @param parser 解析器函数
	 * @return 转换后的对象
	 */
	private <T> T tryConvertToObjectInternal(String json, JsonParserFunction<T> parser) {
		String currentJson = json;
		Exception lastException = null;
		ObjectMapper objectMapper = JsonUtil.getObjectMapper();

		try {
			return parser.parse(objectMapper, currentJson);
		}
		catch (JsonProcessingException e) {
			log.warn("Initial parsing failed, preparing to call LLM: {}", e.getMessage());
		}

		for (int i = 0; i < MAX_RETRY_COUNT; i++) {
			try {
				currentJson = callLlmToFix(currentJson,
						lastException != null ? lastException.getMessage() : "Unknown error");

				return parser.parse(objectMapper, currentJson);
			}
			catch (JsonProcessingException e) {
				lastException = e;
				log.warn("Still failed after {} fix attempt: {}", i + 1, e.getMessage());

				if (i == MAX_RETRY_COUNT - 1) {
					log.error("Finally failed after {} fix attempts", MAX_RETRY_COUNT);
					log.warn("Last fix result: {}", currentJson);
				}
			}
		}

		throw new IllegalArgumentException(
				String.format("Failed to parse JSON after %d LLM fix attempts", MAX_RETRY_COUNT), lastException);
	}

	/**
	 * 函数式接口，用于JSON解析
	 */
	@FunctionalInterface
	private interface JsonParserFunction<T> {

		T parse(ObjectMapper mapper, String json) throws JsonProcessingException;

	}

	private String callLlmToFix(String json, String errorMessage) {
		try {
			String prompt = PromptConstant.getJsonFixPromptTemplate()
				.render(Map.of("json_string", json, "error_message", errorMessage));

			Flux<ChatResponse> responseFlux = llmService.callUser(prompt);
			String fixedJson = llmService.toStringFlux(responseFlux)
				.collect(StringBuilder::new, StringBuilder::append)
				.map(StringBuilder::toString)
				.block();

			// 检查fixedJson是否为null
			if (fixedJson == null) {
				log.warn("LLM fix returned null, using original JSON");
				return json;
			}

			String cleanedJson = MarkdownParserUtil.extractRawText(fixedJson);

			// 确保返回的JSON不为null
			return cleanedJson != null ? cleanedJson : json;
		}
		catch (Exception e) {
			log.error("Exception occurred while calling LLM fix service", e);
			return json;
		}
	}

}
