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

	/**
	 * 主键ID
	 */
	private Long id;

	/**
	 * 关联的智能体ID
	 */
	private Integer agentId;

	/**
	 * 关联的数据源ID
	 */
	private Integer datasourceId;

	/**
	 * 关联的表名
	 */
	private String tableName;

	/**
	 * 数据库中的物理字段名 (例如: csat_score)
	 */
	private String columnName;

	/**
	 * 业务名/别名 (例如: 客户满意度分数)
	 */
	private String businessName;

	/**
	 * 业务名的同义词 (例如: 满意度,客户评分)
	 */
	private String synonyms;

	/**
	 * 业务描述 (用于向LLM解释字段的业务含义)
	 */
	private String businessDescription;

	/**
	 * 数据库中的物理字段的原始注释
	 */
	private String columnComment;

	/**
	 * 物理数据类型 (例如: int, varchar(20))
	 */
	private String dataType;

	/**
	 * 状态: 0 停用 1 启用
	 */
	private Integer status;

	/**
	 * 创建时间
	 */
	private LocalDateTime createdTime;

	/**
	 * 更新时间
	 */
	private LocalDateTime updateTime;

	/**
	 * 获取用于提示的信息
	 */
	public String getPromptInfo() {
		return String.format("业务名称: %s, 表名: %s, 数据库字段名: %s, 字段同义词: %s, 业务描述: %s, 数据类型: %s", businessName, tableName,
				columnName, synonyms, businessDescription, dataType);
	}

}
