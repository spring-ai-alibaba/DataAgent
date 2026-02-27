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
package com.alibaba.cloud.ai.dataagent.service.semantic;

import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticModelAddDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticModelBatchImportDTO;
import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import com.alibaba.cloud.ai.dataagent.vo.BatchImportResult;

import java.io.InputStream;
import java.util.List;

public interface SemanticModelService {

	List<SemanticModel> getAll();

	List<SemanticModel> getEnabledByAgentId(Long agentId);

	List<SemanticModel> getByAgentIdAndTableNames(Long agentId, List<String> tableNames);

	SemanticModel getById(Long id);

	void addSemanticModel(SemanticModel semanticModel);

	boolean addSemanticModel(SemanticModelAddDTO dto);

	void enableSemanticModel(Long id);

	void disableSemanticModel(Long id);

	List<SemanticModel> getByAgentId(Long agentId);

	List<SemanticModel> search(String keyword);

	void deleteSemanticModel(Long id);

	void updateSemanticModel(Long id, SemanticModel semanticModel);

	default void addSemanticModels(List<SemanticModel> semanticModels) {
		semanticModels.forEach(this::addSemanticModel);
	}

	default void enableSemanticModels(List<Long> ids) {
		ids.forEach(this::enableSemanticModel);
	}

	default void disableSemanticModels(List<Long> ids) {
		ids.forEach(this::disableSemanticModel);
	}

	default void deleteSemanticModels(List<Long> ids) {
		ids.forEach(this::deleteSemanticModel);
	}

	BatchImportResult batchImport(SemanticModelBatchImportDTO dto);

	/**
	 * 从Excel文件导入语义模型
	 * @param inputStream Excel文件输入流
	 * @param filename 文件名
	 * @param agentId 智能体ID
	 * @return 导入结果
	 */
	BatchImportResult importFromExcel(InputStream inputStream, String filename, Long agentId);

}
