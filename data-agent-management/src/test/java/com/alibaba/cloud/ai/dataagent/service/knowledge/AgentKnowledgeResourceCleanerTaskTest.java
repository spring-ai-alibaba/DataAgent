/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.service.knowledge;

import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentKnowledgeResourceCleanerTaskTest {

	private AgentKnowledgeResourceCleanerTask task;

	@Mock
	private AgentKnowledgeMapper mapper;

	@Mock
	private AgentKnowledgeResourceManager resourceManager;

	@BeforeEach
	void setUp() {
		task = new AgentKnowledgeResourceCleanerTask(mapper, resourceManager);
	}

	@Test
	void testCleanupZombieResources_noDirtyRecords() {
		when(mapper.selectDirtyRecords(any(LocalDateTime.class), anyInt())).thenReturn(List.of());

		task.cleanupZombieResources();

		verify(resourceManager, never()).deleteFromVectorStore(any(), any());
	}

	@Test
	void testCleanupZombieResources_successfulCleanup() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);
		knowledge.setAgentId(10);

		when(mapper.selectDirtyRecords(any(LocalDateTime.class), anyInt())).thenReturn(List.of(knowledge));
		when(resourceManager.deleteFromVectorStore(10, 1)).thenReturn(true);
		when(resourceManager.deleteKnowledgeFile(knowledge)).thenReturn(true);

		task.cleanupZombieResources();

		verify(mapper).update(knowledge);
		assertEquals(Integer.valueOf(1), knowledge.getIsResourceCleaned());
	}

	@Test
	void testCleanupZombieResources_partialCleanup() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);
		knowledge.setAgentId(10);

		when(mapper.selectDirtyRecords(any(LocalDateTime.class), anyInt())).thenReturn(List.of(knowledge));
		when(resourceManager.deleteFromVectorStore(10, 1)).thenReturn(true);
		when(resourceManager.deleteKnowledgeFile(knowledge)).thenReturn(false);

		task.cleanupZombieResources();

		verify(mapper, never()).update(any());
	}

	@Test
	void testCleanupZombieResources_exceptionInSingleRecord() {
		AgentKnowledge k1 = new AgentKnowledge();
		k1.setId(1);
		k1.setAgentId(10);
		AgentKnowledge k2 = new AgentKnowledge();
		k2.setId(2);
		k2.setAgentId(10);

		when(mapper.selectDirtyRecords(any(LocalDateTime.class), anyInt())).thenReturn(List.of(k1, k2));
		when(resourceManager.deleteFromVectorStore(10, 1)).thenThrow(new RuntimeException("error"));
		when(resourceManager.deleteFromVectorStore(10, 2)).thenReturn(true);
		when(resourceManager.deleteKnowledgeFile(k2)).thenReturn(true);

		task.cleanupZombieResources();

		verify(mapper, times(1)).update(k2);
	}

}
