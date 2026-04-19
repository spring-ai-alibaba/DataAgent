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
package com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RrfFusionStrategyTest {

	private RrfFusionStrategy strategy;

	@BeforeEach
	void setUp() {
		strategy = new RrfFusionStrategy();
	}

	@Test
	void fuseResults_withNullInput_returnsEmptyList() {
		List<Document> result = strategy.fuseResults(10, (List<Document>[]) null);

		assertTrue(result.isEmpty());
	}

	@Test
	void fuseResults_withNoLists_returnsEmptyList() {
		@SuppressWarnings("unchecked")
		List<Document> result = strategy.fuseResults(10);

		assertTrue(result.isEmpty());
	}

	@Test
	void fuseResults_withSingleList_returnsSortedByRrf() {
		Document doc1 = new Document("id1", "content1", Collections.emptyMap());
		Document doc2 = new Document("id2", "content2", Collections.emptyMap());
		List<Document> list = Arrays.asList(doc1, doc2);

		List<Document> result = strategy.fuseResults(10, list);

		assertEquals(2, result.size());
		assertEquals("id1", result.get(0).getId());
		assertEquals("id2", result.get(1).getId());
	}

	@Test
	void fuseResults_withTopKLimit_returnsLimitedResults() {
		Document doc1 = new Document("id1", "content1", Collections.emptyMap());
		Document doc2 = new Document("id2", "content2", Collections.emptyMap());
		Document doc3 = new Document("id3", "content3", Collections.emptyMap());
		List<Document> list = Arrays.asList(doc1, doc2, doc3);

		List<Document> result = strategy.fuseResults(2, list);

		assertEquals(2, result.size());
	}

	@Test
	void fuseResults_withDuplicatesAcrossLists_mergesScores() {
		Document doc1 = new Document("id1", "content1", Collections.emptyMap());
		Document doc2 = new Document("id2", "content2", Collections.emptyMap());
		Document doc1Copy = new Document("id1", "content1 copy", Collections.emptyMap());

		List<Document> list1 = Arrays.asList(doc1, doc2);
		List<Document> list2 = Arrays.asList(doc1Copy);

		@SuppressWarnings("unchecked")
		List<Document> result = strategy.fuseResults(10, list1, list2);

		assertEquals(2, result.size());
		assertEquals("id1", result.get(0).getId());
	}

	@Test
	void fuseResults_withMultipleLists_fusesCorrectly() {
		Document docA = new Document("a", "content a", Collections.emptyMap());
		Document docB = new Document("b", "content b", Collections.emptyMap());
		Document docC = new Document("c", "content c", Collections.emptyMap());

		List<Document> list1 = Arrays.asList(docA, docB);
		List<Document> list2 = Arrays.asList(docC, docA);

		@SuppressWarnings("unchecked")
		List<Document> result = strategy.fuseResults(10, list1, list2);

		assertEquals(3, result.size());
		assertEquals("a", result.get(0).getId());
	}

	@Test
	void fuseResults_withNullListInArray_skipsNull() {
		Document doc1 = new Document("id1", "content1", Collections.emptyMap());
		List<Document> list = Arrays.asList(doc1);

		@SuppressWarnings("unchecked")
		List<Document> result = strategy.fuseResults(10, list, null);

		assertEquals(1, result.size());
		assertEquals("id1", result.get(0).getId());
	}

	@Test
	void fuseResults_withEmptyLists_returnsEmptyList() {
		@SuppressWarnings("unchecked")
		List<Document> result = strategy.fuseResults(10, Collections.emptyList(), Collections.emptyList());

		assertTrue(result.isEmpty());
	}

}
