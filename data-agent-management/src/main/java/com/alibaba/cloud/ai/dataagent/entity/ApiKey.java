/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API Key Entity Class
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ApiKey {

	private Long id;

	private Long userId; // User ID

	private String apiKey; // API key (starts with sk-)

	@JsonIgnore // Don't expose secret in JSON responses
	private String apiSecret; // API secret (for signature verification)

	private String keyName; // Key name

	private Integer status; // Status: 0-disabled, 1-enabled

	private String permissions; // Permission scope (JSON format)

	private Integer rateLimit; // Rate limit (requests per minute)

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime expiresAt; // Expiration time

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime lastUsedTime; // Last used time

	private String lastUsedIp; // Last used IP

	private Long usageCount; // Usage count

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updatedTime;

	private Long createdBy; // Creator ID

	private Integer isDeleted; // Logical delete: 0-not deleted, 1-deleted

}
