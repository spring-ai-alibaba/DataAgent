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
package com.alibaba.cloud.ai.dataagent.service.graph.Context;

import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.service.graph.turn.TurnMemorySnapshot;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import io.opentelemetry.api.trace.Span;
import lombok.Data;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式处理上下文，封装每个 threadId 的所有相关状态
 *
 * @author Makoto
 * @since 2025/11/28
 */
@Data
public class StreamContext {

	private String agentId;

	private String conversationId;

	private String turnId;

	private Disposable disposable;

	private Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink;

	private Span span;

	private TextType textType;

	private String finalAnswer;

	private final TurnMemorySnapshot memorySnapshot = new TurnMemorySnapshot();

	private final StringBuilder reportCollector = new StringBuilder();

	private final List<List<GraphNodeResponse>> timelineBlocks = new ArrayList<>();

	private final Map<String, Integer> nodeAttempts = new HashMap<>();

	private String activeNode;

	private String activeStepId;

	private int activeAttempt;

	private int stepSequence;

	public synchronized StepIdentity resolveStep(String nodeName) {
		if (!Objects.equals(activeNode, nodeName)) {
			activeNode = nodeName;
			activeAttempt = nodeAttempts.merge(nodeName, 1, Integer::sum);
			activeStepId = nodeName + "-" + (++stepSequence);
		}
		return new StepIdentity(activeStepId, activeAttempt);
	}

	public synchronized void recordResponse(GraphNodeResponse response) {
		if (timelineBlocks.isEmpty() || !Objects
			.equals(timelineBlocks.get(timelineBlocks.size() - 1).get(0).getStepId(), response.getStepId())) {
			List<GraphNodeResponse> block = new ArrayList<>();
			block.add(response);
			timelineBlocks.add(block);
			return;
		}
		List<GraphNodeResponse> block = timelineBlocks.get(timelineBlocks.size() - 1);
		if ("ReportGeneratorNode".equals(response.getNodeName()) && response.getTextType() == TextType.MARK_DOWN) {
			GraphNodeResponse first = block.get(0);
			first.setText(reportCollector.toString());
			first.setTextType(TextType.MARK_DOWN);
			return;
		}
		block.add(response);
	}

	public synchronized void appendReport(String chunk) {
		reportCollector.append(chunk);
	}

	public synchronized String getReportContent() {
		return reportCollector.toString();
	}

	public synchronized String timelineJson() {
		if (timelineBlocks.isEmpty()) {
			return null;
		}
		try {
			return JsonUtil.getObjectMapper().writeValueAsString(timelineBlocks);
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to serialize graph timeline", e);
		}
	}

	/**
	 * 收集流式输出内容，用于 Langfuse 上报
	 */
	private final StringBuilder outputCollector = new StringBuilder();

	public void appendOutput(String chunk) {
		outputCollector.append(chunk);
	}

	public String getCollectedOutput() {
		return outputCollector.toString();
	}

	/**
	 * 标记是否已经清理，用于防止重复清理
	 */
	private final AtomicBoolean cleaned = new AtomicBoolean(false);

	private final AtomicBoolean checkpointReleaseDeferred = new AtomicBoolean(false);

	public void deferCheckpointRelease() {
		checkpointReleaseDeferred.set(true);
	}

	public boolean isCheckpointReleaseDeferred() {
		return checkpointReleaseDeferred.get();
	}

	/**
	 * 清理所有资源 线程安全：使用 AtomicBoolean 确保只执行一次
	 */
	public void cleanup() {
		// 使用 compareAndSet 确保只执行一次清理
		if (!cleaned.compareAndSet(false, true)) {
			return;
		}

		// 清理 Disposable
		Disposable localDisposable = disposable;
		if (localDisposable != null && !localDisposable.isDisposed()) {
			try {
				localDisposable.dispose();
			}
			catch (Exception e) {
				// 忽略清理过程中的异常
			}
		}

		// 清理 Sink
		Sinks.Many<ServerSentEvent<GraphNodeResponse>> localSink = sink;
		if (localSink != null) {
			try {
				localSink.tryEmitComplete();
			}
			catch (Exception e) {
				// 忽略清理过程中的异常
			}
		}
	}

	/**
	 * 检查是否已经清理
	 */
	public boolean isCleaned() {
		return cleaned.get();
	}

	public record StepIdentity(String stepId, int attempt) {
	}

}
