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
package com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.impl;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeightedAverageStrategyTest {

	@Test
	void testFuseResults_throwsUnsupportedOperationException() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();
		List<Document> list1 = List.of(new Document("doc1"));
		List<Document> list2 = List.of(new Document("doc2"));

		assertThrows(UnsupportedOperationException.class, () -> strategy.fuseResults(5, list1, list2));
	}

}
