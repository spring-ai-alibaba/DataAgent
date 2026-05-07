/*
 * Copyright 2026 the original author or authors.
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

import com.alibaba.cloud.ai.dataagent.enums.CodePoolExecutorEnum;
import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.service.code.impls.AiSimulationCodeExecutorService;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodePoolExecutorServiceFactoryTest {

	@Mock
	private CodeExecutorProperties properties;

	@Mock
	private LlmService llmService;

	private CodePoolExecutorServiceFactory factory;

	@BeforeEach
	void setUp() {
		factory = new CodePoolExecutorServiceFactory(properties, llmService);
	}

	@Test
	void testGetObject_aiSimulation() {
		when(properties.getCodePoolExecutor()).thenReturn(CodePoolExecutorEnum.AI_SIMULATION);

		CodePoolExecutorService result = factory.getObject();
		assertInstanceOf(AiSimulationCodeExecutorService.class, result);
	}

	@Test
	void testGetObject_unsupported() {
		when(properties.getCodePoolExecutor()).thenReturn(CodePoolExecutorEnum.CONTAINERD);

		assertThrows(IllegalStateException.class, () -> factory.getObject());
	}

	@Test
	void testGetObjectType() {
		assertEquals(CodePoolExecutorService.class, factory.getObjectType());
	}

}
