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
package com.alibaba.cloud.ai.dataagent.service.memory.longterm;

import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.entity.MemoryItem;
import com.alibaba.cloud.ai.dataagent.enums.*;
import com.alibaba.cloud.ai.dataagent.exception.MemoryConflictException;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.mapper.DatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.MemoryItemMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryEventType;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryOutboxService;
import com.alibaba.cloud.ai.dataagent.service.memory.semantic.MemoryVectorIndexService;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

	@Mock
	private DatasourceMapper datasourceMapper;

	@Mock
	private SchemaService schemaService;

	private DataAgentProperties properties;

	private LongTermMemoryService service;

	@BeforeEach
	void setUp() {
		properties = new DataAgentProperties();
		service = new LongTermMemoryService(mapper, outboxService, vectorIndexService, properties, turnMapper,
				agentDatasourceMapper, datasourceMapper, schemaService);
	}

	@Test
	void candidateIsNotPromptVisibleUntilExplicitConfirmation() {
		MemoryItem candidate = item(MemoryScopeType.AGENT);
		when(mapper.selectByIdForUpdate(10L)).thenReturn(candidate);
		when(mapper.confirmCandidate(10L)).thenReturn(1);
		when(mapper.selectById(10L)).thenReturn(candidate);

		service.createCandidate(candidate);
		service.confirm(10L);

		verify(mapper).insert(argThat(item -> item.getStatus() == MemoryStatus.CANDIDATE
				&& item.getIdentityHash() != null && item.getActiveIdentityHash() == null));
		verify(mapper).confirmCandidate(10L);
		verify(outboxService).enqueue("MEMORY_ITEM", "10", MemoryEventType.MEMORY_CONFIRMED, null);
	}

	@Test
	void confirmationRejectsAnotherActiveMemoryWithTheSameIdentity() {
		MemoryItem candidate = item(MemoryScopeType.AGENT);
		MemoryItem active = item(MemoryScopeType.AGENT);
		active.setId(9L);
		active.setStatus(MemoryStatus.CONFIRMED);
		service.createCandidate(candidate);
		when(mapper.selectByIdForUpdate(10L)).thenReturn(candidate);
		when(mapper.selectConfirmedByIdentityHashForUpdate(candidate.getIdentityHash())).thenReturn(active);

		assertThatThrownBy(() -> service.confirm(10L)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("already exists");
		verify(mapper, never()).confirmCandidate(anyLong());
	}

	@Test
	void expiredActiveMemoryIsInvalidatedBeforeConfirmingReplacement() {
		MemoryItem candidate = item(MemoryScopeType.AGENT);
		MemoryItem expired = item(MemoryScopeType.AGENT);
		expired.setId(9L);
		expired.setStatus(MemoryStatus.CONFIRMED);
		expired.setValidUntil(LocalDateTime.now().minusMinutes(1));
		service.createCandidate(candidate);
		when(mapper.selectByIdForUpdate(10L)).thenReturn(candidate);
		when(mapper.selectConfirmedByIdentityHashForUpdate(candidate.getIdentityHash())).thenReturn(expired);
		when(mapper.invalidate(9L)).thenReturn(1);
		when(mapper.confirmCandidate(10L)).thenReturn(1);
		when(mapper.selectById(10L)).thenReturn(candidate);

		service.confirm(10L);

		verify(mapper).invalidate(9L);
		verify(outboxService).enqueue("MEMORY_ITEM", "9", MemoryEventType.MEMORY_INVALIDATED, null);
		verify(mapper).confirmCandidate(10L);
	}

	@Test
	void staleSchemaMemoryNoLongerBlocksAReviewedReplacement() {
		MemoryItem candidate = item(MemoryScopeType.DATASOURCE);
		candidate.setDatasourceId(3);
		candidate.setMemoryKind(MemoryKind.CORRECTION);
		candidate.setSchemaFingerprint("schema-v2");
		MemoryItem stale = item(MemoryScopeType.DATASOURCE);
		stale.setId(9L);
		stale.setDatasourceId(3);
		stale.setMemoryKind(MemoryKind.CORRECTION);
		stale.setSchemaFingerprint("schema-v1");
		stale.setStatus(MemoryStatus.CONFIRMED);
		when(mapper.selectByIdForUpdate(10L)).thenReturn(candidate);
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(7L, 3))
			.thenReturn(new com.alibaba.cloud.ai.dataagent.entity.AgentDatasource(7L, 3));
		when(datasourceMapper.selectByIdForUpdate(3))
			.thenReturn(com.alibaba.cloud.ai.dataagent.entity.Datasource.builder().schemaRevision("schema-v2").build());
		when(mapper.selectConfirmedByIdentityHashForUpdate(anyString())).thenReturn(stale);
		when(mapper.invalidate(9L)).thenReturn(1);
		when(mapper.confirmCandidate(10L)).thenReturn(1);
		when(mapper.selectById(10L)).thenReturn(candidate);

		service.confirm(10L);

		verify(mapper).invalidate(9L);
		verify(outboxService).enqueue("MEMORY_ITEM", "9", MemoryEventType.MEMORY_INVALIDATED, null);
		verify(mapper).confirmCandidate(10L);
	}

	@Test
	void explicitReplacementAtomicallySupersedesTheCurrentValue() {
		MemoryItem candidate = item(MemoryScopeType.AGENT);
		candidate.setSupersedesId(9L);
		MemoryItem current = item(MemoryScopeType.AGENT);
		current.setId(9L);
		current.setStatus(MemoryStatus.CONFIRMED);
		service.createCandidate(candidate);
		when(mapper.selectByIdForUpdate(10L)).thenReturn(candidate);
		when(mapper.selectConfirmedByIdentityHashForUpdate(candidate.getIdentityHash())).thenReturn(current);
		when(mapper.selectByIdForUpdate(9L)).thenReturn(current);
		when(mapper.markSuperseded(9L)).thenReturn(1);
		when(mapper.confirmCandidate(10L)).thenReturn(1);
		when(mapper.selectById(10L)).thenReturn(candidate);

		service.confirm(10L);

		verify(mapper).markSuperseded(9L);
		verify(mapper).confirmCandidate(10L);
		verify(outboxService).enqueue("MEMORY_ITEM", "9", MemoryEventType.MEMORY_INVALIDATED, null);
		verify(outboxService).enqueue("MEMORY_ITEM", "10", MemoryEventType.MEMORY_CONFIRMED, null);
	}

	@Test
	void userScopeIsRejectedWithoutTrustedServerIdentityFeature() {
		MemoryItem item = item(MemoryScopeType.USER_AGENT);

		assertThatThrownBy(() -> service.createCandidate(item)).isInstanceOf(IllegalArgumentException.class)
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
		when(turnMapper.selectById("turn-1")).thenReturn(ConversationTurn.builder()
			.id("turn-1")
			.agentId(8)
			.status(TurnStatus.SUCCEEDED)
			.memoryEligible(true)
			.build());

		assertThatThrownBy(() -> service.createCandidate(item)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("successful turn of the same agent");
	}

	@Test
	void schemaSensitiveDatasourceMemoryUsesCurrentServerRevision() {
		MemoryItem item = item(MemoryScopeType.DATASOURCE);
		item.setMemoryKind(MemoryKind.CORRECTION);
		item.setDatasourceId(3);
		item.setSourceTurnId("turn-1");
		item.setSchemaFingerprint("forged-client-revision");
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(7L, 3))
			.thenReturn(new com.alibaba.cloud.ai.dataagent.entity.AgentDatasource(7L, 3));
		when(schemaService.getSchemaRevision(3)).thenReturn("schema-v1");
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
	void schemaSensitiveDatasourceMemoryRejectsStaleSourceTurn() {
		MemoryItem item = item(MemoryScopeType.DATASOURCE);
		item.setMemoryKind(MemoryKind.QUERY_PATTERN);
		item.setDatasourceId(3);
		item.setSourceTurnId("turn-1");
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(7L, 3))
			.thenReturn(new com.alibaba.cloud.ai.dataagent.entity.AgentDatasource(7L, 3));
		when(schemaService.getSchemaRevision(3)).thenReturn("schema-v2");
		when(turnMapper.selectById("turn-1")).thenReturn(ConversationTurn.builder()
			.id("turn-1")
			.agentId(7)
			.datasourceId(3)
			.schemaFingerprint("schema-v1")
			.status(TurnStatus.SUCCEEDED)
			.memoryEligible(true)
			.build());

		assertThatThrownBy(() -> service.createCandidate(item)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("no longer current");
	}

	@Test
	void schemaSensitiveDatasourceMemoryRejectsSourceTurnWithoutSchemaRevision() {
		MemoryItem item = item(MemoryScopeType.DATASOURCE);
		item.setMemoryKind(MemoryKind.CORRECTION);
		item.setDatasourceId(3);
		item.setSourceTurnId("turn-1");
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(7L, 3))
			.thenReturn(new com.alibaba.cloud.ai.dataagent.entity.AgentDatasource(7L, 3));
		when(schemaService.getSchemaRevision(3)).thenReturn("schema-v2");
		when(turnMapper.selectById("turn-1")).thenReturn(ConversationTurn.builder()
			.id("turn-1")
			.agentId(7)
			.datasourceId(3)
			.status(TurnStatus.SUCCEEDED)
			.memoryEligible(true)
			.build());

		assertThatThrownBy(() -> service.createCandidate(item)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("no longer current");
	}

	@Test
	void datasourcePreferenceDoesNotCarrySchemaRevision() {
		MemoryItem item = item(MemoryScopeType.DATASOURCE);
		item.setDatasourceId(3);
		item.setSchemaFingerprint("forged-client-revision");
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(7L, 3))
			.thenReturn(new com.alibaba.cloud.ai.dataagent.entity.AgentDatasource(7L, 3));

		service.createCandidate(item);

		verify(mapper).insert(argThat(candidate -> candidate.getSchemaFingerprint() == null));
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

		assertThatThrownBy(() -> service.createCandidate(item)).isInstanceOf(IllegalArgumentException.class)
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
		when(mapper.selectByIdForUpdate(10L)).thenReturn(candidate);
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(7L, 3))
			.thenReturn(new com.alibaba.cloud.ai.dataagent.entity.AgentDatasource(7L, 3));
		when(datasourceMapper.selectByIdForUpdate(3))
			.thenReturn(com.alibaba.cloud.ai.dataagent.entity.Datasource.builder().schemaRevision("schema-v1").build());
		when(mapper.selectByIdForUpdate(9L)).thenReturn(superseded);

		assertThatThrownBy(() -> service.confirm(10L)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("same agent, scope, kind and key");
		verify(mapper, never()).markSuperseded(anyLong());
	}

	@Test
	void agentScopeNormalizesIrrelevantOwnerAndDatasourceBeforeHashing() {
		MemoryItem candidate = item(MemoryScopeType.AGENT);
		candidate.setOwnerId(99L);
		candidate.setDatasourceId(3);
		candidate.setIdentityHash("forged-client-value");

		service.createCandidate(candidate);

		assertThat(candidate.getOwnerId()).isNull();
		assertThat(candidate.getDatasourceId()).isNull();
		assertThat(candidate.getIdentityHash()).hasSize(64).isNotEqualTo("forged-client-value");
	}

	@Test
	void semanticRecallPreservesVectorScoreOrderAfterRelationalValidation() {
		MemoryItem lowerScore = MemoryItem.builder().id(2L).scopeType(MemoryScopeType.AGENT).agentId(7).build();
		MemoryItem higherScore = MemoryItem.builder().id(3L).scopeType(MemoryScopeType.AGENT).agentId(7).build();
		when(mapper.selectConfirmedForContext(null, 7, null, null, 2)).thenReturn(List.of());
		when(vectorIndexService.recallMemoryItemIds("sales", null, 7, null, 2)).thenReturn(List.of(3L, 2L));
		when(mapper.selectConfirmedByIds(List.of(3L, 2L))).thenReturn(List.of(lowerScore, higherScore));

		assertThat(service.recallRelevant("sales", null, 7, null, 2)).extracting(MemoryItem::getId)
			.containsExactly(3L, 2L);
	}

	@Test
	void semanticRecallUsesRelevanceBeforeNonDatasourceFallback() {
		MemoryItem datasourceRule = MemoryItem.builder()
			.id(1L)
			.scopeType(MemoryScopeType.DATASOURCE)
			.agentId(7)
			.datasourceId(3)
			.memoryKind(MemoryKind.PREFERENCE)
			.build();
		MemoryItem unrelatedFallback = MemoryItem.builder().id(2L).scopeType(MemoryScopeType.AGENT).agentId(7).build();
		MemoryItem relevant = MemoryItem.builder().id(3L).scopeType(MemoryScopeType.AGENT).agentId(7).build();
		when(schemaService.getSchemaRevision(3)).thenReturn("schema-v1");
		when(mapper.selectConfirmedForContext(null, 7, 3, "schema-v1", 2))
			.thenReturn(List.of(datasourceRule, unrelatedFallback));
		when(vectorIndexService.recallMemoryItemIds("sales", null, 7, 3, 2)).thenReturn(List.of(3L));
		when(mapper.selectConfirmedByIds(List.of(3L))).thenReturn(List.of(relevant));

		assertThat(service.recallRelevant("sales", null, 7, 3, 2)).extracting(MemoryItem::getId)
			.containsExactly(1L, 3L);
	}

	@Test
	void schemaSensitiveCandidateCannotBeConfirmedAfterDatasourceRevisionChanges() {
		MemoryItem candidate = item(MemoryScopeType.DATASOURCE);
		candidate.setDatasourceId(3);
		candidate.setMemoryKind(MemoryKind.CORRECTION);
		candidate.setSchemaFingerprint("schema-v1");
		when(mapper.selectByIdForUpdate(10L)).thenReturn(candidate);
		when(agentDatasourceMapper.selectByAgentIdAndDatasourceId(7L, 3))
			.thenReturn(new com.alibaba.cloud.ai.dataagent.entity.AgentDatasource(7L, 3));
		when(datasourceMapper.selectByIdForUpdate(3))
			.thenReturn(com.alibaba.cloud.ai.dataagent.entity.Datasource.builder().schemaRevision("schema-v2").build());

		assertThatThrownBy(() -> service.confirm(10L)).isInstanceOf(MemoryConflictException.class)
			.hasMessageContaining("schema changed");
		verify(mapper, never()).confirmCandidate(anyLong());
	}

	@Test
	void candidateRejectsJsonNullAndUnboundedEmbeddingPayloads() {
		MemoryItem nullValue = item(MemoryScopeType.AGENT);
		nullValue.setValueJson("null");
		assertThatThrownBy(() -> service.createCandidate(nullValue)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("JSON null");

		MemoryItem oversized = item(MemoryScopeType.AGENT);
		oversized.setValueJson("x".repeat(16_001));
		assertThatThrownBy(() -> service.createCandidate(oversized)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("16000");
	}

	@Test
	void candidateMustRemainUnexpiredThroughConfirmation() {
		MemoryItem alreadyExpired = item(MemoryScopeType.AGENT);
		alreadyExpired.setValidUntil(LocalDateTime.now().minusMinutes(1));
		assertThatThrownBy(() -> service.createCandidate(alreadyExpired)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("future");

		MemoryItem expiresBeforeReview = item(MemoryScopeType.AGENT);
		expiresBeforeReview.setValidUntil(LocalDateTime.now().minusSeconds(1));
		when(mapper.selectByIdForUpdate(10L)).thenReturn(expiresBeforeReview);

		assertThatThrownBy(() -> service.confirm(10L)).isInstanceOf(MemoryConflictException.class)
			.hasMessageContaining("expired");
		verify(mapper, never()).confirmCandidate(anyLong());
	}

	@Test
	void deletingConversationTombstonesMemoryIndexesBeforeRowsAreRemoved() {
		when(mapper.selectByConversationId("conversation-1"))
			.thenReturn(List.of(MemoryItem.builder().id(9L).build(), MemoryItem.builder().id(10L).build()));

		service.deleteByConversation("conversation-1");

		verify(outboxService).enqueue("MEMORY_ITEM", "9", MemoryEventType.MEMORY_INVALIDATED, null);
		verify(outboxService).enqueue("MEMORY_ITEM", "10", MemoryEventType.MEMORY_INVALIDATED, null);
		verify(mapper).deleteByConversationId("conversation-1");
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
