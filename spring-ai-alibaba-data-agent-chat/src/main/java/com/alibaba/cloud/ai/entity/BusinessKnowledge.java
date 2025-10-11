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

import com.alibaba.cloud.ai.dto.BusinessKnowledgeDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Business Knowledge Management Entity Class
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessKnowledge {

	private Long id;

	private String businessTerm; // Business term

	private String description; // Description

	private String synonyms; // Synonyms, comma-separated

	@Builder.Default
	private Boolean isRecall = true; // is recall

	private Long agentId; // Associated agent ID

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	public BusinessKnowledgeDTO toDTO() {
		return BusinessKnowledgeDTO.builder()
			.businessTerm(this.businessTerm)
			.description(this.description)
			.synonyms(this.synonyms)
			.isRecall(this.isRecall)
			.agentId(this.agentId)
			.build();
	}

}
