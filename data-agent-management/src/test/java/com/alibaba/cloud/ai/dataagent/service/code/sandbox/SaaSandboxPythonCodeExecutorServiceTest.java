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
package com.alibaba.cloud.ai.dataagent.service.code.sandbox;

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.service.code.PythonCodeExecutorService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaaSandboxPythonCodeExecutorServiceTest {

	@Test
	void runTask_delegatesAcceptedTaskToSandboxRunner() {
		CodeExecutorProperties properties = new CodeExecutorProperties();
		SaaSandboxTaskRunner taskRunner = mock(SaaSandboxTaskRunner.class);
		PythonCodeExecutorService.TaskRequest request = new PythonCodeExecutorService.TaskRequest("print(1)", "[]",
				List.of());
		when(taskRunner.run(request)).thenReturn(PythonCodeExecutorService.TaskResponse.success("1"));

		try (TestExecutor service = new TestExecutor(properties, taskRunner)) {
			assertThat(service.runTask(request).stdOut()).isEqualTo("1");
		}

		verify(taskRunner).run(request);
	}

	@Test
	void runTask_rejectsOversizedCodeBeforeCreatingSandbox() {
		CodeExecutorProperties properties = new CodeExecutorProperties();
		properties.getSandbox().setMaxCodeBytes(3);
		SaaSandboxTaskRunner taskRunner = mock(SaaSandboxTaskRunner.class);

		try (TestExecutor service = new TestExecutor(properties, taskRunner)) {
			PythonCodeExecutorService.TaskResponse response = service
				.runTask(new PythonCodeExecutorService.TaskRequest("print(1)", "[]", List.of()));
			assertThat(response.isSuccess()).isFalse();
			assertThat(response.exceptionMsg()).contains("size limit");
		}
	}

	private static final class TestExecutor extends SaaSandboxPythonCodeExecutorService implements AutoCloseable {

		private TestExecutor(CodeExecutorProperties properties, SaaSandboxTaskRunner taskRunner) {
			super(properties, taskRunner);
		}

	}

}
