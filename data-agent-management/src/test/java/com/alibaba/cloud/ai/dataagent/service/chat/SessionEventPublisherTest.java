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

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SessionEventPublisherTest {

	private SessionEventPublisher publisher;

	@BeforeEach
	void setUp() {
		publisher = new SessionEventPublisher();
	}

	@Test
	void register_emitsHeartbeatAfterSubscription() {
		StepVerifier.withVirtualTime(() -> publisher.register(1).take(1))
			.thenAwait(Duration.ofSeconds(2))
			.assertNext(sse -> {
				assertEquals("heartbeat", sse.comment());
				assertNull(sse.data());
			})
			.verifyComplete();
	}

	@Test
	void publishTitleUpdated_emitsEventToSubscriber() {
		Integer agentId = 1;
		Flux<ServerSentEvent<SessionUpdateEvent>> flux = publisher.register(agentId);

		StepVerifier.create(flux.filter(sse -> sse.data() != null).take(1))
			.then(() -> publisher.publishTitleUpdated(agentId, "session-1", "New Title"))
			.assertNext(sse -> {
				SessionUpdateEvent event = sse.data();
				assertNotNull(event);
				assertEquals("session-1", event.getSessionId());
				assertEquals("New Title", event.getTitle());
				assertEquals(SessionUpdateEvent.TYPE_TITLE_UPDATED, event.getType());
				assertEquals(SessionUpdateEvent.TYPE_TITLE_UPDATED, sse.event());
			})
			.verifyComplete();
	}

	@Test
	void publishTitleUpdated_withNullAgentId_doesNotThrow() {
		assertDoesNotThrow(() -> publisher.publishTitleUpdated(null, "session-1", "title"));

		Flux<ServerSentEvent<SessionUpdateEvent>> flux = publisher.register(1);
		StepVerifier.create(flux.filter(sse -> sse.data() != null).take(1))
			.then(() -> publisher.publishTitleUpdated(1, "session-after-null", "Current Title"))
			.assertNext(sse -> assertEquals("session-after-null", sse.data().getSessionId()))
			.verifyComplete();
	}

	@Test
	void publishTitleUpdated_withNoSubscribers_doesNotBufferStaleEvent() {
		assertDoesNotThrow(() -> publisher.publishTitleUpdated(999, "session-1", "title"));

		Flux<ServerSentEvent<SessionUpdateEvent>> flux = publisher.register(999);
		StepVerifier.create(flux.filter(sse -> sse.data() != null).take(1))
			.then(() -> publisher.publishTitleUpdated(999, "session-current", "Current Title"))
			.assertNext(sse -> assertEquals("session-current", sse.data().getSessionId()))
			.verifyComplete();
	}

}
