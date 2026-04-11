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
import com.alibaba.cloud.ai.dataagent.agent.session.AgentSessionRegistry;
import com.alibaba.cloud.ai.dataagent.management.service.chat.ChatMessageService;
import com.alibaba.cloud.ai.dataagent.management.service.chat.ChatSessionService;
import io.agentscope.core.hook.Hook;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentScopeHookFactory {

	private final ChatSessionService chatSessionService;

	private final ChatMessageService chatMessageService;

	private final AgentSessionRegistry sessionRegistry;

	public List<Hook> create(GraphRequest request, @Nullable AgentRuntimeEventPublisher eventPublisher) {
		List<Hook> hooks = new ArrayList<>();
		if (eventPublisher != null) {
			hooks.add(new AgentScopeStreamingHook(request.getAgentId(), request.getThreadId(), request.isNl2sqlOnly(),
					eventPublisher));
		}
		hooks.add(new AgentScopeMemoryPersistenceHook(request.getThreadId(), request.getRuntimeRequestId(), sessionRegistry,
				chatSessionService, chatMessageService));
		HumanFeedbackHook humanFeedbackHook = HumanFeedbackHook.from(request);
		if (humanFeedbackHook != null) {
			hooks.add(humanFeedbackHook);
		}
		return hooks;
	}

}
