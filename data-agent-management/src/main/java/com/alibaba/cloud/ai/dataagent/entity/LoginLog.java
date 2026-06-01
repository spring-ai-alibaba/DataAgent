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
 * Login Log Entity Class
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class LoginLog {

	private Long id;

	private Long userId; // User ID (may be NULL on failure)

	private String username; // Login username

	private String loginType; // Login type: password, oauth, api_key

	private Integer loginStatus; // Login status: 0-failed, 1-success

	private String failureReason; // Failure reason

	private String ipAddress; // Login IP

	private String location; // Login location (parsed from IP)

	private String deviceType; // Device type

	private String deviceInfo; // Device info

	private String browser; // Browser

	private String os; // Operating system

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime loginTime; // Login time

}
