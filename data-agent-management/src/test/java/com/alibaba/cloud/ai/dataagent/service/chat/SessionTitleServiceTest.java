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
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionTitleServiceTest {

	private SessionTitleService service;

	@Mock
	private ChatSessionService chatSessionService;

	@Mock
	private SessionEventPublisher sessionEventPublisher;

	@Mock
	private LlmService llmService;

	private ExecutorService executorService;

	@BeforeEach
	void setUp() {
		executorService = Executors.newSingleThreadExecutor();
		service = new SessionTitleService(chatSessionService, sessionEventPublisher, llmService, executorService);
	}

	@Test
	void scheduleTitleGeneration_withBlankSessionId_doesNothing() {
		service.scheduleTitleGeneration("", "hello");
		service.scheduleTitleGeneration(null, "hello");

		verifyNoInteractions(chatSessionService);
	}

	@Test
	void scheduleTitleGeneration_withBlankMessage_doesNothing() {
		service.scheduleTitleGeneration("session-1", "");
		service.scheduleTitleGeneration("session-1", null);

		verifyNoInteractions(chatSessionService);
	}

	@Test
	void scheduleTitleGeneration_sessionNotFound_doesNotCallRename() throws Exception {
		when(chatSessionService.findBySessionId("session-1")).thenReturn(null);

		service.scheduleTitleGeneration("session-1", "hello");
		executorService.shutdown();
		executorService.awaitTermination(5, TimeUnit.SECONDS);

		verify(chatSessionService).findBySessionId("session-1");
		verify(chatSessionService, never()).renameSession(anyString(), anyString());
	}

	@Test
	void scheduleTitleGeneration_sessionHasCustomTitle_skips() throws Exception {
		ChatSession session = ChatSession.builder().id("session-1").agentId(1).title("Custom Title").build();
		when(chatSessionService.findBySessionId("session-1")).thenReturn(session);

		service.scheduleTitleGeneration("session-1", "hello");
		executorService.shutdown();
		executorService.awaitTermination(5, TimeUnit.SECONDS);

		verify(chatSessionService, never()).renameSession(anyString(), anyString());
	}

	@Test
	void scheduleTitleGeneration_generatesAndPersistsTitle() throws Exception {
		ChatSession session = ChatSession.builder().id("session-1").agentId(1).title("\u65b0\u4f1a\u8bdd").build();
		when(chatSessionService.findBySessionId("session-1")).thenReturn(session);

		Flux<ChatResponse> chatResponseFlux = Flux.empty();
		when(llmService.call(anyString(), anyString())).thenReturn(chatResponseFlux);
		when(llmService.toStringFlux(chatResponseFlux)).thenReturn(Flux.just("Generated", " Title"));

		service.scheduleTitleGeneration("session-1", "hello world");
		executorService.shutdown();
		executorService.awaitTermination(5, TimeUnit.SECONDS);

		verify(chatSessionService).renameSession(eq("session-1"), eq("Generated Title"));
		verify(sessionEventPublisher).publishTitleUpdated(eq(1), eq("session-1"), eq("Generated Title"));
	}

	@Test
	void scheduleTitleGeneration_duplicateSessionId_skipsSecondCall() throws Exception {
		ChatSession session = ChatSession.builder().id("session-1").agentId(1).title("\u65b0\u4f1a\u8bdd").build();
		when(chatSessionService.findBySessionId("session-1")).thenReturn(session);

		Flux<ChatResponse> chatResponseFlux = Flux.empty();
		when(llmService.call(anyString(), anyString())).thenReturn(chatResponseFlux);
		when(llmService.toStringFlux(chatResponseFlux)).thenReturn(Flux.just("Title"));

		service.scheduleTitleGeneration("session-1", "hello");
		service.scheduleTitleGeneration("session-1", "hello again");
		executorService.shutdown();
		executorService.awaitTermination(5, TimeUnit.SECONDS);

		verify(chatSessionService, atMostOnce()).renameSession(anyString(), anyString());
	}

}
