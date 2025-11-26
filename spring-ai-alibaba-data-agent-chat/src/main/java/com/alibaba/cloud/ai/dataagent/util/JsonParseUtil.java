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

import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * JSON解析工具类，支持自动修复格式错误的JSON
 */
@Slf4j
@Component
@AllArgsConstructor
public class JsonParseUtil {

	private ObjectMapper objectMapper;

	private LlmService llmService;

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
		if (json == null || json.trim().isEmpty()) {
			return null;
		}

		try {
			return objectMapper.readValue(json, clazz);
		}
		catch (JsonProcessingException e) {
			// 构建修复词
			String prompt = JSON_FIX_PROMPT.replace("{json_string}", json).replace("{error_message}", e.getMessage());

			Flux<ChatResponse> responseFlux = llmService.callUser(prompt);
			String fixedJson = llmService.toStringFlux(responseFlux)
				.collect(StringBuilder::new, StringBuilder::append)
				.map(StringBuilder::toString)
				.block();
			try {
				return objectMapper.readValue(fixedJson, clazz);
			}
			catch (JsonProcessingException retryException) {
				log.error("LLM修复后仍然无法解释: {}", retryException.getMessage());
				log.error("修复后的json: {}", fixedJson);
				throw new IllegalArgumentException("无法解析JSON，即使经过LLM修复也失败", retryException);
			}

		}

	}

}