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

import com.alibaba.cloud.ai.dto.SemanticModelDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

@Service
public class SemanticModelRecallService {

	private static final String FIELD_GET_BY_DATASET_IDS = """
			SELECT
			    agent_id,
				field_name,
				synonyms,
				origin_name,
				description,
				origin_description,
				type,
				is_recall,
				status
			FROM semantic_model WHERE agent_id = ? AND status = 1 AND is_recall = 1
			""";

	private final JdbcTemplate jdbcTemplate;

	public SemanticModelRecallService(JdbcTemplate jdbcTemplate) {
		Assert.notNull(jdbcTemplate, "jdbcTemplate cannot be null");
		this.jdbcTemplate = jdbcTemplate;
	}

	// Get agent fields by data_set_id
	public List<SemanticModelDTO> getFieldByDataSetId(String dataSetId) {
		return this.jdbcTemplate.query(FIELD_GET_BY_DATASET_IDS, new Object[] { dataSetId }, (rs, rowNum) -> {
			SemanticModelDTO dto = new SemanticModelDTO();
			Long agentIdLong = rs.getObject("agent_id", Long.class);
			dto.setAgentId(agentIdLong);
			dto.setOriginalFieldName(rs.getString("origin_name"));
			dto.setAgentFieldName(rs.getString("field_name"));
			dto.setFieldSynonyms(rs.getString("synonyms"));
			dto.setFieldDescription(rs.getString("description"));
			dto.setOriginalDescription(rs.getString("origin_description"));
			dto.setFieldType(rs.getString("type"));
			dto.setDefaultRecall(rs.getObject("is_recall", Boolean.class));
			dto.setEnabled(rs.getObject("status", Boolean.class));
			return dto;
		});
	}

}
