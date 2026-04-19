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

import com.alibaba.cloud.ai.dataagent.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.service.graph.GraphService;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphControllerTest {

	@Mock
	private GraphService graphService;

	@Mock
	private ServerHttpResponse serverHttpResponse;

	@Mock
	private HttpHeaders httpHeaders;

	private GraphController graphController;

	@BeforeEach
	void setUp() {
		graphController = new GraphController(graphService);
		when(serverHttpResponse.getHeaders()).thenReturn(httpHeaders);
	}

	@Test
	void streamSearch_validRequest_invokesGraphServiceAndReturnsFlux() {
		doNothing().when(graphService).graphStreamProcess(any(Sinks.Many.class), any(GraphRequest.class));

		Flux<ServerSentEvent<GraphNodeResponse>> result = graphController.streamSearch("agent-1", "thread-1",
				"show me sales data", false, null, false, false, serverHttpResponse);

		assertNotNull(result);

		ArgumentCaptor<GraphRequest> requestCaptor = ArgumentCaptor.forClass(GraphRequest.class);
		verify(graphService).graphStreamProcess(any(Sinks.Many.class), requestCaptor.capture());

		GraphRequest captured = requestCaptor.getValue();
		assertEquals("agent-1", captured.getAgentId());
		assertEquals("thread-1", captured.getThreadId());
		assertEquals("show me sales data", captured.getQuery());
		assertFalse(captured.isHumanFeedback());
	}

	@Test
	void streamSearch_humanFeedback_passesHumanFeedbackParams() {
		doNothing().when(graphService).graphStreamProcess(any(Sinks.Many.class), any(GraphRequest.class));

		Flux<ServerSentEvent<GraphNodeResponse>> result = graphController.streamSearch("agent-1", "thread-2",
				"approve this plan", true, "looks good", false, false, serverHttpResponse);

		assertNotNull(result);

		ArgumentCaptor<GraphRequest> requestCaptor = ArgumentCaptor.forClass(GraphRequest.class);
		verify(graphService).graphStreamProcess(any(Sinks.Many.class), requestCaptor.capture());

		GraphRequest captured = requestCaptor.getValue();
		assertTrue(captured.isHumanFeedback());
		assertEquals("looks good", captured.getHumanFeedbackContent());
		assertFalse(captured.isRejectedPlan());
	}

	@Test
	void streamSearch_nl2sqlOnly_setsNl2sqlOnlyFlag() {
		doNothing().when(graphService).graphStreamProcess(any(Sinks.Many.class), any(GraphRequest.class));

		Flux<ServerSentEvent<GraphNodeResponse>> result = graphController.streamSearch("agent-1", null, "SELECT query",
				false, null, false, true, serverHttpResponse);

		assertNotNull(result);

		ArgumentCaptor<GraphRequest> requestCaptor = ArgumentCaptor.forClass(GraphRequest.class);
		verify(graphService).graphStreamProcess(any(Sinks.Many.class), requestCaptor.capture());

		GraphRequest captured = requestCaptor.getValue();
		assertTrue(captured.isNl2sqlOnly());
		assertNull(captured.getThreadId());
	}

}
