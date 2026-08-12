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
package com.alibaba.cloud.ai.dataagent.service.code;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PythonCodeExecutorServiceTest {

	@Test
	void taskRequest_copiesDependenciesAndUsesEmptyListForNull() {
		PythonCodeExecutorService.TaskRequest empty = new PythonCodeExecutorService.TaskRequest("print(1)", "[]", null);
		PythonCodeExecutorService.TaskRequest populated = new PythonCodeExecutorService.TaskRequest("print(1)", "[]",
				List.of("pandas>=2,<3"));

		assertThat(empty.dependencies()).isEmpty();
		assertThat(populated.dependencies()).containsExactly("pandas>=2,<3");
	}

	@Test
	void taskResponse_distinguishesPythonFailureFromInfrastructureFailure() {
		PythonCodeExecutorService.TaskResponse pythonFailure = PythonCodeExecutorService.TaskResponse.failure("partial",
				"ValueError");
		PythonCodeExecutorService.TaskResponse infrastructureFailure = PythonCodeExecutorService.TaskResponse
			.exception("Docker unavailable");

		assertThat(pythonFailure.executionSuccessButResultFailed()).isTrue();
		assertThat(infrastructureFailure.executionSuccessButResultFailed()).isFalse();
		assertThat(infrastructureFailure.exceptionMsg()).contains("Docker unavailable");
	}

}
