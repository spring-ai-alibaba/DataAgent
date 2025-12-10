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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.model.DynamicModelFactory;
import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.model.SwitchableChatModel;
import com.alibaba.cloud.ai.dataagent.model.SwitchableEmbeddingModel;
import com.alibaba.cloud.ai.dataagent.service.ModelConfigDataService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

// TODO 2025/12/10 合并包后移动到DataAgentConfiguration  中
@Configuration
public class SwitchableModelConfig {

	@Bean
	@Primary
	public SwitchableChatModel chatModel(DynamicModelFactory modelFactory, ModelConfigDataService configService) {
		// 1. 尝试读配置
		ModelConfigDTO config = configService.getActiveConfigByType(ModelType.CHAT);

		// 2. 如果数据库没配，给一个不可用的默认值，保证系统能启动，但调用会报错提示去配置
		if (config == null) {
			config = ModelConfigDTO.builder()
				.provider("unknown")
				.baseUrl("http://please-configure-in-admin-panel")
				.apiKey("dummy-key")
				.modelName("dummy-model")
				.build();
		}

		// 3. 创建
		ChatModel initialModel = modelFactory.createChatModel(config);
		return new SwitchableChatModel(initialModel);
	}

	@Bean
	@Primary
	public SwitchableEmbeddingModel embeddingModel(DynamicModelFactory modelFactory,
			ModelConfigDataService configService) {
		ModelConfigDTO config = configService.getActiveConfigByType(ModelType.EMBEDDING);

		// 兜底逻辑：如果 config 为 null (数据库没数据 或 报错)，创建一个假的配置
		// 目的：为了让 modelFactory 能成功 new 出对象，而不报错
		if (config == null) {
			config = new ModelConfigDTO();
			config.setProvider("unknown");
			config.setBaseUrl("http://dummy-url-waiting-for-config");
			config.setApiKey("dummy-key");
			config.setModelName("dummy-model");
		}

		// 创建初始模型
		// 注意：OpenAiEmbeddingModel 在 new 的时候通常不会发起网络请求，
		// 所以即使 Key 是假的，这里也不会报错，直到真正调用 call() 时才会失败。
		EmbeddingModel initialModel = modelFactory.createEmbeddingModel(config);

		// 包装返回
		return new SwitchableEmbeddingModel(initialModel);
	}

}
