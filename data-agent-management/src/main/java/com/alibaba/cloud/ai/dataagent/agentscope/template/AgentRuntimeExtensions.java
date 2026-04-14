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
package com.alibaba.cloud.ai.dataagent.agentscope.template;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import java.util.List;
import java.util.Map;

public record AgentRuntimeExtensions(Toolkit toolkit, Memory memory, ToolExecutionContext toolExecutionContext,
		List<Hook> hooks, Map<String, Object> attributes) {

	private static final AgentRuntimeExtensions EMPTY = new AgentRuntimeExtensions(null, null,
			ToolExecutionContext.empty(), List.of(), Map.of());

	public AgentRuntimeExtensions {
		toolExecutionContext = toolExecutionContext == null ? ToolExecutionContext.empty() : toolExecutionContext;
		hooks = hooks == null ? List.of() : List.copyOf(hooks);
		attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
	}

	public static AgentRuntimeExtensions empty() {
		return EMPTY;
	}

}
