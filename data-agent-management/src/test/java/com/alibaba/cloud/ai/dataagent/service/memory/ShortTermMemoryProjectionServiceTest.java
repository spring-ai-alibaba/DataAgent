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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortTermMemoryProjectionServiceTest {

	@Mock
	private ConversationTurnMapper turnMapper;

	@Mock
	private ChatMemory chatMemory;

	@Test
	void rebuildProjectsAuthoritativeTurnsIntoFrameworkChatMemoryInChronologicalOrder() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getMemory().setRecentTurns(2);
		ShortTermMemoryProjectionService service = new ShortTermMemoryProjectionService(turnMapper, chatMemory,
				properties);
		when(turnMapper.selectRecentSuccessful("conversation-1", 2)).thenReturn(List.of(
				turn("new question", "new canonical", "new result"),
				turn("old question", "old canonical", "old result")));

		service.rebuild("conversation-1");

		ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
		verify(chatMemory).clear("conversation-1");
		verify(chatMemory).add(eq("conversation-1"), messages.capture());
		assertThat(messages.getValue())
			.extracting(Message::getText)
			.containsExactly("old question", "已验证查询: old canonical\n已验证结果: old result", "new question",
					"已验证查询: new canonical\n已验证结果: new result");
	}

	private ConversationTurn turn(String raw, String canonical, String result) {
		return ConversationTurn.builder()
			.rawQuery(raw)
			.canonicalQuery(canonical)
			.resultSummary(result)
			.build();
	}

}
