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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeightedAverageStrategyTest {

	@Test
	void fuseResults_withNullInput_returnsEmptyList() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();

		List<Document> result = strategy.fuseResults(10, (List<Document>[]) null);

		assertTrue(result.isEmpty());
	}

	@Test
	void fuseResults_withTopKLessThanOne_returnsEmptyList() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();
		Document doc = new Document("id1", "content1", Collections.emptyMap());

		List<Document> result = strategy.fuseResults(0, List.of(doc));

		assertTrue(result.isEmpty());
	}

	@Test
	void fuseResults_withSingleList_keepsRankOrder() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();
		Document doc1 = new Document("id1", "content1", Collections.emptyMap());
		Document doc2 = new Document("id2", "content2", Collections.emptyMap());

		List<Document> result = strategy.fuseResults(10, Arrays.asList(doc1, doc2));

		assertEquals(2, result.size());
		assertEquals("id1", result.get(0).getId());
		assertEquals("id2", result.get(1).getId());
	}

	@Test
	void fuseResults_withTopKLimit_returnsLimitedResults() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();
		Document doc1 = new Document("id1", "content1", Collections.emptyMap());
		Document doc2 = new Document("id2", "content2", Collections.emptyMap());
		Document doc3 = new Document("id3", "content3", Collections.emptyMap());

		List<Document> result = strategy.fuseResults(2, Arrays.asList(doc1, doc2, doc3));

		assertEquals(2, result.size());
	}

	@Test
	void fuseResults_withDuplicatesAcrossLists_mergesScores() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();
		Document docA = new Document("a", "content a", Collections.emptyMap());
		Document docB = new Document("b", "content b", Collections.emptyMap());
		Document docC = new Document("c", "content c", Collections.emptyMap());
		Document docACopy = new Document("a", "content a copy", Collections.emptyMap());

		List<Document> result = strategy.fuseResults(10, Arrays.asList(docB, docA, docC), List.of(docACopy));

		assertEquals(3, result.size());
		assertEquals("a", result.get(0).getId());
	}

	@Test
	void fuseResults_withWeights_prefersHigherWeightedSource() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();
		Document vectorDoc = new Document("vector", "vector content", Collections.emptyMap());
		Document keywordDoc = new Document("keyword", "keyword content", Collections.emptyMap());

		List<Document> result = strategy.fuseResultsWithWeights(10, Arrays.asList(0.2, 0.8), List.of(vectorDoc),
				List.of(keywordDoc));

		assertEquals(2, result.size());
		assertEquals("keyword", result.get(0).getId());
	}

	@Test
	void fuseResults_withNullListAndNullDocument_skipsInvalidEntries() {
		WeightedAverageStrategy strategy = new WeightedAverageStrategy();
		Document doc = new Document("id1", "content1", Collections.emptyMap());

		List<Document> result = strategy.fuseResults(10, Arrays.asList(doc, null), null);

		assertEquals(1, result.size());
		assertEquals("id1", result.get(0).getId());
	}

}
