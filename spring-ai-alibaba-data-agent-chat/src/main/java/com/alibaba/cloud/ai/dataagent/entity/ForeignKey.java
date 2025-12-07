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
 * 逻辑外键实体类 用于定义数据源中表之间的逻辑外键关系，帮助 LLM 理解数据关联
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForeignKey {

	/**
	 * 主键ID
	 */
	private Integer id;

	/**
	 * 数据源ID
	 */
	private Integer datasourceId;

	/**
	 * 主表名称（源表）
	 */
	private String sourceTable;

	/**
	 * 主表字段名称
	 */
	private String sourceColumn;

	/**
	 * 关联表名称（目标表）
	 */
	private String targetTable;

	/**
	 * 关联表字段名称
	 */
	private String targetColumn;

	/**
	 * 描述信息（可选） 用于描述外键关系的语义，帮助 LLM 理解 例如："订单关联用户"、"部门经理ID关联员工ID"
	 */
	private String description;

	/**
	 * 创建时间
	 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime createTime;

	/**
	 * 更新时间
	 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime updateTime;

}
