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
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentKnowledgeResourceManagerTest {

	private AgentKnowledgeResourceManager manager;

	@Mock
	private TextSplitterFactory textSplitterFactory;

	@Mock
	private FileStorageService fileStorageService;

	@Mock
	private AgentVectorStoreService agentVectorStoreService;

	@BeforeEach
	void setUp() {
		manager = new AgentKnowledgeResourceManager(textSplitterFactory, fileStorageService, agentVectorStoreService);
	}

	@Test
	void testDeleteFromVectorStore_success() {
		boolean result = manager.deleteFromVectorStore(1, 10);
		assertTrue(result);
		verify(agentVectorStoreService).deleteDocumentsByMetedata(eq("1"), anyMap());
	}

	@Test
	void testDeleteFromVectorStore_notFoundError() {
		doThrow(new RuntimeException("not found")).when(agentVectorStoreService)
			.deleteDocumentsByMetedata(anyString(), anyMap());

		boolean result = manager.deleteFromVectorStore(1, 10);
		assertTrue(result);
	}

	@Test
	void testDeleteFromVectorStore_otherError() {
		doThrow(new RuntimeException("connection refused")).when(agentVectorStoreService)
			.deleteDocumentsByMetedata(anyString(), anyMap());

		boolean result = manager.deleteFromVectorStore(1, 10);
		assertFalse(result);
	}

	@Test
	void testDeleteKnowledgeFile_notDocumentType() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(10);
		knowledge.setType(KnowledgeType.QA);

		boolean result = manager.deleteKnowledgeFile(knowledge);
		assertTrue(result);
		verify(fileStorageService, never()).deleteFile(anyString());
	}

	@Test
	void testDeleteKnowledgeFile_noFilePath() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(10);
		knowledge.setType(KnowledgeType.DOCUMENT);
		knowledge.setFilePath(null);

		boolean result = manager.deleteKnowledgeFile(knowledge);
		assertTrue(result);
	}

	@Test
	void testDeleteKnowledgeFile_success() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(10);
		knowledge.setType(KnowledgeType.DOCUMENT);
		knowledge.setFilePath("uploads/test.pdf");
		when(fileStorageService.deleteFile("uploads/test.pdf")).thenReturn(true);

		boolean result = manager.deleteKnowledgeFile(knowledge);
		assertTrue(result);
	}

	@Test
	void testDeleteKnowledgeFile_deleteFailed() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(10);
		knowledge.setType(KnowledgeType.DOCUMENT);
		knowledge.setFilePath("uploads/test.pdf");
		when(fileStorageService.deleteFile("uploads/test.pdf")).thenReturn(false);

		boolean result = manager.deleteKnowledgeFile(knowledge);
		assertFalse(result);
	}

	@Test
	void testDeleteKnowledgeFile_fileNotFoundError() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(10);
		knowledge.setType(KnowledgeType.DOCUMENT);
		knowledge.setFilePath("uploads/test.pdf");
		when(fileStorageService.deleteFile("uploads/test.pdf")).thenThrow(new RuntimeException("No such file"));

		boolean result = manager.deleteKnowledgeFile(knowledge);
		assertTrue(result);
	}

	@Test
	void testDeleteKnowledgeFile_otherError() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(10);
		knowledge.setType(KnowledgeType.DOCUMENT);
		knowledge.setFilePath("uploads/test.pdf");
		when(fileStorageService.deleteFile("uploads/test.pdf")).thenThrow(new RuntimeException("disk error"));

		boolean result = manager.deleteKnowledgeFile(knowledge);
		assertFalse(result);
	}

}
