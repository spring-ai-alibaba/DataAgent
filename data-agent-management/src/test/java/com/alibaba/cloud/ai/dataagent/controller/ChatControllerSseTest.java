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
import com.alibaba.cloud.ai.dataagent.exception.SessionNotFoundException;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatMessageService;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatSessionService;
import com.alibaba.cloud.ai.dataagent.service.chat.SessionTitleService;
import com.alibaba.cloud.ai.dataagent.service.graph.GraphService;
import com.alibaba.cloud.ai.dataagent.util.ReportTemplateUtil;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatControllerSseTest {

    private ChatController controller;
    private ChatSessionService chatSessionService;
    private ChatMessageService chatMessageService;
    private GraphService graphService;

    @BeforeEach
    void setUp() {
        chatSessionService = mock(ChatSessionService.class);
        chatMessageService = mock(ChatMessageService.class);
        graphService = mock(GraphService.class);
        SessionTitleService sessionTitleService = mock(SessionTitleService.class);
        ReportTemplateUtil reportTemplateUtil = mock(ReportTemplateUtil.class);
        controller = new ChatController(chatSessionService, chatMessageService,
            sessionTitleService, reportTemplateUtil, graphService);
    }

    @Test
    void sessionNotFound_throwsSessionNotFoundException() {
        when(chatSessionService.findBySessionId("no-such")).thenReturn(null);

        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setRole("user");
        dto.setContent("hello");
        dto.setMessageType("text");

        assertThrows(SessionNotFoundException.class,
            () -> controller.sendMessage("no-such", dto, new MockServerHttpResponse()));
    }

    @Test
    void validSession_callsGraphService_andSavesAssistantMessage() {
        ChatSession session = new ChatSession();
        session.setId("sess-1");
        session.setAgentId(1);

        when(chatSessionService.findBySessionId("sess-1")).thenReturn(session);
        when(chatMessageService.saveMessage(any())).thenReturn(new ChatMessage());
        doAnswer(inv -> {
            Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = inv.getArgument(0);
            sink.tryEmitNext(ServerSentEvent.<GraphNodeResponse>builder()
                .event("complete")
                .data(GraphNodeResponse.complete("1", "sess-1"))
                .build());
            sink.tryEmitComplete();
            return null;
        }).when(graphService).graphStreamProcess(any(), any());

        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setRole("user");
        dto.setContent("hello");
        dto.setMessageType("text");

        var flux = controller.sendMessage("sess-1", dto, new MockServerHttpResponse());
        assertNotNull(flux);
        verify(graphService).graphStreamProcess(any(), any());

        StepVerifier.create(flux)
            .expectNextCount(1)
            .verifyComplete();

        verify(chatMessageService, never()).saveAssistantMessage(any(), any());
    }

    @Test
    void tokenAccumulation_savesAssistantMessageWithConcatenatedText() {
        ChatSession session = new ChatSession();
        session.setId("sess-2");
        session.setAgentId(1);

        when(chatSessionService.findBySessionId("sess-2")).thenReturn(session);
        when(chatMessageService.saveMessage(any())).thenReturn(new ChatMessage());
        doAnswer(inv -> {
            Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = inv.getArgument(0);
            sink.tryEmitNext(ServerSentEvent.<GraphNodeResponse>builder()
                .event("token")
                .data(GraphNodeResponse.builder()
                    .agentId("1")
                    .threadId("sess-2")
                    .text("Hello")
                    .build())
                .build());
            sink.tryEmitNext(ServerSentEvent.<GraphNodeResponse>builder()
                .event("token")
                .data(GraphNodeResponse.builder()
                    .agentId("1")
                    .threadId("sess-2")
                    .text(" World")
                    .build())
                .build());
            sink.tryEmitNext(ServerSentEvent.<GraphNodeResponse>builder()
                .event("complete")
                .data(GraphNodeResponse.complete("1", "sess-2"))
                .build());
            sink.tryEmitComplete();
            return null;
        }).when(graphService).graphStreamProcess(any(), any());

        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setRole("user");
        dto.setContent("hi");
        dto.setMessageType("text");

        var flux = controller.sendMessage("sess-2", dto, new MockServerHttpResponse());

        StepVerifier.create(flux)
            .expectNextCount(3)
            .verifyComplete();

        verify(chatMessageService).saveAssistantMessage(eq("sess-2"), eq("Hello World"));
    }

}
