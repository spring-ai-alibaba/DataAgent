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
package com.alibaba.cloud.ai.dataagent.service.chat;

import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import com.alibaba.cloud.ai.dataagent.service.graph.turn.ConversationTurnService;
import com.alibaba.cloud.ai.dataagent.service.memory.longterm.LongTermMemoryService;
import com.alibaba.cloud.ai.dataagent.service.memory.shortterm.ConversationMemoryGateway;
import com.alibaba.cloud.ai.dataagent.service.memory.shortterm.ConversationSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes every durable and derived memory owned by a conversation.
 */
@Service
@RequiredArgsConstructor
public class ConversationMemoryCleanupService {

	private final ConversationTurnService turnService;

	private final ConversationSummaryService summaryService;

	private final LongTermMemoryService longTermMemoryService;

	private final ChatSessionMapper chatSessionMapper;

	private final ConversationMemoryGateway memoryGateway;

	@Transactional
	public void forgetConversation(String conversationId) {
		chatSessionMapper.lockBySessionId(conversationId);
		longTermMemoryService.deleteByConversation(conversationId);
		turnService.deleteByConversation(conversationId);
		summaryService.delete(conversationId);
		memoryGateway.clear(conversationId);
	}

}
