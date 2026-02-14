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
 * User Entity Class
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class User {

	private Long id;

	private String username; // Login account

	@JsonIgnore // Don't expose password in JSON responses
	private String password; // BCrypt encrypted password

	private String email; // Email

	private String phone; // Phone number

	private String realName; // Real name

	private String avatar; // Avatar URL

	private Integer status; // User status: 0-disabled, 1-enabled, 2-locked

	private Integer userType; // User type: 0-normal user, 1-admin

	private Integer failedLoginCount; // Consecutive failed login count

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime lockedUntil; // Account locked until time

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime passwordChangedTime; // Password changed time

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime lastLoginTime; // Last login time

	private String lastLoginIp; // Last login IP

	private Integer loginCount; // Total login count

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updatedTime;

	private Long createdBy; // Creator ID

	private Long updatedBy; // Updater ID

	private Integer isDeleted; // Logical delete: 0-not deleted, 1-deleted

}
