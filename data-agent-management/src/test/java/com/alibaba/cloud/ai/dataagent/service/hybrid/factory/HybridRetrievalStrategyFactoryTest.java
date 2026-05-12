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
package com.alibaba.cloud.ai.dataagent.service.hybrid.factory;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.FusionStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.HybridRetrievalStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.impl.DefaultHybridRetrievalStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.impl.ElasticsearchHybridRetrievalStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridRetrievalStrategyFactoryTest {

	private HybridRetrievalStrategyFactory factory;

	@Mock
	private ExecutorService executorService;

	@Mock
	private VectorStore vectorStore;

	@Mock
	private FusionStrategy fusionStrategy;

	@Mock
	private DataAgentProperties dataAgentProperties;

	@Mock
	private DataAgentProperties.VectorStoreProperties vectorStoreConfig;

	@BeforeEach
	void setUp() {
		factory = new HybridRetrievalStrategyFactory();
		ReflectionTestUtils.setField(factory, "executorService", executorService);
		ReflectionTestUtils.setField(factory, "vectorStore", vectorStore);
		ReflectionTestUtils.setField(factory, "fusionStrategy", fusionStrategy);
		ReflectionTestUtils.setField(factory, "dataAgentProperties", dataAgentProperties);
		ReflectionTestUtils.setField(factory, "vectorStoreType", "simple");
		ReflectionTestUtils.setField(factory, "elasticsearchIndexName", "test-index");
	}

	@Test
	void testGetObject_hybridDisabled() throws Exception {
		when(dataAgentProperties.getVectorStore()).thenReturn(vectorStoreConfig);
		when(vectorStoreConfig.isEnableHybridSearch()).thenReturn(false);

		HybridRetrievalStrategy result = factory.getObject();
		assertNull(result);
	}

	@Test
	void testGetObject_elasticsearch() throws Exception {
		when(dataAgentProperties.getVectorStore()).thenReturn(vectorStoreConfig);
		when(vectorStoreConfig.isEnableHybridSearch()).thenReturn(true);
		when(vectorStoreConfig.getElasticsearchMinScore()).thenReturn(0.5);
		ReflectionTestUtils.setField(factory, "vectorStoreType", "elasticsearch");

		HybridRetrievalStrategy result = factory.getObject();
		assertNotNull(result);
		assertInstanceOf(ElasticsearchHybridRetrievalStrategy.class, result);
	}

	@Test
	void testGetObject_defaultStrategy() throws Exception {
		when(dataAgentProperties.getVectorStore()).thenReturn(vectorStoreConfig);
		when(vectorStoreConfig.isEnableHybridSearch()).thenReturn(true);
		ReflectionTestUtils.setField(factory, "vectorStoreType", "simple");

		HybridRetrievalStrategy result = factory.getObject();
		assertNotNull(result);
		assertInstanceOf(DefaultHybridRetrievalStrategy.class, result);
	}

	@Test
	void testGetObjectType() {
		assertEquals(HybridRetrievalStrategy.class, factory.getObjectType());
	}

	@Test
	void testIsSingleton() {
		assertTrue(factory.isSingleton());
	}

}
