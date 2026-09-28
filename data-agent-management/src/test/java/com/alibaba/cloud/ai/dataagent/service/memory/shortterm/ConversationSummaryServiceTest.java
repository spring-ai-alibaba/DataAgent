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
package com.alibaba.cloud.ai.dataagent.service.memory.shortterm;

import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.alibaba.cloud.ai.graph.store.stores.MemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationSummaryServiceTest {

	@Mock
	private ConversationTurnMapper turnMapper;

	@Mock
	private ChatSessionMapper chatSessionMapper;

	private Store store;

	private DataAgentProperties properties;

	private ConversationSummaryService service;

	@BeforeEach
	void setUp() {
		properties = new DataAgentProperties();
		properties.getMemory().setRecentTurns(1);
		store = new MemoryStore();
		service = new ConversationSummaryService(turnMapper, chatSessionMapper, properties, store);
	}

	@Test
	void summaryIsDeterministicallyRebuiltFromTurnsOutsideRecentWindow() {
		stubActiveConversation();
		when(turnMapper.selectContextTimeline("conversation-1"))
			.thenReturn(List.of(turn("turn-1", "raw one", "canonical one", "result one"),
					turn("turn-2", "raw two", null, "result two")));
		when(turnMapper.selectSummaryBoundaryTurnId("conversation-1", 1)).thenReturn("turn-1");

		service.rebuild("conversation-1");

		ConversationSummaryService.Summary summary = service.load("conversation-1");
		assertThat(summary.summaryText()).contains("canonical one", "result one")
			.doesNotContain("raw two", "result two");
		assertThat(summary.coveredThroughTurnId()).isEqualTo("turn-1");
		assertThat(store.size()).isEqualTo(1);
	}

	@Test
	void boundedSummaryKeepsTheNewestHistoricalTurnsAndMakesOmissionExplicit() {
		stubActiveConversation();
		DataAgentProperties properties = new DataAgentProperties();
		properties.getMemory().setRecentTurns(1);
		properties.getMemory().setMaxSummaryLength(500);
		service = new ConversationSummaryService(turnMapper, chatSessionMapper, properties, store);
		when(turnMapper.selectContextTimeline("conversation-1")).thenReturn(
				List.of(turn("turn-1", "oldest", null, "a".repeat(700)), turn("turn-2", "older", null, "b".repeat(700)),
						turn("turn-3", "newer historical", null, "c".repeat(700)),
						turn("turn-4", "latest", null, "latest result")));
		when(turnMapper.selectSummaryBoundaryTurnId("conversation-1", 1)).thenReturn("turn-3");

		service.rebuild("conversation-1");

		ConversationSummaryService.Summary summary = service.load("conversation-1");
		assertThat(summary.summaryText()).contains("newer historical", "较早历史已因上下文预算省略").doesNotContain("oldest");
		assertThat(summary.coveredThroughTurnId()).isEqualTo("turn-3");
	}

	@Test
	void summaryUsesFrameworkStoreUpsertAndCanBeDeletedWhenNoHistoryNeedsSummarizing() {
		stubActiveConversation();
		store.putItem(StoreItem.of(ConversationSummaryService.summaryNamespace("conversation-1"),
				ConversationSummaryService.SUMMARY_KEY,
				Map.of("summaryText", "stale", "coveredThroughTurnId", "turn-0")));
		when(turnMapper.selectContextTimeline("conversation-1"))
			.thenReturn(List.of(turn("turn-1", "latest", null, "latest result")));

		service.rebuild("conversation-1");

		assertThat(service.load("conversation-1")).isNull();
		assertThat(store.isEmpty()).isTrue();
	}

	@Test
	void nonVerifiedFinalTurnsAdvanceTheWindowWithoutEnteringVerifiedSummary() {
		stubActiveConversation();
		ConversationTurn unverified = turn("turn-2", "configuration help", null, "not verified");
		unverified.setMemoryEligible(false);
		when(turnMapper.selectContextTimeline("conversation-1"))
			.thenReturn(List.of(turn("turn-1", "verified old", null, "verified result"), unverified,
					turn("turn-3", "latest", null, "latest result")));
		when(turnMapper.selectSummaryBoundaryTurnId("conversation-1", 1)).thenReturn("turn-2");

		service.rebuild("conversation-1");

		ConversationSummaryService.Summary summary = service.load("conversation-1");
		assertThat(summary.summaryText()).contains("verified old").doesNotContain("configuration help", "not verified");
		assertThat(summary.coveredThroughTurnId()).isEqualTo("turn-2");
	}

	@Test
	void delayedCompletionCannotRecreateSummaryForDeletedConversation() {
		store.putItem(StoreItem.of(ConversationSummaryService.summaryNamespace("conversation-1"),
				ConversationSummaryService.SUMMARY_KEY,
				Map.of("summaryText", "stale", "coveredThroughTurnId", "turn-0")));
		when(chatSessionMapper.lockBySessionId("conversation-1")).thenReturn(null);

		service.rebuild("conversation-1");

		assertThat(store.isEmpty()).isTrue();
		verifyNoInteractions(turnMapper);
	}

	@Test
	void unreadableSummaryProjectionCannotBreakContextAssembly() {
		Store unavailableStore = mock(Store.class);
		when(unavailableStore.getItem(ConversationSummaryService.summaryNamespace("conversation-1"),
				ConversationSummaryService.SUMMARY_KEY))
			.thenThrow(new IllegalStateException("corrupt projection"));
		ConversationSummaryService unavailableService = new ConversationSummaryService(turnMapper, chatSessionMapper,
				new DataAgentProperties(), unavailableStore);

		assertThat(unavailableService.load("conversation-1")).isNull();
	}

	@Test
	void missingNodeLocalProjectionIsRebuiltFromDurableTurnsOnRead() {
		when(turnMapper.selectSummaryBoundaryTurnId("conversation-1", 1)).thenReturn("turn-1");
		when(turnMapper.selectContextTimeline("conversation-1")).thenReturn(List.of(
				turn("turn-1", "historical", null, "verified result"),
				turn("turn-2", "latest", null, "latest result")));

		ConversationSummaryService.Summary summary = service.load("conversation-1");

		assertThat(summary.coveredThroughTurnId()).isEqualTo("turn-1");
		assertThat(summary.summaryText()).contains("historical", "verified result");
		assertThat(store.size()).isEqualTo(1);
	}

	@Test
	void staleProjectionIsRefreshedEvenBeforeTheOutboxWorkerRuns() {
		store.putItem(StoreItem.of(ConversationSummaryService.summaryNamespace("conversation-1"),
				ConversationSummaryService.SUMMARY_KEY,
				Map.of("summaryText", "stale", "coveredThroughTurnId", "turn-0")));
		when(turnMapper.selectSummaryBoundaryTurnId("conversation-1", 1)).thenReturn("turn-2");
		when(turnMapper.selectContextTimeline("conversation-1")).thenReturn(
				List.of(turn("turn-1", "old", null, "old result"), turn("turn-2", "new historical", null, "new result"),
						turn("turn-3", "latest", null, "latest result")));

		ConversationSummaryService.Summary summary = service.load("conversation-1");

		assertThat(summary.coveredThroughTurnId()).isEqualTo("turn-2");
		assertThat(summary.summaryText()).contains("new historical").doesNotContain("stale");
	}

	@Test
	void matchingBoundaryUsesTheFrameworkProjectionWithoutScanningTheTimeline() {
		store.putItem(StoreItem.of(ConversationSummaryService.summaryNamespace("conversation-1"),
				ConversationSummaryService.SUMMARY_KEY,
				Map.of("summaryText", "current", "coveredThroughTurnId", "turn-2")));
		when(turnMapper.selectSummaryBoundaryTurnId("conversation-1", 1)).thenReturn("turn-2");

		assertThat(service.load("conversation-1").summaryText()).isEqualTo("current");
		verify(turnMapper, never()).selectContextTimeline("conversation-1");
	}

	@Test
	void nodeLocalProjectionCacheIsBounded() {
		properties.getMemory().setSummaryCacheMaxEntries(2);
		when(chatSessionMapper.lockBySessionId(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
		when(turnMapper.selectContextTimeline(anyString())).thenAnswer(invocation -> {
			String conversationId = invocation.getArgument(0);
			return List.of(turn(conversationId + "-old", "historical", null, "result"),
					turn(conversationId + "-new", "latest", null, "latest result"));
		});

		service.rebuild("conversation-1");
		service.rebuild("conversation-2");
		service.rebuild("conversation-3");

		assertThat(store.size()).isEqualTo(2);
	}

	private void stubActiveConversation() {
		when(chatSessionMapper.lockBySessionId("conversation-1")).thenReturn("conversation-1");
	}

	private ConversationTurn turn(String id, String rawQuery, String canonicalQuery, String result) {
		return ConversationTurn.builder()
			.id(id)
			.rawQuery(rawQuery)
			.canonicalQuery(canonicalQuery)
			.resultSummary(result)
			.memoryEligible(true)
			.build();
	}

}
