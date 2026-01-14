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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.SignalType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manage SSE streams that push session updates to frontend.
 */
@Slf4j
@Service
public class SessionEventPublisher {

	private final Map<Integer, AgentSessionSink> sinks = new ConcurrentHashMap<>();

	public Flux<ServerSentEvent<SessionUpdateEvent>> register(Integer agentId) {
		AgentSessionSink sink = sinks.computeIfAbsent(agentId, id -> new AgentSessionSink());
		sink.increment();
		return sink.sink.asFlux().doFinally(signalType -> cleanup(agentId, sink, signalType));
	}

	public void publishTitleUpdated(Integer agentId, String sessionId, String title) {
		if (agentId == null) {
			return;
		}
		SessionUpdateEvent event = SessionUpdateEvent.titleUpdated(sessionId, title);
		AgentSessionSink sink = sinks.get(agentId);
		if (sink == null) {
			log.debug("No active subscribers for agent {}, skip pushing session title update", agentId);
			return;
		}
		Sinks.EmitResult result = sink.sink.tryEmitNext(ServerSentEvent.builder(event).event(event.getType()).build());
		if (result.isFailure()) {
			log.warn("Failed to emit session title update for agent {}, session {}, reason {}", agentId, sessionId,
					result);
		}
	}

	private void cleanup(Integer agentId, AgentSessionSink sink, SignalType signalType) {
		int current = sink.decrement();
		if (current <= 0) {
			sink.sink.tryEmitComplete();
			sinks.remove(agentId);
			log.debug("Cleaned session update sink for agent {} due to signal {}", agentId, signalType);
		}
	}

	private static class AgentSessionSink {

		private final AtomicInteger subscribers = new AtomicInteger(0);

		private final Sinks.Many<ServerSentEvent<SessionUpdateEvent>> sink = Sinks.many()
			.multicast()
			.onBackpressureBuffer();

		private void increment() {
			subscribers.incrementAndGet();
		}

		private int decrement() {
			return subscribers.decrementAndGet();
		}

	}

}
