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
package com.alibaba.cloud.ai.dataagent.service.graph;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.MultiTurnContextManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MultiTurnContextManagerTest {

	@Mock
	private DataAgentProperties properties;

	private MultiTurnContextManager contextManager;

	@BeforeEach
	void setUp() {
		when(properties.getMaxturnhistory()).thenReturn(5);
		when(properties.getMaxplanlength()).thenReturn(2000);
		contextManager = new MultiTurnContextManager(properties);
	}

	@Test
	void beginTurn_newThread_createsPending() {
		contextManager.beginTurn("thread-1", "What is the total revenue?");
		contextManager.appendPlannerChunk("thread-1", "Plan: query revenue");
		contextManager.finishTurn("thread-1");

		String context = contextManager.buildContext("thread-1");
		assertTrue(context.contains("What is the total revenue?"));
		assertTrue(context.contains("Plan: query revenue"));
	}

	@Test
	void beginTurn_blankThread_doesNothing() {
		contextManager.beginTurn("", "question");
		contextManager.beginTurn(null, "question");
		contextManager.beginTurn("thread-1", "");
		contextManager.beginTurn("thread-1", null);

		String context = contextManager.buildContext("thread-1");
		assertEquals("(无)", context);
	}

	@Test
	void finishTurn_completesAndStoresInHistory() {
		contextManager.beginTurn("thread-1", "Query 1");
		contextManager.appendPlannerChunk("thread-1", "Plan A");
		contextManager.finishTurn("thread-1");

		String context = contextManager.buildContext("thread-1");
		assertTrue(context.contains("Query 1"));
		assertTrue(context.contains("Plan A"));
	}

	@Test
	void finishTurn_noPlannerOutput_skipsHistory() {
		contextManager.beginTurn("thread-1", "Query 1");
		contextManager.finishTurn("thread-1");

		String context = contextManager.buildContext("thread-1");
		assertEquals("(无)", context);
	}

	@Test
	void discardPending_removesPendingTurn() {
		contextManager.beginTurn("thread-1", "Query 1");
		contextManager.appendPlannerChunk("thread-1", "Plan");
		contextManager.discardPending("thread-1");
		contextManager.finishTurn("thread-1");

		String context = contextManager.buildContext("thread-1");
		assertEquals("(无)", context);
	}

	@Test
	void restartLastTurn_restartsFromLastTurn() {
		contextManager.beginTurn("thread-1", "Query 1");
		contextManager.appendPlannerChunk("thread-1", "Plan A");
		contextManager.finishTurn("thread-1");

		contextManager.restartLastTurn("thread-1");
		contextManager.appendPlannerChunk("thread-1", "Plan B revised");
		contextManager.finishTurn("thread-1");

		String context = contextManager.buildContext("thread-1");
		assertFalse(context.contains("Plan A"));
		assertTrue(context.contains("Plan B revised"));
	}

	@Test
	void restartLastTurn_noHistory_doesNothing() {
		contextManager.restartLastTurn("thread-1");

		String context = contextManager.buildContext("thread-1");
		assertEquals("(无)", context);
	}

	@Test
	void buildContext_noHistory_returnsEmpty() {
		String context = contextManager.buildContext("nonexistent-thread");
		assertEquals("(无)", context);
	}

	@Test
	void buildContext_withHistory_formatsCorrectly() {
		contextManager.beginTurn("thread-1", "Question A");
		contextManager.appendPlannerChunk("thread-1", "Plan A");
		contextManager.finishTurn("thread-1");

		contextManager.beginTurn("thread-1", "Question B");
		contextManager.appendPlannerChunk("thread-1", "Plan B");
		contextManager.finishTurn("thread-1");

		String context = contextManager.buildContext("thread-1");
		assertTrue(context.contains("用户: Question A"));
		assertTrue(context.contains("AI计划: Plan A"));
		assertTrue(context.contains("用户: Question B"));
		assertTrue(context.contains("AI计划: Plan B"));
	}

	@Test
	void buildContext_exceedsMaxTurns_truncatesOldest() {
		when(properties.getMaxturnhistory()).thenReturn(2);
		MultiTurnContextManager mgr = new MultiTurnContextManager(properties);

		for (int i = 1; i <= 3; i++) {
			mgr.beginTurn("thread-1", "Q" + i);
			mgr.appendPlannerChunk("thread-1", "P" + i);
			mgr.finishTurn("thread-1");
		}

		String context = mgr.buildContext("thread-1");
		assertFalse(context.contains("Q1"));
		assertTrue(context.contains("Q2"));
		assertTrue(context.contains("Q3"));
	}

	@Test
	void multipleThreads_isolateContextCorrectly() {
		contextManager.beginTurn("thread-A", "Question A");
		contextManager.appendPlannerChunk("thread-A", "Plan for A");
		contextManager.finishTurn("thread-A");

		contextManager.beginTurn("thread-B", "Question B");
		contextManager.appendPlannerChunk("thread-B", "Plan for B");
		contextManager.finishTurn("thread-B");

		String threadAContext = contextManager.buildContext("thread-A");
		String threadBContext = contextManager.buildContext("thread-B");

		assertTrue(threadAContext.contains("Question A"));
		assertFalse(threadAContext.contains("Question B"));
		assertTrue(threadBContext.contains("Question B"));
		assertFalse(threadBContext.contains("Question A"));
	}

	@Test
	void appendPlannerChunk_validThread_appendsToBuilder() {
		contextManager.beginTurn("thread-1", "Q1");
		contextManager.appendPlannerChunk("thread-1", "chunk1 ");
		contextManager.appendPlannerChunk("thread-1", "chunk2 ");
		contextManager.appendPlannerChunk("thread-1", "chunk3");
		contextManager.finishTurn("thread-1");

		String context = contextManager.buildContext("thread-1");
		assertTrue(context.contains("chunk1 chunk2 chunk3"));
	}

	@Test
	void appendPlannerChunk_noPending_ignores() {
		contextManager.appendPlannerChunk("thread-1", "orphan chunk");
		String context = contextManager.buildContext("thread-1");
		assertEquals("(无)", context);
	}

	@Test
	void appendPlannerChunk_blankInputs_ignores() {
		contextManager.beginTurn("thread-1", "Q1");
		contextManager.appendPlannerChunk("", "chunk");
		contextManager.appendPlannerChunk("thread-1", "");
		contextManager.appendPlannerChunk(null, "chunk");
		contextManager.appendPlannerChunk("thread-1", null);
		contextManager.finishTurn("thread-1");

		String context = contextManager.buildContext("thread-1");
		assertEquals("(无)", context);
	}

}
