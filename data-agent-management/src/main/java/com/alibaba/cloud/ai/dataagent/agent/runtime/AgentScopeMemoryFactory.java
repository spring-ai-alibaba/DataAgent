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
package com.alibaba.cloud.ai.dataagent.agent.runtime;

import com.alibaba.cloud.ai.dataagent.management.entity.ChatMessage;
import com.alibaba.cloud.ai.dataagent.management.service.chat.ChatMessageService;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentScopeMemoryFactory {

	private static final int MEMORY_MESSAGE_LIMIT = 20;

	private final ChatMessageService chatMessageService;

	public Memory create(String threadId) {
		InMemoryMemory memory = new InMemoryMemory();
		if (!StringUtils.hasText(threadId)) {
			return memory;
		}
		List<ChatMessage> history = chatMessageService.findRecentBySessionId(threadId, MEMORY_MESSAGE_LIMIT);
		history.stream().map(this::toMessage).filter(msg -> msg != null).forEach(memory::addMessage);
		log.debug("Loaded {} history messages into AgentScope memory, threadId={}", history.size(), threadId);
		return memory;
	}

	private Msg toMessage(ChatMessage message) {
		if (message == null || !StringUtils.hasText(message.getContent())) {
			return null;
		}
		return Msg.builder()
			.name(resolveName(message.getRole()))
			.role(resolveRole(message.getRole()))
			.textContent(message.getContent())
			.build();
	}

	private String resolveName(String role) {
		return StringUtils.hasText(role) ? role : "user";
	}

	private MsgRole resolveRole(String role) {
		if (!StringUtils.hasText(role)) {
			return MsgRole.USER;
		}
		return switch (role.trim().toLowerCase()) {
			case "assistant" -> MsgRole.ASSISTANT;
			case "system" -> MsgRole.SYSTEM;
			case "tool" -> MsgRole.TOOL;
			default -> MsgRole.USER;
		};
	}

}
