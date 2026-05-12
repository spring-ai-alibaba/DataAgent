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

import com.alibaba.cloud.ai.dataagent.dto.ChatMessageDTO;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
	void createSession_nullBody_createsWithNulls() {
		ChatSession session = ChatSession.builder().id("uuid-2").agentId(1).build();
		when(chatSessionService.createSession(1, null, null)).thenReturn(session);

		ResponseEntity<ChatSession> result = chatController.createSession(1, null);

		assertEquals(200, result.getStatusCode().value());
		assertEquals("uuid-2", result.getBody().getId());
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
	void deleteSession_serviceThrows_returns500() {
		doThrow(new RuntimeException("db error")).when(chatSessionService).deleteSession("uuid-1");

		ResponseEntity<ApiResponse> result = chatController.deleteSession("uuid-1");

		assertEquals(500, result.getStatusCode().value());
		assertFalse(result.getBody().isSuccess());
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

	@Test
	void downloadReport_nullContent_returnsBadRequest() {
		ResponseEntity<byte[]> result = chatController.convertAndDownloadHtml("uuid-1", null);

		assertEquals(400, result.getStatusCode().value());
	}

	@Test
	void downloadReport_templateThrows_returns500() {
		when(reportTemplateUtil.getHeader()).thenThrow(new RuntimeException("template error"));

		ResponseEntity<byte[]> result = chatController.convertAndDownloadHtml("uuid-1", "content");

		assertEquals(500, result.getStatusCode().value());
	}

	@Test
	void pinSession_pinTrue_returnsSuccess() {
		doNothing().when(chatSessionService).pinSession("uuid-1", true);

		ResponseEntity<ApiResponse> result = chatController.pinSession("uuid-1", true);

		assertEquals(200, result.getStatusCode().value());
		assertTrue(result.getBody().isSuccess());
		verify(chatSessionService).pinSession("uuid-1", true);
	}

	@Test
	void pinSession_pinFalse_returnsUnpinnedMessage() {
		doNothing().when(chatSessionService).pinSession("uuid-1", false);

		ResponseEntity<ApiResponse> result = chatController.pinSession("uuid-1", false);

		assertEquals(200, result.getStatusCode().value());
		assertTrue(result.getBody().isSuccess());
	}

	@Test
	void pinSession_serviceThrows_returns500() {
		doThrow(new RuntimeException("pin error")).when(chatSessionService).pinSession("uuid-1", true);

		ResponseEntity<ApiResponse> result = chatController.pinSession("uuid-1", true);

		assertEquals(500, result.getStatusCode().value());
		assertFalse(result.getBody().isSuccess());
	}

	@Test
	void renameSession_validTitle_returnsSuccess() {
		doNothing().when(chatSessionService).renameSession("uuid-1", "New Title");

		ResponseEntity<ApiResponse> result = chatController.renameSession("uuid-1", "New Title");

		assertEquals(200, result.getStatusCode().value());
		assertTrue(result.getBody().isSuccess());
		verify(chatSessionService).renameSession("uuid-1", "New Title");
	}

	@Test
	void renameSession_emptyTitle_returnsBadRequest() {
		ResponseEntity<ApiResponse> result = chatController.renameSession("uuid-1", "");

		assertEquals(400, result.getStatusCode().value());
		assertFalse(result.getBody().isSuccess());
	}

	@Test
	void renameSession_blankTitle_returnsBadRequest() {
		ResponseEntity<ApiResponse> result = chatController.renameSession("uuid-1", "   ");

		assertEquals(400, result.getStatusCode().value());
	}

	@Test
	void renameSession_serviceThrows_returns500() {
		doThrow(new RuntimeException("rename error")).when(chatSessionService).renameSession("uuid-1", "title");

		ResponseEntity<ApiResponse> result = chatController.renameSession("uuid-1", "title");

		assertEquals(500, result.getStatusCode().value());
		assertFalse(result.getBody().isSuccess());
	}

	@Test
	void renameSession_trimmedTitle_trimsBefore() {
		doNothing().when(chatSessionService).renameSession("uuid-1", "trimmed");

		ResponseEntity<ApiResponse> result = chatController.renameSession("uuid-1", "  trimmed  ");

		assertEquals(200, result.getStatusCode().value());
		verify(chatSessionService).renameSession("uuid-1", "trimmed");
	}

	@Test
	void getAgentSessions_returnsSessions() {
		List<ChatSession> sessions = List.of(ChatSession.builder().id("s1").agentId(1).build(),
				ChatSession.builder().id("s2").agentId(1).build());
		when(chatSessionService.findByAgentId(1)).thenReturn(sessions);

		ResponseEntity<List<ChatSession>> result = chatController.getAgentSessions(1);

		assertEquals(200, result.getStatusCode().value());
		assertEquals(2, result.getBody().size());
	}

	@Test
	void clearAgentSessions_returns200() {
		doNothing().when(chatSessionService).clearSessionsByAgentId(1);

		ResponseEntity<ApiResponse> result = chatController.clearAgentSessions(1);

		assertEquals(200, result.getStatusCode().value());
		assertTrue(result.getBody().isSuccess());
	}

	@Test
	void saveMessage_validRequest_returnsMessage() {
		ChatMessageDTO dto = new ChatMessageDTO();
		dto.setRole("user");
		dto.setContent("Hello");
		dto.setMessageType("text");
		dto.setTitleNeeded(false);

		ChatMessage saved = ChatMessage.builder().id(1L).sessionId("uuid-1").role("user").content("Hello").build();
		when(chatMessageService.saveMessage(any())).thenReturn(saved);

		ResponseEntity<ChatMessage> result = chatController.saveMessage("uuid-1", dto);

		assertEquals(200, result.getStatusCode().value());
		assertEquals("Hello", result.getBody().getContent());
		verify(chatSessionService).updateSessionTime("uuid-1");
	}

	@Test
	void saveMessage_titleNeeded_schedulesTitleGeneration() {
		ChatMessageDTO dto = new ChatMessageDTO();
		dto.setRole("user");
		dto.setContent("What is AI?");
		dto.setTitleNeeded(true);

		ChatMessage saved = ChatMessage.builder()
			.id(1L)
			.sessionId("uuid-1")
			.role("user")
			.content("What is AI?")
			.build();
		when(chatMessageService.saveMessage(any())).thenReturn(saved);

		chatController.saveMessage("uuid-1", dto);

		verify(sessionTitleService).scheduleTitleGeneration("uuid-1", "What is AI?");
	}

	@Test
	void saveMessage_serviceThrows_returns500() {
		ChatMessageDTO dto = new ChatMessageDTO();
		dto.setRole("user");
		dto.setContent("test");
		when(chatMessageService.saveMessage(any())).thenThrow(new RuntimeException("save error"));

		ResponseEntity<ChatMessage> result = chatController.saveMessage("uuid-1", dto);

		assertEquals(500, result.getStatusCode().value());
	}

	@Test
	void downloadReport_contentDispositionHasReportFilename() {
		when(reportTemplateUtil.getHeader()).thenReturn("<html>");
		when(reportTemplateUtil.getFooter()).thenReturn("</html>");

		ResponseEntity<byte[]> result = chatController.convertAndDownloadHtml("uuid-1", "content");

		String disposition = result.getHeaders().getContentDisposition().toString();
		assertTrue(disposition.contains("report_"));
	}

}
