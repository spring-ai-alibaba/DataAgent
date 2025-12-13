/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.dataagent.dto.schema;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建逻辑外键的 DTO
 */
@Data
public class CreateLogicalRelationDTO {

	/**
	 * 主表名（例如 t_order）
	 */
	@NotBlank(message = "主表名不能为空")
	private String sourceTableName;

	/**
	 * 主表字段名（例如 buyer_uid）
	 */
	@NotBlank(message = "主表字段名不能为空")
	private String sourceColumnName;

	/**
	 * 关联表名（例如 t_user）
	 */
	@NotBlank(message = "关联表名不能为空")
	private String targetTableName;

	/**
	 * 关联表字段名（例如 id）
	 */
	@NotBlank(message = "关联表字段名不能为空")
	private String targetColumnName;

	/**
	 * 关系类型（可选） 1:1, 1:N, N:1 - 辅助LLM理解数据基数
	 */
	private String relationType;

	/**
	 * 业务描述（可选）
	 */
	private String description;

}
