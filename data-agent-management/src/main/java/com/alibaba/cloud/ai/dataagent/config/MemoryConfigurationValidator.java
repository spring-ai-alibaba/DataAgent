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

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Rejects memory modes whose security prerequisites are not implemented by the current
 * application.
 */
@Component
@RequiredArgsConstructor
public class MemoryConfigurationValidator implements InitializingBean {

	private final DataAgentProperties properties;

	@Override
	public void afterPropertiesSet() {
		if (properties.getMemory().getMaxContextLength() < 512) {
			throw new IllegalStateException("memory.max-context-length must be at least 512 characters");
		}
		if (properties.getMemory().getSummaryCacheMaxEntries() < 1) {
			throw new IllegalStateException("memory.summary-cache-max-entries must be at least 1");
		}
		if (properties.getMemory().isUserScopeEnabled()) {
			throw new IllegalStateException("USER_AGENT memory requires a trusted server-derived user identity; "
					+ "the current application only authenticates agents, so user-scope-enabled must remain false");
		}
	}

}
