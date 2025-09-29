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

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * User-defined prompt configuration entity class
 *
 * @author Makoto
 */
@TableName("user_prompt_config")
@Data
@NoArgsConstructor
public class UserPromptConfig {

	/**
	 * Configuration ID
	 */
	@TableId(value = "id", type = IdType.ASSIGN_UUID)
	private String id;

	/**
	 * Configuration name
	 */
	@TableField("name")
	private String name;

	/**
	 * Prompt type (e.g., report-generator, planner, etc.)
	 */
	@TableField("prompt_type")
	private String promptType;

	/**
	 * User-defined system prompt content
	 */
	@TableField("system_prompt")
	private String systemPrompt;

	/**
	 * Whether to enable this configuration
	 */
	@TableField("enabled")
	private Boolean enabled = true;

	/**
	 * Configuration description
	 */
	@TableField("description")
	private String description;

	/**
	 * Configuration priority (higher number = higher priority)
	 */
	@TableField("priority")
	private Integer priority = 0;

	/**
	 * Configuration order for display
	 */
	@TableField("display_order")
	private Integer displayOrder = 0;

	/**
	 * Creation time
	 */
	@TableField(value = "create_time", fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	/**
	 * Update time
	 */
	@TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

	/**
	 * Creator
	 */
	@TableField("creator")
	private String creator;

	// Constructors

	public UserPromptConfig(String promptType, String systemPrompt) {
		this();
		this.promptType = promptType;
		this.systemPrompt = systemPrompt;
	}

	public String getOptimizationPrompt() {
		return this.systemPrompt;
	}

	public void setOptimizationPrompt(String optimizationPrompt) {
		this.systemPrompt = optimizationPrompt;
	}

}
