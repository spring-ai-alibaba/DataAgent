/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.util;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

/**
 * @author zhangshenghang
 */
public class ChatResponseUtil {

	public static ChatResponse createResponse(String statusMessage) {
		return createPureResponse(statusMessage + "\n");
	}

	public static ChatResponse createPureResponse(String message) {
		AssistantMessage assistantMessage = new AssistantMessage(message);
		Generation generation = new Generation(assistantMessage);
		return new ChatResponse(List.of(generation));
	}

	public static String getText(ChatResponse chatResponse) {
		return chatResponse.getResult().getOutput().getText();
	}

}
