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
package com.alibaba.cloud.ai.dataagent.agent.controller;

import com.alibaba.cloud.ai.dataagent.agent.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.agent.service.GraphService;
import com.alibaba.cloud.ai.dataagent.agent.vo.GraphNodeResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_COMPLETE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_ERROR;

@Slf4j
@RestController
@AllArgsConstructor
@CrossOrigin(origins = "*")
@RequestMapping("/api")
public class GraphController {

	private final GraphService graphService;

	@GetMapping(value = "/stream/search", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<GraphNodeResponse>> streamSearch(@RequestParam("agentId") String agentId,
			@RequestParam(value = "agentType", required = false) String agentType,
			@RequestParam(value = "threadId", required = false) String threadId, @RequestParam("query") String query,
			@RequestParam(value = "scene", required = false) String scene,
			@RequestParam(value = "humanFeedback", required = false) boolean humanFeedback,
			@RequestParam(value = "humanFeedbackContent", required = false) String humanFeedbackContent,
			@RequestParam(value = "rejectedPlan", required = false) boolean rejectedPlan,
			@RequestParam(value = "nl2sqlOnly", required = false) boolean nl2sqlOnly, ServerHttpResponse response) {
		response.getHeaders().add("Cache-Control", "no-cache");
		response.getHeaders().add("Connection", "keep-alive");
		response.getHeaders().add("Access-Control-Allow-Origin", "*");

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();
		GraphRequest request = GraphRequest.builder()
			.agentId(agentId)
			.agentType(agentType)
			.threadId(threadId)
			.query(query)
			.scene(scene)
			.humanFeedback(humanFeedback)
			.humanFeedbackContent(humanFeedbackContent)
			.rejectedPlan(rejectedPlan)
			.nl2sqlOnly(nl2sqlOnly)
			.build();
		graphService.graphStreamProcess(sink, request);

		return sink.asFlux()
			.filter(sse -> {
				if (STREAM_EVENT_COMPLETE.equals(sse.event()) || STREAM_EVENT_ERROR.equals(sse.event())) {
					return true;
				}
				return sse.data() != null && sse.data().getText() != null && !sse.data().getText().isEmpty();
			})
			.doOnSubscribe(subscription -> log.info("Client subscribed to aiagent stream, threadId: {}", request.getThreadId()))
			.doOnCancel(() -> {
				log.info("Client disconnected from aiagent stream, threadId: {}", request.getThreadId());
				if (request.getThreadId() != null && request.getRuntimeRequestId() != null) {
					graphService.stopStreamProcessing(request.getThreadId(), request.getRuntimeRequestId());
				}
			})
			.doOnError(error -> {
				log.error("Error occurred during aiagent streaming, threadId: {}", request.getThreadId(), error);
				if (request.getThreadId() != null && request.getRuntimeRequestId() != null) {
					graphService.stopStreamProcessing(request.getThreadId(), request.getRuntimeRequestId());
				}
			})
			.doOnComplete(() -> log.info("Aiagent stream completed successfully, threadId: {}", request.getThreadId()));
	}

}
