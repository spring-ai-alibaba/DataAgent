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
package com.alibaba.cloud.ai.dataagent.service.memory.projection;

import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.entity.MemoryItem;
import com.alibaba.cloud.ai.dataagent.entity.MemoryOutboxEvent;
import com.alibaba.cloud.ai.dataagent.enums.MemoryStatus;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.mapper.MemoryItemMapper;
import com.alibaba.cloud.ai.dataagent.mapper.MemoryOutboxMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryEventType;
import com.alibaba.cloud.ai.dataagent.service.memory.semantic.MemoryVectorIndexService;
import com.alibaba.cloud.ai.dataagent.service.memory.shortterm.ConversationSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Idempotently projects durable turn and memory events into rebuildable summaries and the
 * optional vector index.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryProjectionWorker {

	private final MemoryOutboxMapper outboxMapper;

	private final ConversationTurnMapper turnMapper;

	private final MemoryItemMapper memoryItemMapper;

	private final ConversationSummaryService summaryService;

	private final MemoryVectorIndexService vectorIndexService;

	private final DataAgentProperties properties;

	@Scheduled(initialDelayString = "${spring.ai.alibaba.data-agent.memory.outbox-initial-delay-ms:10000}",
			fixedDelayString = "${spring.ai.alibaba.data-agent.memory.outbox-poll-delay-ms:2000}")
	public void projectReadyEvents() {
		int maxAttempts = Math.max(1, properties.getMemory().getOutboxMaxAttempts());
		List<MemoryOutboxEvent> events;
		try {
			outboxMapper.recoverStale(LocalDateTime.now().minusMinutes(5));
			events = outboxMapper.selectReady(Math.max(1, properties.getMemory().getOutboxBatchSize()), maxAttempts);
		}
		catch (RuntimeException e) {
			log.warn("Memory projection polling is unavailable: {}", e.getMessage());
			return;
		}
		for (MemoryOutboxEvent event : events) {
			if (outboxMapper.claim(event.getId()) != 1) {
				continue;
			}
			try {
				project(event);
				outboxMapper.markDone(event.getId());
			}
			catch (RuntimeException e) {
				int attempt = event.getAttemptCount() == null ? 1 : event.getAttemptCount() + 1;
				long retryDelaySeconds = Math.min(300, 1L << Math.min(8, attempt));
				String error = StringUtils
					.abbreviate(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()), 4000);
				outboxMapper.markFailed(event.getId(), error, LocalDateTime.now().plusSeconds(retryDelaySeconds));
				log.warn("Memory projection event {} failed on attempt {}: {}", event.getId(), attempt, error);
			}
		}
	}

	private void project(MemoryOutboxEvent event) {
		switch (event.getEventType()) {
			case MemoryEventType.TURN_SUCCEEDED -> projectTurn(event.getAggregateId());
			case MemoryEventType.TURN_INVALIDATED -> vectorIndexService.deleteTurn(event.getAggregateId());
			case MemoryEventType.MEMORY_CONFIRMED -> projectConfirmedMemory(Long.valueOf(event.getAggregateId()));
			case MemoryEventType.MEMORY_INVALIDATED ->
				vectorIndexService.deleteMemoryItem(Long.valueOf(event.getAggregateId()));
			default -> throw new IllegalArgumentException("Unknown memory event type: " + event.getEventType());
		}
	}

	private void projectTurn(String turnId) {
		ConversationTurn turn = turnMapper.selectById(turnId);
		if (turn == null || !Boolean.TRUE.equals(turn.getMemoryEligible())) {
			return;
		}
		summaryService.rebuild(turn.getConversationId());
		vectorIndexService.indexTurn(turn);
	}

	private void projectConfirmedMemory(Long memoryItemId) {
		MemoryItem item = memoryItemMapper.selectById(memoryItemId);
		if (item != null && item.getStatus() == MemoryStatus.CONFIRMED) {
			vectorIndexService.indexMemoryItem(item);
		}
	}

}
