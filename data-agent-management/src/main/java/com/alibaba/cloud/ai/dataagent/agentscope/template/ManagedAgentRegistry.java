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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ManagedAgentRegistry {

	private final Map<String, ManagedAgent> agentsByType;

	public ManagedAgentRegistry(List<ManagedAgent> managedAgents) {
		this.agentsByType = managedAgents.stream()
			.collect(Collectors.toUnmodifiableMap(agent -> normalize(agent.getAgentType()), Function.identity()));
	}

	public ManagedAgent getRequired() {
		ManagedAgent managedAgent = this.agentsByType.get(normalize(CommonAgent.AGENT_TYPE));
		if (managedAgent == null) {
			throw new IllegalStateException("ManagedAgent registry missing type: " + CommonAgent.AGENT_TYPE);
		}
		return managedAgent;
	}

	private String normalize(String agentType) {
		return agentType == null ? "" : agentType.toLowerCase(Locale.ROOT);
	}

}
