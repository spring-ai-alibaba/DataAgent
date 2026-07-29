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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationMemoryCleanupServiceTest {

	@Mock
	private ConversationTurnService turnService;

	@Mock
	private ConversationSummaryService summaryService;

	@Mock
	private LongTermMemoryService longTermMemoryService;

	@Mock
	private ConversationMemoryGateway memoryGateway;

	@Mock
	private ChatSessionMapper chatSessionMapper;

	@Mock
	private ConversationMemoryCleanupService service;

	@BeforeEach
	void setUp() {
		service = new ConversationMemoryCleanupService(turnService, summaryService, longTermMemoryService,
				chatSessionMapper, memoryGateway);
	}

	@Test
	void forgettingConversationDelegatesToEachMemoryOwnerInForeignKeySafeOrder() {
		service.forgetConversation("conversation-1");

		InOrder order = inOrder(chatSessionMapper, longTermMemoryService, turnService, summaryService, memoryGateway);
		order.verify(chatSessionMapper).lockBySessionId("conversation-1");
		order.verify(longTermMemoryService).deleteByConversation("conversation-1");
		order.verify(turnService).deleteByConversation("conversation-1");
		order.verify(summaryService).delete("conversation-1");
		order.verify(memoryGateway).clear("conversation-1");
	}

}
