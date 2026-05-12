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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.ModelConfigDataService;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.ModelConfigOpsService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import com.alibaba.cloud.ai.dataagent.vo.ModelCheckVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelConfigControllerTest {

	@Mock
	private ModelConfigDataService modelConfigDataService;

	@Mock
	private ModelConfigOpsService modelConfigOpsService;

	private ModelConfigController modelConfigController;

	@BeforeEach
	void setUp() {
		modelConfigController = new ModelConfigController(modelConfigDataService, modelConfigOpsService);
	}

	@Test
	void createModelConfig_validRequest_returnsCreated() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.baseUrl("https://api.openai.com")
			.modelName("gpt-4")
			.modelType("CHAT")
			.build();
		doNothing().when(modelConfigDataService).addConfig(any(ModelConfigDTO.class));

		ApiResponse<String> result = modelConfigController.add(config);

		assertTrue(result.isSuccess());
		verify(modelConfigDataService).addConfig(any(ModelConfigDTO.class));
	}

	@Test
	void activateModel_validId_activatesModel() {
		doNothing().when(modelConfigOpsService).activateConfig(1);

		ApiResponse<String> result = modelConfigController.activate(1);

		assertTrue(result.isSuccess());
		verify(modelConfigOpsService).activateConfig(1);
	}

	@Test
	void activateModel_failure_returnsError() {
		doThrow(new RuntimeException("config not found")).when(modelConfigOpsService).activateConfig(999);

		ApiResponse<String> result = modelConfigController.activate(999);

		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().contains("config not found"));
	}

	@Test
	void testConnection_validConfig_returnsSuccess() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.baseUrl("https://api.openai.com")
			.modelName("gpt-4")
			.modelType("CHAT")
			.build();
		doNothing().when(modelConfigOpsService).testConnection(any(ModelConfigDTO.class));

		ApiResponse<String> result = modelConfigController.testConnection(config);

		assertTrue(result.isSuccess());
	}

	@Test
	void checkReady_allConfigured_returnsReady() {
		ModelConfigDTO chatConfig = ModelConfigDTO.builder().modelType("CHAT").isActive(true).build();
		ModelConfigDTO embeddingConfig = ModelConfigDTO.builder().modelType("EMBEDDING").isActive(true).build();
		when(modelConfigDataService.getActiveConfigByType(ModelType.CHAT)).thenReturn(chatConfig);
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(embeddingConfig);

		ApiResponse<ModelCheckVo> result = modelConfigController.checkReady();

		assertTrue(result.isSuccess());
		assertTrue(result.getData().isReady());
		assertTrue(result.getData().isChatModelReady());
		assertTrue(result.getData().isEmbeddingModelReady());
	}

	@Test
	void checkReady_missingEmbedding_returnsNotReady() {
		ModelConfigDTO chatConfig = ModelConfigDTO.builder().modelType("CHAT").isActive(true).build();
		when(modelConfigDataService.getActiveConfigByType(ModelType.CHAT)).thenReturn(chatConfig);
		when(modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING)).thenReturn(null);

		ApiResponse<ModelCheckVo> result = modelConfigController.checkReady();

		assertTrue(result.isSuccess());
		assertFalse(result.getData().isReady());
		assertTrue(result.getData().isChatModelReady());
		assertFalse(result.getData().isEmbeddingModelReady());
	}

}
