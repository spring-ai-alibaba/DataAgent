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
package com.alibaba.cloud.ai.dataagent.service.memory.semantic;

import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EpisodicMemoryServiceTest {

	@Mock
	private ConversationTurnMapper turnMapper;

	@Mock
	private MemoryVectorIndexService vectorIndexService;

	private EpisodicMemoryService service;

	@BeforeEach
	void setUp() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getMemory().setEpisodicTopK(3);
		service = new EpisodicMemoryService(turnMapper, vectorIndexService, properties);
	}

	@Test
	void semanticCandidatesAreRevalidatedAgainstOwnerAgentDatasourceAndConversation() {
		when(vectorIndexService.recallTurnIds("sales", 99L, 7, 3, 3))
			.thenReturn(List.of("wrong-owner", "wrong-agent", "wrong-datasource", "current", "valid"));
		when(turnMapper.selectSuccessfulByIds(anyList()))
			.thenReturn(List.of(turn("wrong-owner", "other", 100L, 7, 3),
					turn("wrong-agent", "other", 99L, 8, 3),
					turn("wrong-datasource", "other", 99L, 7, 4),
					turn("current", "conversation-1", 99L, 7, 3),
					turn("valid", "conversation-2", 99L, 7, 3)));

		List<ConversationTurn> recalled = service.recallRelevant("sales", 99L, 7, 3, "conversation-1");

		assertThat(recalled).extracting(ConversationTurn::getId).containsExactly("valid");
		verify(turnMapper).selectRecentSuccessfulByOwner(99L, 7, 3, "conversation-1", 3);
	}

	@Test
	void validSemanticCandidatesKeepVectorRankingAndRelationalFallbackFillsTheWindow() {
		when(vectorIndexService.recallTurnIds("sales", 99L, 7, 3, 3))
			.thenReturn(List.of("semantic-second", "stale", "semantic-first"));
		// Deliberately return rows in a different order: an SQL IN predicate does not
		// carry the vector ranking.
		when(turnMapper.selectSuccessfulByIds(anyList()))
			.thenReturn(List.of(turn("semantic-first", "conversation-2", 99L, 7, 3),
					turn("semantic-second", "conversation-3", 99L, 7, 3)));
		when(turnMapper.selectRecentSuccessfulByOwner(99L, 7, 3, "conversation-1", 3))
			.thenReturn(List.of(turn("semantic-first", "conversation-2", 99L, 7, 3),
					turn("recent", "conversation-4", 99L, 7, 3)));

		List<ConversationTurn> recalled = service.recallRelevant("sales", 99L, 7, 3, "conversation-1");

		assertThat(recalled).extracting(ConversationTurn::getId)
			.containsExactly("semantic-second", "semantic-first", "recent");
	}

	@Test
	void staleSemanticCandidatesFallBackToRecentRelationalTurns() {
		when(vectorIndexService.recallTurnIds("sales", 99L, 7, 3, 3)).thenReturn(List.of("stale"));
		when(turnMapper.selectSuccessfulByIds(List.of("stale"))).thenReturn(List.of());
		when(turnMapper.selectRecentSuccessfulByOwner(99L, 7, 3, "conversation-1", 3))
			.thenReturn(List.of(turn("recent", "conversation-2", 99L, 7, 3)));

		List<ConversationTurn> recalled = service.recallRelevant("sales", 99L, 7, 3, "conversation-1");

		assertThat(recalled).extracting(ConversationTurn::getId).containsExactly("recent");
	}

	@Test
	void relationalRecentTurnsAreUsedWhenVectorSearchReturnsNoCandidates() {
		when(vectorIndexService.recallTurnIds("sales", 99L, 7, 3, 3)).thenReturn(List.of());
		when(turnMapper.selectRecentSuccessfulByOwner(99L, 7, 3, "conversation-1", 3))
			.thenReturn(List.of(turn("recent", "conversation-2", 99L, 7, 3)));

		List<ConversationTurn> recalled = service.recallRelevant("sales", 99L, 7, 3, "conversation-1");

		assertThat(recalled).extracting(ConversationTurn::getId).containsExactly("recent");
	}

	@Test
	void ownerlessRequestsNeverAttemptCrossConversationRecall() {
		assertThat(service.recallRelevant("sales", null, 7, 3, "conversation-1")).isEmpty();
		assertThat(service.recallRelevant("sales", 99L, null, 3, "conversation-1")).isEmpty();
		assertThat(service.recallRelevant("sales", 99L, 7, null, "conversation-1")).isEmpty();

		verifyNoInteractions(turnMapper, vectorIndexService);
	}

	private ConversationTurn turn(String id, String conversationId, Long ownerId, Integer agentId,
			Integer datasourceId) {
		return ConversationTurn.builder()
			.id(id)
			.conversationId(conversationId)
			.ownerId(ownerId)
			.agentId(agentId)
			.datasourceId(datasourceId)
			.build();
	}

}
