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

import com.alibaba.cloud.ai.dataagent.enums.CodePoolExecutorEnum;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
	 * Specify implementation class of code container pool runtime service
	 */
	CodePoolExecutorEnum codePoolExecutor = CodePoolExecutorEnum.DOCKER;

	/**
	 * Service host, use default address if null
	 */
	String host = null;

	/**
	 * Image name, can customize image with common third-party dependencies to replace
	 * this configuration
	 */
	String imageName = "continuumio/anaconda3:latest";

	/**
	 * Container name prefix
	 */
	String containerNamePrefix = "nl2sql-python-exec-";

	/**
	 * Task blocking queue size
	 */
	Integer taskQueueSize = 5;

	/**
	 * Maximum number of core containers
	 */
	Integer coreContainerNum = 2;

	/**
	 * Maximum number of temporary containers
	 */
	Integer tempContainerNum = 2;

	/**
	 * Core thread count of thread pool
	 */
	Integer coreThreadSize = 5;

	/**
	 * Maximum thread count of thread pool
	 */
	Integer maxThreadSize = 5;

	/**
	 * Survival time of temporary containers, in minutes
	 */
	Integer tempContainerAliveTime = 5;

	/**
	 * Task survival time of thread pool, in seconds
	 */
	Long keepThreadAliveTime = 60L;

	/**
	 * Task blocking queue size of thread pool
	 */
	Integer threadQueueSize = 10;

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
	String codeTimeout = "60s";

	/**
	 * Maximum container runtime
	 */
	Long containerTimeout = 3000L;

	/**
	 * Container network mode
	 */
	String networkMode = "none";

	/**
	 * Python执行的最大重试次数
	 */
	Integer pythonMaxTriesCount = 5;

}
