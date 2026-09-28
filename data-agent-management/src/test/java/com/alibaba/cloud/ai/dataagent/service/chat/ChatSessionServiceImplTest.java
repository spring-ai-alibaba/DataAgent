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
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import com.alibaba.cloud.ai.dataagent.service.memory.lifecycle.MemoryLifecycleService;
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

	@Mock
	private AgentMapper agentMapper;

	@Mock
	private MemoryLifecycleService memoryLifecycleService;

	@BeforeEach
	void setUp() {
		service = new ChatSessionServiceImpl(chatSessionMapper, agentMapper, memoryLifecycleService);
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
		ChatSession result = service.createSession(agentId, title);

		assertNotNull(result);
		assertNotNull(result.getId());
		assertEquals(agentId, result.getAgentId());
		assertEquals(title, result.getTitle());
		assertEquals("active", result.getStatus());
		assertNull(result.getUserId());
		verify(chatSessionMapper).insert(any(ChatSession.class));
	}

	@Test
	void createSession_withNullTitle_usesDefault() {
		ChatSession result = service.createSession(1, null);

		assertEquals("\u65b0\u4f1a\u8bdd", result.getTitle());
		verify(chatSessionMapper).insert(any(ChatSession.class));
	}

	@Test
	void clearSessionsByAgentId_callsSoftDelete() {
		when(agentMapper.lockById(1L)).thenReturn(1L);
		when(chatSessionMapper.selectByAgentId(1))
			.thenReturn(List.of(ChatSession.builder().id("session-1").build(),
					ChatSession.builder().id("session-2").build()));
		when(chatSessionMapper.softDeleteByAgentId(eq(1), any(LocalDateTime.class))).thenReturn(3);

		service.clearSessionsByAgentId(1);

		verify(agentMapper).lockById(1L);
		verify(chatSessionMapper).softDeleteByAgentId(eq(1), any(LocalDateTime.class));
		verify(memoryLifecycleService).forgetConversation("session-1", 1);
		verify(memoryLifecycleService).forgetConversation("session-2", 1);
	}

	@Test
	void clearSessionsByAgentId_rejectsMissingAgentBeforeEnumeratingSessions() {
		when(agentMapper.lockById(7L)).thenReturn(null);

		assertThrows(IllegalArgumentException.class, () -> service.clearSessionsByAgentId(7));

		verify(chatSessionMapper, never()).selectByAgentId(anyInt());
		verify(memoryLifecycleService, never()).forgetConversation(anyString(), anyInt());
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
		when(chatSessionMapper.softDeleteById(eq("session-1"), any(LocalDateTime.class))).thenReturn(1);

		service.deleteSession("session-1", 7);

		verify(chatSessionMapper).softDeleteById(eq("session-1"), any(LocalDateTime.class));
		verify(memoryLifecycleService).forgetConversation("session-1", 7);
	}

	@Test
	void deleteSession_softDeleteFailureRollsBackInsteadOfReportingSuccess() {
		when(chatSessionMapper.softDeleteById(eq("session-1"), any(LocalDateTime.class))).thenReturn(0);

		assertThrows(IllegalStateException.class, () -> service.deleteSession("session-1", 7));
	}

}
