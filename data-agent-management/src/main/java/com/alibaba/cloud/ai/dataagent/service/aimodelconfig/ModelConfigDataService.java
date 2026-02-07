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

import com.alibaba.cloud.ai.dataagent.enums.ModelTier;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import jakarta.annotation.Nullable;

import java.util.List;

public interface ModelConfigDataService {

	ModelConfig findById(Integer id);

	/**
	 * 切换指定模型配置为激活状态，同时禁用同类型其他配置
	 *
	 * @apiNote 当模型类型为 {@link ModelType#CHAT} 时，规格不能为空
	 */
	void switchActiveStatus(Integer id, ModelType type, @Nullable ModelTier tier);

	List<ModelConfigDTO> listConfigs();

	void addConfig(ModelConfigDTO dto);

	ModelConfig updateConfigInDb(ModelConfigDTO dto);

	void deleteConfig(Integer id);

	ModelConfigDTO getActiveConfigByType(ModelType modelType);

	/**
	 * 根据模型类型和规格获取激活的模型配置
	 * @implNote  如果找不到匹配的配置，则回退到 {@link #getActiveConfigByType}
	 */
	ModelConfigDTO getActiveConfigByTypeAndTier(ModelType modelType, ModelTier modelTier);

}
