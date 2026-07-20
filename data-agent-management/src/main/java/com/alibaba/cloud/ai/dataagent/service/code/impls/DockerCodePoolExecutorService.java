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
import com.alibaba.cloud.ai.dataagent.service.code.CodePoolExecutorService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.dockerjava.api.model.HostConfig.newHostConfig;

/**
 * Runs concurrent Python tasks in one long-lived Docker container. Each task uses a
 * UUID-scoped working directory and a dedicated Docker exec session.
 *
 * @author vlsmb
 * @since 2025/7/12
 */
@Slf4j
public class DockerCodePoolExecutorService implements CodePoolExecutorService, AutoCloseable {

	private static final int MAX_LOG_SIZE = 5 * 1024 * 1024;

	private static final String CONTAINER_TASK_ROOT = "/tmp/dataagent-tasks";

	private final CodeExecutorProperties properties;

	private final DockerClient dockerClient;

	private final boolean isRemote;

	private final Semaphore executionSlots;

	private final Object containerLock = new Object();

	private final AtomicBoolean closed = new AtomicBoolean();

	private volatile String singletonContainerId;

	public DockerCodePoolExecutorService(CodeExecutorProperties properties, DockerClient dockerClient,
			boolean isRemote) {
		this.properties = Objects.requireNonNull(properties, "properties");
		this.dockerClient = Objects.requireNonNull(dockerClient, "dockerClient");
		this.isRemote = isRemote;
		this.executionSlots = new Semaphore(Math.max(1, properties.getMaxConcurrentTasks()), true);
		log.info("Docker singleton executor initialized. Mode: {}, maxConcurrentTasks={}",
				this.isRemote ? "Remote" : "Local", Math.max(1, properties.getMaxConcurrentTasks()));
	}

	HostConfig createHostConfig() {
		HostConfig config = newHostConfig().withMemory(this.properties.getLimitMemory() * 1024L * 1024L)
			.withCpuCount(this.properties.getCpuCore())
			.withCapDrop(Capability.ALL)
			.withAutoRemove(false)
			.withTmpFs(Map.of("/tmp", ""))
			.withNetworkMode(this.properties.getNetworkMode());
		if (StringUtils.hasText(this.properties.getSecurityOpt())) {
			config.withSecurityOpts(List.of(this.properties.getSecurityOpt()));
		}
		return config;
	}

	@Override
	public TaskResponse runTask(TaskRequest request) {
		if (closed.get()) {
			return TaskResponse.exception("Docker code executor is closed");
		}

		boolean acquired = false;
		try {
			executionSlots.acquire();
			acquired = true;
			if (closed.get()) {
				return TaskResponse.exception("Docker code executor is closed");
			}
			String containerId = ensureSingletonContainer();
			return executeTask(request, containerId);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return TaskResponse.exception("Interrupted while waiting to execute Python code");
		}
		catch (Exception exception) {
			log.error("Error executing Docker task: {}", exception.getMessage(), exception);
			invalidateContainerIfStopped();
			return TaskResponse.exception(exception.getMessage());
		}
		finally {
			if (acquired) {
				executionSlots.release();
			}
		}
	}

	private String ensureSingletonContainer() {
		String current = singletonContainerId;
		if (isContainerRunning(current)) {
			return current;
		}

		synchronized (containerLock) {
			current = singletonContainerId;
			if (isContainerRunning(current)) {
				return current;
			}
			removeContainerQuietly(current);
			cleanupContainersFromPreviousProcesses();

			CreateContainerResponse container = dockerClient.createContainerCmd(properties.getImageName())
				.withName(singletonContainerName())
				.withWorkingDir("/")
				.withHostConfig(createHostConfig())
				.withCmd("sh", "-c", singletonContainerStartupCommand())
				.exec();
			dockerClient.startContainerCmd(container.getId()).exec();
			singletonContainerId = container.getId();
			log.info("Started Docker singleton container. containerId={}, name={}", container.getId(),
					singletonContainerName());
			return container.getId();
		}
	}

	private TaskResponse executeTask(TaskRequest request, String containerId) throws Exception {
		String executionId = UUID.randomUUID().toString();
		String containerTaskDir = CONTAINER_TASK_ROOT + "/" + executionId;
		try {
			runControlCommand(containerId, "mkdir -p " + containerTaskDir);
			writeContainerFile(containerId, containerTaskDir, "script.py", request.code());
			writeContainerFile(containerId, containerTaskDir, "requirements.txt", request.requirement());
			writeContainerFile(containerId, containerTaskDir, "input_data.txt", request.input());
			log.info("Executing Python task. containerId={}, executionId={}, inputBytes={}", containerId, executionId,
					request.input() == null ? 0 : request.input().getBytes(StandardCharsets.UTF_8).length);

			ExecResult result = runExec(containerId, containerTaskDir, buildTaskCommand());
			if (result.exitCode() != 0) {
				log.error("Python task failed. containerId={}, executionId={}, exitCode={}, stderr={}", containerId,
						executionId, result.exitCode(), result.stderr());
				return TaskResponse.failure(result.stdout(), result.stderr());
			}
			return TaskResponse.success(result.stdout());
		}
		finally {
			try {
				runControlCommand(containerId, "rm -rf " + containerTaskDir);
			}
			catch (Exception cleanupFailure) {
				log.warn("Failed to remove container task directory {}: {}", containerTaskDir,
						cleanupFailure.getMessage());
			}
		}
	}

