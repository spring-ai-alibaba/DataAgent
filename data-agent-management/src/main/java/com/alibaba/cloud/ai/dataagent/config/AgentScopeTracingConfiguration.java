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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.properties.AgentScopeObservabilityProperties;
import io.agentscope.core.tracing.TracerRegistry;
import io.agentscope.core.tracing.telemetry.TelemetryTracer;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AgentScopeObservabilityProperties.class)
public class AgentScopeTracingConfiguration implements SmartInitializingSingleton {

	private final AgentScopeObservabilityProperties properties;

	private final OpenTelemetryConfig openTelemetryConfig;

	@Qualifier("langfuseTracer")
	private final Tracer langfuseTracer;

	@Qualifier("agentScopeLocalTracer")
	private final Tracer agentScopeLocalTracer;

	@Bean("agentScopeTracer")
	@Primary
	public Tracer agentScopeTracer() {
		return selectTracer();
	}

	@Override
	public void afterSingletonsInstantiated() {
		if (!properties.isEnabled()) {
			log.info("AgentScope native tracing is disabled by configuration.");
			return;
		}

		TracerRegistry.register(TelemetryTracer.builder().tracer(selectTracer()).build());
		if (properties.isUseLangfuseTracer() && openTelemetryConfig.isEnabled()) {
			log.info(
					"AgentScope native tracing initialized with local trace cache and Langfuse OpenTelemetry exporter.");
			return;
		}

		log.info("AgentScope native tracing initialized with local trace cache only.");
	}

	private Tracer selectTracer() {
		if (properties.isUseLangfuseTracer() && openTelemetryConfig.isEnabled()) {
			return langfuseTracer;
		}
		return agentScopeLocalTracer;
	}

}
