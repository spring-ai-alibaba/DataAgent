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
package com.alibaba.cloud.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Semantic Model Configuration Entity Class
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemanticModel {

	private Long id;

	private Long agentId; // Agent ID

	private String originalFieldName; // Original field name

	private String agentFieldName; // Agent field name

	private String fieldSynonyms; // Field name synonyms, comma-separated

	private String fieldDescription; // Field description

	private String fieldType; // Field type

	private String originalDescription; // Original field description

	private Boolean defaultRecall; // Default recall

	private Boolean enabled; // Whether enabled

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	public SemanticModel(Long agentId, String originalFieldName, String agentFieldName, String fieldSynonyms,
			String fieldDescription, String fieldType, String originalDescription, Boolean defaultRecall,
			Boolean enabled) {
		this.agentId = agentId;
		this.originalFieldName = originalFieldName;
		this.agentFieldName = agentFieldName;
		this.fieldSynonyms = fieldSynonyms;
		this.fieldDescription = fieldDescription;
		this.fieldType = fieldType;
		this.originalDescription = originalDescription;
		this.defaultRecall = defaultRecall;
		this.enabled = enabled;
	}

}
