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
import com.alibaba.cloud.ai.mapper.SemanticModelMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class SemanticModelServiceImpl implements SemanticModelService {

	private final SemanticModelMapper semanticModelMapper;

	@Override
	public List<SemanticModel> getAll() {
		return semanticModelMapper.selectAll();
	}

	@Override
	public List<SemanticModel> getEnabledByAgentId(Long agentId) {
		return semanticModelMapper.selectEnabledByAgentId(agentId);
	}

	@Override
	public SemanticModel getById(Long id) {
		return semanticModelMapper.selectById(id);
	}

	@Override
	public void addSemanticModel(SemanticModel semanticModel) {
		semanticModelMapper.insert(semanticModel);
	}

	@Override
	public void enableSemanticModel(Long id) {
		semanticModelMapper.enableById(id);
	}

	@Override
	public void disableSemanticModel(Long id) {
		semanticModelMapper.disableById(id);
	}

	@Override
	public List<SemanticModel> getByAgentId(Long agentId) {
		return semanticModelMapper.selectByAgentId(agentId);
	}

	@Override
	public List<SemanticModel> search(String keyword) {
		return semanticModelMapper.searchByKeyword(keyword);
	}

	@Override
	public void deleteSemanticModel(Long id) {
		semanticModelMapper.deleteById(id);
	}

	@Override
	public void updateSemanticModel(Long id, SemanticModel semanticModel) {
		semanticModel.setId(id);
		semanticModelMapper.updateById(semanticModel);
	}

}
