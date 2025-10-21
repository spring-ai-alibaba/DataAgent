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

package com.alibaba.cloud.ai.service.semantic;

import com.alibaba.cloud.ai.entity.SemanticModel;

import java.util.List;

public interface SemanticModelService {

	List<SemanticModel> getAll();

	List<SemanticModel> getEnabledByAgentId(Long agentId);

	SemanticModel getById(Long id);

	void addSemanticModel(SemanticModel semanticModel);

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

}
