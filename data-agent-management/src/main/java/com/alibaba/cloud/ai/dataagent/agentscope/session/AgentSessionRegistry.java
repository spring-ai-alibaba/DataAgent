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
package com.alibaba.cloud.ai.dataagent.agentscope.session;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class AgentSessionRegistry {

	private final ConcurrentHashMap<String, ConcurrentHashMap<String, RequestExecutionState>> requestStatesByThreadId = new ConcurrentHashMap<>();

	public void register(String threadId, String runtimeRequestId) {
		RequestExecutionState state = getOrCreateState(threadId, runtimeRequestId);
		state.cancelled.set(false);
		state.runningThread.set(null);
	}

	public void markCancelled(String threadId, String runtimeRequestId) {
		RequestExecutionState state = getState(threadId, runtimeRequestId);
		if (state == null) {
			return;
		}
		state.cancelled.set(true);
		Thread runningThread = state.runningThread.get();
		if (runningThread != null) {
			runningThread.interrupt();
		}
	}

	public void markRunning(String threadId, String runtimeRequestId, Thread thread) {
		getOrCreateState(threadId, runtimeRequestId).runningThread.set(thread);
	}

	public void clearRunning(String threadId, String runtimeRequestId) {
		RequestExecutionState state = getState(threadId, runtimeRequestId);
		if (state != null) {
			state.runningThread.set(null);
		}
	}

	public boolean isActive(String threadId, String runtimeRequestId) {
		RequestExecutionState state = getState(threadId, runtimeRequestId);
		return state != null && !state.cancelled.get();
	}

	public boolean isCancelled(String threadId, String runtimeRequestId) {
		RequestExecutionState state = getState(threadId, runtimeRequestId);
		return state != null && state.cancelled.get();
	}

	public void finish(String threadId, String runtimeRequestId) {
		if (threadId == null || threadId.isBlank() || runtimeRequestId == null || runtimeRequestId.isBlank()) {
			return;
		}
		requestStatesByThreadId.computeIfPresent(threadId, (key, states) -> {
			states.remove(runtimeRequestId);
			return states.isEmpty() ? null : states;
		});
	}

	private RequestExecutionState getOrCreateState(String threadId, String runtimeRequestId) {
		if (threadId == null || threadId.isBlank() || runtimeRequestId == null || runtimeRequestId.isBlank()) {
			throw new IllegalArgumentException("threadId and runtimeRequestId must not be blank");
		}
		ConcurrentHashMap<String, RequestExecutionState> states = requestStatesByThreadId.computeIfAbsent(threadId,
				key -> new ConcurrentHashMap<>());
		return states.computeIfAbsent(runtimeRequestId, key -> new RequestExecutionState());
	}

	private RequestExecutionState getState(String threadId, String runtimeRequestId) {
		if (threadId == null || threadId.isBlank() || runtimeRequestId == null || runtimeRequestId.isBlank()) {
			return null;
		}
		ConcurrentHashMap<String, RequestExecutionState> states = requestStatesByThreadId.get(threadId);
		return states == null ? null : states.get(runtimeRequestId);
	}

	private static final class RequestExecutionState {

		private final AtomicBoolean cancelled = new AtomicBoolean(false);

		private final AtomicReference<Thread> runningThread = new AtomicReference<>();

	}

}
