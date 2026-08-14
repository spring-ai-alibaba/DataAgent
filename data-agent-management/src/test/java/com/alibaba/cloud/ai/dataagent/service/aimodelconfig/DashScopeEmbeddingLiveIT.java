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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("live")
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class DashScopeEmbeddingLiveIT {

	private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

	private static final String DEFAULT_EMBEDDINGS_PATH = "/embeddings";

	private static final int EXPECTED_DIMENSIONS = 1024;

	private final DynamicModelFactory modelFactory = new DynamicModelFactory();

	@Test
	void embeddings_callDashScopeAndReturnDistinctFiniteVectorsWithUsage() {
		EmbeddingModel embeddingModel = modelFactory.createEmbeddingModel(ModelConfigDTO.builder()
			.provider("qwen")
			.apiKey(requiredApiKey())
			.baseUrl(baseUrl())
			.embeddingsPath(environmentOrDefault("DATAAGENT_LIVE_DASHSCOPE_EMBEDDINGS_PATH", DEFAULT_EMBEDDINGS_PATH))
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
		assertThat(vector).hasSize(EXPECTED_DIMENSIONS);
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
		if (!apiKey.equals(apiKey.strip())) {
			throw new IllegalStateException("DashScope API key must not contain leading or trailing whitespace");
		}
		return apiKey;
	}

	private String baseUrl() {
		return environmentOrDefault("DATAAGENT_LIVE_DASHSCOPE_BASE_URL", DEFAULT_BASE_URL).replaceAll("/+$", "");
	}

	private String environmentOrDefault(String name, String defaultValue) {
		String value = System.getenv(name);
		return StringUtils.hasText(value) ? value : defaultValue;
	}

}
