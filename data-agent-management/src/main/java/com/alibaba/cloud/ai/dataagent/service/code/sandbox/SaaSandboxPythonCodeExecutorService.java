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
import com.alibaba.cloud.ai.dataagent.service.code.sandbox.runtime.SaaSandboxTaskRunner;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class SaaSandboxPythonCodeExecutorService implements PythonCodeExecutorService {

	private final CodeExecutorProperties properties;

	private final SaaSandboxTaskRunner taskRunner;

	private final ThreadPoolExecutor executor;

	public SaaSandboxPythonCodeExecutorService(CodeExecutorProperties properties, SaaSandboxTaskRunner taskRunner) {
		this.properties = properties;
		this.taskRunner = taskRunner;
		int concurrency = properties.getSandbox().getMaxConcurrency();
		this.executor = new ThreadPoolExecutor(concurrency, concurrency, 0L, TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<>(properties.getSandbox().getQueueCapacity()),
				new ThreadPoolExecutor.AbortPolicy());
	}

	@Override
	public TaskResponse runTask(TaskRequest request) {
		try {
			validateSize("Python code", request.code(), properties.getSandbox().getMaxCodeBytes());
			validateSize("Python input", request.input(), properties.getSandbox().getMaxInputBytes());
		}
		catch (IllegalArgumentException ex) {
			return TaskResponse.exception(ex.getMessage());
		}

		Future<TaskResponse> future;
		try {
			future = executor.submit(() -> taskRunner.run(request));
		}
		catch (RuntimeException ex) {
			return TaskResponse.exception("Python sandbox capacity is exhausted");
		}

		Duration taskTimeout = properties.getSandbox()
			.getDependencyInstallTimeout()
			.plus(properties.getCodeTimeout())
			.plusSeconds(30);
		try {
			return future.get(taskTimeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			future.cancel(true);
			return TaskResponse.exception("Python sandbox task timed out");
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return TaskResponse.exception("Python sandbox task was interrupted");
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			return TaskResponse.exception(cause == null ? ex.getMessage() : cause.getMessage());
		}
	}

	private void validateSize(String label, String value, int maximumBytes) {
		if (value == null) {
			throw new IllegalArgumentException(label + " must not be null");
		}
		if (value.getBytes(StandardCharsets.UTF_8).length > maximumBytes) {
			throw new IllegalArgumentException(label + " exceeds the configured size limit");
		}
	}

	@PreDestroy
	public void close() {
		executor.shutdownNow();
	}

}
