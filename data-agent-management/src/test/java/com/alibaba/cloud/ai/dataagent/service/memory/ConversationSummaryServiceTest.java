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

import com.alibaba.cloud.ai.dataagent.entity.ConversationSummary;
import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationSummaryMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationSummaryServiceTest {

	@Mock
	private ConversationTurnMapper turnMapper;

	@Mock
	private ConversationSummaryMapper summaryMapper;

	@Mock
	private ChatSessionMapper chatSessionMapper;

	private ConversationSummaryService service;

	@BeforeEach
	void setUp() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getMemory().setRecentTurns(1);
		service = new ConversationSummaryService(turnMapper, summaryMapper, chatSessionMapper, properties);
	}

	@Test
	void summaryIsDeterministicallyRebuiltFromTurnsOutsideRecentWindow() {
		when(turnMapper.selectAllSuccessful("conversation-1")).thenReturn(List.of(
				turn("turn-1", "raw one", "canonical one", "result one"),
				turn("turn-2", "raw two", null, "result two")));

		service.rebuild("conversation-1");

		ArgumentCaptor<ConversationSummary> captor = ArgumentCaptor.forClass(ConversationSummary.class);
		verify(summaryMapper).insert(captor.capture());
		assertThat(captor.getValue().getSummaryText()).contains("canonical one", "result one")
			.doesNotContain("raw two", "result two");
		assertThat(captor.getValue().getCoveredThroughTurnId()).isEqualTo("turn-1");
	}

	@Test
	void boundedSummaryKeepsTheNewestHistoricalTurnsAndMakesOmissionExplicit() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getMemory().setRecentTurns(1);
		properties.getMemory().setMaxSummaryLength(500);
		service = new ConversationSummaryService(turnMapper, summaryMapper, chatSessionMapper, properties);
		when(turnMapper.selectAllSuccessful("conversation-1")).thenReturn(
				List.of(turn("turn-1", "oldest", null, "a".repeat(700)), turn("turn-2", "older", null, "b".repeat(700)),
						turn("turn-3", "newer historical", null, "c".repeat(700)),
						turn("turn-4", "latest", null, "latest result")));

		service.rebuild("conversation-1");

		ArgumentCaptor<ConversationSummary> captor = ArgumentCaptor.forClass(ConversationSummary.class);
		verify(summaryMapper).insert(captor.capture());
		assertThat(captor.getValue().getSummaryText()).contains("newer historical", "较早历史已因上下文预算省略")
			.doesNotContain("oldest");
		assertThat(captor.getValue().getCoveredThroughTurnId()).isEqualTo("turn-3");
	}

	@Test
	void concurrentSummaryInsertFallsBackToUpdatingTheWinningRow() {
		when(turnMapper.selectAllSuccessful("conversation-1")).thenReturn(List.of(
				turn("turn-1", "raw one", "canonical one", "result one"),
				turn("turn-2", "raw two", null, "result two")));
		doThrow(new org.springframework.dao.DuplicateKeyException("concurrent insert"))
			.when(summaryMapper)
			.insert(any(ConversationSummary.class));

		service.rebuild("conversation-1");

		verify(summaryMapper).update(argThat(summary -> "turn-1".equals(summary.getCoveredThroughTurnId())));
	}

	private ConversationTurn turn(String id, String rawQuery, String canonicalQuery, String result) {
		return ConversationTurn.builder()
			.id(id)
			.rawQuery(rawQuery)
			.canonicalQuery(canonicalQuery)
			.resultSummary(result)
			.build();
	}

}
