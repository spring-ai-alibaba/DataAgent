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
 * User Session Entity Class
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserSession {

	private Long id;

	private String sessionId; // Session ID (UUID or JWT Token ID)

	private Long userId; // User ID

	private String token; // JWT Token (complete token)

	private String refreshToken; // Refresh token

	private String deviceType; // Device type: web/mobile/desktop

	private String deviceInfo; // Device info (User-Agent)

	private String ipAddress; // Login IP

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime loginTime; // Login time

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime expiresAt; // Token expiration time

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime lastActivityTime; // Last activity time

	private Integer isActive; // Is active: 0-no, 1-yes

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime logoutTime; // Logout time

	private Integer logoutType; // Logout type: 1-active, 2-timeout, 3-forced

}
