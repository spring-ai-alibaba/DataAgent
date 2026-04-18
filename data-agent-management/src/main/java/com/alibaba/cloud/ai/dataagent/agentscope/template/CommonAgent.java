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

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.ExecutionConfig;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CommonAgent implements ManagedAgent {

	public static final String AGENT_TYPE = "commonagent";

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

	private static final int DEFAULT_MAX_ITERS = 10;

	private static final ExecutionConfig DEFAULT_MODEL_EXECUTION_CONFIG = ExecutionConfig.builder()
		.timeout(Duration.ofMinutes(2))
		.maxAttempts(2)
		.build();

	private static final ExecutionConfig DEFAULT_TOOL_EXECUTION_CONFIG = ExecutionConfig.builder()
		.timeout(Duration.ofSeconds(30))
		.maxAttempts(1)
		.build();

	@Override
	public String getAgentType() {
		return AGENT_TYPE;
	}

	@Override
	public Msg run(AgentRunContext context) {
		Objects.requireNonNull(context, "AgentRunContext must not be null");
		Objects.requireNonNull(context.model(), "AgentScope model must not be null");
		AgentRuntimeExtensions extensions = context.extensions();
		ReActAgent.Builder builder = ReActAgent.builder()
			.name(AGENT_TYPE)
			.sysPrompt(defaultSystemPrompt(context.systemPrompt(), extensions.skillInstructions()))
			.model(context.model())
			.maxIters(DEFAULT_MAX_ITERS)
			.modelExecutionConfig(DEFAULT_MODEL_EXECUTION_CONFIG)
			.toolExecutionConfig(DEFAULT_TOOL_EXECUTION_CONFIG);
		if (extensions.toolkit() != null) {
			builder.toolkit(extensions.toolkit());
		}
		if (extensions.memory() != null) {
			builder.memory(extensions.memory());
		}
		if (extensions.toolExecutionContext() != null) {
			builder.toolExecutionContext(extensions.toolExecutionContext());
		}
		if (extensions.skillBox() != null) {
			builder.skillBox(extensions.skillBox());
		}
		List<Hook> hooks = extensions.hooks();
		if (!hooks.isEmpty()) {
			builder.hooks(hooks);
		}
		ReActAgent agent = builder.build();
		return agent
			.call(Msg.builder()
				.name("user")
				.role(MsgRole.USER)
				.textContent(defaultUserPrompt(context.userPrompt()))
				.build())
			.block(resolveTimeout(context.timeout()));
	}

	private String defaultSystemPrompt(String systemPrompt, String skillInstructions) {
		String basePrompt = StringUtils.hasText(systemPrompt) ? systemPrompt.trim() : "";
		String runtimeSkillPrompt = StringUtils.hasText(skillInstructions) ? skillInstructions.trim() : "";
		if (!StringUtils.hasText(basePrompt)) {
			return runtimeSkillPrompt;
		}
		if (!StringUtils.hasText(runtimeSkillPrompt)) {
			return basePrompt;
		}
		return basePrompt + System.lineSeparator() + System.lineSeparator() + runtimeSkillPrompt;
	}

	private String defaultUserPrompt(String userPrompt) {
		return userPrompt == null ? "" : userPrompt;
	}

	private Duration resolveTimeout(Duration timeout) {
		return timeout == null ? DEFAULT_TIMEOUT : timeout;
	}

}
