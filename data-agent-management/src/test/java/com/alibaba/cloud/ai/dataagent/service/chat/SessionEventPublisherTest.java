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

import com.alibaba.cloud.ai.dataagent.vo.SessionUpdateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class SessionEventPublisherTest {

	private SessionEventPublisher publisher;

	@BeforeEach
	void setUp() {
		publisher = new SessionEventPublisher();
	}

	@Test
	void register_returnsFlux() {
		Flux<ServerSentEvent<SessionUpdateEvent>> flux = publisher.register(1);

		assertNotNull(flux);
	}

	@Test
	void publishTitleUpdated_emitsEventToSubscriber() {
		Integer agentId = 1;
		Flux<ServerSentEvent<SessionUpdateEvent>> flux = publisher.register(agentId);

		publisher.publishTitleUpdated(agentId, "session-1", "New Title");

		StepVerifier.create(flux.filter(sse -> sse.data() != null).take(1)).assertNext(sse -> {
			SessionUpdateEvent event = sse.data();
			assertNotNull(event);
			assertEquals("session-1", event.getSessionId());
			assertEquals("New Title", event.getTitle());
			assertEquals(SessionUpdateEvent.TYPE_TITLE_UPDATED, event.getType());
		}).verifyComplete();
	}

	@Test
	void publishTitleUpdated_withNullAgentId_doesNotThrow() {
		assertDoesNotThrow(() -> publisher.publishTitleUpdated(null, "session-1", "title"));
	}

	@Test
	void publishTitleUpdated_withNoSubscribers_doesNotThrow() {
		assertDoesNotThrow(() -> publisher.publishTitleUpdated(999, "session-1", "title"));
	}

}
