/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.service.aimodelconfig;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("live")
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AlibabaCloudModelLiveIT {

	private static final String EXPECTED_CHAT_RESPONSE = "DATAAGENT_LIVE_OK";

	private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode";

	private final DynamicModelFactory modelFactory = new DynamicModelFactory();

	@Test
	void streamingChat_callsAlibabaCloudAndReturnsContentWithUsage() {
		ChatModel chatModel = modelFactory.createChatModel(ModelConfigDTO.builder()
			.provider("qwen")
			.apiKey(requiredApiKey())
			.baseUrl(baseUrl())
			.modelName(environmentOrDefault("DATAAGENT_LIVE_DASHSCOPE_CHAT_MODEL", "qwen-plus"))
			.modelType("CHAT")
			.temperature(0.0)
			.maxTokens(32)
			.build());

		Prompt prompt = new Prompt(
				"Return exactly DATAAGENT_LIVE_OK with no punctuation, explanation, or Markdown formatting.");
		List<ChatResponse> responses = chatModel.stream(prompt).collectList().block(Duration.ofSeconds(90));

		assertThat(responses).isNotNull().isNotEmpty();
		String content = responses.stream()
			.map(ChatResponse::getResult)
			.filter(Objects::nonNull)
			.map(result -> result.getOutput().getText())
			.filter(Objects::nonNull)
			.reduce("", String::concat)
			.trim();
		assertThat(content).isEqualTo(EXPECTED_CHAT_RESPONSE);

		int totalTokens = responses.stream()
			.map(ChatResponse::getMetadata)
			.filter(Objects::nonNull)
			.map(metadata -> metadata.getUsage())
			.filter(Objects::nonNull)
			.map(usage -> usage.getTotalTokens())
			.filter(Objects::nonNull)
			.mapToInt(Integer::intValue)
			.max()
			.orElse(0);
		assertThat(totalTokens).isPositive();
	}

	@Test
	void embeddings_callAlibabaCloudAndReturnDistinctFiniteVectors() {
		EmbeddingModel embeddingModel = modelFactory.createEmbeddingModel(ModelConfigDTO.builder()
			.provider("qwen")
			.apiKey(requiredApiKey())
			.baseUrl(baseUrl())
			.modelName(environmentOrDefault("DATAAGENT_LIVE_DASHSCOPE_EMBEDDING_MODEL", "text-embedding-v4"))
			.modelType("EMBEDDING")
			.build());

		EmbeddingResponse response = embeddingModel
			.embedForResponse(List.of("查询订单表中的销售总额", "How to bake sourdough bread"));

		assertThat(response.getResults()).hasSize(2);
		float[] first = response.getResults().get(0).getOutput();
		float[] second = response.getResults().get(1).getOutput();
		assertVector(first);
		assertVector(second);
		assertThat(Arrays.equals(first, second)).isFalse();
		assertThat(response.getMetadata()).isNotNull();
		assertThat(response.getMetadata().getUsage()).isNotNull();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isPositive();
	}

	private void assertVector(float[] vector) {
		assertThat(vector).hasSize(1024);
		double squaredNorm = 0.0;
		for (float value : vector) {
			assertThat(Float.isFinite(value)).as("embedding value must be finite").isTrue();
			squaredNorm += value * value;
		}
		assertThat(squaredNorm).isPositive();
	}

	private String requiredApiKey() {
		String apiKey = environmentOrDefault("DATAAGENT_LIVE_DASHSCOPE_API_KEY", System.getenv("DASHSCOPE_API_KEY"));
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalStateException(
					"Set DATAAGENT_LIVE_DASHSCOPE_API_KEY or DASHSCOPE_API_KEY before running live-model tests");
		}
		return apiKey;
	}

	private String baseUrl() {
		String value = environmentOrDefault("DATAAGENT_LIVE_DASHSCOPE_BASE_URL", DEFAULT_BASE_URL).replaceAll("/+$",
				"");
		return value.endsWith("/v1") ? value.substring(0, value.length() - 3) : value;
	}

	private String environmentOrDefault(String name, String defaultValue) {
		String value = System.getenv(name);
		return StringUtils.hasText(value) ? value : defaultValue;
	}

}
