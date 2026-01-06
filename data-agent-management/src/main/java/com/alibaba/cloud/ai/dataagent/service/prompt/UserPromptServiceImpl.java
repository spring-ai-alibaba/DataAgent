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
package com.alibaba.cloud.ai.dataagent.service.prompt;

import com.alibaba.cloud.ai.dataagent.dto.prompt.PromptConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.UserPromptConfig;
import com.alibaba.cloud.ai.dataagent.mapper.UserPromptConfigMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * User Prompt Configuration Management Service Provides CRUD functionality for prompt
 * configurations, supports runtime configuration updates
 *
 * @author Makoto
 */
@Slf4j
@Service
@AllArgsConstructor
public class UserPromptServiceImpl implements UserPromptService {

	private final UserPromptConfigMapper userPromptConfigMapper;

	@Override
	public UserPromptConfig saveOrUpdateConfig(PromptConfigDTO configDTO) {
		log.info("保存或更新提示词优化配置：{}", configDTO);

		UserPromptConfig config;
		if (configDTO.id() != null) {
			// Update existing configuration
			config = userPromptConfigMapper.selectById(configDTO.id());
			if (config != null) {
				config.setName(configDTO.name());
				config.setAgentId(configDTO.agentId());
				config.setSystemPrompt(configDTO.optimizationPrompt());
				config.setEnabled(configDTO.enabled());
				config.setDescription(configDTO.description());
				config.setPriority(configDTO.priority() != null ? configDTO.priority() : 0);
				config.setDisplayOrder(configDTO.displayOrder() != null ? configDTO.displayOrder() : 0);
				userPromptConfigMapper.updateById(config);
			}
			else {
				// ID不存在，创建新配置
				config = new UserPromptConfig();
				config.setId(configDTO.id());
				config.setName(configDTO.name());
				config.setPromptType(configDTO.promptType());
				config.setAgentId(configDTO.agentId());
				config.setSystemPrompt(configDTO.optimizationPrompt());
				config.setEnabled(configDTO.enabled());
				config.setDescription(configDTO.description());
				config.setCreator(configDTO.creator());
				config.setPriority(configDTO.priority() != null ? configDTO.priority() : 0);
				config.setDisplayOrder(configDTO.displayOrder() != null ? configDTO.displayOrder() : 0);
				userPromptConfigMapper.insert(config);
			}
		}
		else {
			// Create new configuration
			config = new UserPromptConfig();
			config.setId(UUID.randomUUID().toString());
			config.setName(configDTO.name());
			config.setPromptType(configDTO.promptType());
			config.setAgentId(configDTO.agentId());
			config.setSystemPrompt(configDTO.optimizationPrompt());
			config.setEnabled(configDTO.enabled());
			config.setDescription(configDTO.description());
			config.setCreator(configDTO.creator());
			config.setPriority(configDTO.priority() != null ? configDTO.priority() : 0);
			config.setDisplayOrder(configDTO.displayOrder() != null ? configDTO.displayOrder() : 0);
			userPromptConfigMapper.insert(config);
		}

		// 如果配置启用，直接启用该配置（支持多个配置同时启用）
		if (Boolean.TRUE.equals(config.getEnabled())) {
			userPromptConfigMapper.enableById(config.getId());
			log.info("已启用提示词类型 [{}] 的配置：{}", config.getPromptType(), config.getId());
		}

		return config;
	}

	@Override
	public UserPromptConfig getConfigById(String id) {
		return userPromptConfigMapper.selectById(id);
	}

	@Override
	public List<UserPromptConfig> getActiveConfigsByType(String promptType, Long agentId) {
		return userPromptConfigMapper.getActiveConfigsByType(promptType, agentId);
	}

	@Override
	public UserPromptConfig getActiveConfigByType(String promptType, Long agentId) {
		return userPromptConfigMapper.selectActiveByPromptType(promptType, agentId);
	}

	@Override
	public List<UserPromptConfig> getAllConfigs() {
		return userPromptConfigMapper.selectAll();
	}

	@Override
	public List<UserPromptConfig> getConfigsByType(String promptType, Long agentId) {
		return userPromptConfigMapper.getConfigsByType(promptType, agentId);
	}

	@Override
	public boolean deleteConfig(String id) {
		UserPromptConfig config = userPromptConfigMapper.selectById(id);
		if (config != null) {
			// 从数据库删除
			int deleted = userPromptConfigMapper.deleteById(id);
			if (deleted > 0) {
				log.info("已删除配置：{}", id);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean enableConfig(String id) {
		UserPromptConfig config = userPromptConfigMapper.selectById(id);
		if (config != null) {
			// Enable the current configuration (支持多个配置同时启用)
			int updated = userPromptConfigMapper.enableById(id);
			if (updated > 0) {
				log.info("已启用配置：{}", id);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean disableConfig(String id) {
		int updated = userPromptConfigMapper.disableById(id);
		if (updated > 0) {
			log.info("已禁用配置：{}", id);
			return true;
		}
		return false;
	}

	@Override
	public List<UserPromptConfig> getOptimizationConfigs(String promptType, Long agentId) {
		return getActiveConfigsByType(promptType, agentId);
	}

	@Override
	public boolean enableConfigs(List<String> ids) {
		for (String id : ids) {
			userPromptConfigMapper.enableById(id);
		}
		log.info("批量启用配置成功：{}", ids);
		return true;
	}

	@Override
	public boolean disableConfigs(List<String> ids) {
		for (String id : ids) {
			userPromptConfigMapper.disableById(id);
		}
		log.info("批量禁用配置成功：{}", ids);
		return true;
	}

	@Override
	public boolean updatePriority(String id, Integer priority) {
		UserPromptConfig config = userPromptConfigMapper.selectById(id);
		if (config != null) {
			config.setPriority(priority);
			userPromptConfigMapper.updateById(config);
			log.info("更新配置优先级成功：{} -> {}", id, priority);
			return true;
		}
		return false;
	}

	@Override
	public boolean updateDisplayOrder(String id, Integer displayOrder) {
		UserPromptConfig config = userPromptConfigMapper.selectById(id);
		if (config != null) {
			config.setDisplayOrder(displayOrder);
			userPromptConfigMapper.updateById(config);
			log.info("更新配置显示顺序成功：{} -> {}", id, displayOrder);
			return true;
		}
		return false;
	}

}
