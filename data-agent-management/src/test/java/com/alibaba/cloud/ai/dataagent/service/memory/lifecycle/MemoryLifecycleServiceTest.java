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
package com.alibaba.cloud.ai.dataagent.service.memory.lifecycle;

import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.service.memory.longterm.LongTermMemoryService;
import com.alibaba.cloud.ai.dataagent.service.graph.runtime.ActiveGraphRunRegistry;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryEventType;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryLifecycleServiceTest {

	@Mock
	private ConversationTurnMapper turnMapper;

	@Mock
	private AgentMapper agentMapper;

	@Mock
	private ChatMessageMapper chatMessageMapper;

	@Mock
	private LongTermMemoryService longTermMemoryService;

	@Mock
	private ChatSessionMapper chatSessionMapper;

	@Mock
	private MemoryOutboxService outboxService;

	@Mock
	private ActiveGraphRunRegistry activeGraphRunRegistry;

	private MemoryLifecycleService service;

	@BeforeEach
	void setUp() {
		service = new MemoryLifecycleService(turnMapper, agentMapper, chatMessageMapper, longTermMemoryService,
				chatSessionMapper, outboxService, activeGraphRunRegistry);
	}

	@Test
	void forgettingConversationDeletesFactsThenEmitsDerivedStoreCleanup() {
		when(chatSessionMapper.lockAnyBySessionId("conversation-1")).thenReturn("conversation-1");
		when(chatSessionMapper.selectAnyBySessionId("conversation-1"))
			.thenReturn(ChatSession.builder().id("conversation-1").agentId(7).build());
		when(turnMapper.selectByConversationId("conversation-1"))
			.thenReturn(List.of(ConversationTurn.builder().id("turn-1").acceptedRunId("run-1").build()));

		service.forgetConversation("conversation-1", 7);

		InOrder order = inOrder(chatSessionMapper, activeGraphRunRegistry, longTermMemoryService, turnMapper,
				chatMessageMapper,
				outboxService);
		order.verify(chatSessionMapper).lockAnyBySessionId("conversation-1");
		order.verify(chatSessionMapper).selectAnyBySessionId("conversation-1");
		order.verify(activeGraphRunRegistry).quiesceConversationForDeletion("conversation-1", "7");
		order.verify(longTermMemoryService).deleteByConversation("conversation-1");
		order.verify(turnMapper).selectByConversationId("conversation-1");
		order.verify(outboxService).enqueue("GRAPH_RUN", "run-1", MemoryEventType.GRAPH_CHECKPOINT_RELEASE, null);
		order.verify(outboxService).enqueue("CONVERSATION_TURN", "turn-1", MemoryEventType.TURN_INVALIDATED, null);
		order.verify(turnMapper).deleteByConversationId("conversation-1");
		order.verify(chatMessageMapper).deleteBySessionId("conversation-1");
		order.verify(outboxService)
			.enqueue("CONVERSATION", "conversation-1", MemoryEventType.CONVERSATION_FORGOTTEN, null);
	}

	@Test
	void forgettingAgentCascadesEveryConversationAndRemainingAgentMemory() {
		when(agentMapper.lockById(7L)).thenReturn(7L);
		when(chatSessionMapper.selectAllByAgentId(7))
			.thenReturn(List.of(ChatSession.builder().id("conversation-1").agentId(7).build(),
					ChatSession.builder().id("conversation-2").agentId(7).build()));
		when(chatSessionMapper.lockAnyBySessionId("conversation-1")).thenReturn("conversation-1");
		when(chatSessionMapper.lockAnyBySessionId("conversation-2")).thenReturn("conversation-2");
		when(chatSessionMapper.selectAnyBySessionId("conversation-1"))
			.thenReturn(ChatSession.builder().id("conversation-1").agentId(7).build());
		when(chatSessionMapper.selectAnyBySessionId("conversation-2"))
			.thenReturn(ChatSession.builder().id("conversation-2").agentId(7).build());
		when(turnMapper.selectByConversationId("conversation-1"))
			.thenReturn(List.of(ConversationTurn.builder().id("turn-1").build()));
		when(turnMapper.selectByConversationId("conversation-2"))
			.thenReturn(List.of(ConversationTurn.builder().id("turn-2").build()));

		service.forgetAgent(7);

		verify(agentMapper).lockById(7L);
		verify(turnMapper).deleteByConversationId("conversation-1");
		verify(turnMapper).deleteByConversationId("conversation-2");
		verify(longTermMemoryService).deleteByAgent(7);
		verify(chatSessionMapper).softDeleteByAgentId(eq(7), any(LocalDateTime.class));
	}

	@Test
	void forgettingAgentRejectsMissingAgentBeforeEnumeratingConversations() {
		when(agentMapper.lockById(7L)).thenReturn(null);

		assertThrows(IllegalArgumentException.class, () -> service.forgetAgent(7));

		verify(chatSessionMapper, never()).selectAllByAgentId(any());
		verify(longTermMemoryService, never()).deleteByAgent(any());
	}

	@Test
	void forgettingConversationRejectsCrossAgentDeletionBeforeChangingMemory() {
		when(chatSessionMapper.lockAnyBySessionId("conversation-1")).thenReturn("conversation-1");
		when(chatSessionMapper.selectAnyBySessionId("conversation-1"))
			.thenReturn(ChatSession.builder().id("conversation-1").agentId(7).build());

		assertThrows(IllegalArgumentException.class, () -> service.forgetConversation("conversation-1", 8));

		verify(longTermMemoryService, never()).deleteByConversation(any());
		verify(turnMapper, never()).deleteByConversationId(any());
		verify(chatMessageMapper, never()).deleteBySessionId(any());
		verify(outboxService, never()).enqueue(any(), any(), any(), any());
	}

	@Test
	void forgettingConversationRejectsUnknownConversation() {
		when(chatSessionMapper.lockAnyBySessionId("missing")).thenReturn(null);

		assertThrows(IllegalArgumentException.class, () -> service.forgetConversation("missing", 7));

		verify(longTermMemoryService, never()).deleteByConversation(any());
	}

	@Test
	void datasourceAndBindingDeletionInvalidateTheirMemoryScopes() {
		service.invalidateDatasource(3);
		service.invalidateDatasourceBinding(7, 3);

		verify(longTermMemoryService).deleteByDatasource(3);
		verify(longTermMemoryService).deleteByAgentAndDatasource(7, 3);
	}

}
