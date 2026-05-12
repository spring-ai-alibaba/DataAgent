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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.service.agent.AgentService;
import com.alibaba.cloud.ai.dataagent.vo.ApiKeyResponse;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

	@Mock
	private AgentService agentService;

	private AgentController agentController;

	@BeforeEach
	void setUp() {
		agentController = new AgentController(agentService);
	}

	@Test
	void createAgent_validRequest_returnsCreatedAgent() {
		Agent input = Agent.builder().name("Test Agent").description("A test agent").build();
		Agent saved = Agent.builder().id(1L).name("Test Agent").description("A test agent").status("draft").build();
		when(agentService.save(any(Agent.class))).thenReturn(saved);

		Agent result = agentController.create(input);

		assertNotNull(result);
		assertEquals(1L, result.getId());
		assertEquals("Test Agent", result.getName());
		assertEquals("draft", result.getStatus());
		verify(agentService).save(any(Agent.class));
	}

	@Test
	void getAgent_existingId_returnsAgent() {
		Agent agent = Agent.builder().id(1L).name("Test Agent").build();
		when(agentService.findById(1L)).thenReturn(agent);

		Agent result = agentController.get(1L);

		assertNotNull(result);
		assertEquals(1L, result.getId());
	}

	@Test
	void getAgent_nonExistingId_throwsNotFoundException() {
		when(agentService.findById(999L)).thenReturn(null);

		assertThrows(ResponseStatusException.class, () -> agentController.get(999L));
	}

	@Test
	void deleteAgent_existingId_callsDeleteOnService() {
		Agent agent = Agent.builder().id(1L).name("Test Agent").build();
		when(agentService.findById(1L)).thenReturn(agent);

		agentController.delete(1L);

		verify(agentService).deleteById(1L);
	}

	@Test
	void publishAgent_validAgent_updatesStatusToPublished() {
		Agent agent = Agent.builder().id(1L).name("Test Agent").status("draft").build();
		when(agentService.findById(1L)).thenReturn(agent);
		when(agentService.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

		Agent result = agentController.publish(1L);

		assertEquals("published", result.getStatus());
		verify(agentService).save(any(Agent.class));
	}

	@Test
	void generateApiKey_validAgent_returnsKeyResponse() {
		Agent agent = Agent.builder().id(1L).name("Test Agent").build();
		Agent updated = Agent.builder().id(1L).apiKey("sk-abc123").apiKeyEnabled(1).build();
		when(agentService.findById(1L)).thenReturn(agent);
		when(agentService.generateApiKey(1L)).thenReturn(updated);

		ApiResponse<ApiKeyResponse> result = agentController.generateApiKey(1L);

		assertTrue(result.isSuccess());
		assertEquals("sk-abc123", result.getData().getApiKey());
		assertEquals(1, result.getData().getApiKeyEnabled());
	}

	@Test
	void list_withKeyword_callsSearch() {
		List<Agent> agents = List.of(Agent.builder().id(1L).name("Sales Agent").build());
		when(agentService.search("Sales")).thenReturn(agents);

		List<Agent> result = agentController.list(null, "Sales");

		assertEquals(1, result.size());
		verify(agentService).search("Sales");
		verify(agentService, never()).findAll();
	}

}
