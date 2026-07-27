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

import com.alibaba.cloud.ai.dataagent.service.code.PythonCodeExecutorService;
import com.alibaba.cloud.ai.sandbox.tools.base.SaaBasePythonRunner;
import io.agentscope.runtime.sandbox.box.BaseSandbox;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SaaSandboxTaskRunner {

	private final SaaSandboxRuntime sandboxRuntime;

	private final PythonSandboxBootstrapBuilder bootstrapBuilder;

	private final SandboxExecutionResultParser resultParser;

	public SaaSandboxTaskRunner(SaaSandboxRuntime sandboxRuntime, PythonSandboxBootstrapBuilder bootstrapBuilder,
			SandboxExecutionResultParser resultParser) {
		this.sandboxRuntime = sandboxRuntime;
		this.bootstrapBuilder = bootstrapBuilder;
		this.resultParser = resultParser;
	}

	public PythonCodeExecutorService.TaskResponse run(PythonCodeExecutorService.TaskRequest request) {
		try (BaseSandbox sandbox = new BaseSandbox(sandboxRuntime.getOrStart(), "dataagent",
				UUID.randomUUID().toString())) {
			SaaBasePythonRunner pythonRunner = new SaaBasePythonRunner();
			pythonRunner.setSandbox(sandbox);

			if (!request.dependencies().isEmpty()) {
				SandboxExecutionResult installResult = run(pythonRunner,
						bootstrapBuilder.buildDependencyInstaller(request.dependencies()));
				if (!installResult.success()) {
					return PythonCodeExecutorService.TaskResponse.failure(installResult.stdout(),
							formatError(installResult));
				}
			}

			SandboxExecutionResult executionResult = run(pythonRunner,
					bootstrapBuilder.buildCodeExecution(request.code(), request.input()));
			if (executionResult.success()) {
				return PythonCodeExecutorService.TaskResponse.success(executionResult.stdout());
			}
			return PythonCodeExecutorService.TaskResponse.failure(executionResult.stdout(),
					formatError(executionResult));
		}
		catch (Exception ex) {
			return PythonCodeExecutorService.TaskResponse.exception(ex.getMessage());
		}
	}

	private SandboxExecutionResult run(SaaBasePythonRunner pythonRunner, String code) {
		SaaBasePythonRunner.RunPythonToolResponse response = pythonRunner
			.apply(new SaaBasePythonRunner.RunPythonToolRequest(code), null);
		return resultParser.parse(response.output().result());
	}

	private String formatError(SandboxExecutionResult result) {
		String errorType = result.errorType() == null ? "PythonError" : result.errorType();
		String errorMessage = result.errorMessage() == null ? "" : ": " + result.errorMessage();
		String stderr = result.stderr() == null ? "" : "\n" + result.stderr();
		return errorType + errorMessage + stderr;
	}

}
