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
package com.alibaba.cloud.ai.dataagent.strategy;

import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnhancedTokenCountBatchingStrategyTest {

	@Test
	void testBatch_smallBatch_withinTextLimit() {
		EnhancedTokenCountBatchingStrategy strategy = new EnhancedTokenCountBatchingStrategy(EncodingType.CL100K_BASE,
				8000, 0.1, 10);

		List<Document> documents = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			documents.add(new Document("Short text " + i));
		}

		List<List<Document>> batches = strategy.batch(documents);
		assertNotNull(batches);
		assertFalse(batches.isEmpty());

		int totalDocs = batches.stream().mapToInt(List::size).sum();
		assertEquals(5, totalDocs);
	}

	@Test
	void testBatch_exceedsTextCountLimit() {
		EnhancedTokenCountBatchingStrategy strategy = new EnhancedTokenCountBatchingStrategy(EncodingType.CL100K_BASE,
				100000, 0.1, 3);

		List<Document> documents = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			documents.add(new Document("Text " + i));
		}

		List<List<Document>> batches = strategy.batch(documents);
		assertNotNull(batches);

		for (List<Document> batch : batches) {
			assertTrue(batch.size() <= 3, "Each batch should have at most 3 documents");
		}

		int totalDocs = batches.stream().mapToInt(List::size).sum();
		assertEquals(10, totalDocs);
	}

	@Test
	void testBatch_emptyList() {
		EnhancedTokenCountBatchingStrategy strategy = new EnhancedTokenCountBatchingStrategy(EncodingType.CL100K_BASE,
				8000, 0.1, 10);

		List<List<Document>> batches = strategy.batch(List.of());
		assertNotNull(batches);
		assertTrue(batches.isEmpty());
	}

	@Test
	void testBatch_singleDocument() {
		EnhancedTokenCountBatchingStrategy strategy = new EnhancedTokenCountBatchingStrategy(EncodingType.CL100K_BASE,
				8000, 0.1, 10);

		List<Document> documents = List.of(new Document("Single document"));
		List<List<Document>> batches = strategy.batch(documents);
		assertNotNull(batches);
		assertEquals(1, batches.size());
		assertEquals(1, batches.get(0).size());
	}

	@Test
	void testBatch_maxTextCountOfOne() {
		EnhancedTokenCountBatchingStrategy strategy = new EnhancedTokenCountBatchingStrategy(EncodingType.CL100K_BASE,
				100000, 0.1, 1);

		List<Document> documents = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			documents.add(new Document("Doc " + i));
		}

		List<List<Document>> batches = strategy.batch(documents);
		assertNotNull(batches);

		for (List<Document> batch : batches) {
			assertEquals(1, batch.size());
		}
		assertEquals(5, batches.size());
	}

}
