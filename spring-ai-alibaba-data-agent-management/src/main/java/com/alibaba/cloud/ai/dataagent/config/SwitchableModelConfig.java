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
// TODO 2025/12/10 合并包后移动到DataAgentConfiguration  中

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.model.DynamicModelFactory;
import com.alibaba.cloud.ai.dataagent.service.ModelConfigDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.HotSwappableTargetSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class SwitchableModelConfig {

	// ========================================================================
	// 1. ChatModel 配置区域
	// ========================================================================
	/**
	 * 定义 ChatModel 的“热插拔源”。 这个 Bean 持有真正的 ChatModel 实例，Service 层注入它来执行 swap() 操作。
	 */
	@Bean
	public HotSwappableTargetSource chatModelTargetSource(DynamicModelFactory factory,
			ModelConfigDataService modelConfigDataService) {
		// 1. 获取 Chat 配置
		ModelConfigDTO config = modelConfigDataService.getActiveConfigByType(ModelType.CHAT);

		// 2. 兜底逻辑：防止数据库为空导致启动失败
		if (config == null) {
			log.warn("【警告】未找到激活的 CHAT 模型配置，系统将使用 Dummy 配置启动。");
			config = createDummyConfig(ModelType.CHAT);
		}

		// 3. 创建初始对象
		ChatModel initialModel = factory.createChatModel(config);

		// 4. 包装进 TargetSource
		return new HotSwappableTargetSource(initialModel);
	}

	/**
	 * 定义对外暴露的 ChatModel 代理对象。 所有注入 ChatModel 的地方，实际上拿到的都是这个代理。
	 */
	@Bean
	@Primary
	public ChatModel chatModel(@Qualifier("chatModelTargetSource") HotSwappableTargetSource targetSource) {
		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.setTargetSource(targetSource);
		proxyFactory.addInterface(ChatModel.class);
		return (ChatModel) proxyFactory.getProxy();
	}

	// ========================================================================
	// 2. EmbeddingModel 配置区域
	// ========================================================================

	/**
	 * 定义 EmbeddingModel 的“热插拔源”。
	 */
	@Bean
	public HotSwappableTargetSource embeddingModelTargetSource(DynamicModelFactory factory,
			ModelConfigDataService modelConfigDataService) {
		// 1. 获取 Embedding 配置
		ModelConfigDTO config = modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING);

		// 2. 兜底逻辑
		if (config == null) {
			log.warn("【警告】未找到激活的 EMBEDDING 模型配置，系统将使用 Dummy 配置启动。");
			config = createDummyConfig(ModelType.EMBEDDING);
		}

		// 3. 创建初始对象
		EmbeddingModel initialModel = factory.createEmbeddingModel(config);

		// 4. 包装
		return new HotSwappableTargetSource(initialModel);
	}

	/**
	 * 定义对外暴露的 EmbeddingModel 代理对象。
	 */
	@Bean
	@Primary
	public EmbeddingModel embeddingModel(
			@Qualifier("embeddingModelTargetSource") HotSwappableTargetSource targetSource) {
		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.setTargetSource(targetSource);
		proxyFactory.addInterface(EmbeddingModel.class);
		return (EmbeddingModel) proxyFactory.getProxy();
	}

	private ModelConfigDTO createDummyConfig(ModelType type) {
		return ModelConfigDTO.builder()
			.provider("unknown")
			.baseUrl("http://dummy-url-waiting-for-config")
			.apiKey("dummy-key-to-pass-validation")
			.modelName("dummy-model")
			.modelType(type.getCode())
			.temperature(0.7)
			.build();
	}

}
