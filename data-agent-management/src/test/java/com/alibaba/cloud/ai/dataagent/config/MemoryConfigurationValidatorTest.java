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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryConfigurationValidatorTest {

	@Test
	void safeDefaultsPass() {
		assertThatCode(() -> new MemoryConfigurationValidator(new DataAgentProperties()).afterPropertiesSet())
			.doesNotThrowAnyException();
	}

	@Test
	void userScopeCannotStartWithoutServerIdentity() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getMemory().setUserScopeEnabled(true);

		assertThatThrownBy(() -> new MemoryConfigurationValidator(properties).afterPropertiesSet())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("trusted server-derived user identity");
	}

	@Test
	void contextBudgetCannotBeConfiguredBelowSafeEnvelope() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getMemory().setMaxContextLength(511);

		assertThatThrownBy(() -> new MemoryConfigurationValidator(properties).afterPropertiesSet())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("max-context-length");
	}

	@Test
	void summaryCacheMustHaveABoundedPositiveCapacity() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getMemory().setSummaryCacheMaxEntries(0);

		assertThatThrownBy(() -> new MemoryConfigurationValidator(properties).afterPropertiesSet())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("summary-cache-max-entries");
	}

}
