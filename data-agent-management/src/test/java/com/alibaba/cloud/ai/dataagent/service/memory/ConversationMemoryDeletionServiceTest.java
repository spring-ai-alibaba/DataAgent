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
import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationSummaryMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.mapper.MemoryItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationMemoryDeletionServiceTest {

	@Mock
	private ConversationTurnMapper turnMapper;

	@Mock
	private ConversationSummaryMapper summaryMapper;

	@Mock
	private MemoryItemMapper memoryItemMapper;

	@Mock
	private ChatMessageMapper chatMessageMapper;

	@Mock
	private ChatMemory chatMemory;

	@Mock
	private ChatSessionMapper chatSessionMapper;

	@Mock
	private MemoryOutboxService outboxService;

	private ConversationMemoryDeletionService service;

	@BeforeEach
	void setUp() {
		service = new ConversationMemoryDeletionService(turnMapper, summaryMapper, memoryItemMapper, chatMessageMapper,
				chatSessionMapper, chatMemory, outboxService);
	}

	@Test
	void forgettingConversationTombstonesIndexesAndDeletesAllOwnedState() {
		when(turnMapper.selectByConversationId("conversation-1"))
			.thenReturn(List.of(ConversationTurn.builder().id("turn-1").build()));
		when(memoryItemMapper.selectByConversationId("conversation-1"))
			.thenReturn(List.of(MemoryItem.builder().id(10L).build()));

		service.forgetConversation("conversation-1");

		verify(chatSessionMapper).lockBySessionId("conversation-1");
		verify(outboxService).enqueue("CONVERSATION_TURN", "turn-1", MemoryEventType.TURN_INVALIDATED, null);
		verify(outboxService).enqueue("MEMORY_ITEM", "10", MemoryEventType.MEMORY_INVALIDATED, null);
		verify(memoryItemMapper).deleteByConversationId("conversation-1");
		verify(summaryMapper).deleteByConversationId("conversation-1");
		verify(turnMapper).deleteByConversationId("conversation-1");
		verify(chatMessageMapper).deleteBySessionId("conversation-1");
		verify(chatMemory).clear("conversation-1");
	}

}
