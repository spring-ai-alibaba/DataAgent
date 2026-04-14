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
package com.alibaba.cloud.ai.dataagent.agentscope.runtime;

import com.alibaba.cloud.ai.dataagent.agentscope.session.AgentSessionRegistry;
import com.alibaba.cloud.ai.dataagent.entity.ChatMessage;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatMessageService;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatSessionService;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

public class AgentScopeMemoryPersistenceHook implements Hook {

	private static final String MEMORY_MESSAGE_TYPE = "memory-text";

	private static final String MEMORY_METADATA = "{\"source\":\"agentscope\",\"visibility\":\"memory-only\"}";

	private final String threadId;

	private final String runtimeRequestId;

	private final AgentSessionRegistry sessionRegistry;

	private final ChatSessionService chatSessionService;

	private final ChatMessageService chatMessageService;

	public AgentScopeMemoryPersistenceHook(String threadId, String runtimeRequestId, AgentSessionRegistry sessionRegistry,
			ChatSessionService chatSessionService, ChatMessageService chatMessageService) {
		this.threadId = threadId;
		this.runtimeRequestId = runtimeRequestId;
		this.sessionRegistry = sessionRegistry;
		this.chatSessionService = chatSessionService;
		this.chatMessageService = chatMessageService;
	}

	@Override
	public int priority() {
		return 200;
	}

	@Override
	public <T extends HookEvent> Mono<T> onEvent(T event) {
		if (event instanceof PostCallEvent postCallEvent) {
			persistFinalMessage(postCallEvent);
		}
		return Mono.just(event);
	}

	private void persistFinalMessage(PostCallEvent event) {
		if (!StringUtils.hasText(threadId) || chatSessionService.findBySessionId(threadId) == null) {
			return;
		}
		if (sessionRegistry.isCancelled(threadId, runtimeRequestId)) {
			return;
		}
		String finalText = event.getFinalMessage() == null ? null : event.getFinalMessage().getTextContent();
		if (!StringUtils.hasText(finalText)) {
			return;
		}
		chatMessageService.saveMessage(ChatMessage.builder()
			.sessionId(threadId)
			.role("assistant")
			.content(finalText)
			.messageType(MEMORY_MESSAGE_TYPE)
			.metadata(MEMORY_METADATA)
			.build());
	}

}
