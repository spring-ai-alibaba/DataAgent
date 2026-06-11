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
package com.alibaba.cloud.ai.dataagent.enums;

import lombok.Getter;

/**
 * Chat 模型接口协议枚举。 CHAT_COMPLETIONS：标准 OpenAI Chat Completions 格式（默认，/v1/chat/completions）；
 * RESPONSES：OpenAI Responses API 格式（/v1/responses），用于仅支持该协议的模型服务商。
 */
@Getter
public enum ChatApiProtocol {

	/**
	 * 标准 Chat Completions 协议（默认）
	 */
	CHAT_COMPLETIONS("CHAT_COMPLETIONS"),

	/**
	 * OpenAI Responses API 协议
	 */
	RESPONSES("RESPONSES");

	private final String code;

	ChatApiProtocol(String code) {
		this.code = code;
	}

}
