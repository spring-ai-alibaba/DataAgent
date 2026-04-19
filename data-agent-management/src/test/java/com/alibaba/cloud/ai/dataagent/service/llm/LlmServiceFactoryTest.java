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
package com.alibaba.cloud.ai.dataagent.service.llm;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.alibaba.cloud.ai.dataagent.service.llm.impls.BlockLlmService;
import com.alibaba.cloud.ai.dataagent.service.llm.impls.StreamLlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmServiceFactoryTest {

	@Mock
	private DataAgentProperties properties;

	@Mock
	private AiModelRegistry aiModelRegistry;

	private LlmServiceFactory factory;

	@BeforeEach
	void setUp() {
		factory = new LlmServiceFactory(properties, aiModelRegistry);
	}

	@Test
	void testGetObject_block() {
		when(properties.getLlmServiceType()).thenReturn(LlmServiceEnum.BLOCK);

		LlmService result = factory.getObject();
		assertInstanceOf(BlockLlmService.class, result);
	}

	@Test
	void testGetObject_stream() {
		when(properties.getLlmServiceType()).thenReturn(LlmServiceEnum.STREAM);

		LlmService result = factory.getObject();
		assertInstanceOf(StreamLlmService.class, result);
	}

	@Test
	void testGetObject_null_defaultsToStream() {
		when(properties.getLlmServiceType()).thenReturn(null);

		LlmService result = factory.getObject();
		assertInstanceOf(StreamLlmService.class, result);
	}

	@Test
	void testGetObjectType() {
		assertEquals(LlmService.class, factory.getObjectType());
	}

}
