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
package com.alibaba.cloud.ai.dataagent.service.chat;

import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionTitleServiceTest {

	private SessionTitleService service;

	@Mock
	private ChatSessionMapper chatSessionMapper;

	@Mock
	private SessionEventPublisher sessionEventPublisher;

	@Mock
	private LlmService llmService;

	private ExecutorService executorService;

	@BeforeEach
	void setUp() {
		executorService = Executors.newSingleThreadExecutor();
		service = new SessionTitleService(chatSessionMapper, sessionEventPublisher, llmService, executorService);
	}

	@Test
	void scheduleTitleGeneration_withBlankSessionId_doesNothing() {
		service.scheduleTitleGeneration("", "hello");
		service.scheduleTitleGeneration(null, "hello");

		verifyNoInteractions(chatSessionMapper);
	}

	@Test
	void scheduleTitleGeneration_withBlankMessage_doesNothing() {
		service.scheduleTitleGeneration("session-1", "");
		service.scheduleTitleGeneration("session-1", null);

		verifyNoInteractions(chatSessionMapper);
	}

	@Test
	void scheduleTitleGeneration_sessionNotFound_doesNotCallRename() throws Exception {
		when(chatSessionMapper.selectBySessionId("session-1")).thenReturn(null);

		service.scheduleTitleGeneration("session-1", "hello");
		executorService.shutdown();
		assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS), "the title task must actually finish");

		verify(chatSessionMapper).selectBySessionId("session-1");
		verify(chatSessionMapper, never()).updateTitle(anyString(), anyString(), any(LocalDateTime.class));
	}

	@Test
	void scheduleTitleGeneration_sessionHasCustomTitle_skips() throws Exception {
		ChatSession session = ChatSession.builder().id("session-1").agentId(1).title("Custom Title").build();
		when(chatSessionMapper.selectBySessionId("session-1")).thenReturn(session);

		service.scheduleTitleGeneration("session-1", "hello");
		executorService.shutdown();
		assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS), "the title task must actually finish");

		verify(chatSessionMapper, never()).updateTitle(anyString(), anyString(), any(LocalDateTime.class));
	}

	@Test
	void scheduleTitleGeneration_generatesAndPersistsTitle() throws Exception {
		ChatSession session = ChatSession.builder().id("session-1").agentId(1).title("\u65b0\u4f1a\u8bdd").build();
		when(chatSessionMapper.selectBySessionId("session-1")).thenReturn(session);
		when(chatSessionMapper.updateTitle(eq("session-1"), eq("Generated Title"), any(LocalDateTime.class)))
			.thenReturn(1);

		Flux<ChatResponse> chatResponseFlux = Flux.empty();
		when(llmService.call(anyString(), anyString())).thenReturn(chatResponseFlux);
		when(llmService.toStringFlux(chatResponseFlux)).thenReturn(Flux.just("Generated", " Title"));

		service.scheduleTitleGeneration("session-1", "hello world");
		executorService.shutdown();
		assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS), "the title task must actually finish");

		verify(chatSessionMapper).updateTitle(eq("session-1"), eq("Generated Title"), any(LocalDateTime.class));
		verify(sessionEventPublisher).publishTitleUpdated(eq(1), eq("session-1"), eq("Generated Title"));
	}

	@Test
	void scheduleTitleGeneration_duplicateSessionId_skipsSecondCall() throws Exception {
		ChatSession session = ChatSession.builder().id("session-1").agentId(1).title("\u65b0\u4f1a\u8bdd").build();
		CountDownLatch firstTaskStarted = new CountDownLatch(1);
		CountDownLatch allowFirstTaskToFinish = new CountDownLatch(1);
		when(chatSessionMapper.selectBySessionId("session-1")).thenAnswer(invocation -> {
			firstTaskStarted.countDown();
			if (!allowFirstTaskToFinish.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("timed out waiting to finish the first title task");
			}
			return session;
		});

		Flux<ChatResponse> chatResponseFlux = Flux.empty();
		when(llmService.call(anyString(), anyString())).thenReturn(chatResponseFlux);
		when(llmService.toStringFlux(chatResponseFlux)).thenReturn(Flux.just("Title"));
		when(chatSessionMapper.updateTitle(eq("session-1"), eq("Title"), any(LocalDateTime.class))).thenReturn(1);

		service.scheduleTitleGeneration("session-1", "hello");
		try {
			assertTrue(firstTaskStarted.await(5, TimeUnit.SECONDS), "the first title task must actually start");
			service.scheduleTitleGeneration("session-1", "hello again");
		}
		finally {
			allowFirstTaskToFinish.countDown();
		}
		executorService.shutdown();
		assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS), "the title task must actually finish");

		verify(chatSessionMapper, times(1)).selectBySessionId("session-1");
		verify(llmService, times(1)).call(anyString(), anyString());
		verify(chatSessionMapper, times(1)).updateTitle(eq("session-1"), eq("Title"), any(LocalDateTime.class));
		verify(sessionEventPublisher, times(1)).publishTitleUpdated(1, "session-1", "Title");
	}

	@Test
	void deletedSessionDuringGenerationIsNotPublished() throws Exception {
		ChatSession session = ChatSession.builder().id("session-1").agentId(1).title("\u65b0\u4f1a\u8bdd").build();
		when(chatSessionMapper.selectBySessionId("session-1")).thenReturn(session);
		Flux<ChatResponse> chatResponseFlux = Flux.empty();
		when(llmService.call(anyString(), anyString())).thenReturn(chatResponseFlux);
		when(llmService.toStringFlux(chatResponseFlux)).thenReturn(Flux.just("Title"));
		when(chatSessionMapper.updateTitle(eq("session-1"), eq("Title"), any(LocalDateTime.class))).thenReturn(0);

		service.scheduleTitleGeneration("session-1", "hello");
		executorService.shutdown();
		assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS), "the title task must actually finish");

		verifyNoInteractions(sessionEventPublisher);
	}

}
