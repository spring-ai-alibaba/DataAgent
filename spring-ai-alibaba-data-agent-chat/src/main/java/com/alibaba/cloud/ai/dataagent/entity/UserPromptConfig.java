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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPromptConfig {

	/**
	 * Configuration ID
	 */
	private String id;

	/**
	 * Configuration name
	 */
	private String name;

	/**
	 * Prompt type (e.g., report-generator, planner, etc.)
	 */
	private String promptType;

	/**
	 * Associated agent ID, null means global configuration
	 */
	private Long agentId;

	/**
	 * User-defined system prompt content
	 */
	private String systemPrompt;

	/**
	 * Whether to enable this configuration
	 */
	@Builder.Default
	private Boolean enabled = true;

	/**
	 * Configuration description
	 */
	private String description;

	/**
	 * Configuration priority (higher number = higher priority)
	 */
	@Builder.Default
	private Integer priority = 0;

	/**
	 * Configuration order for display
	 */
	@Builder.Default
	private Integer displayOrder = 0;

	/**
	 * Creation time
	 */
	private LocalDateTime createTime;

	/**
	 * Update time
	 */
	private LocalDateTime updateTime;

	/**
	 * Creator
	 */
	private String creator;

	public String getOptimizationPrompt() {
		return this.systemPrompt;
	}

	public void setOptimizationPrompt(String optimizationPrompt) {
		this.systemPrompt = optimizationPrompt;
	}

}
