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
package com.alibaba.cloud.ai.dataagent.entity;

import com.alibaba.cloud.ai.dataagent.common.enums.EmbeddingStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
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
	private Integer isRecall = 1; // Whether to recall (0: not recall, 1: recall)

	private Long agentId; // Associated agent ID

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updatedTime;

	// 向量化状态：PENDING待处理，PROCESSING处理中，COMPLETED已完成，FAILED失败
	private EmbeddingStatus embeddingStatus;

	// 操作失败的错误信息
	private String errorMsg;

	// 0=未删除, 1=已删除
	@Builder.Default
	private Integer isDeleted = 0;

}
