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

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.service.code.docker.DockerExecutorFactory;
import com.alibaba.cloud.ai.dataagent.service.code.impls.AiSimulationCodeExecutorService;
import com.alibaba.cloud.ai.dataagent.service.code.impls.LocalCodePoolExecutorService;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import lombok.AllArgsConstructor;

/**
 * 运行 Python 任务的容器池实现选择器
 *
 * @author vlsmb
 * @since 2025/7/28
 */
@AllArgsConstructor
public class CodePoolExecutorServiceFactory {

	private final CodeExecutorProperties properties;

	private final LlmService llmService;

	private final DockerExecutorFactory dockerExecutorFactory;

	public CodePoolExecutorService getObject() {
		return switch (properties.getCodePoolExecutor()) {
			case DOCKER -> dockerExecutorFactory.create(properties);
			case LOCAL -> new LocalCodePoolExecutorService(properties);
			case AI_SIMULATION -> new AiSimulationCodeExecutorService(llmService);
			default ->
				throw new IllegalStateException("This option does not have a corresponding implementation class yet.");
		};
	}

	public Class<?> getObjectType() {
		return CodePoolExecutorService.class;
	}

}
