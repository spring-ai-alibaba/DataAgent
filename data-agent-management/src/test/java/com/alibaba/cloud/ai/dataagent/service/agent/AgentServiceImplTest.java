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
package com.alibaba.cloud.ai.dataagent.service.agent;

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.alibaba.cloud.ai.dataagent.security.ApiKeyCredentialService;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.service.knowledge.AgentKnowledgeResourceManager;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

	@Mock
	private AgentMapper agentMapper;

	@Mock
	private AgentVectorStoreService agentVectorStoreService;

	@Mock
	private FileStorageService fileStorageService;

	@Mock
	private ApiKeyCredentialService apiKeyCredentialService;

	@Mock
	private AgentKnowledgeMapper agentKnowledgeMapper;

	@Mock
	private AgentKnowledgeResourceManager agentKnowledgeResourceManager;

	private AgentServiceImpl agentService;

	@BeforeEach
	void setUp() {
		agentService = new AgentServiceImpl(agentMapper, agentVectorStoreService, fileStorageService,
				apiKeyCredentialService, agentKnowledgeMapper, agentKnowledgeResourceManager);
	}

	@Test
	void queryMethods_delegateToMapper() {
		Agent agent = Agent.builder().id(1L).name("agent").build();
		when(agentMapper.findAll()).thenReturn(List.of(agent));
		when(agentMapper.findById(1L)).thenReturn(agent);
		when(agentMapper.findByStatus("published")).thenReturn(List.of(agent));
		when(agentMapper.searchByKeyword("agent")).thenReturn(List.of(agent));

		assertEquals(1, agentService.findAll().size());
		assertEquals(agent, agentService.findById(1L));
		assertEquals(1, agentService.findByStatus("published").size());
		assertEquals(1, agentService.search("agent").size());
	}

	@Test
	void save_newAgent_setsDefaultsAndInserts() {
		Agent agent = new Agent();

		agentService.save(agent);

		assertNotNull(agent.getCreateTime());
		assertNotNull(agent.getUpdateTime());
		assertEquals(0, agent.getApiKeyEnabled());
		verify(agentMapper).insert(agent);
	}

	@Test
	void save_existingAgent_updates() {
		Agent agent = Agent.builder().id(1L).apiKeyEnabled(null).build();

		agentService.save(agent);

		assertEquals(0, agent.getApiKeyEnabled());
		verify(agentMapper).updateById(agent);
	}

	@Test
	void deleteById_allResourcesClean_deletesKnowledgeAndAgent() {
		Agent agent = Agent.builder().id(1L).avatar("avatar.png").build();
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(7);
		knowledge.setAgentId(1);
		when(agentMapper.findById(1L)).thenReturn(agent);
		when(agentKnowledgeMapper.selectByAgentIdIncludeDeleted(1)).thenReturn(List.of(knowledge));
		when(agentKnowledgeResourceManager.cleanupResources(knowledge)).thenReturn(true);
		when(agentVectorStoreService.deleteDocumentsByMetadata(eq("1"), any())).thenReturn(true);
		when(fileStorageService.deleteFile("avatar.png")).thenReturn(true);
		when(agentMapper.deleteById(1L)).thenReturn(1);

		agentService.deleteById(1L);

		verify(agentKnowledgeMapper).deleteByAgentId(1);
		verify(agentMapper).deleteById(1L);
	}

	@Test
	void deleteById_knowledgeCleanupFails_keepsDatabaseRecords() {
		Agent agent = Agent.builder().id(1L).build();
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(7);
		when(agentMapper.findById(1L)).thenReturn(agent);
		when(agentKnowledgeMapper.selectByAgentIdIncludeDeleted(1)).thenReturn(List.of(knowledge));
		when(agentKnowledgeResourceManager.cleanupResources(knowledge)).thenReturn(false);

		assertThrows(IllegalStateException.class, () -> agentService.deleteById(1L));
		verify(agentKnowledgeMapper, never()).deleteByAgentId(1);
		verify(agentMapper, never()).deleteById(1L);
	}

	@Test
	void deleteById_vectorCleanupThrows_keepsDatabaseRecords() {
		when(agentMapper.findById(1L)).thenReturn(Agent.builder().id(1L).build());
		when(agentKnowledgeMapper.selectByAgentIdIncludeDeleted(1)).thenReturn(List.of());
		when(agentVectorStoreService.deleteDocumentsByMetadata(eq("1"), any()))
			.thenThrow(new RuntimeException("vector unavailable"));

		assertThrowsExactly(RuntimeException.class, () -> agentService.deleteById(1L));
		verify(agentMapper, never()).deleteById(1L);
	}

	@Test
	void deleteById_vectorCleanupReturnsFalse_keepsDatabaseRecords() {
		when(agentMapper.findById(1L)).thenReturn(Agent.builder().id(1L).build());
		when(agentKnowledgeMapper.selectByAgentIdIncludeDeleted(1)).thenReturn(List.of());
		when(agentVectorStoreService.deleteDocumentsByMetadata(eq("1"), any())).thenReturn(false);

		assertThrows(IllegalStateException.class, () -> agentService.deleteById(1L));
		verify(agentMapper, never()).deleteById(1L);
	}

	@Test
	void deleteById_missingAgent_failsFast() {
		when(agentMapper.findById(99L)).thenReturn(null);

		assertThrows(IllegalArgumentException.class, () -> agentService.deleteById(99L));
		verify(agentVectorStoreService, never()).deleteDocumentsByMetadata(anyString(), any());
	}

	@Test
	void generateApiKey_returnsRawKeyButPersistsEncodedCredential() {
		Agent agent = Agent.builder().id(1L).build();
		when(agentMapper.findById(1L)).thenReturn(agent);
		when(apiKeyCredentialService.encode(anyString())).thenReturn("{bcrypt}hash::1234");

		Agent result = agentService.generateApiKey(1L);

		assertNotNull(result.getApiKey());
		assertEquals(1, result.getApiKeyEnabled());
		verify(agentMapper).updateApiKey(1L, "{bcrypt}hash::1234", 1);
	}

	@Test
	void deleteApiKey_clearsCredentialAndDisablesAuthentication() {
		when(agentMapper.findById(1L)).thenReturn(Agent.builder().id(1L).apiKey("credential").build());

		Agent result = agentService.deleteApiKey(1L);

		assertNull(result.getApiKey());
		assertEquals(0, result.getApiKeyEnabled());
		verify(agentMapper).updateApiKey(1L, null, 0);
	}

	@Test
	void toggleApiKey_withCredential_enablesAuthentication() {
		when(agentMapper.findById(1L)).thenReturn(Agent.builder().id(1L).apiKey("credential").build());

		Agent result = agentService.toggleApiKey(1L, true);

		assertEquals(1, result.getApiKeyEnabled());
		verify(agentMapper).toggleApiKey(1L, 1);
	}

	@Test
	void toggleApiKey_withoutCredential_rejectsEnable() {
		when(agentMapper.findById(1L)).thenReturn(Agent.builder().id(1L).build());

		assertThrows(IllegalStateException.class, () -> agentService.toggleApiKey(1L, true));
		verify(agentMapper, never()).toggleApiKey(1L, 1);
	}

	@Test
	void getApiKeyMasked_delegatesStoredCredentialMasking() {
		when(agentMapper.findById(1L)).thenReturn(Agent.builder().id(1L).apiKey("{bcrypt}hash::1234").build());
		when(apiKeyCredentialService.mask("{bcrypt}hash::1234")).thenReturn("****1234");

		assertEquals("****1234", agentService.getApiKeyMasked(1L));
	}

}
