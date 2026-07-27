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
package com.alibaba.cloud.ai.dataagent.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.PROJECT_PROPERTIES_PREFIX;

/**
 * @author vlsmb
 * @since 2025/7/12
 */
@Getter
@Setter
@ConfigurationProperties(prefix = CodeExecutorProperties.CONFIG_PREFIX)
public class CodeExecutorProperties {

	public static final String CONFIG_PREFIX = PROJECT_PROPERTIES_PREFIX + ".code-executor";

	/**
	 * Maximum container memory, in MB
	 */
	Long limitMemory = 500L;

	/**
	 * Number of container CPU cores
	 */
	Long cpuCore = 1L;

	/**
	 * Python code execution time limit
	 */
	Duration codeTimeout = Duration.ofSeconds(60);

	/**
	 * Python执行的最大重试次数
	 */
	Integer pythonMaxTriesCount = 5;

	private Sandbox sandbox = new Sandbox();

	@Getter
	@Setter
	public static class Sandbox {

		String dockerHost = "unix:///var/run/docker.sock";

		String imageName = "agentscope-registry.ap-southeast-1.cr.aliyuncs.com/agentscope/runtime-sandbox-base:latest";

		String containerPrefix = "dataagent-sandbox-";

		Integer maxConcurrency = 4;

		Integer queueCapacity = 10;

		Integer maxCodeBytes = 256 * 1024;

		Integer maxInputBytes = 10 * 1024 * 1024;

		Integer maxOutputBytes = 1024 * 1024;

		Integer maxErrorBytes = 256 * 1024;

		Integer maxMetadataBytes = 8 * 1024;

		Integer maxDependencies = 20;

		String packageIndexUrl = "https://pypi.org/simple";

		Duration dependencyInstallTimeout = Duration.ofMinutes(3);

		Integer maxConnections = 4096;

	}

}
