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
package com.alibaba.cloud.ai.dataagent.service.llm;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.alibaba.cloud.ai.dataagent.service.llm.impls.BlockLlmService;
import com.alibaba.cloud.ai.dataagent.service.llm.impls.StreamLlmService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class LlmServiceFactory implements FactoryBean<LlmService> {

	private final DataAgentProperties properties;

	private final AiModelRegistry aiModelRegistry;

	@Override
	public LlmService getObject() {
		if (LlmServiceEnum.BLOCK.equals(properties.getLlmServiceType())) {
			return new BlockLlmService(aiModelRegistry);
		}
		else {
			return new StreamLlmService(aiModelRegistry);
		}
	}

	@Override
	public Class<?> getObjectType() {
		return LlmService.class;
	}

}
