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
package com.alibaba.cloud.ai.dataagent.service.hybrid.fusion;

import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.impl.RrfFusionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RrfFusionStrategy的单元测试 测试RRF（Reciprocal Rank Fusion）融合策略的功能
 */
class RrfFusionStrategyTest {

	private RrfFusionStrategy rrfFusionStrategy;

	@BeforeEach
	void setUp() {
		rrfFusionStrategy = new RrfFusionStrategy();
	}

	@Test
	void testFuseResultsWithValidInput() {
		// 准备测试数据
		List<Document> vectorResults = List.of(new Document("id1", "content1", Map.of("score", 0.9)),
				new Document("id2", "content2", Map.of("score", 0.8)),
				new Document("id3", "content3", Map.of("score", 0.7)));

		List<Document> keywordResults = List.of(new Document("id2", "content2", Map.of("score", 0.9)),
				new Document("id4", "content4", Map.of("score", 0.8)),
				new Document("id5", "content5", Map.of("score", 0.7)));

		int topK = 5;

		// 执行测试
		List<Document> result = rrfFusionStrategy.fuseResults(topK, vectorResults, keywordResults);

		// 验证结果
		assertNotNull(result);
		assertEquals(5, result.size());

		// 验证结果顺序：id2应该排在第一位（在两个列表中都出现）
		assertEquals("id2", result.get(0).getId());

		// 验证结果包含所有唯一的文档
		List<String> resultIds = result.stream().map(Document::getId).toList();
		assertTrue(resultIds.contains("id1"));
		assertTrue(resultIds.contains("id2"));
		assertTrue(resultIds.contains("id3"));
		assertTrue(resultIds.contains("id4"));
		assertTrue(resultIds.contains("id5"));
	}

	@Test
	void testFuseResultsWithEmptyVectorResults() {
		// 准备测试数据
		List<Document> vectorResults = List.of();
		List<Document> keywordResults = List.of(new Document("id1", "content1", Map.of("score", 0.9)),
				new Document("id2", "content2", Map.of("score", 0.8)));

		int topK = 3;

		// 执行测试
		List<Document> result = rrfFusionStrategy.fuseResults(topK, vectorResults, keywordResults);

		// 验证结果
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals("id1", result.get(0).getId());
		assertEquals("id2", result.get(1).getId());
	}

	@Test
	void testFuseResultsWithEmptyKeywordResults() {
		// 准备测试数据
		List<Document> vectorResults = List.of(new Document("id1", "content1", Map.of("score", 0.9)),
				new Document("id2", "content2", Map.of("score", 0.8)));
		List<Document> keywordResults = List.of();

		int topK = 3;

		// 执行测试
		List<Document> result = rrfFusionStrategy.fuseResults(topK, vectorResults, keywordResults);

		// 验证结果
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals("id1", result.get(0).getId());
		assertEquals("id2", result.get(1).getId());
	}

	@Test
	void testFuseResultsWithBothEmptyResults() {
		// 准备测试数据
		List<Document> vectorResults = List.of();
		List<Document> keywordResults = List.of();

		int topK = 3;

		// 执行测试
		List<Document> result = rrfFusionStrategy.fuseResults(topK, vectorResults, keywordResults);

		// 验证结果
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void testFuseResultsWithZeroTopK() {
		// 准备测试数据
		List<Document> vectorResults = List.of(new Document("id1", "content1", Map.of("score", 0.9)),
				new Document("id2", "content2", Map.of("score", 0.8)));
		List<Document> keywordResults = List.of(new Document("id3", "content3", Map.of("score", 0.9)),
				new Document("id4", "content4", Map.of("score", 0.8)));

		int topK = 0;

		// 执行测试
		List<Document> result = rrfFusionStrategy.fuseResults(topK, vectorResults, keywordResults);

		// 验证结果
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void testFuseResultsWithDuplicateDocuments() {
		// 准备测试数据 - 两个列表包含相同的文档
		List<Document> vectorResults = List.of(new Document("id1", "content1", Map.of("score", 0.9)),
				new Document("id2", "content2", Map.of("score", 0.8)));
		List<Document> keywordResults = List.of(new Document("id1", "content1", Map.of("score", 0.9)),
				new Document("id2", "content2", Map.of("score", 0.8)));

		int topK = 3;

		// 执行测试
		List<Document> result = rrfFusionStrategy.fuseResults(topK, vectorResults, keywordResults);

		// 验证结果
		assertNotNull(result);
		assertEquals(2, result.size());

		// 验证结果包含所有唯一的文档
		List<String> resultIds = result.stream().map(Document::getId).toList();
		assertTrue(resultIds.contains("id1"));
		assertTrue(resultIds.contains("id2"));
	}

	@Test
	void testFuseResultsWithNullInput() {
		// 测试空输入
		int topK = 3;

		// 执行测试 - 应该处理null输入而不抛出异常
		List<Document> result1 = rrfFusionStrategy.fuseResults(topK, null, List.of());
		assertNotNull(result1);

		List<Document> result2 = rrfFusionStrategy.fuseResults(topK, List.of(), null);
		assertNotNull(result2);

		List<Document> result3 = rrfFusionStrategy.fuseResults(topK, null, null);
		assertNotNull(result3);
	}

	@Test
	void testFuseResultsWithTopKLessThanTotalDocuments() {
		// 准备测试数据 - 总共有6个唯一文档，但topK设置为4
		List<Document> vectorResults = List.of(new Document("id1", "content1", Map.of("score", 0.9)),
				new Document("id2", "content2", Map.of("score", 0.8)),
				new Document("id3", "content3", Map.of("score", 0.7)),
				new Document("id4", "content4", Map.of("score", 0.6)));

		List<Document> keywordResults = List.of(new Document("id2", "content2", Map.of("score", 0.95)), // id2在两个列表中都出现
				new Document("id5", "content5", Map.of("score", 0.85)),
				new Document("id6", "content6", Map.of("score", 0.75)));

		int topK = 4; // 小于总文档数(6)

		// 执行测试
		List<Document> result = rrfFusionStrategy.fuseResults(topK, vectorResults, keywordResults);

		// 验证结果
		assertNotNull(result);
		assertEquals(4, result.size()); // 结果数量应该等于topK

		// 验证结果顺序：id2应该排在第一位（在两个列表中都出现）
		assertEquals("id2", result.get(0).getId());

		// 验证结果包含的文档ID
		List<String> resultIds = result.stream().map(Document::getId).toList();

		// 确保结果包含topK个文档
		assertEquals(4, resultIds.size());

		// 验证id2在结果中（因为它在两个列表中都出现）
		assertTrue(resultIds.contains("id2"));

		// 验证结果不包含所有文档（因为topK限制了数量）
		assertFalse(resultIds.containsAll(List.of("id1", "id2", "id3", "id4", "id5", "id6")));
	}

}
