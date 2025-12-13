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

/**
 * Chat Session Entity Class
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession {

	private String id; // UUID

	private Integer agentId;

	private String title;

	private String status; // active, archived, deleted

	@Builder.Default
	private Boolean isPinned = false; // Whether pinned

	private Long userId;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	public ChatSession(String id, Integer agentId, String title, String status, Long userId) {
		this.id = id;
		this.agentId = agentId;
		this.title = title;
		this.status = status;
		this.isPinned = false;
		this.userId = userId;
	}

}
