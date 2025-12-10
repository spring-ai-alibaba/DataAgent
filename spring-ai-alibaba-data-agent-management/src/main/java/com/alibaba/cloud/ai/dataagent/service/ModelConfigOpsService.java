
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
package com.alibaba.cloud.ai.dataagent.service;

import com.alibaba.cloud.ai.dataagent.converter.ModelConfigConverter;
import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.model.DynamicModelFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.aop.target.HotSwappableTargetSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ModelConfigOpsService {

	private final ModelConfigDataService configService;

	private final DynamicModelFactory modelFactory;

	private final HotSwappableTargetSource chatModelTargetSource;

	private final HotSwappableTargetSource embeddingModelTargetSource;

	public ModelConfigOpsService(ModelConfigDataService configService, DynamicModelFactory modelFactory,
			@Qualifier("chatModelTargetSource") HotSwappableTargetSource chatModelTargetSource,
			@Qualifier("embeddingModelTargetSource") HotSwappableTargetSource embeddingModelTargetSource) {
		this.configService = configService;
		this.modelFactory = modelFactory;
		this.chatModelTargetSource = chatModelTargetSource;
		this.embeddingModelTargetSource = embeddingModelTargetSource;
	}

	/**
	 * 专门处理：更新配置并热刷新的聚合逻辑
	 */
	@Transactional(rollbackFor = Exception.class)
	public void updateAndRefresh(ModelConfigDTO dto) {
		// 1. 调用基础服务更新数据库
		ModelConfig entity = configService.updateConfigInDb(dto);

		// 2. 检查是否是激活状态
		if (Boolean.TRUE.equals(entity.getIsActive())) {
			try {
				// 3. 刷新内存模型
				log.info("Detected update on active config [{}], refreshing memory...", entity.getModelType());
				refreshMemoryModel(entity);
			}
			catch (Exception e) {
				// 抛出异常回滚数据库事务
				throw new RuntimeException("配置更新失败，新配置验证未通过: " + e.getMessage());
			}
		}
	}

	/**
	 * 激活指定配置
	 */
	@Transactional(rollbackFor = Exception.class)
	public void activateConfig(Integer id) {
		// 1. 查数据
		ModelConfig entity = configService.findById(id);
		if (entity == null) {
			throw new RuntimeException("配置不存在");
		}

		// 2. 刷新内存模型
		log.info("Activating config ID={}, Type={}...", id, entity.getModelType());
		refreshMemoryModel(entity);

		// 3. 更新数据库状态 (调用数据层)
		configService.switchActiveStatus(id, entity.getModelType());

		log.info("Config ID={} activated successfully.", id);
	}

	/**
	 * 私有方法：根据实体创建并替换内存代理
	 */
	private void refreshMemoryModel(ModelConfig entity) {
		ModelConfigDTO config = ModelConfigConverter.toDTO(entity);
		ModelType type = entity.getModelType();

		if (ModelType.CHAT.equals(type)) {
			ChatModel newModel = modelFactory.createChatModel(config);
			chatModelTargetSource.swap(newModel);
		}
		else if (ModelType.EMBEDDING.equals(type)) {
			EmbeddingModel newModel = modelFactory.createEmbeddingModel(config);
			embeddingModelTargetSource.swap(newModel);
		}
		else {
			throw new RuntimeException("未知的模型类型: " + type);
		}
	}

}
