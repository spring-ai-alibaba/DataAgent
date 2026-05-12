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

import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.FusionStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.impl.RrfFusionStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.impl.WeightedAverageStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class FusionStrategyFactoryTest {

	@Test
	void testGetObject_rrf() throws Exception {
		FusionStrategyFactory factory = new FusionStrategyFactory();
		ReflectionTestUtils.setField(factory, "fusionStrategyType", "rrf");

		FusionStrategy result = factory.getObject();
		assertInstanceOf(RrfFusionStrategy.class, result);
	}

	@Test
	void testGetObject_weighted() throws Exception {
		FusionStrategyFactory factory = new FusionStrategyFactory();
		ReflectionTestUtils.setField(factory, "fusionStrategyType", "weighted");

		FusionStrategy result = factory.getObject();
		assertInstanceOf(WeightedAverageStrategy.class, result);
	}

	@Test
	void testGetObject_unknownFallsBackToRrf() throws Exception {
		FusionStrategyFactory factory = new FusionStrategyFactory();
		ReflectionTestUtils.setField(factory, "fusionStrategyType", "unknown");

		FusionStrategy result = factory.getObject();
		assertInstanceOf(RrfFusionStrategy.class, result);
	}

	@Test
	void testGetObject_caseInsensitive() throws Exception {
		FusionStrategyFactory factory = new FusionStrategyFactory();
		ReflectionTestUtils.setField(factory, "fusionStrategyType", "RRF");

		FusionStrategy result = factory.getObject();
		assertInstanceOf(RrfFusionStrategy.class, result);
	}

	@Test
	void testGetObjectType() {
		FusionStrategyFactory factory = new FusionStrategyFactory();
		assertEquals(FusionStrategy.class, factory.getObjectType());
	}

	@Test
	void testIsSingleton() {
		FusionStrategyFactory factory = new FusionStrategyFactory();
		assertTrue(factory.isSingleton());
	}

}
