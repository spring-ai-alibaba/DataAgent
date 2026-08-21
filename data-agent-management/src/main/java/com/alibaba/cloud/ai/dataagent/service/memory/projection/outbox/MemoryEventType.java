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
package com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox;

import java.util.Set;

/**
 * Durable projection events.
 */
public final class MemoryEventType {

	public static final String TURN_COMPLETED = "TURN_COMPLETED";

	/** Legacy event name retained for pending rows created before the v2 projection. */
	public static final String LEGACY_TURN_SUCCEEDED = "TURN_SUCCEEDED";

	public static final String TURN_INVALIDATED = "TURN_INVALIDATED";

	public static final String CONVERSATION_FORGOTTEN = "CONVERSATION_FORGOTTEN";

	public static final String GRAPH_CHECKPOINT_RELEASE = "GRAPH_CHECKPOINT_RELEASE";

	public static final String MEMORY_CONFIRMED = "MEMORY_CONFIRMED";

	public static final String MEMORY_INVALIDATED = "MEMORY_INVALIDATED";

	private static final Set<String> GUARANTEED_RETRY_TYPES = Set.of(TURN_INVALIDATED, CONVERSATION_FORGOTTEN,
			GRAPH_CHECKPOINT_RELEASE, MEMORY_INVALIDATED);

	/**
	 * Destructive projection events must keep retrying: relational authorization checks
	 * prevent stale indexes from becoming prompt-visible, but physical forget and
	 * checkpoint-release obligations must not silently become terminal dead letters.
	 */
	public static boolean requiresGuaranteedRetry(String eventType) {
		return GUARANTEED_RETRY_TYPES.contains(eventType);
	}

	private MemoryEventType() {
	}

}
