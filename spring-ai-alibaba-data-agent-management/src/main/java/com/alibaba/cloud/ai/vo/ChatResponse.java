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
package com.alibaba.cloud.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chat Response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

	private String sessionId;

	private String message;

	private String messageType; // text, sql, result, error

	private String sql; // Generated SQL statement

	private Object result; // Query result

	private String error; // Error message

	public ChatResponse(String sessionId, String message, String messageType) {
		this.sessionId = sessionId;
		this.message = message;
		this.messageType = messageType;
	}

}
