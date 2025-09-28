/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.service.code;

import com.alibaba.cloud.ai.config.CodeExecutorProperties;
import com.alibaba.cloud.ai.service.code.impls.AiSimulationCodeExecutorService;
import com.alibaba.cloud.ai.service.code.impls.DockerCodePoolExecutorService;
import com.alibaba.cloud.ai.service.code.impls.LocalCodePoolExecutorService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

/**
 * 运行Python任务的容器池（工厂Bean）
 *
 * @author vlsmb
 * @since 2025/7/28
 */
@Component
public class CodePoolExecutorServiceFactory implements FactoryBean<CodePoolExecutorService> {

	private final CodeExecutorProperties properties;

	private final ChatClient.Builder chatClientBuilder;

	public CodePoolExecutorServiceFactory(CodeExecutorProperties properties, ChatClient.Builder chatClientBuilder) {
		this.properties = properties;
		this.chatClientBuilder = chatClientBuilder;
	}

	@Override
	public CodePoolExecutorService getObject() {
		return switch (properties.getCodePoolExecutor()) {
			case DOCKER -> new DockerCodePoolExecutorService(properties);
			case LOCAL -> new LocalCodePoolExecutorService(properties);
			case AI_SIMULATION -> new AiSimulationCodeExecutorService(chatClientBuilder);
			default ->
				throw new IllegalStateException("This option does not have a corresponding implementation class yet.");
		};
	}

	@Override
	public Class<?> getObjectType() {
		return CodePoolExecutorService.class;
	}

}