	String buildTaskCommand() {
		return String.format(
				"if [ -s requirements.txt ]; then mkdir -p packages && pip3 install --no-cache-dir --target packages -r requirements.txt > /dev/null; fi && "
						+ "PYTHONPATH=\"$PWD/packages${PYTHONPATH:+:$PYTHONPATH}\" timeout -s SIGKILL %s python3 -u script.py < input_data.txt",
				properties.getCodeTimeout());
	}

	private void writeContainerFile(String containerId, String taskDir, String fileName, String content)
			throws InterruptedException {
		byte[] bytes = Objects.requireNonNullElse(content, "").getBytes(StandardCharsets.UTF_8);
		String command = bytes.length == 0 ? ": > " + fileName : "head -c " + bytes.length + " > " + fileName;
		ExecResult result = runExec(containerId, taskDir, command, bytes.length == 0 ? null : bytes);
		if (result.exitCode() != 0) {
			throw new IllegalStateException("Failed to write " + fileName + ": " + result.stderr());
		}
	}

	private ExecResult runExec(String containerId, String workingDir, String command) throws InterruptedException {
		return runExec(containerId, workingDir, command, null);
	}

	private ExecResult runExec(String containerId, String workingDir, String command, byte[] stdin)
			throws InterruptedException {
		ExecCreateCmdResponse exec = dockerClient.execCreateCmd(containerId)
			.withAttachStdout(true)
			.withAttachStderr(true)
			.withAttachStdin(stdin != null)
			.withWorkingDir(workingDir)
			.withCmd("sh", "-c", command)
			.exec();

		StringBuilder stdout = new StringBuilder();
		StringBuilder stderr = new StringBuilder();
		ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
			@Override
			public void onNext(Frame frame) {
				String payload = new String(frame.getPayload(), StandardCharsets.UTF_8);
				if (frame.getStreamType() == StreamType.STDERR) {
					appendWithLimit(stderr, payload, MAX_LOG_SIZE);
				}
				else {
					appendWithLimit(stdout, payload, MAX_LOG_SIZE);
				}
			}
		};

		var startCommand = dockerClient.execStartCmd(exec.getId());
		if (stdin != null) {
			startCommand.withStdIn(new ByteArrayInputStream(stdin));
		}
		boolean completed = startCommand.exec(callback)
			.awaitCompletion(properties.getContainerTimeout(), TimeUnit.SECONDS);
		if (!completed) {
			try {
				callback.close();
			}
			catch (IOException closeFailure) {
				log.debug("Failed to close timed-out Docker exec callback: {}", closeFailure.getMessage());
			}
			throw new IllegalStateException("Docker exec timed out");
		}

		InspectExecResponse inspect = dockerClient.inspectExecCmd(exec.getId()).exec();
		Long exitCode = inspect.getExitCodeLong();
		if (exitCode == null) {
			throw new IllegalStateException("Docker exec completed without an exit code");
		}
		return new ExecResult(exitCode.intValue(), stdout.toString(), stderr.toString());
	}

	private void runControlCommand(String containerId, String command) throws InterruptedException {
		ExecResult result = runExec(containerId, "/", command);
		if (result.exitCode() != 0) {
			throw new IllegalStateException("Container command failed: " + result.stderr());
		}
	}

	private boolean isContainerRunning(String containerId) {
		if (!StringUtils.hasText(containerId)) {
			return false;
		}
		try {
			InspectContainerResponse response = dockerClient.inspectContainerCmd(containerId).exec();
			return Boolean.TRUE.equals(response.getState().getRunning());
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

	private void invalidateContainerIfStopped() {
		String current = singletonContainerId;
		if (!isContainerRunning(current)) {
			synchronized (containerLock) {
				if (Objects.equals(singletonContainerId, current)) {
					removeContainerQuietly(current);
					singletonContainerId = null;
				}
			}
		}
	}

	private void cleanupContainersFromPreviousProcesses() {
		List<Container> containers = dockerClient.listContainersCmd()
			.withShowAll(true)
			.withNameFilter(List.of(properties.getContainerNamePrefix()))
			.exec();
		for (Container container : containers) {
			if (hasManagedName(container)) {
				removeContainerQuietly(container.getId());
			}
		}
	}

	private boolean hasManagedName(Container container) {
		if (container.getNames() == null) {
			return false;
		}
		String expectedPrefix = "/" + properties.getContainerNamePrefix();
		for (String name : container.getNames()) {
			if (name.startsWith(expectedPrefix)) {
				return true;
			}
		}
		return false;
	}

	String singletonContainerName() {
		return properties.getContainerNamePrefix() + "singleton";
	}

	String singletonContainerStartupCommand() {
		return "mkdir -p " + CONTAINER_TASK_ROOT + " && while true; do sleep 3600; done";
	}

	private void removeContainerQuietly(String containerId) {
		if (!StringUtils.hasText(containerId)) {
			return;
		}
		try {
			dockerClient.removeContainerCmd(containerId).withForce(true).exec();
			log.info("Removed Docker container: {}", containerId);
		}
		catch (RuntimeException exception) {
			log.debug("Container {} could not be removed: {}", containerId, exception.getMessage());
		}
	}

	private void appendWithLimit(StringBuilder builder, String payload, int limit) {
		int remaining = limit - builder.length();
		if (remaining > 0) {
			builder.append(payload, 0, Math.min(payload.length(), remaining));
		}
		if (payload.length() > remaining && !builder.toString().endsWith("...[Output truncated]")) {
			builder.append("\n...[Output truncated]");
		}
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) {
			return;
		}
		synchronized (containerLock) {
			removeContainerQuietly(singletonContainerId);
			singletonContainerId = null;
		}
		try {
			dockerClient.close();
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to close Docker client", exception);
		}
	}

	private record ExecResult(int exitCode, String stdout, String stderr) {
	}

}
