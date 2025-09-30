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
package com.alibaba.cloud.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Chat Message Entity Class
 */
@TableName("chat_message")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@TableField("session_id")
	private String sessionId;

	@TableField("role")
	private String role; // user, assistant, system

	@TableField("content")
	private String content;

	@TableField("message_type")
	private String messageType; // text, sql, result, error

	@TableField("metadata")
	private String metadata; // JSON格式的元数据

	@TableField(value = "create_time", fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	public ChatMessage(String sessionId, String role, String content, String messageType) {
		this.sessionId = sessionId;
		this.role = role;
		this.content = content;
		this.messageType = messageType;
	}

	public ChatMessage(String sessionId, String role, String content, String messageType, String metadata) {
		this.sessionId = sessionId;
		this.role = role;
		this.content = content;
		this.messageType = messageType;
		this.metadata = metadata;
	}

}
