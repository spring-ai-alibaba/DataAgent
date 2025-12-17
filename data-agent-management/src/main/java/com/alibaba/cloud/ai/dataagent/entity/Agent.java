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

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent Entity Class
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Agent {

	private Long id;

	private String name; // Agent name

	private String description; // Agent description

	private String avatar; // Avatar URL

	// todo: 改为枚举
	private String status; // Status: draft-pending publication, published-published,
							// offline-offline

	private String apiKey; // API Key for external access, format sk-xxx

	@Builder.Default
	private Integer apiKeyEnabled = 0; // 0/1 toggle for API access

	private String prompt; // Custom Prompt configuration

	private String category; // Category

	private Long adminId; // Admin ID

	private String tags; // Tags, comma separated

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createTime;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updateTime;

	// Whether human review is enabled for this agent
	@Builder.Default
	private Integer humanReviewEnabled = 0; // 0/1 for JDBC compatibility

}
