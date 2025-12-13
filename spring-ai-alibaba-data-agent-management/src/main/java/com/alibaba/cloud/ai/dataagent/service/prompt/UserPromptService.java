/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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

import java.util.List;

public interface UserPromptService {

	/**
	 * Create or update prompt configuration
	 * @param configDTO configuration data transfer object
	 * @return saved configuration object
	 */
	UserPromptConfig saveOrUpdateConfig(PromptConfigDTO configDTO);

	/**
	 * Get configuration by ID
	 * @param id configuration ID
	 * @return configuration object, returns null if not exists
	 */
	UserPromptConfig getConfigById(String id);

	/**
	 * 根据提示词类型和智能体获取所有启用的配置
	 * @param promptType 提示词类型
	 * @param agentId 智能体ID，为空表示不按智能体过滤
	 * @return 配置列表
	 */
	List<UserPromptConfig> getActiveConfigsByType(String promptType, Long agentId);

	/**
	 * Get enabled configuration by prompt type and agent
	 * @param promptType prompt type
	 * @param agentId agent id, null means not filtered by agent
	 * @return configuration object, returns null if not exists
	 */
	UserPromptConfig getActiveConfigByType(String promptType, Long agentId);

	/**
	 * Get all configurations
	 * @return configuration list
	 */
	List<UserPromptConfig> getAllConfigs();

	/**
	 * Get all configurations by prompt type and agent
	 * @param promptType prompt type
	 * @param agentId agent id, null means not filtered by agent
	 * @return configuration list
	 */
	List<UserPromptConfig> getConfigsByType(String promptType, Long agentId);

	/**
	 * Delete configuration
	 * @param id configuration ID
	 * @return whether deletion succeeded
	 */
	boolean deleteConfig(String id);

	/**
	 * Enable specified configuration
	 * @param id configuration ID
	 * @return whether operation succeeded
	 */
	boolean enableConfig(String id);

	/**
	 * Disable specified configuration
	 * @param id configuration ID
	 * @return whether operation succeeded
	 */
	boolean disableConfig(String id);

	/**
	 * Get optimization configurations by prompt type and agent
	 * @param promptType prompt type
	 * @param agentId agent id, null means not filtered by agent
	 * @return optimization configuration list
	 */
	List<UserPromptConfig> getOptimizationConfigs(String promptType, Long agentId);

	/**
	 * 批量启用配置
	 * @param ids 配置ID列表
	 * @return 操作结果
	 */
	boolean enableConfigs(List<String> ids);

	/**
	 * 批量禁用配置
	 * @param ids 配置ID列表
	 * @return 操作结果
	 */
	boolean disableConfigs(List<String> ids);

	/**
	 * 更新配置优先级
	 * @param id 配置ID
	 * @param priority 优先级
	 * @return 操作结果
	 */
	boolean updatePriority(String id, Integer priority);

	/**
	 * 更新配置显示顺序
	 * @param id 配置ID
	 * @param displayOrder 显示顺序
	 * @return 操作结果
	 */
	boolean updateDisplayOrder(String id, Integer displayOrder);

}
