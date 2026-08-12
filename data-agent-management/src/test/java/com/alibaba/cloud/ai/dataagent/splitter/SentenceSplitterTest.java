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
package com.alibaba.cloud.ai.dataagent.splitter;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SentenceSplitterTest {

	@Test
	void builder_defaultValues() {
		SentenceSplitter splitter = SentenceSplitter.builder().build();
		List<Document> result = splitter.apply(List.of(new Document("a".repeat(1001))));

		assertEquals(2, result.size());
		assertEquals(1000, result.get(0).getText().length());
		assertEquals(1, result.get(1).getText().length());
	}

	@Test
	void builder_customValues() {
		SentenceSplitter splitter = SentenceSplitter.builder().withChunkSize(500).withSentenceOverlap(0).build();
		List<Document> result = splitter.apply(List.of(new Document("a".repeat(1001))));

		assertEquals(3, result.size());
		assertEquals(500, result.get(0).getText().length());
		assertEquals(500, result.get(1).getText().length());
		assertEquals(1, result.get(2).getText().length());
	}

	@Test
	void apply_emptyList_returnsEmpty() {
		SentenceSplitter splitter = SentenceSplitter.builder().build();
		List<Document> result = splitter.apply(Collections.emptyList());
		assertTrue(result.isEmpty());
	}

	@Test
	void apply_nullList_returnsEmpty() {
		SentenceSplitter splitter = SentenceSplitter.builder().build();
		List<Document> result = splitter.apply(null);
		assertTrue(result.isEmpty());
	}

	@Test
	void apply_singleShortSentence() {
		SentenceSplitter splitter = SentenceSplitter.builder().withChunkSize(1000).build();
		Document doc = new Document("这是一个测试句子。");
		List<Document> result = splitter.apply(List.of(doc));
		assertEquals(1, result.size());
		assertEquals("这是一个测试句子。", result.get(0).getText());
	}

	@Test
	void apply_multipleSentences_fitsInOneChunk() {
		SentenceSplitter splitter = SentenceSplitter.builder().withChunkSize(1000).build();
		Document doc = new Document("第一句话。第二句话。第三句话。");
		List<Document> result = splitter.apply(List.of(doc));
		assertEquals(1, result.size());
		assertEquals("第一句话。第二句话。第三句话。", result.get(0).getText());
	}

	@Test
	void apply_multipleSentences_splitIntoChunks() {
		SentenceSplitter splitter = SentenceSplitter.builder().withChunkSize(10).withSentenceOverlap(0).build();
		Document doc = new Document("第一句话。第二句话。第三句话。");
		List<Document> result = splitter.apply(List.of(doc));
		assertEquals(2, result.size());
		assertEquals("第一句话。第二句话。", result.get(0).getText());
		assertEquals("第三句话。", result.get(1).getText());
	}

	@Test
	void apply_preservesMetadata() {
		SentenceSplitter splitter = SentenceSplitter.builder().withChunkSize(1000).build();
		Document doc = new Document("这是测试。", Map.of("key", "value"));
		List<Document> result = splitter.apply(List.of(doc));
		assertFalse(result.isEmpty());
		assertEquals("value", result.get(0).getMetadata().get("key"));
		assertEquals(0, result.get(0).getMetadata().get("chunk_index"));
		assertEquals(result.get(0).getText().length(), result.get(0).getMetadata().get("chunk_size"));
		assertEquals("sentence", result.get(0).getMetadata().get("splitter_type"));
	}

	@Test
	void apply_englishSentences() {
		SentenceSplitter splitter = SentenceSplitter.builder().withChunkSize(50).withSentenceOverlap(0).build();
		Document doc = new Document("First sentence. Second sentence. Third sentence.");
		List<Document> result = splitter.apply(List.of(doc));
		assertEquals(1, result.size());
		assertEquals("First sentence. Second sentence. Third sentence.", result.get(0).getText());
	}

	@Test
	void apply_mixedPunctuation() {
		SentenceSplitter splitter = SentenceSplitter.builder().withChunkSize(1000).build();
		Document doc = new Document("问题一？问题二！句号。分号；");
		List<Document> result = splitter.apply(List.of(doc));
		assertEquals(1, result.size());
		assertEquals("问题一？问题二！句号。分号；", result.get(0).getText());
	}

	@Test
	void apply_veryLongSentence_splitsByChunkSize() {
		SentenceSplitter splitter = SentenceSplitter.builder().withChunkSize(20).withSentenceOverlap(0).build();
		String longText = "a".repeat(100);
		Document doc = new Document(longText);
		List<Document> result = splitter.apply(List.of(doc));
		assertEquals(5, result.size());
		assertTrue(result.stream().allMatch(chunk -> chunk.getText().length() == 20));
		assertEquals(longText, result.stream().map(Document::getText).reduce("", String::concat));
	}

	@Test
	void apply_sentenceWithNewlines() {
		SentenceSplitter splitter = SentenceSplitter.builder().withChunkSize(1000).build();
		Document doc = new Document("第一段\n第二段\n第三段");
		List<Document> result = splitter.apply(List.of(doc));
		assertEquals(1, result.size());
		assertEquals("第一段第二段第三段", result.get(0).getText());
	}

	@Test
	void apply_withOverlap_hasOverlappingContent() {
		SentenceSplitter splitter = SentenceSplitter.builder().withChunkSize(15).withSentenceOverlap(1).build();
		Document doc = new Document("第一句。第二句。第三句。第四句。");
		List<Document> result = splitter.apply(List.of(doc));
		assertEquals(2, result.size());
		assertEquals("第一句。第二句。第三句。", result.get(0).getText());
		assertEquals("第三句。第四句。", result.get(1).getText());
	}

	@Test
	void apply_emptyTextDocument_skipped() {
		SentenceSplitter splitter = SentenceSplitter.builder().build();
		Document doc = new Document("");
		List<Document> result = splitter.apply(List.of(doc));
		assertTrue(result.isEmpty());
	}

	@Test
	void apply_multipleDocuments() {
		SentenceSplitter splitter = SentenceSplitter.builder().withChunkSize(1000).build();
		Document doc1 = new Document("第一篇文档的内容。");
		Document doc2 = new Document("第二篇文档的内容。");
		List<Document> result = splitter.apply(List.of(doc1, doc2));
		assertEquals(2, result.size());
		assertEquals(List.of("第一篇文档的内容。", "第二篇文档的内容。"), result.stream().map(Document::getText).toList());
		assertEquals(0, result.get(0).getMetadata().get("chunk_index"));
		assertEquals(0, result.get(1).getMetadata().get("chunk_index"));
	}

	@Test
	void apply_longEnglishText_respectsWordBoundary() {
		SentenceSplitter splitter = SentenceSplitter.builder().withChunkSize(30).withSentenceOverlap(0).build();
		String text = "This is a really long sentence that should definitely get split into parts";
		Document doc = new Document(text);
		List<Document> result = splitter.apply(List.of(doc));
		assertTrue(result.size() >= 2);
		assertTrue(result.stream().allMatch(chunk -> chunk.getText().length() <= 30));
		assertEquals(text, result.stream().map(Document::getText).reduce("", String::concat));
		for (int i = 0; i < result.size() - 1; i++) {
			String current = result.get(i).getText();
			String next = result.get(i + 1).getText();
			assertFalse(Character.isLetterOrDigit(current.charAt(current.length() - 1))
					&& Character.isLetterOrDigit(next.charAt(0)));
		}
	}

	@Test
	void builder_negativeChunkSize_usesDefault() {
		SentenceSplitter splitter = SentenceSplitter.builder().withChunkSize(-1).build();
		Document doc = new Document("a".repeat(1001));
		List<Document> result = splitter.apply(List.of(doc));
		assertEquals(2, result.size());
		assertEquals(1000, result.get(0).getText().length());
		assertEquals(1, result.get(1).getText().length());
	}

	@Test
	void builder_negativeOverlap_usesDefault() {
		SentenceSplitter splitter = SentenceSplitter.builder().withChunkSize(15).withSentenceOverlap(-1).build();
		Document doc = new Document("第一句。第二句。第三句。第四句。");
		List<Document> result = splitter.apply(List.of(doc));
		assertEquals(2, result.size());
		assertEquals("第一句。第二句。第三句。", result.get(0).getText());
		assertEquals("第三句。第四句。", result.get(1).getText());
	}

}
