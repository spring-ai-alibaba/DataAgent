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

import com.alibaba.cloud.ai.dataagent.dto.memory.CreateMemoryItemRequest;
import com.alibaba.cloud.ai.dataagent.entity.MemoryItem;
import com.alibaba.cloud.ai.dataagent.enums.MemoryKind;
import com.alibaba.cloud.ai.dataagent.enums.MemoryScopeType;
import com.alibaba.cloud.ai.dataagent.service.memory.longterm.LongTermMemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LongTermMemoryControllerTest {

	@Mock
	private LongTermMemoryService memoryService;

	private LongTermMemoryController controller;

	@BeforeEach
	void setUp() {
		controller = new LongTermMemoryController(memoryService);
	}

	@Test
	void createCandidatePersistsCanonicalJsonValue() throws Exception {
		CreateMemoryItemRequest request = new CreateMemoryItemRequest();
		request.setScopeType(MemoryScopeType.AGENT);
		request.setMemoryKind(MemoryKind.PREFERENCE);
		request.setMemoryKey("report-format");
		request.setValue(new ObjectMapper().readTree("{\"format\":\"compact\"}"));
		when(memoryService.createCandidate(org.mockito.ArgumentMatchers.any()))
			.thenAnswer(invocation -> invocation.getArgument(0));

		controller.createCandidate(7, request);

		ArgumentCaptor<MemoryItem> itemCaptor = ArgumentCaptor.forClass(MemoryItem.class);
		verify(memoryService).createCandidate(itemCaptor.capture());
		assertThat(itemCaptor.getValue().getAgentId()).isEqualTo(7);
		assertThat(itemCaptor.getValue().getValueJson()).isEqualTo("{\"format\":\"compact\"}");
	}

	@Test
	void confirmDoesNotRevealAnotherAgentsMemory() {
		when(memoryService.findById(42L)).thenReturn(MemoryItem.builder().id(42L).agentId(8).build());

		assertThatThrownBy(() -> controller.confirm(7, 42L))
			.isInstanceOfSatisfying(ResponseStatusException.class,
					exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
			.hasMessageContaining("Memory item not found");
		verify(memoryService, never()).confirm(42L);
	}

	@Test
	void invalidateMapsMissingMemoryToNotFound() {
		when(memoryService.findById(42L)).thenThrow(new IllegalArgumentException("Memory item not found: 42"));

		assertThatThrownBy(() -> controller.invalidate(7, 42L))
			.isInstanceOfSatisfying(ResponseStatusException.class,
					exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
			.hasMessageContaining("Memory item not found");
		verify(memoryService, never()).invalidate(42L);
	}

}
