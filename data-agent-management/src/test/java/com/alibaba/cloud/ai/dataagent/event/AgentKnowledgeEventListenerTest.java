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
package com.alibaba.cloud.ai.dataagent.event;

import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.service.knowledge.AgentKnowledgeResourceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentKnowledgeEventListenerTest {

	private AgentKnowledgeEventListener listener;

	@Mock
	private AgentKnowledgeMapper agentKnowledgeMapper;

	@Mock
	private AgentKnowledgeResourceManager agentKnowledgeResourceManager;

	@BeforeEach
	void setUp() {
		listener = new AgentKnowledgeEventListener(agentKnowledgeMapper, agentKnowledgeResourceManager);
	}

	@Test
	void handleEmbeddingEvent_knowledgeNotFound_returnsEarly() {
		AgentKnowledgeEmbeddingEvent event = new AgentKnowledgeEmbeddingEvent(this, 1, "token");
		when(agentKnowledgeMapper.selectById(1)).thenReturn(null);

		listener.handleEmbeddingEvent(event);

		verify(agentKnowledgeMapper).selectById(1);
		verify(agentKnowledgeMapper, never()).update(any());
		verifyNoInteractions(agentKnowledgeResourceManager);
	}

	@Test
	void handleEmbeddingEvent_success_updatesStatusToCompleted() throws Exception {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);
		knowledge.setAgentId(10);
		AgentKnowledgeEmbeddingEvent event = new AgentKnowledgeEmbeddingEvent(this, 1, "token");

		when(agentKnowledgeMapper.selectById(1)).thenReturn(knowledge);
		doNothing().when(agentKnowledgeResourceManager).doEmbedingToVectorStore(knowledge);

		listener.handleEmbeddingEvent(event);

		verify(agentKnowledgeMapper, times(2)).update(knowledge);
		assertEquals(EmbeddingStatus.COMPLETED, knowledge.getEmbeddingStatus());
	}

	@Test
	void handleEmbeddingEvent_failure_updatesStatusToFailed() throws Exception {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);
		knowledge.setAgentId(10);
		AgentKnowledgeEmbeddingEvent event = new AgentKnowledgeEmbeddingEvent(this, 1, "token");

		when(agentKnowledgeMapper.selectById(1)).thenReturn(knowledge);
		doThrow(new RuntimeException("embedding failed")).when(agentKnowledgeResourceManager)
			.doEmbedingToVectorStore(knowledge);

		listener.handleEmbeddingEvent(event);

		assertEquals(EmbeddingStatus.FAILED, knowledge.getEmbeddingStatus());
		assertNotNull(knowledge.getErrorMsg());
		assertTrue(knowledge.getErrorMsg().contains("embedding failed"));
	}

	@Test
	void handleDeletionEvent_knowledgeNotFound_returnsEarly() {
		AgentKnowledgeDeletionEvent event = new AgentKnowledgeDeletionEvent(this, 1);
		when(agentKnowledgeMapper.selectByIdIncludeDeleted(1)).thenReturn(null);

		listener.handleDeletionEvent(event);

		verify(agentKnowledgeMapper).selectByIdIncludeDeleted(1);
		verify(agentKnowledgeMapper, never()).update(any());
	}

	@Test
	void handleDeletionEvent_allCleanupSucceeds_marksResourceCleaned() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);
		knowledge.setAgentId(10);
		AgentKnowledgeDeletionEvent event = new AgentKnowledgeDeletionEvent(this, 1);

		when(agentKnowledgeMapper.selectByIdIncludeDeleted(1)).thenReturn(knowledge);
		when(agentKnowledgeResourceManager.deleteFromVectorStore(10, 1)).thenReturn(true);
		when(agentKnowledgeResourceManager.deleteKnowledgeFile(knowledge)).thenReturn(true);

		listener.handleDeletionEvent(event);

		assertEquals(1, knowledge.getIsResourceCleaned());
		verify(agentKnowledgeMapper).update(knowledge);
	}

	@Test
	void handleDeletionEvent_partialCleanup_doesNotMarkCleaned() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);
		knowledge.setAgentId(10);
		AgentKnowledgeDeletionEvent event = new AgentKnowledgeDeletionEvent(this, 1);

		when(agentKnowledgeMapper.selectByIdIncludeDeleted(1)).thenReturn(knowledge);
		when(agentKnowledgeResourceManager.deleteFromVectorStore(10, 1)).thenReturn(true);
		when(agentKnowledgeResourceManager.deleteKnowledgeFile(knowledge)).thenReturn(false);

		listener.handleDeletionEvent(event);

		verify(agentKnowledgeMapper, never()).update(any());
	}

	@Test
	void handleDeletionEvent_exceptionDuringCleanup_doesNotMarkCleaned() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);
		knowledge.setAgentId(10);
		AgentKnowledgeDeletionEvent event = new AgentKnowledgeDeletionEvent(this, 1);

		when(agentKnowledgeMapper.selectByIdIncludeDeleted(1)).thenReturn(knowledge);
		when(agentKnowledgeResourceManager.deleteFromVectorStore(10, 1))
			.thenThrow(new RuntimeException("cleanup error"));

		listener.handleDeletionEvent(event);

		verify(agentKnowledgeMapper, never()).update(any());
	}

}
