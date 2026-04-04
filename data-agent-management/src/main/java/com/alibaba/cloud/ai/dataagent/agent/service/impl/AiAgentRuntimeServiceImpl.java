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
package com.alibaba.cloud.ai.dataagent.agent.service.impl;

import com.alibaba.cloud.ai.dataagent.agent.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.agent.service.GraphService;
import com.alibaba.cloud.ai.dataagent.agent.session.AgentSessionRegistry;
import com.alibaba.cloud.ai.dataagent.agent.vo.GraphNodeResponse;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Sinks;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_COMPLETE;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentRuntimeServiceImpl implements GraphService {

	private static final String RUNTIME_NODE_NAME = "AiAgentRuntimeShell";

	private static final String STREAM_EVENT_MESSAGE = "message";

	private final AgentSessionRegistry sessionRegistry;

	@Override
	public String nl2sql(String naturalQuery, String agentId) {
		log.info("NL2SQL runtime shell invoked for agentId={}", agentId);
		return "-- Agent runtime shell is ready. AgentScope orchestration has not been connected yet.";
	}

	@Override
	public void graphStreamProcess(Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink, GraphRequest graphRequest) {
		String threadId = StringUtils.hasText(graphRequest.getThreadId()) ? graphRequest.getThreadId()
				: UUID.randomUUID().toString();
		graphRequest.setThreadId(threadId);
		sessionRegistry.register(threadId);

		GraphNodeResponse response = GraphNodeResponse.builder()
			.agentId(graphRequest.getAgentId())
			.threadId(threadId)
			.nodeName(RUNTIME_NODE_NAME)
			.textType(TextType.TEXT)
			.text(buildShellMessage(graphRequest))
			.build();

		sink.tryEmitNext(ServerSentEvent.builder(response).event(STREAM_EVENT_MESSAGE).build());
		sink.tryEmitNext(ServerSentEvent
			.builder(GraphNodeResponse.complete(graphRequest.getAgentId(), threadId))
			.event(STREAM_EVENT_COMPLETE)
			.build());
		sink.tryEmitComplete();
		sessionRegistry.remove(threadId);
	}

	@Override
	public void stopStreamProcessing(String threadId) {
		sessionRegistry.remove(threadId);
	}

	private String buildShellMessage(GraphRequest request) {
		if (StringUtils.hasText(request.getHumanFeedbackContent())) {
			return "已收到人工反馈，但 AgentScope 运行时尚未接入。当前仅保留运行入口壳子。";
		}
		if (request.isNl2sqlOnly()) {
			return "NL2SQL 运行入口已切换为新壳子，旧 SAA Graph 编排已移除，等待接入 AgentScope。";
		}
		return "AI Agent 运行入口已完成壳子化，旧 SAA Graph 编排已移除，等待接入 AgentScope。";
	}

}