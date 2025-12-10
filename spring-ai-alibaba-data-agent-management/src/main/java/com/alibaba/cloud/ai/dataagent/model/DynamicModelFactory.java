/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.model;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DynamicModelFactory {

	/**
	 * 统一使用 OpenAiChatModel，通过 baseUrl 实现多厂商兼容
	 */
	public ChatModel createChatModel(ModelConfigDTO config) {
		// 1. 准备 Base URL
		String baseUrl = config.getBaseUrl();
		if (!StringUtils.hasText(baseUrl))
			throw new IllegalArgumentException("baseUrl must not be empty");

		String apiKey = config.getApiKey();
		if (!StringUtils.hasText(apiKey))
			throw new IllegalArgumentException("apiKey must not be empty");

		String modelName = config.getModelName();
		if (!StringUtils.hasText(modelName))
			throw new IllegalArgumentException("modelName must not be empty");

		// 2. 构建 OpenAiApi (核心通讯对象)
		var openAiApi = OpenAiApi.builder().apiKey(apiKey).baseUrl(baseUrl).build();

		// 3. 构建运行时选项 (设置默认的模型名称，如 "deepseek-chat" 或 "gpt-4")
		var openAiChatOptions = OpenAiChatOptions.builder()
			.model(modelName)
			.temperature(config.getTemperature())
			.maxTokens(config.getMaxTokens())
			.build();
		// 4. 返回统一的 OpenAiChatModel
		return OpenAiChatModel.builder().openAiApi(openAiApi).defaultOptions(openAiChatOptions).build();
	}

	/**
	 * Embedding 同理
	 */
	public EmbeddingModel createEmbeddingModel(ModelConfigDTO config) {
		var openAiApi = OpenAiApi.builder().apiKey(config.getApiKey()).baseUrl(config.getBaseUrl()).build();

		return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
				OpenAiEmbeddingOptions.builder().model(config.getModelName()).build(),
				RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

}
