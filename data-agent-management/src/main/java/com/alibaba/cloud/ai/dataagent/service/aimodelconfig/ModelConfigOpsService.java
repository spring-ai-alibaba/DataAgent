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

import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@AllArgsConstructor
public class ModelConfigOpsService {

	private final ModelConfigDataService modelConfigDataService;

	private final DynamicModelFactory modelFactory;

	private final AiModelRegistry aiModelRegistry;

	private final ObjectMapper objectMapper = JsonUtil.getObjectMapper();

	/**
	 * 专门处理：更新配置并热刷新的聚合逻辑
	 */
	@Transactional(rollbackFor = Exception.class)
	public void updateAndRefresh(ModelConfigDTO dto) {
		// 1. 更新数据库
		ModelConfig entity = modelConfigDataService.updateConfigInDb(dto);

		// 2. 检查是否是激活状态
		if (Boolean.TRUE.equals(entity.getIsActive())) {
			try {
				// 3. 刷新内存模型
				log.info("Detected update on active config [{}], refreshing memory...", entity.getModelType());
				refreshMemoryModel(entity.getModelType());
			}
			catch (Exception e) {
				// 抛出异常回滚数据库事务
				throw new RuntimeException("配置更新失败: " + e.getMessage(), e);
			}
		}
	}

	/**
	 * 激活指定配置
	 */
	@Transactional(rollbackFor = Exception.class)
	public void activateConfig(Integer id) {
		// 1. 查数据
		ModelConfig entity = modelConfigDataService.findById(id);
		if (entity == null) {
			throw new RuntimeException("配置不存在");
		}

		// 2. 刷新内存模型
		log.info("Activating config ID={}, Type={}...", id, entity.getModelType());
		refreshMemoryModel(entity.getModelType());

		// 3. 更新数据库状态 (调用数据层)
		modelConfigDataService.switchActiveStatus(id, entity.getModelType());

		log.info("Config ID={} activated successfully.", id);
	}

	/**
	 * 私有方法：根据实体创建并替换内存代理
	 */
	private void refreshMemoryModel(ModelType type) {
		if (ModelType.CHAT.equals(type)) {
			aiModelRegistry.refreshChat();
		}
		else if (ModelType.EMBEDDING.equals(type)) {
			aiModelRegistry.refreshEmbedding();
		}
		else {
			throw new RuntimeException("未知的模型类型: " + type);
		}
	}

	/**
	 * 测试连接逻辑 注意：这里创建的模型是“临时”的，用完即丢，不会影响当前系统正在运行的模型
	 */
	public void testConnection(ModelConfigDTO config) {
		String modelType = config.getModelType();

		try {
			if (ModelType.CHAT.getCode().equalsIgnoreCase(modelType)) {
				testChatModel(config);
			}
			else if (ModelType.EMBEDDING.getCode().equalsIgnoreCase(modelType)) {
				testEmbeddingModel(config);
			}
			else {
				throw new IllegalArgumentException("未知的模型类型: " + modelType);
			}
		}
		catch (Exception e) {
			try {
				log.error("Failed to test model connection. Config: {}", objectMapper.writeValueAsString(config), e);
			}
			catch (JsonProcessingException e1) {
				log.error("Failed to convert config to JSON. Config: {}", config, e1);
			}
			// 重新抛出异常，让 Controller 捕获并展示给前端
			// 如果是 OpenAiHttpException，通常包含具体的 API 错误信息
			throw new RuntimeException(parseErrorMessage(e));
		}
	}

	private void testChatModel(ModelConfigDTO config) {
		log.info("Testing Chat Model connection, provider: {}, modelName: {}", config.getProvider(),
				config.getModelName());

		// 1. 创建临时模型
		ChatModel tempModel = modelFactory.createChatModel(config);

		// 2. 发起最轻量的请求
		String promptText = "Hello";

		// 3. 调用
		String response = tempModel.call(promptText);

		// 4. 校验结果
		if (!StringUtils.hasText(response)) {
			throw new RuntimeException("模型返回内容为空");
		}
		log.info("Chat Model test passed. Response: {}", response);
	}

	private void testEmbeddingModel(ModelConfigDTO config) {
		log.info("Testing Embedding Model connection, provider: {} modelName: {}", config.getProvider(),
				config.getModelName());
		// 1. 创建临时模型
		EmbeddingModel tempModel = modelFactory.createEmbeddingModel(config);

		// 2. 发起请求
		float[] embedding = tempModel.embed("Test");

		// 3. 校验结果
		if (embedding == null || embedding.length == 0) {
			throw new RuntimeException("模型生成的向量为空");
		}
		log.info("Embedding Model test passed. Dimension: {}", embedding.length);
	}

	/**
	 * 辅助方法：提取更友好的错误信息 Spring AI 抛出的异常有时候嵌套很深
	 */
	private String parseErrorMessage(Exception e) {
		// 如果是 401，通常是 Key 错
		if (e.getMessage().contains("401")) {
			return "鉴权失败 (401)，请检查 API Key 是否正确。";
		}
		// 如果是 404，通常是 BaseUrl 或 Path 错
		if (e.getMessage().contains("404")) {
			return "接口未找到 (404)，请检查 Base URL 或者路径配置地址。";
		}
		// 如果是 429，额度没了
		if (e.getMessage().contains("429")) {
			return "请求过多或余额不足 (429)，请检查厂商额度。";
		}
		// 其他错误直接返回原样
		return e.getMessage();
	}

}
