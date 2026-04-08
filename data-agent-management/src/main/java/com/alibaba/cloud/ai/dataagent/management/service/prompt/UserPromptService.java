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
package com.alibaba.cloud.ai.dataagent.management.service.prompt;

import com.alibaba.cloud.ai.dataagent.management.dto.prompt.PromptConfigDTO;
import com.alibaba.cloud.ai.dataagent.management.entity.UserPromptConfig;

import java.util.List;

public interface UserPromptService {

	UserPromptConfig saveOrUpdateConfig(PromptConfigDTO configDTO);

	UserPromptConfig getConfigById(String id);

	List<UserPromptConfig> getActiveConfigs(String agentType, Long agentId);

	UserPromptConfig getActiveConfig(String agentType, Long agentId);

	List<UserPromptConfig> getAllConfigs();

	List<UserPromptConfig> getConfigs(String agentType, Long agentId);

	boolean deleteConfig(String id);

	boolean enableConfig(String id);

	boolean disableConfig(String id);

	List<UserPromptConfig> getOptimizationConfigs(String agentType, Long agentId);

	boolean enableConfigs(List<String> ids);

	boolean disableConfigs(List<String> ids);

	boolean updatePriority(String id, Integer priority);

	boolean updateDisplayOrder(String id, Integer displayOrder);

}
