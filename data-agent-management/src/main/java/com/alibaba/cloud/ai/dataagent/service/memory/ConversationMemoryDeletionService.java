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
package com.alibaba.cloud.ai.dataagent.service.memory;

import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.entity.MemoryItem;
import com.alibaba.cloud.ai.dataagent.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationSummaryMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.mapper.MemoryItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Removes every durable and derived memory owned by a conversation.
 */
@Service
@RequiredArgsConstructor
public class ConversationMemoryDeletionService {

	private final ConversationTurnMapper turnMapper;

	private final ConversationSummaryMapper summaryMapper;

	private final MemoryItemMapper memoryItemMapper;

	private final ChatMessageMapper chatMessageMapper;

	private final ChatMemory chatMemory;

	private final MemoryOutboxService outboxService;

	@Transactional
	public void forgetConversation(String conversationId) {
		List<ConversationTurn> turns = turnMapper.selectByConversationId(conversationId);
		List<MemoryItem> memoryItems = memoryItemMapper.selectByConversationId(conversationId);
		turns.forEach(turn -> outboxService.enqueue("CONVERSATION_TURN", turn.getId(),
				MemoryEventType.TURN_INVALIDATED, null));
		memoryItems.forEach(item -> outboxService.enqueue("MEMORY_ITEM", item.getId().toString(),
				MemoryEventType.MEMORY_INVALIDATED, null));
		memoryItemMapper.deleteByConversationId(conversationId);
		summaryMapper.deleteByConversationId(conversationId);
		turnMapper.deleteByConversationId(conversationId);
		chatMessageMapper.deleteBySessionId(conversationId);
		chatMemory.clear(conversationId);
	}

}
