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
package com.alibaba.cloud.ai.dataagent.properties;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = AgentScopeObservabilityProperties.CONFIG_PREFIX)
public class AgentScopeObservabilityProperties {

	public static final String CONFIG_PREFIX = Constant.PROJECT_PROPERTIES_PREFIX + ".agentscope.observability";

	/**
	 * 是否启用 AgentScope 原生 tracing。
	 */
	private boolean enabled = true;

	/**
	 * 是否优先复用现有 Langfuse OpenTelemetry tracer 作为导出通道。
	 */
	private boolean useLangfuseTracer = true;

}
