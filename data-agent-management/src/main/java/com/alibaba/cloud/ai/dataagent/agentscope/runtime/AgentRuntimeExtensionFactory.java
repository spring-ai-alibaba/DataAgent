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
package com.alibaba.cloud.ai.dataagent.agentscope.runtime;

import com.alibaba.cloud.ai.dataagent.agentscope.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.agentscope.template.AgentRuntimeExtensions;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentRuntimeExtensionFactory {

	private final AgentScopeToolkitFactory toolkitFactory;

	private final AgentScopeMemoryFactory memoryFactory;

	private final AgentScopeHookFactory hookFactory;

	private final AgentScopeSkillBoxFactory skillBoxFactory;

	public AgentRuntimeExtensions create(GraphRequest request, @Nullable AgentRuntimeEventPublisher eventPublisher,
			Map<String, ToolCallback> toolCallbacks) {
		Toolkit toolkit = toolkitFactory.buildToolkit(toolCallbacks);
		SkillBox skillBox = skillBoxFactory.create(request.getAgentId(), toolkit);
		Memory memory = memoryFactory.create(request.getThreadId());
		AgentRuntimeRequestMetadata requestMetadata = new AgentRuntimeRequestMetadata(request.getAgentId(),
				request.getThreadId(), request.isNl2sqlOnly());
		ToolExecutionContext toolExecutionContext = ToolExecutionContext.builder()
			.register(requestMetadata)
			.register("graphRequest", request)
			.build();
		List<Hook> hooks = hookFactory.create(request, eventPublisher);
		Map<String, Object> attributes = new HashMap<>();
		attributes.put("threadId", request.getThreadId());
		return new AgentRuntimeExtensions(toolkit, memory, toolExecutionContext, hooks, attributes, skillBox, "");
	}

}
