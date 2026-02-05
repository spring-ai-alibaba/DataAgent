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
package com.alibaba.cloud.ai.dataagent.converter;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

public class ModelConfigConverter {

	/**
	 * Entity -> DTO 用于把数据库数据转给前端看
	 */
	public static ModelConfigDTO toDTO(ModelConfig entity) {
		if (entity == null) {
			return null;
		}
		return ModelConfigDTO.builder()
			.id(entity.getId())
			.provider(entity.getProvider())
			.baseUrl(entity.getBaseUrl())
			.modelName(entity.getModelName())
			.temperature(entity.getTemperature())
			.maxTokens(entity.getMaxTokens())
			.isActive(entity.getIsActive())
			.apiKey(entity.getApiKey())
			.modelType(entity.getModelType().getCode())
			.completionsPath(entity.getCompletionsPath())
			.embeddingsPath(entity.getEmbeddingsPath())
			.proxyEnabled(entity.getProxyEnabled())
			.proxyHost(entity.getProxyHost())
			.proxyPort(entity.getProxyPort())
			.proxyUsername(entity.getProxyUsername())
			.proxyPassword(entity.getProxyPassword())
			.build();
	}

	/**
	 * DTO -> Entity 用于新增配置
	 */
	public static ModelConfig toEntity(ModelConfigDTO dto) {
		Assert.notNull(dto, "ModelConfigDTO cannot be null.");
		ModelConfig entity = new ModelConfig();
		// 新增时 ID 由数据库生成，所以这里通常不设置 ID，或者仅当 dto.id 有值时设置
		entity.setId(dto.getId());
		entity.setProvider(dto.getProvider());
		entity.setBaseUrl(dto.getBaseUrl());
		// 新增时，DTO 里的 Key 肯定是明文，直接存
		entity.setApiKey(dto.getApiKey());
		entity.setModelName(dto.getModelName());
		entity.setTemperature(dto.getTemperature());
		entity.setMaxTokens(dto.getMaxTokens());
		entity.setModelType(ModelType.fromCode(dto.getModelType()));
		entity.setCompletionsPath(dto.getCompletionsPath());
		entity.setEmbeddingsPath(dto.getEmbeddingsPath());
		entity.setProxyEnabled(dto.getProxyEnabled());
		entity.setProxyHost(dto.getProxyHost());
		entity.setProxyPort(dto.getProxyPort());
		entity.setProxyUsername(dto.getProxyUsername());
		entity.setProxyPassword(dto.getProxyPassword());
		// 默认值处理
		entity.setIsActive(false);
		entity.setIsDeleted(0);
		entity.setCreatedTime(LocalDateTime.now());
		entity.setUpdatedTime(LocalDateTime.now());

		return entity;
	}

}
