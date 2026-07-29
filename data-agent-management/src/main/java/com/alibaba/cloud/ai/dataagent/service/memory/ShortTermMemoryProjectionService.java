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
package com.alibaba.cloud.ai.dataagent.service.memory;

import com.alibaba.cloud.ai.dataagent.entity.ConversationTurn;
import com.alibaba.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rebuilds Spring AI ChatMemory as a disposable projection of authoritative turns.
 */
@Service
@RequiredArgsConstructor
public class ShortTermMemoryProjectionService {

	private final ConversationTurnMapper turnMapper;

	private final ChatMemory chatMemory;

	private final DataAgentProperties properties;

	public void rebuild(String conversationId) {
		List<ConversationTurn> turns = new ArrayList<>(
				turnMapper.selectRecentSuccessful(conversationId, Math.max(1, properties.getMemory().getRecentTurns())));
		Collections.reverse(turns);
		chatMemory.clear(conversationId);
		List<Message> messages = new ArrayList<>();
		for (ConversationTurn turn : turns) {
			messages.add(new UserMessage(turn.getRawQuery()));
			messages.add(new AssistantMessage(compactResult(turn)));
		}
		if (!messages.isEmpty()) {
			chatMemory.add(conversationId, messages);
		}
	}

	private String compactResult(ConversationTurn turn) {
		String query = StringUtils.defaultIfBlank(turn.getCanonicalQuery(), turn.getRawQuery());
		String result = StringUtils.defaultIfBlank(turn.getResultSummary(), "(无可用结果摘要)");
		return "已验证查询: " + query + "\n已验证结果: " + result;
	}

}
