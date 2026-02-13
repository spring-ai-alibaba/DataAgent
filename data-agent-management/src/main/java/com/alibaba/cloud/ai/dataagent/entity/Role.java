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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Role Entity Class
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Role {

	private Long id;

	private String roleCode; // Role code (e.g., ADMIN, ANALYST, VIEWER)

	private String roleName; // Role name

	private String description; // Role description

	private String permissions; // Permission list (JSON format)

	private Integer isSystem; // Is system role: 0-no, 1-yes (system roles cannot be deleted)

	private Integer sortOrder; // Sort order

	private Integer status; // Status: 0-disabled, 1-enabled

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updatedTime;

	private Long createdBy; // Creator ID

	private Long updatedBy; // Updater ID

	private Integer isDeleted; // Logical delete: 0-not deleted, 1-deleted

}
