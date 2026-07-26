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
import com.alibaba.cloud.ai.dataagent.support.KeywordEmbeddingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingModelCompatibilityValidatorTest {

	private EmbeddingModelCompatibilityValidator validator;

	@BeforeEach
	void setUp() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getVectorStore().setEmbeddingDimension(1024);
		validator = new EmbeddingModelCompatibilityValidator(properties);
	}

	@Test
	void matchingDimensionIsAccepted() {
		assertThatCode(() -> validator.validateDimension(1024)).doesNotThrowAnyException();
	}

	@Test
	void mismatchedDimensionIsRejectedBeforeVectorsAreMixed() {
		assertThatThrownBy(() -> validator.validateDimension(1536)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("new collection");
	}

	@Test
	void changingEmbeddingSpaceIsRejected() {
		ModelConfigDTO current = embeddingConfig("provider", "https://one", "embedding-a");
		ModelConfigDTO target = embeddingConfig("provider", "https://one", "embedding-b");

		assertThatThrownBy(() -> validator.validateModelChange(current, target))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("rebuild all schema and knowledge vectors");
	}

	@Test
	void credentialRotationForTheSameEmbeddingSpaceIsAccepted() {
		ModelConfigDTO current = embeddingConfig("provider", "https://one", "embedding-a");
		current.setApiKey("old-key");
		ModelConfigDTO target = embeddingConfig("provider", "https://one", "embedding-a");
		target.setApiKey("new-key");

		assertThatCode(() -> validator.validateModelChange(current, target)).doesNotThrowAnyException();
	}

	@Test
	void embeddingModelIsValidatedOnceAgainstTheConfiguredDimension() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getVectorStore().setEmbeddingDimension(3);
		EmbeddingModelCompatibilityValidator dimensionValidator = new EmbeddingModelCompatibilityValidator(properties);
		KeywordEmbeddingModel model = new KeywordEmbeddingModel();

		assertThatCode(() -> {
			dimensionValidator.validateModel(model);
			dimensionValidator.validateModel(model);
		}).doesNotThrowAnyException();
	}

	@Test
	void disabledDimensionValidationDoesNotInspectTheModel() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getVectorStore().setEmbeddingDimension(0);
		EmbeddingModelCompatibilityValidator dimensionValidator = new EmbeddingModelCompatibilityValidator(properties);

		assertThatCode(() -> dimensionValidator.validateModel(null)).doesNotThrowAnyException();
	}

	@Test
	void missingEmbeddingConfigurationsDoNotTriggerAHotSwitchFailure() {
		ModelConfigDTO target = embeddingConfig("provider", "https://one", "embedding-a");

		assertThatCode(() -> validator.validateModelChange(null, target)).doesNotThrowAnyException();
		assertThatCode(() -> validator.validateModelChange(target, null)).doesNotThrowAnyException();
	}

	private ModelConfigDTO embeddingConfig(String provider, String baseUrl, String modelName) {
		return ModelConfigDTO.builder()
			.provider(provider)
			.baseUrl(baseUrl)
			.modelName(modelName)
			.embeddingsPath("/v1/embeddings")
			.modelType("EMBEDDING")
			.build();
	}

}
