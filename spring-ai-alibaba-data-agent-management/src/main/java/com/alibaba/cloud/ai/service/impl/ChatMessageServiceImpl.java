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
package com.alibaba.cloud.ai.service.impl;

import com.alibaba.cloud.ai.entity.ChatMessage;
import com.alibaba.cloud.ai.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Chat Message Service Class
 */
@Slf4j
@Service
public class ChatMessageServiceImpl implements ChatMessageService {

	private final ChatMessageMapper chatMessageMapper;

	public ChatMessageServiceImpl(ChatMessageMapper chatMessageMapper) {
		this.chatMessageMapper = chatMessageMapper;
	}

	@Override
	public List<ChatMessage> findBySessionId(String sessionId) {
		return chatMessageMapper.selectBySessionId(sessionId);
	}

	@Override
	public ChatMessage saveMessage(ChatMessage message) {
		chatMessageMapper.insert(message);
		log.info("Saved message: {} for session: {}", message.getId(), message.getSessionId());
		return message;
	}

	@Override
	public void saveUserMessage(String sessionId, String content) {
		ChatMessage message = new ChatMessage(sessionId, "user", content, "text");
		saveMessage(message);
	}

	@Override
	public void saveAssistantMessage(String sessionId, String content, String messageType, String metadata) {
		ChatMessage message = new ChatMessage(sessionId, "assistant", content, messageType, metadata);
		saveMessage(message);
	}

}
