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
package com.alibaba.cloud.ai.dataagent.service.code.impls;

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.service.code.local.ExecutableProgramLocator;
import com.alibaba.cloud.ai.dataagent.service.code.local.ExecutionTimeoutParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalCodePoolExecutorServiceTest {

	@Mock
	private ExecutableProgramLocator programLocator;

	private final ExecutionTimeoutParser timeoutParser = new ExecutionTimeoutParser();

	@Test
	void constructor_nonPythonExecutable_throwsSpecificConfigurationFailure() {
		when(programLocator.findFirst(anyList())).thenReturn(Optional.of("python-test"), Optional.empty());

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> new LocalCodePoolExecutorService(properties(), programLocator, timeoutParser));

		assertTrue(error.getMessage().contains("Python 3"));
	}

	@Test
	void constructor_pythonUnavailable_throwsSpecificConfigurationFailure() {
		when(programLocator.findFirst(anyList())).thenReturn(Optional.empty());

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> new LocalCodePoolExecutorService(properties(), programLocator, timeoutParser));

		assertTrue(error.getMessage().contains("Python"));
	}

	private CodeExecutorProperties properties() {
		CodeExecutorProperties properties = new CodeExecutorProperties();
		properties.setContainerNamePrefix("test-local-");
		properties.setCodeTimeout("60s");
		properties.setContainerTimeout(5L);
		properties.setTaskQueueSize(2);
		properties.setCoreContainerNum(1);
		properties.setTempContainerNum(1);
		properties.setCoreThreadSize(1);
		properties.setMaxThreadSize(1);
		properties.setThreadQueueSize(2);
		return properties;
	}

}
