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
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Semantic Model Configuration Entity Class
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticModel {

	private Long id;

	private Long agentId;

	private String fieldName;

	private String conversationName;

	private String synonyms;

	private String description;

	private String type;

	private LocalDateTime createdTime;

	private LocalDateTime updateTime;

	private Integer status;

	public String getPromptInfo() {
		return String.format("对话字段名称: %s, 数据库字段名: %s, 字段同义词: %s, 字段描述: %s, 字段类型: %s", conversationName, fieldName,
				synonyms, description, type);
	}

}
