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
import com.alibaba.cloud.ai.dataagent.converter.ModelConfigConverter;
import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import com.alibaba.cloud.ai.dataagent.mapper.ModelConfigMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.dataagent.converter.ModelConfigConverter.toDTO;
import static com.alibaba.cloud.ai.dataagent.converter.ModelConfigConverter.toEntity;

@Slf4j
@Service
@AllArgsConstructor
public class ModelConfigDataServiceImpl implements ModelConfigDataService {

	private final ModelConfigMapper modelConfigMapper;

	@Override
	public ModelConfig findById(Integer id) {
		return modelConfigMapper.findById(id);
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void switchActiveStatus(Integer id, ModelType type) {
		// 1. 禁用同类型其他配置
		modelConfigMapper.deactivateOthers(type.getCode(), id);

		// 2. 启用当前配置
		ModelConfig entity = modelConfigMapper.findById(id);
		if (entity != null) {
			entity.setIsActive(true);
			entity.setUpdatedTime(LocalDateTime.now());
			modelConfigMapper.updateById(entity);
		}
	}

	@Override
	public List<ModelConfigDTO> listConfigs() {
		return modelConfigMapper.findAll().stream().map(ModelConfigConverter::toDTO).collect(Collectors.toList());
	}

	@Override
	public void addConfig(ModelConfigDTO dto) {
		clean(dto);
		// 只存库，不切换
		modelConfigMapper.insert(toEntity(dto));
	}

	private void clean(ModelConfigDTO dto) {
		dto.setModelName(dto.getModelName().trim());
		dto.setBaseUrl(dto.getBaseUrl().trim());
		dto.setApiKey(dto.getApiKey().trim());
		if (dto.getCompletionsPath() != null) {
			dto.setCompletionsPath(dto.getCompletionsPath().trim());
		}
		if (dto.getEmbeddingsPath() != null) {
			dto.setEmbeddingsPath(dto.getEmbeddingsPath().trim());
		}
	}

	/**
	 * 更新配置到数据库 (不处理热切换) 返回更新后的实体，以便上层业务判断是否需要刷新内存
	 */
	@Transactional(rollbackFor = Exception.class)
	@Override
	public ModelConfig updateConfigInDb(ModelConfigDTO dto) {
		clean(dto);
		// 1. 查旧数据
		ModelConfig entity = modelConfigMapper.findById(dto.getId());
		if (entity == null) {
			throw new RuntimeException("配置不存在");
		}

		// 不准更改模型类型
		if (!entity.getModelType().getCode().equals(dto.getModelType()))
			throw new RuntimeException("模型类型不允许修改");

		// 2. 合并字段
		mergeDtoToEntity(dto, entity);
		entity.setUpdatedTime(LocalDateTime.now());

		// 3. 更新数据库
		modelConfigMapper.updateById(entity);

		return entity;
	}

	private static void mergeDtoToEntity(ModelConfigDTO dto, ModelConfig oldEntity) {
		oldEntity.setProvider(dto.getProvider());
		oldEntity.setBaseUrl(dto.getBaseUrl());
		oldEntity.setModelName(dto.getModelName());
		oldEntity.setTemperature(dto.getTemperature());
		oldEntity.setMaxTokens(dto.getMaxTokens()); // 新增字段
		oldEntity.setCompletionsPath(dto.getCompletionsPath()); // 路径字段
		oldEntity.setEmbeddingsPath(dto.getEmbeddingsPath()); // 路径字段
		oldEntity.setUpdatedTime(LocalDateTime.now()); // 更新时间
		oldEntity.setProxyEnabled(dto.getProxyEnabled());
		oldEntity.setProxyHost(dto.getProxyHost());
		oldEntity.setProxyPort(dto.getProxyPort());
		oldEntity.setProxyUsername(dto.getProxyUsername());
		oldEntity.setProxyPassword(dto.getProxyPassword());

		// 只有当前端传来的 Key 不包含 "****" 时，才说明用户真的改了 Key，否则保持原样
		if (dto.getApiKey() != null && !dto.getApiKey().contains("****")) {
			oldEntity.setApiKey(dto.getApiKey());
		}
	}

	@Override
	public void deleteConfig(Integer id) {
		// 1. 先查询是否存在
		ModelConfig entity = modelConfigMapper.findById(id);
		if (entity == null) {
			throw new RuntimeException("配置不存在");
		}

		// 2. 如果是激活状态，禁止删除
		if (Boolean.TRUE.equals(entity.getIsActive())) {
			throw new RuntimeException("该配置当前正在使用中，无法删除！请先激活其他配置，再进行删除操作。");
		}

		// 3. 执行删除逻辑
		entity.setIsDeleted(1);
		entity.setUpdatedTime(LocalDateTime.now());
		int updated = modelConfigMapper.updateById(entity);
		if (updated == 0) {
			throw new RuntimeException("删除失败");
		}
	}

	@Override
	public ModelConfigDTO getActiveConfigByType(ModelType modelType) {
		ModelConfig entity = modelConfigMapper.selectActiveByType(modelType.getCode());
		if (entity == null) {
			log.warn("Activation model configuration of type [{}] not found, attempting to downgrade...", modelType);
			return null;
		}
		return toDTO(entity);
	}

}
