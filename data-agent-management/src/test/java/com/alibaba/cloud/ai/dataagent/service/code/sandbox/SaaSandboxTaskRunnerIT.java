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

/**
 * Requires a running local Docker daemon and the configured AgentScope base image.
 */
class SaaSandboxTaskRunnerIT {

	@Test
	void run_installsDeclaredDependencyAndExecutesPythonInSaaSandbox() {
		CodeExecutorProperties properties = new CodeExecutorProperties();
		SaaSandboxRuntime runtime = new SaaSandboxRuntime(properties);
		try {
			PythonSandboxBootstrapBuilder builder = new PythonSandboxBootstrapBuilder(properties);
			SandboxExecutionResultParser parser = new SandboxExecutionResultParser(properties);
			SaaSandboxTaskRunner runner = new SaaSandboxTaskRunner(runtime, builder, parser);
			PythonCodeExecutorService.TaskRequest request = new PythonCodeExecutorService.TaskRequest("""
					import json
					import six
					import sys
					input_data = json.load(sys.stdin)
					print(json.dumps({"six_version": six.__version__, "rows": len(input_data)}))
					""", "[{\"id\":1}]", List.of("six==1.17.0"));

			PythonCodeExecutorService.TaskResponse response = runner.run(request);

			assertThat(response.isSuccess()).as(response.toString()).isTrue();
			assertThat(response.stdOut()).contains("\"six_version\": \"1.17.0\"").contains("\"rows\": 1");
		}
		finally {
			runtime.close();
		}
	}

}
