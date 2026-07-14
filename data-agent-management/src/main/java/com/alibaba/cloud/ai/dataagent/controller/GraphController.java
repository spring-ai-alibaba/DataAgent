package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.enums.GraphEventType;
import com.alibaba.cloud.ai.dataagent.service.graph.GraphService;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_COMPLETE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_ERROR;

/**
 * @author zhangshenghang
 * @author vlsmb
 */
@Slf4j
@RestController
@AllArgsConstructor
@CrossOrigin(origins = "*")
@RequestMapping("/api")
public class GraphController {

	private final GraphService graphService;

	@GetMapping(value = "/stream/search", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<GraphNodeResponse>> streamSearch(@RequestParam("agentId") String agentId,
			@RequestParam(value = "conversationId", required = false) String conversationId,
			@RequestParam(value = "threadId", required = false) String threadId, @RequestParam("query") String query,
			@RequestParam(value = "humanFeedback", required = false) boolean humanFeedback,
			@RequestParam(value = "humanFeedbackContent", required = false) String humanFeedbackContent,
			@RequestParam(value = "rejectedPlan", required = false) boolean rejectedPlan,
			@RequestParam(value = "nl2sqlOnly", required = false) boolean nl2sqlOnly) {

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();

		GraphRequest request = GraphRequest.builder()
			.agentId(agentId)
			.conversationId(conversationId)
			.threadId(threadId)
			.query(query)
			.humanFeedback(humanFeedback)
			.humanFeedbackContent(humanFeedbackContent)
			.rejectedPlan(rejectedPlan)
			.nl2sqlOnly(nl2sqlOnly)
			.build();
		graphService.graphStreamProcess(sink, request);

		return sink.asFlux().filter(sse -> {
			// 1. 如果 event 是 "complete" 或 "error"，直接放行（不管 text 是否为空）
			if (STREAM_EVENT_COMPLETE.equals(sse.event()) || STREAM_EVENT_ERROR.equals(sse.event())) {
				return true;
			}
			// Protocol events carry state transitions in eventType and do not require
			// display text.
			if (sse.data() != null && sse.data().getEventType() != null
					&& !GraphEventType.NODE_OUTPUT.equals(sse.data().getEventType())) {
				return true;
			}
			// 判断字符串是否为空
			return sse.data() != null && sse.data().getText() != null && !sse.data().getText().isEmpty();
		})
			.doOnSubscribe(subscription -> log.info("Client subscribed to stream, threadId: {}", request.getThreadId()))
			.doOnCancel(() -> {
				log.info("Client disconnected from stream, threadId: {}", request.getThreadId());
				if (request.getThreadId() != null) {
					graphService.stopStreamProcessing(request.getThreadId());
				}
			})
			.doOnError(e -> {
				log.error("Error occurred during streaming, threadId: {}: ", request.getThreadId(), e);
				if (request.getThreadId() != null) {
					graphService.stopStreamProcessing(request.getThreadId());
				}
			})
			.doOnComplete(() -> log.info("Stream completed successfully, threadId: {}", request.getThreadId()));
	}

	@PostMapping("/stream/stop")
	public ResponseEntity<Void> stopStream(@RequestParam("conversationId") String conversationId,
			@RequestParam(value = "threadId", required = false) String threadId) {
		if (StringUtils.hasText(threadId)) {
			graphService.stopStreamProcessing(threadId);
		}
		else {
			graphService.stopStreamProcessingByConversationId(conversationId);
		}
		return ResponseEntity.noContent().build();
	}

}
