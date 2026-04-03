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

import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceImplTest {

	private ChatSessionServiceImpl service;

	@Mock
	private ChatSessionMapper chatSessionMapper;

	@BeforeEach
	void setUp() {
		service = new ChatSessionServiceImpl(chatSessionMapper);
	}

	@Test
	void findByAgentId_returnsSessions() {
		Integer agentId = 1;
		List<ChatSession> expected = List.of(ChatSession.builder().id("s1").agentId(agentId).build(),
				ChatSession.builder().id("s2").agentId(agentId).build());
		when(chatSessionMapper.selectByAgentId(agentId)).thenReturn(expected);

		List<ChatSession> result = service.findByAgentId(agentId);

		assertEquals(2, result.size());
		assertEquals("s1", result.get(0).getId());
		verify(chatSessionMapper).selectByAgentId(agentId);
	}

	@Test
	void findByAgentId_returnsEmptyList() {
		when(chatSessionMapper.selectByAgentId(999)).thenReturn(List.of());

		List<ChatSession> result = service.findByAgentId(999);

		assertTrue(result.isEmpty());
	}

	@Test
	void findBySessionId_returnsSession() {
		ChatSession expected = ChatSession.builder().id("session-1").agentId(1).title("test").build();
		when(chatSessionMapper.selectBySessionId("session-1")).thenReturn(expected);

		ChatSession result = service.findBySessionId("session-1");

		assertNotNull(result);
		assertEquals("session-1", result.getId());
	}

	@Test
	void findBySessionId_returnsNullWhenNotFound() {
		when(chatSessionMapper.selectBySessionId("nonexistent")).thenReturn(null);

		ChatSession result = service.findBySessionId("nonexistent");

		assertNull(result);
	}

	@Test
	void createSession_withTitle() {
		Integer agentId = 1;
		String title = "My Session";
		Long userId = 100L;

		ChatSession result = service.createSession(agentId, title, userId);

		assertNotNull(result);
		assertNotNull(result.getId());
		assertEquals(agentId, result.getAgentId());
		assertEquals(title, result.getTitle());
		assertEquals("active", result.getStatus());
		assertEquals(userId, result.getUserId());
		verify(chatSessionMapper).insert(any(ChatSession.class));
	}

	@Test
	void createSession_withNullTitle_usesDefault() {
		ChatSession result = service.createSession(1, null, 100L);

		assertEquals("\u65b0\u4f1a\u8bdd", result.getTitle());
		verify(chatSessionMapper).insert(any(ChatSession.class));
	}

	@Test
	void clearSessionsByAgentId_callsSoftDelete() {
		when(chatSessionMapper.softDeleteByAgentId(eq(1), any(LocalDateTime.class))).thenReturn(3);

		service.clearSessionsByAgentId(1);

		verify(chatSessionMapper).softDeleteByAgentId(eq(1), any(LocalDateTime.class));
	}

	@Test
	void updateSessionTime_callsMapper() {
		service.updateSessionTime("session-1");

		verify(chatSessionMapper).updateSessionTime(eq("session-1"), any(LocalDateTime.class));
	}

	@Test
	void pinSession_callsMapper() {
		service.pinSession("session-1", true);

		verify(chatSessionMapper).updatePinStatus(eq("session-1"), eq(true), any(LocalDateTime.class));
	}

	@Test
	void renameSession_callsMapper() {
		service.renameSession("session-1", "New Title");

		verify(chatSessionMapper).updateTitle(eq("session-1"), eq("New Title"), any(LocalDateTime.class));
	}

	@Test
	void deleteSession_callsSoftDelete() {
		service.deleteSession("session-1");

		verify(chatSessionMapper).softDeleteById(eq("session-1"), any(LocalDateTime.class));
	}

}
