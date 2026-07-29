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
import com.alibaba.cloud.ai.dataagent.entity.MemoryOutboxEvent;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.mapper.MemoryItemMapper;
import com.alibaba.cloud.ai.dataagent.mapper.MemoryOutboxMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryProjectionWorkerTest {

	@Mock
	private MemoryOutboxMapper outboxMapper;

	@Mock
	private ConversationTurnMapper turnMapper;

	@Mock
	private MemoryItemMapper memoryItemMapper;

	@Mock
	private ConversationSummaryService summaryService;

	@Mock
	private MemoryVectorIndexService vectorIndexService;

	private MemoryProjectionWorker worker;

	@BeforeEach
	void setUp() {
		worker = new MemoryProjectionWorker(outboxMapper, turnMapper, memoryItemMapper, summaryService,
				vectorIndexService, new DataAgentProperties());
	}

	@Test
	void successfulTurnEventRebuildsDerivedIndexesAndIsMarkedDone() {
		MemoryOutboxEvent event = event(1L, MemoryEventType.TURN_SUCCEEDED, "turn-1");
		ConversationTurn turn = ConversationTurn.builder()
			.id("turn-1")
			.conversationId("conversation-1")
			.memoryEligible(true)
			.build();
		when(outboxMapper.selectReady(20, 5)).thenReturn(List.of(event));
		when(outboxMapper.claim(1L)).thenReturn(1);
		when(turnMapper.selectById("turn-1")).thenReturn(turn);

		worker.projectReadyEvents();

		verify(outboxMapper).recoverStale(any(LocalDateTime.class));
		verify(summaryService).rebuild("conversation-1");
		verify(vectorIndexService).indexTurn(turn);
		verify(outboxMapper).markDone(1L);
		verify(outboxMapper, never()).markFailed(anyLong(), anyString(), any(LocalDateTime.class));
	}

	@Test
	void failedProjectionIsRetriedInsteadOfBeingAcknowledged() {
		MemoryOutboxEvent event = event(2L, MemoryEventType.MEMORY_INVALIDATED, "10");
		when(outboxMapper.selectReady(20, 5)).thenReturn(List.of(event));
		when(outboxMapper.claim(2L)).thenReturn(1);
		doThrow(new IllegalStateException("vector unavailable")).when(vectorIndexService).deleteMemoryItem(10L);

		worker.projectReadyEvents();

		verify(outboxMapper).markFailed(eq(2L), contains("vector unavailable"), any(LocalDateTime.class));
		verify(outboxMapper, never()).markDone(2L);
	}

	private MemoryOutboxEvent event(Long id, String eventType, String aggregateId) {
		return MemoryOutboxEvent.builder().id(id).eventType(eventType).aggregateId(aggregateId).attemptCount(0).build();
	}

}
