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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.entity.ChatMessage;
import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatMessageService;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatSessionService;
import com.alibaba.cloud.ai.dataagent.service.chat.SessionTitleService;
import com.alibaba.cloud.ai.dataagent.util.ReportTemplateUtil;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

	@Mock
	private ChatSessionService chatSessionService;

	@Mock
	private ChatMessageService chatMessageService;

	@Mock
	private SessionTitleService sessionTitleService;

	@Mock
	private ReportTemplateUtil reportTemplateUtil;

	private ChatController chatController;

	@BeforeEach
	void setUp() {
		chatController = new ChatController(chatSessionService, chatMessageService, sessionTitleService,
				reportTemplateUtil);
	}

	@Test
	void createSession_validRequest_returnsSession() {
		ChatSession session = ChatSession.builder()
			.id("uuid-1")
			.agentId(1)
			.title("New Session")
			.status("active")
			.build();
		when(chatSessionService.createSession(1, "New Session", null)).thenReturn(session);

		ResponseEntity<ChatSession> result = chatController.createSession(1, Map.of("title", "New Session"));

		assertEquals(200, result.getStatusCode().value());
		assertNotNull(result.getBody());
		assertEquals("uuid-1", result.getBody().getId());
	}

	@Test
	void getMessages_validSession_returnsMessages() {
		List<ChatMessage> messages = List.of(
				ChatMessage.builder().id(1L).sessionId("uuid-1").role("user").content("Hello").build(),
				ChatMessage.builder().id(2L).sessionId("uuid-1").role("assistant").content("Hi there").build());
		when(chatMessageService.findBySessionId("uuid-1")).thenReturn(messages);

		ResponseEntity<List<ChatMessage>> result = chatController.getSessionMessages("uuid-1");

		assertEquals(200, result.getStatusCode().value());
		assertEquals(2, result.getBody().size());
	}

	@Test
	void deleteSession_existingId_returns200() {
		doNothing().when(chatSessionService).deleteSession("uuid-1");

		ResponseEntity<ApiResponse> result = chatController.deleteSession("uuid-1");

		assertEquals(200, result.getStatusCode().value());
		assertTrue(result.getBody().isSuccess());
		verify(chatSessionService).deleteSession("uuid-1");
	}

	@Test
	void downloadReport_validContent_returnsHtml() {
		when(reportTemplateUtil.getHeader()).thenReturn("<html><head></head><body>");
		when(reportTemplateUtil.getFooter()).thenReturn("</body></html>");

		ResponseEntity<byte[]> result = chatController.convertAndDownloadHtml("uuid-1", "# Report Content");

		assertEquals(200, result.getStatusCode().value());
		assertNotNull(result.getBody());
		String html = new String(result.getBody());
		assertTrue(html.contains("# Report Content"));
		assertTrue(html.contains("<html>"));
	}

	@Test
	void downloadReport_emptyContent_returnsBadRequest() {
		ResponseEntity<byte[]> result = chatController.convertAndDownloadHtml("uuid-1", "");

		assertEquals(400, result.getStatusCode().value());
	}

}
