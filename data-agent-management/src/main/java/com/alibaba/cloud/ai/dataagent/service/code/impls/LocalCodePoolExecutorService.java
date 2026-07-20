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
import com.alibaba.cloud.ai.dataagent.service.code.local.ExecutableProgramLocator;
import com.alibaba.cloud.ai.dataagent.service.code.local.ExecutionTimeoutParser;
import com.alibaba.cloud.ai.dataagent.service.code.local.PathExecutableProgramLocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 使用本地Python3环境运行代码的实现类，要求本地的Python3需要有pandas等数据分析库。
 *
 * @author vlsmb
 * @since 2025/8/23
 */
@Slf4j
public class LocalCodePoolExecutorService extends AbstractCodePoolExecutorService implements CodePoolExecutorService {

	private final ConcurrentHashMap<String, Path> containers;

	private static final List<String> PYTHON_NAMES = List.of("python3", "pypy3", "py3", "python", "pypy");

	private static final List<String> PIP_NAMES = List.of("pip3", "pip");

	private final String pythonExecutable;

	private final String pipExecutable;

	private final ExecutionTimeoutParser timeoutParser;

	// 对于本地运行这个实现类，“容器”为临时文件夹
	public LocalCodePoolExecutorService(CodeExecutorProperties properties) {
		this(properties, new PathExecutableProgramLocator(), new ExecutionTimeoutParser());
	}

	LocalCodePoolExecutorService(CodeExecutorProperties properties, ExecutableProgramLocator programLocator,
			ExecutionTimeoutParser timeoutParser) {
		this(properties, timeoutParser, locatePrograms(programLocator));
	}

	private LocalCodePoolExecutorService(CodeExecutorProperties properties, ExecutionTimeoutParser timeoutParser,
			LocalPrograms programs) {
		super(properties);
		this.containers = new ConcurrentHashMap<>();
		this.pythonExecutable = programs.pythonExecutable();
		this.pipExecutable = programs.pipExecutable();
		this.timeoutParser = timeoutParser;
	}

	@Override
	protected String createNewContainer() throws Exception {
		Path container = Files.createTempDirectory(this.properties.getContainerNamePrefix());
		String containerId = container.toString();
		this.containers.put(containerId, container);
		return containerId;
	}

