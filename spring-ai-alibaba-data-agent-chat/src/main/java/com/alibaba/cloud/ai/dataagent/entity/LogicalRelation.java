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

package com.alibaba.cloud.ai.dataagent.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 逻辑外键配置实体类 用于定义数据源中表之间的逻辑外键关系，帮助 LLM 理解数据关联
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogicalRelation {

	/**
	 * 主键ID
	 */
	private Integer id;

	/**
	 * 关联的数据源ID
	 */
	private Integer datasourceId;

	/**
	 * 主表名（例如 t_order）
	 */
	private String sourceTableName;

	/**
	 * 主表字段名（例如 buyer_uid）
	 */
	private String sourceColumnName;

	/**
	 * 关联表名（例如 t_user）
	 */
	private String targetTableName;

	/**
	 * 关联表字段名（例如 id）
	 */
	private String targetColumnName;

	/**
	 * 关系类型（可选） 1:1, 1:N, N:1 - 辅助LLM理解数据基数
	 */
	private String relationType;

	/**
	 * 业务描述（可选） 存入Prompt中帮助LLM理解 例如："订单表通过buyer_uid关联用户表id"
	 */
	private String description;

	/**
	 * 逻辑删除标志 0-未删除, 1-已删除
	 */
	private Integer isDeleted;

	/**
	 * 创建时间
	 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime createdTime;

	/**
	 * 更新时间
	 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime updatedTime;

}
