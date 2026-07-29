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
import com.alibaba.cloud.ai.dataagent.enums.*;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.mapper.MemoryItemMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LongTermMemoryServiceTest {

	@Mock
	private MemoryItemMapper mapper;

	@Mock
	private MemoryOutboxService outboxService;

	@Mock
	private MemoryVectorIndexService vectorIndexService;

	@Mock
	private ConversationTurnMapper turnMapper;

	@Mock
	private AgentDatasourceMapper agentDatasourceMapper;

	private DataAgentProperties properties;

	private LongTermMemoryService service;

	@BeforeEach
	void setUp() {
		properties = new DataAgentProperties();
		service = new LongTermMemoryService(mapper, outboxService, vectorIndexService, properties, turnMapper,
				agentDatasourceMapper);
	}

	@Test
	void candidateIsNotPromptVisibleUntilExplicitConfirmation() {
		MemoryItem candidate = item(MemoryScopeType.AGENT);
		when(mapper.selectById(10L)).thenReturn(candidate);

		service.createCandidate(candidate);
		service.confirm(10L);

		verify(mapper).insert(argThat(item -> item.getStatus() == MemoryStatus.CANDIDATE));
		verify(mapper).updateStatus(10L, MemoryStatus.CONFIRMED);
		verify(outboxService).enqueue("MEMORY_ITEM", "10", MemoryEventType.MEMORY_CONFIRMED, null);
	}

	@Test
	void userScopeIsRejectedWithoutTrustedServerIdentityFeature() {
		MemoryItem item = item(MemoryScopeType.USER_AGENT);

		assertThatThrownBy(() -> service.createCandidate(item))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("trusted server-derived owner identity");
		verifyNoInteractions(mapper);
	}

	@Test
	void enabledUserScopeDerivesOwnerFromVerifiedSourceTurn() {
		properties.getMemory().setUserScopeEnabled(true);
		MemoryItem item = item(MemoryScopeType.USER_AGENT);
		item.setSourceTurnId("turn-1");
		when(turnMapper.selectById("turn-1")).thenReturn(ConversationTurn.builder()
			.id("turn-1")
			.agentId(7)
			.ownerId(99L)
			.status(TurnStatus.SUCCEEDED)
			.memoryEligible(true)
			.build());
		when(mapper.selectById(10L)).thenAnswer(invocation -> item);

		service.createCandidate(item);

		verify(mapper).insert(argThat(candidate -> Long.valueOf(99L).equals(candidate.getOwnerId())
				&& candidate.getStatus() == MemoryStatus.CANDIDATE));
	}

	@Test
	void sourceTurnMustBeSuccessfulAndBelongToSameAgent() {
		MemoryItem item = item(MemoryScopeType.DATASOURCE);
		item.setDatasourceId(3);
		item.setSourceTurnId("turn-1");
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(7L, 3))
			.thenReturn(new com.alibaba.cloud.ai.dataagent.entity.AgentDatasource(7L, 3));
		when(turnMapper.selectById("turn-1"))
			.thenReturn(ConversationTurn.builder()
				.id("turn-1")
				.agentId(8)
				.status(TurnStatus.SUCCEEDED)
				.memoryEligible(true)
				.build());

		assertThatThrownBy(() -> service.createCandidate(item))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("successful turn of the same agent");
	}

	@Test
	void datasourceMemoryInheritsSourceSchemaFingerprint() {
		MemoryItem item = item(MemoryScopeType.DATASOURCE);
		item.setDatasourceId(3);
		item.setSourceTurnId("turn-1");
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(7L, 3))
			.thenReturn(new com.alibaba.cloud.ai.dataagent.entity.AgentDatasource(7L, 3));
		when(turnMapper.selectById("turn-1")).thenReturn(ConversationTurn.builder()
			.id("turn-1")
			.agentId(7)
			.datasourceId(3)
			.schemaFingerprint("schema-v1")
			.status(TurnStatus.SUCCEEDED)
			.memoryEligible(true)
			.build());
		when(mapper.selectById(10L)).thenAnswer(invocation -> item);

		service.createCandidate(item);

		verify(mapper).insert(argThat(candidate -> "schema-v1".equals(candidate.getSchemaFingerprint())));
	}

	@Test
	void datasourceMemoryRejectsSourceFromAnotherDatasource() {
		MemoryItem item = item(MemoryScopeType.DATASOURCE);
		item.setDatasourceId(3);
		item.setSourceTurnId("turn-1");
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(7L, 3))
			.thenReturn(new com.alibaba.cloud.ai.dataagent.entity.AgentDatasource(7L, 3));
		when(turnMapper.selectById("turn-1")).thenReturn(ConversationTurn.builder()
			.id("turn-1")
			.agentId(7)
			.datasourceId(4)
			.status(TurnStatus.SUCCEEDED)
			.memoryEligible(true)
			.build());

		assertThatThrownBy(() -> service.createCandidate(item))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("source turn datasource");
	}

	@Test
	void confirmationCannotSupersedeMemoryFromAnotherDatasourceScope() {
		MemoryItem candidate = item(MemoryScopeType.DATASOURCE);
		candidate.setDatasourceId(3);
		candidate.setSupersedesId(9L);
		MemoryItem superseded = item(MemoryScopeType.DATASOURCE);
		superseded.setId(9L);
		superseded.setDatasourceId(4);
		superseded.setStatus(MemoryStatus.CONFIRMED);
		when(mapper.selectById(10L)).thenReturn(candidate);
		when(mapper.selectById(9L)).thenReturn(superseded);

		assertThatThrownBy(() -> service.confirm(10L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("same agent, scope and key");
		verify(mapper, never()).markSuperseded(anyLong());
	}

	private MemoryItem item(MemoryScopeType scopeType) {
		return MemoryItem.builder()
			.id(10L)
			.scopeType(scopeType)
			.agentId(7)
			.memoryKind(MemoryKind.PREFERENCE)
			.memoryKey("currency")
			.valueJson("\"CNY\"")
			.build();
	}

}
