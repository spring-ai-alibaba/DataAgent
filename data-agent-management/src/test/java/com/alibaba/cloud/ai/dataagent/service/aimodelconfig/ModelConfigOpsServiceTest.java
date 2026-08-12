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

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelConfigOpsServiceTest {

	private ModelConfigOpsService service;

	@Mock
	private ModelConfigDataService modelConfigDataService;

	@Mock
	private DynamicModelFactory modelFactory;

	@Mock
	private AiModelRegistry aiModelRegistry;

	@BeforeEach
	void setUp() {
		service = new ModelConfigOpsService(modelConfigDataService, modelFactory, aiModelRegistry,
				new EmbeddingModelCompatibilityValidator(new DataAgentProperties()));
	}

	@Test
	void testUpdateAndRefresh_activeChat() {
		ModelConfigDTO dto = new ModelConfigDTO();
		ModelConfig entity = new ModelConfig();
		entity.setIsActive(true);
		entity.setModelType(ModelType.CHAT);
		when(modelConfigDataService.updateConfigInDb(dto)).thenReturn(entity);

		service.updateAndRefresh(dto);

		verify(aiModelRegistry).refreshChat();
	}

	@Test
	void testUpdateAndRefresh_activeEmbedding() {
		ModelConfigDTO dto = new ModelConfigDTO();
		ModelConfig entity = new ModelConfig();
		entity.setIsActive(true);
		entity.setModelType(ModelType.EMBEDDING);
		when(modelConfigDataService.updateConfigInDb(dto)).thenReturn(entity);

		service.updateAndRefresh(dto);

		verify(aiModelRegistry).refreshEmbedding();
	}

	@Test
	void testUpdateAndRefresh_inactive() {
		ModelConfigDTO dto = new ModelConfigDTO();
		ModelConfig entity = new ModelConfig();
		entity.setIsActive(false);
		when(modelConfigDataService.updateConfigInDb(dto)).thenReturn(entity);

		service.updateAndRefresh(dto);

		verify(aiModelRegistry, never()).refreshChat();
		verify(aiModelRegistry, never()).refreshEmbedding();
	}

	@Test
	void testActivateConfig_chat() {
		ModelConfig entity = new ModelConfig();
		entity.setModelType(ModelType.CHAT);
		when(modelConfigDataService.findById(1)).thenReturn(entity);

		service.activateConfig(1);

		InOrder activationOrder = inOrder(modelConfigDataService, aiModelRegistry);
		activationOrder.verify(modelConfigDataService).switchActiveStatus(1, ModelType.CHAT);
		activationOrder.verify(aiModelRegistry).refreshChat();
	}

	@Test
	void testActivateConfig_notFound() {
		when(modelConfigDataService.findById(1)).thenReturn(null);

		assertThrowsExactly(RuntimeException.class, () -> service.activateConfig(1));
	}

	@Test
	void testTestConnection_chat_usesStoredApiKey() {
		ModelConfig entity = new ModelConfig();
		entity.setId(1);
		entity.setModelType(ModelType.CHAT);
		entity.setProvider("openai");
		entity.setModelName("gpt-4");
		entity.setApiKey("stored-api-key");
		when(modelConfigDataService.findById(1)).thenReturn(entity);

		ChatModel chatModel = mock(ChatModel.class);
		when(modelFactory.createChatModel(argThat(config -> "stored-api-key".equals(config.getApiKey()))))
			.thenReturn(chatModel);
		when(chatModel.call("Hello")).thenReturn("Hi there");

		assertDoesNotThrow(() -> service.testConnection(1));
		verify(modelFactory).createChatModel(argThat(config -> "stored-api-key".equals(config.getApiKey())));
		verify(chatModel).call("Hello");
	}

	@Test
	void testTestConnection_embedding() {
		ModelConfig entity = new ModelConfig();
		entity.setId(2);
		entity.setModelType(ModelType.EMBEDDING);
		entity.setProvider("openai");
		entity.setModelName("text-embedding");
		when(modelConfigDataService.findById(2)).thenReturn(entity);

		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(modelFactory.createEmbeddingModel(any(ModelConfigDTO.class))).thenReturn(embeddingModel);
		when(embeddingModel.embed("Test")).thenReturn(new float[] { 0.1f, 0.2f });

		assertDoesNotThrow(() -> service.testConnection(2));
		verify(modelFactory).createEmbeddingModel(argThat(config -> "text-embedding".equals(config.getModelName())));
		verify(embeddingModel).embed("Test");
	}

	@Test
	void testTestConnection_unknownType() {
		ModelConfig entity = new ModelConfig();
		entity.setId(3);
		entity.setModelType(null);
		when(modelConfigDataService.findById(3)).thenReturn(entity);

		IllegalArgumentException exception = assertThrowsExactly(IllegalArgumentException.class,
				() -> service.testConnection(3));

		assertEquals("未知的模型类型: null", exception.getMessage());
		verifyNoInteractions(modelFactory);
	}

	@Test
	void testTestConnection_notFound() {
		when(modelConfigDataService.findById(99)).thenReturn(null);

		IllegalArgumentException exception = assertThrowsExactly(IllegalArgumentException.class,
				() -> service.testConnection(99));

		assertEquals("配置不存在", exception.getMessage());
		verifyNoInteractions(modelFactory);
	}

	@Test
	void testTestConnection_chatReturnsEmpty() {
		ModelConfig entity = new ModelConfig();
		entity.setId(4);
		entity.setModelType(ModelType.CHAT);
		when(modelConfigDataService.findById(4)).thenReturn(entity);

		ChatModel chatModel = mock(ChatModel.class);
		when(modelFactory.createChatModel(any(ModelConfigDTO.class))).thenReturn(chatModel);
		when(chatModel.call("Hello")).thenReturn("");

		RuntimeException exception = assertThrowsExactly(RuntimeException.class, () -> service.testConnection(4));

		assertEquals("模型返回内容为空", exception.getMessage());
	}

	@Test
	void testTestConnection_embeddingReturnsEmpty() {
		ModelConfig entity = new ModelConfig();
		entity.setId(5);
		entity.setModelType(ModelType.EMBEDDING);
		when(modelConfigDataService.findById(5)).thenReturn(entity);

		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(modelFactory.createEmbeddingModel(any(ModelConfigDTO.class))).thenReturn(embeddingModel);
		when(embeddingModel.embed("Test")).thenReturn(new float[0]);

		RuntimeException exception = assertThrowsExactly(RuntimeException.class, () -> service.testConnection(5));

		assertEquals("模型生成的向量为空", exception.getMessage());
	}

	@Test
	void testTestConnection_exceptionWithoutMessage_returnsExceptionType() {
		ModelConfig entity = new ModelConfig();
		entity.setId(6);
		entity.setModelType(ModelType.CHAT);
		when(modelConfigDataService.findById(6)).thenReturn(entity);

		ChatModel chatModel = mock(ChatModel.class);
		when(modelFactory.createChatModel(any(ModelConfigDTO.class))).thenReturn(chatModel);
		when(chatModel.call("Hello")).thenThrow(new RuntimeException());

		RuntimeException exception = assertThrowsExactly(RuntimeException.class, () -> service.testConnection(6));

		assertEquals("RuntimeException", exception.getMessage());
	}

}
