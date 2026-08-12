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
package com.alibaba.cloud.ai.dataagent.service.aimodelconfig;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/**
 * Prevents a vector collection from silently mixing embeddings produced by different
 * model spaces.
 */
@Component
@RequiredArgsConstructor
public class EmbeddingModelCompatibilityValidator {

	private final DataAgentProperties properties;

	private volatile EmbeddingModel validatedModel;

	public void validateDimension(int actualDimension) {
		int expectedDimension = properties.getVectorStore().getEmbeddingDimension();
		if (expectedDimension > 0 && expectedDimension != actualDimension) {
			throw new IllegalStateException("Embedding dimension " + actualDimension
					+ " does not match vector-store dimension " + expectedDimension
					+ ". Use a new collection and rebuild vectors before activating this model.");
		}
	}

	public void validateModel(EmbeddingModel model) {
		if (properties.getVectorStore().getEmbeddingDimension() <= 0 || validatedModel == model) {
			return;
		}
		synchronized (this) {
			if (validatedModel != model) {
				validateDimension(model.dimensions());
				validatedModel = model;
			}
		}
	}

	public void validateModelChange(ModelConfigDTO current, ModelConfigDTO target) {
		if (current == null || target == null || sameEmbeddingSpace(current, target)) {
			return;
		}
		throw new IllegalStateException("Embedding model hot switch is unsafe for existing vectors. "
				+ "Create a new versioned collection, rebuild all schema and knowledge vectors, then switch traffic.");
	}

	private boolean sameEmbeddingSpace(ModelConfigDTO left, ModelConfigDTO right) {
		return Objects.equals(left.getProvider(), right.getProvider())
				&& Objects.equals(left.getBaseUrl(), right.getBaseUrl())
				&& Objects.equals(left.getModelName(), right.getModelName())
				&& Objects.equals(left.getEmbeddingsPath(), right.getEmbeddingsPath());
	}

}
