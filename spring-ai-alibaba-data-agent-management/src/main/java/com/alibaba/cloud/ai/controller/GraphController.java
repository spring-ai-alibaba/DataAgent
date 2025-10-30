/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.controller;

import com.alibaba.cloud.ai.dto.GraphRequest;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.service.graph.GraphService;
import com.alibaba.cloud.ai.vo.GraphNodeResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * @author zhangshenghang
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("nl2sql")
public class GraphController {

	private final GraphService graphService;

	@GetMapping(value = "/stream/search", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<GraphNodeResponse>> streamSearch(@RequestBody GraphRequest request,
			HttpServletResponse response) {
		// Set SSE-related HTTP headers
		response.setCharacterEncoding("UTF-8");
		response.setContentType("text/event-stream");
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("Connection", "keep-alive");
		response.setHeader("Access-Control-Allow-Origin", "*");
		response.setHeader("Access-Control-Allow-Headers", "Cache-Control");

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();

		graphService.graphStreamProcess(sink, request);

		return sink.asFlux()
			.doOnSubscribe(subscription -> log.info("Client subscribed to stream"))
			.doOnCancel(() -> log.info("Client disconnected from stream"))
			.doOnError(e -> log.error("Error occurred during streaming: ", e))
			.doOnComplete(() -> log.info("Stream completed successfully"));
	}

}
