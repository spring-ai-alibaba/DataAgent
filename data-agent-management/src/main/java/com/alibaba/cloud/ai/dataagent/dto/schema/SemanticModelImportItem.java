/*
 * Copyright 2024-2026 the original author or authors.
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

package com.alibaba.cloud.ai.dataagent.dto.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 语义模型导入项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticModelImportItem {

	@NotBlank(message = "表名不能为空")
	private String tableName;

	@NotBlank(message = "字段名不能为空")
	private String columnName;

	@NotBlank(message = "业务名称不能为空")
	private String businessName;

	private String synonyms;

	@JsonAlias({ "businessDesc", "description", "desc" })
	private String businessDescription;

	private String columnComment;

	@NotBlank(message = "数据类型不能为空")
	private String dataType;

	/**
	 * 创建时间（可选，用于导入时指定创建时间）
	 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createTime;

}
