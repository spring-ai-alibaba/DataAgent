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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Rebuildable summary of successful turns older than the recent-turn window.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummary {

	private String conversationId;

	private String summaryText;

	private String coveredThroughTurnId;

	@Builder.Default
	private Long version = 1L;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

}