	@Override
	protected TaskResponse execTaskInContainer(TaskRequest request, String containerId) {
		Path container = this.containers.get(containerId);

		// 写入Py代码和标准输入
		Path scriptFile = container.resolve("script.py");
		Path stdinFile = container.resolve("stdin.txt");
		Path requirementFile = container.resolve("requirements.txt");
		try {
			Files.write(scriptFile, Optional.ofNullable(request.code()).orElse("").getBytes(StandardCharsets.UTF_8));
			Files.write(stdinFile, Optional.ofNullable(request.input()).orElse("").getBytes(StandardCharsets.UTF_8));
			Files.write(requirementFile,
					Optional.ofNullable(request.requirement()).orElse("").getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception e) {
			log.error("Create temp file failed: {}", e.getMessage(), e);
			return TaskResponse.exception(e.getMessage());
		}

		// 如果有requirements，则先安装依赖
		if (this.pipExecutable != null && StringUtils.hasText(request.requirement())) {
			ProcessBuilder pip = new ProcessBuilder(this.pipExecutable, "install", "--no-cache-dir", "-r",
					requirementFile.toAbsolutePath().toString());
			pip.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			pip.redirectError(ProcessBuilder.Redirect.DISCARD);
			Process process = null;

			try {
				process = pip.start();
				boolean completed = process.waitFor(this.properties.getContainerTimeout(), TimeUnit.MINUTES);
				if (!completed) {
					process.destroy();
					if (process.isAlive()) {
						process.destroyForcibly();
					}
					throw new RuntimeException("Pip command timed out.");
				}
			}
			catch (Exception e) {
				// 即使PIP安装失败，仍然尝试运行Python代码
				log.warn("Pip install failed: {}", e.getMessage(), e);
			}
			finally {
				if (process != null && process.isAlive()) {
					process.destroyForcibly();
				}
			}
		}

		// 运行Python代码
		Process process = null;
		try {
			ProcessBuilder pb = new ProcessBuilder(this.pythonExecutable, scriptFile.toAbsolutePath().toString());
			pb.directory(container.toFile());
			pb.redirectInput(stdinFile.toFile());
			pb.environment().put("PYTHONIOENCODING", StandardCharsets.UTF_8.name());
			process = pb.start();

			// 读取stdout和stderr
			StringWriter stdoutWriter = new StringWriter();
			StringWriter stderrWriter = new StringWriter();
			try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
					BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
				CompletableFuture<Void> stdoutFuture = CompletableFuture.runAsync(() -> {
					try {
						stdoutReader.transferTo(stdoutWriter);
					}
					catch (IOException e) {
						stderrWriter.write("Error reading stdout: " + e.getMessage());
					}
				});
				CompletableFuture<Void> stderrFuture = CompletableFuture.runAsync(() -> {
					try {
						stderrReader.transferTo(stderrWriter);
					}
					catch (IOException e) {
						stderrWriter.write("Error reading stderr: " + e.getMessage());
					}
				});

				// 等待进程完成，带超时限制
				boolean completed = process.waitFor(this.timeoutParser.parse(this.properties.getCodeTimeout()),
						TimeUnit.MILLISECONDS);
				if (!completed) {
					process.destroy();
					if (process.isAlive()) {
						process.destroyForcibly();
					}
					return TaskResponse.failure("", "python code timeout, Killed.");
				}

				// 等待输出读取完成，给输出读取额外2秒时间
				CompletableFuture.allOf(stdoutFuture, stderrFuture).get(2, TimeUnit.SECONDS);
			}

			// 返回结果
			int exitCode = process.exitValue();
			String stdout = stdoutWriter.toString();
			String stderr = stderrWriter.toString();
			if (exitCode != 0) {
				return TaskResponse.failure(stdout, stderr);
			}
			else {
				return TaskResponse.success(stdout);
			}

		}
		catch (Exception e) {
			log.error("Python execution failed: {}", e.getMessage(), e);
			return TaskResponse.exception(e.getMessage());
		}
		finally {
			if (process != null && process.isAlive()) {
				process.destroyForcibly();
			}
		}
	}

	@Override
	protected void stopContainer(String containerId) throws Exception {
		// 临时文件夹没有停止方法
	}

	@Override
	protected void removeContainer(String containerId) throws Exception {
		Path container = this.containers.remove(containerId);
		this.clearTempDir(container);
	}

	private static LocalPrograms locatePrograms(ExecutableProgramLocator locator) {
		String python = locator.findFirst(PYTHON_NAMES)
			.filter(LocalCodePoolExecutorService::isPython3Executable)
			.orElseThrow(() -> new IllegalStateException(
					"No valid Python 3 interpreter was found in PATH. Please install Python 3."));
		String pip = locator.findFirst(PIP_NAMES).orElse(null);
		return new LocalPrograms(python, pip);
	}

	private static boolean isPython3Executable(String executable) {
		try {
			Process process = new ProcessBuilder(executable, "--version").start();
			boolean completed = process.waitFor(5, TimeUnit.SECONDS);
			if (!completed) {
				process.destroyForcibly();
				return false;
			}

			String version;
			try (BufferedReader stdout = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
					BufferedReader stderr = new BufferedReader(
							new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
				version = stdout.lines().findFirst().orElseGet(() -> stderr.lines().findFirst().orElse(""));
			}

			return process.exitValue() == 0 && version.matches("(?i).*(python|pypy)\\s+3(\\.|\\s|$).*");
		}
		catch (Exception e) {
			return false;
		}
	}

	private record LocalPrograms(String pythonExecutable, String pipExecutable) {
	}

}
