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
package com.alibaba.cloud.ai.dataagent.agent.runtime;

import com.alibaba.cloud.ai.dataagent.agent.dto.GraphRequest;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

public class HumanFeedbackHook implements Hook {

	private final boolean pauseAfterPlanning;

	private final AtomicBoolean replayRequested;

	private final String feedbackDirective;

	private HumanFeedbackHook(boolean pauseAfterPlanning, boolean replayRequested, String feedbackDirective) {
		this.pauseAfterPlanning = pauseAfterPlanning;
		this.replayRequested = new AtomicBoolean(replayRequested);
		this.feedbackDirective = feedbackDirective;
	}

	public static HumanFeedbackHook from(GraphRequest request) {
		if (request.isNl2sqlOnly()) {
			return null;
		}
		boolean hasFeedbackContent = StringUtils.hasText(request.getHumanFeedbackContent());
		boolean requiresReplay = hasFeedbackContent || request.isRejectedPlan();
		boolean requiresPause = request.isHumanFeedback() && !requiresReplay;
		if (!requiresPause && !requiresReplay) {
			return null;
		}
		return new HumanFeedbackHook(requiresPause, requiresReplay, buildDirective(request));
	}

	@Override
	public <T extends HookEvent> Mono<T> onEvent(T event) {
		if (!(event instanceof PostReasoningEvent postReasoningEvent)) {
			return Mono.just(event);
		}
		if (pauseAfterPlanning) {
			postReasoningEvent.stopAgent();
			return Mono.just(event);
		}
		if (replayRequested.compareAndSet(true, false)) {
			postReasoningEvent.gotoReasoning(List.of(Msg.builder()
				.name("human-review")
				.role(MsgRole.SYSTEM)
				.textContent(feedbackDirective)
				.build()));
		}
		return Mono.just(event);
	}

	private static String buildDirective(GraphRequest request) {
		StringBuilder builder = new StringBuilder("Human review directive:");
		if (request.isRejectedPlan()) {
			builder.append("\n- The previous plan was rejected. Re-plan before continuing.");
		}
		if (StringUtils.hasText(request.getHumanFeedbackContent())) {
			builder.append("\n- Feedback: ").append(request.getHumanFeedbackContent());
		}
		return builder.toString();
	}

}
