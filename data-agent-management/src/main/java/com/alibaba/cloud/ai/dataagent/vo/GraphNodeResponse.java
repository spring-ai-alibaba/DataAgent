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
package com.alibaba.cloud.ai.dataagent.vo;

import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.enums.GraphEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GraphNodeResponse {

	private String agentId;

	private String threadId;

	@Builder.Default
	private GraphEventType eventType = GraphEventType.NODE_OUTPUT;

	private String stepId;

	private Integer attempt;

	// 使用Constant常量
	private String nodeName;

	private TextType textType;

	private String text;

	private Long sequence;

	/** Workflow start time in epoch milliseconds. */
	private Long workflowStartedAt;

	/** Current node start time in epoch milliseconds. */
	private Long nodeStartedAt;

	/** Final or currently observed node duration in milliseconds. */
	private Long nodeElapsedMs;

	/** Final or currently observed workflow duration in milliseconds. */
	private Long totalElapsedMs;

	/** Indicates that this event only updates timing metadata. */
	@Builder.Default
	private boolean timingOnly = false;

	@Builder.Default
	private String interactionType = "normal";

	@Builder.Default
	private boolean awaitingInput = false;

	@Builder.Default
	private boolean error = false;

	@Builder.Default
	private boolean complete = false;

	public static GraphNodeResponse error(String agentId, String threadId, String text) {
		return GraphNodeResponse.builder()
			.agentId(agentId)
			.threadId(threadId)
			.text(text)
			.error(true)
			.textType(TextType.TEXT)
			.build();
	}

	public static GraphNodeResponse complete(String agentId, String threadId) {
		return GraphNodeResponse.builder()
			.agentId(agentId)
			.threadId(threadId)
			.complete(true)
			.textType(TextType.TEXT)
			.build();
	}

	public static GraphNodeResponse finalAnswer(String agentId, String threadId, String text) {
		return GraphNodeResponse.builder()
			.agentId(agentId)
			.threadId(threadId)
			.eventType(GraphEventType.FINAL_ANSWER)
			.textType(TextType.TEXT)
			.text(text)
			.build();
	}

}
