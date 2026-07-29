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

	private ConversationSummaryService service;

	@BeforeEach
	void setUp() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getMemory().setRecentTurns(1);
		store = new MemoryStore();
		service = new ConversationSummaryService(turnMapper, chatSessionMapper, properties, store);
	}

	@Test
	void summaryIsDeterministicallyRebuiltFromTurnsOutsideRecentWindow() {
		when(turnMapper.selectAllSuccessful("conversation-1")).thenReturn(List.of(
				turn("turn-1", "raw one", "canonical one", "result one"),
				turn("turn-2", "raw two", null, "result two")));

		service.rebuild("conversation-1");

		ConversationSummaryService.Summary summary = service.load("conversation-1");
		assertThat(summary.summaryText()).contains("canonical one", "result one")
			.doesNotContain("raw two", "result two");
		assertThat(summary.coveredThroughTurnId()).isEqualTo("turn-1");
		assertThat(store.size()).isEqualTo(1);
	}

	@Test
	void boundedSummaryKeepsTheNewestHistoricalTurnsAndMakesOmissionExplicit() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getMemory().setRecentTurns(1);
		properties.getMemory().setMaxSummaryLength(500);
		service = new ConversationSummaryService(turnMapper, chatSessionMapper, properties, store);
		when(turnMapper.selectAllSuccessful("conversation-1")).thenReturn(
				List.of(turn("turn-1", "oldest", null, "a".repeat(700)), turn("turn-2", "older", null, "b".repeat(700)),
						turn("turn-3", "newer historical", null, "c".repeat(700)),
						turn("turn-4", "latest", null, "latest result")));

		service.rebuild("conversation-1");

		ConversationSummaryService.Summary summary = service.load("conversation-1");
		assertThat(summary.summaryText()).contains("newer historical", "较早历史已因上下文预算省略")
			.doesNotContain("oldest");
		assertThat(summary.coveredThroughTurnId()).isEqualTo("turn-3");
	}

	@Test
	void summaryUsesFrameworkStoreUpsertAndCanBeDeletedWhenNoHistoryNeedsSummarizing() {
		store.putItem(StoreItem.of(ConversationSummaryService.summaryNamespace("conversation-1"),
				ConversationSummaryService.SUMMARY_KEY,
				Map.of("summaryText", "stale", "coveredThroughTurnId", "turn-0")));
		when(turnMapper.selectAllSuccessful("conversation-1"))
			.thenReturn(List.of(turn("turn-1", "latest", null, "latest result")));

		service.rebuild("conversation-1");

		assertThat(service.load("conversation-1")).isNull();
		assertThat(store.isEmpty()).isTrue();
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
