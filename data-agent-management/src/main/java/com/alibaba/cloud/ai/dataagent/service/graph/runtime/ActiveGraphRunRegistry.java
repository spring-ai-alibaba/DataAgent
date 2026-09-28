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
package com.alibaba.cloud.ai.dataagent.service.graph.runtime;

import com.alibaba.cloud.ai.dataagent.service.graph.Context.StreamContext;
import com.alibaba.cloud.ai.dataagent.service.langfuse.LangfuseService;
import com.alibaba.cloud.ai.dataagent.service.langfuse.NodeTracingLifecycleListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the node-local registry of active graph subscriptions.
 *
 * <p>
 * This deliberately has no dependency on the compiled graph, checkpoint saver, turn
 * service, or datasource services. Destructive memory lifecycle operations can therefore
 * quiesce active subscriptions before deleting their durable facts without creating a
 * constructor cycle back through {@code GraphServiceImpl}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveGraphRunRegistry {

	private final ConcurrentHashMap<String, StreamContext> contexts = new ConcurrentHashMap<>();

	/**
	 * Registration and conversation-wide quiescence must be mutually exclusive. A weakly
	 * consistent map scan alone could miss a run registered concurrently with deletion.
	 */
	private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();

	private final LangfuseService langfuseReporter;

	private final NodeTracingLifecycleListener nodeTracingLifecycleListener;

	public boolean register(String threadId, StreamContext context) {
		lifecycleLock.readLock().lock();
		try {
			return contexts.putIfAbsent(threadId, context) == null;
		}
		finally {
			lifecycleLock.readLock().unlock();
		}
	}

	public StreamContext get(String threadId) {
		return contexts.get(threadId);
	}

	public boolean remove(String threadId, StreamContext expectedContext) {
		lifecycleLock.readLock().lock();
		try {
			return contexts.remove(threadId, expectedContext);
		}
		finally {
			lifecycleLock.readLock().unlock();
		}
	}

	public StreamContext removeOwned(String threadId, String agentId, boolean deferCheckpointRelease) {
		if (!StringUtils.hasText(threadId) || !StringUtils.hasText(agentId)) {
			return null;
		}
		lifecycleLock.readLock().lock();
		try {
			return removeOwnedWithoutLifecycleLock(threadId, agentId, deferCheckpointRelease);
		}
		finally {
			lifecycleLock.readLock().unlock();
		}
	}

	public List<String> findRunIds(String conversationId, String agentId) {
		if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(agentId)) {
			return List.of();
		}
		lifecycleLock.readLock().lock();
		try {
			return findRunIdsWithoutLifecycleLock(conversationId, agentId);
		}
		finally {
			lifecycleLock.readLock().unlock();
		}
	}

	public boolean isActive(String threadId, StreamContext context) {
		return contexts.get(threadId) == context && !context.isCleaned();
	}

	/**
	 * Removes and disposes local subscriptions while leaving checkpoint release to the
	 * caller's durable deletion outbox.
	 */
	public void quiesceConversationForDeletion(String conversationId, String agentId) {
		if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(agentId)) {
			return;
		}
		List<RemovedRun> removedRuns;
		lifecycleLock.writeLock().lock();
		try {
			removedRuns = findRunIdsWithoutLifecycleLock(conversationId, agentId).stream().map(threadId -> {
				StreamContext context = removeOwnedWithoutLifecycleLock(threadId, agentId, true);
				return context == null ? null : new RemovedRun(threadId, context);
			}).filter(java.util.Objects::nonNull).toList();
		}
		finally {
			lifecycleLock.writeLock().unlock();
		}
		for (RemovedRun removedRun : removedRuns) {
			String threadId = removedRun.threadId();
			StreamContext context = removedRun.context();
			if (context == null) {
				continue;
			}
			synchronized (context) {
				context.cleanup();
			}
			if (context.getSpan() != null && context.getSpan().isRecording()) {
				langfuseReporter.endSpanSuccess(context.getSpan(), threadId, context.getCollectedOutput());
			}
			nodeTracingLifecycleListener.discardThread(threadId);
			log.info("Quiesced graph run {} before deleting conversation {}", threadId, conversationId);
		}
	}

	private StreamContext removeOwnedWithoutLifecycleLock(String threadId, String agentId,
			boolean deferCheckpointRelease) {
		AtomicReference<StreamContext> removed = new AtomicReference<>();
		contexts.computeIfPresent(threadId, (ignored, current) -> {
			if (!agentId.equals(current.getAgentId())) {
				return current;
			}
			if (deferCheckpointRelease) {
				// Publish the deferral before atomically removing the entry. Setup and
				// terminal callbacks that subsequently observe an inactive context cannot
				// release the checkpoint ahead of the deleting transaction's commit.
				current.deferCheckpointRelease();
			}
			removed.set(current);
			return null;
		});
		return removed.get();
	}

	private List<String> findRunIdsWithoutLifecycleLock(String conversationId, String agentId) {
		return contexts.entrySet()
			.stream()
			.filter(entry -> conversationId.equals(entry.getValue().getConversationId())
					&& agentId.equals(entry.getValue().getAgentId()))
			.map(java.util.Map.Entry::getKey)
			.toList();
	}

	private record RemovedRun(String threadId, StreamContext context) {
	}

}
