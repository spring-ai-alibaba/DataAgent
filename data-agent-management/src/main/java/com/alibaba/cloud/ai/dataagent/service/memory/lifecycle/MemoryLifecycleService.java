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
package com.alibaba.cloud.ai.dataagent.service.memory.lifecycle;

import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.alibaba.cloud.ai.dataagent.service.graph.runtime.ActiveGraphRunRegistry;
import com.alibaba.cloud.ai.dataagent.service.memory.longterm.LongTermMemoryService;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryEventType;
import com.alibaba.cloud.ai.dataagent.service.memory.projection.outbox.MemoryOutboxService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Owns deletion and invalidation propagation for authoritative and derived memory.
 * Framework stores are cleared by the Outbox projection after this transaction commits.
 */
@Service
@RequiredArgsConstructor
public class MemoryLifecycleService {

	private final ConversationTurnMapper turnMapper;

	private final AgentMapper agentMapper;

	private final ChatMessageMapper chatMessageMapper;

	private final LongTermMemoryService longTermMemoryService;

	private final ChatSessionMapper chatSessionMapper;

	private final MemoryOutboxService outboxService;

	private final ActiveGraphRunRegistry activeGraphRunRegistry;

	@Transactional
	public void forgetConversation(String conversationId, Integer expectedAgentId) {
		forgetConversationInCurrentTransaction(conversationId, expectedAgentId);
	}

	@Transactional
	public void forgetAgent(Integer agentId) {
		if (agentId == null || agentMapper.lockById(agentId.longValue()) == null) {
			throw new IllegalArgumentException("Agent not found: " + agentId);
		}
		for (ChatSession session : chatSessionMapper.selectAllByAgentId(agentId)) {
			forgetConversationInCurrentTransaction(session.getId(), agentId);
		}
		longTermMemoryService.deleteByAgent(agentId);
		chatSessionMapper.softDeleteByAgentId(agentId, LocalDateTime.now());
	}

	@Transactional
	public void invalidateDatasource(Integer datasourceId) {
		longTermMemoryService.deleteByDatasource(datasourceId);
	}

	@Transactional
	public void invalidateDatasourceBinding(Integer agentId, Integer datasourceId) {
		longTermMemoryService.deleteByAgentAndDatasource(agentId, datasourceId);
	}

	private void forgetConversationInCurrentTransaction(String conversationId, Integer expectedAgentId) {
		if (expectedAgentId == null || !conversationId.equals(chatSessionMapper.lockAnyBySessionId(conversationId))) {
			throw new IllegalArgumentException("Conversation not found");
		}
		ChatSession session = chatSessionMapper.selectAnyBySessionId(conversationId);
		if (session == null || !expectedAgentId.equals(session.getAgentId())) {
			throw new IllegalArgumentException("Conversation not found");
		}
		if (session.getAgentId() != null) {
			activeGraphRunRegistry.quiesceConversationForDeletion(conversationId, session.getAgentId().toString());
		}
		longTermMemoryService.deleteByConversation(conversationId);
		for (ConversationTurn turn : turnMapper.selectByConversationId(conversationId)) {
			if (StringUtils.isNotBlank(turn.getAcceptedRunId())) {
				// A WAITING_REVIEW run has no active StreamContext, so stopping active
				// streams is insufficient. Release its framework checkpoint only after
				// this
				// transaction commits through the durable outbox.
				outboxService.enqueue("GRAPH_RUN", turn.getAcceptedRunId(), MemoryEventType.GRAPH_CHECKPOINT_RELEASE,
						null);
			}
			outboxService.enqueue("CONVERSATION_TURN", turn.getId(), MemoryEventType.TURN_INVALIDATED, null);
		}
		turnMapper.deleteByConversationId(conversationId);
		chatMessageMapper.deleteBySessionId(conversationId);
		outboxService.enqueue("CONVERSATION", conversationId, MemoryEventType.CONVERSATION_FORGOTTEN, null);
	}

}
