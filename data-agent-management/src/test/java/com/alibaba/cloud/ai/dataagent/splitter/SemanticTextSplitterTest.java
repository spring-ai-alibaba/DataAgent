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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticTextSplitterTest {

	@Mock
	private EmbeddingModel embeddingModel;

	@Test
	void splitText_nullInput_returnsEmptyList() {
		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.minChunkSize(50)
			.maxChunkSize(200)
			.similarityThreshold(0.5)
			.build();

		assertTrue(splitter.splitText(null).isEmpty());
	}

	@Test
	void splitText_emptyInput_returnsEmptyList() {
		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.minChunkSize(50)
			.maxChunkSize(200)
			.similarityThreshold(0.5)
			.build();

		assertTrue(splitter.splitText("").isEmpty());
	}

	@Test
	void splitText_whitespaceInput_returnsEmptyList() {
		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.minChunkSize(50)
			.maxChunkSize(200)
			.similarityThreshold(0.5)
			.build();

		assertTrue(splitter.splitText("   ").isEmpty());
	}

	@Test
	void splitText_singleShortSentence_returnsSingleChunk() {
		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.minChunkSize(10)
			.maxChunkSize(500)
			.similarityThreshold(0.5)
			.build();

		List<String> chunks = splitter.splitText("Hello world.");
		assertEquals(1, chunks.size());
		assertEquals("Hello world.", chunks.get(0));
	}

	@Test
	void splitText_singleLongSentence_splitsByMaxChunkSize() {
		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.minChunkSize(10)
			.maxChunkSize(20)
			.similarityThreshold(0.5)
			.build();

		String longText = "This is a very long sentence that exceeds the maximum chunk size and must be split.";
		List<String> chunks = splitter.splitText(longText);

		assertTrue(chunks.size() > 1);
		for (String chunk : chunks) {
			assertTrue(chunk.length() <= 20);
		}
	}

	@Test
	void splitText_multipleSentences_withinMaxSize_returnsSingleChunk() {
		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.minChunkSize(10)
			.maxChunkSize(500)
			.similarityThreshold(0.5)
			.build();

		when(embeddingModel.dimensions()).thenReturn(3);
		List<Embedding> embeddings = List.of(new Embedding(new float[] { 1.0f, 0.0f, 0.0f }, 0),
				new Embedding(new float[] { 0.99f, 0.1f, 0.0f }, 1));
		when(embeddingModel.embedForResponse(anyList())).thenReturn(new EmbeddingResponse(embeddings));

		String text = "First sentence. Second sentence.";
		List<String> chunks = splitter.splitText(text);
		assertEquals(1, chunks.size());
	}

	@Test
	void splitText_multipleSentences_semanticShift_splitsChunks() {
		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.minChunkSize(10)
			.maxChunkSize(500)
			.similarityThreshold(0.9)
			.build();

		when(embeddingModel.dimensions()).thenReturn(3);

		List<Embedding> embeddings = List.of(new Embedding(new float[] { 1.0f, 0.0f, 0.0f }, 0),
				new Embedding(new float[] { 0.0f, 1.0f, 0.0f }, 1), new Embedding(new float[] { 0.0f, 0.0f, 1.0f }, 2));
		when(embeddingModel.embedForResponse(anyList())).thenReturn(new EmbeddingResponse(embeddings));

		String text = "First topic sentence. Completely different topic. Another unrelated topic.";
		List<String> chunks = splitter.splitText(text);
		assertTrue(chunks.size() >= 2);
	}

	@Test
	void splitText_embeddingFails_fillsZeroVectors_stillSplits() {
		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.minChunkSize(10)
			.maxChunkSize(500)
			.similarityThreshold(0.5)
			.build();

		when(embeddingModel.dimensions()).thenReturn(3);
		when(embeddingModel.embedForResponse(anyList())).thenThrow(new RuntimeException("API error"));

		String text = "First sentence. Second sentence.";
		List<String> chunks = splitter.splitText(text);
		assertFalse(chunks.isEmpty());
	}

	@Test
	void splitText_chineseSentences_splitsCorrectly() {
		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.minChunkSize(5)
			.maxChunkSize(30)
			.similarityThreshold(0.9)
			.build();

		when(embeddingModel.dimensions()).thenReturn(3);

		List<Embedding> embeddings = List.of(new Embedding(new float[] { 1.0f, 0.0f, 0.0f }, 0),
				new Embedding(new float[] { 0.0f, 1.0f, 0.0f }, 1), new Embedding(new float[] { 0.0f, 0.0f, 1.0f }, 2));
		when(embeddingModel.embedForResponse(anyList())).thenReturn(new EmbeddingResponse(embeddings));

		String text = "这是第一个话题的句子。这是完全不同的话题。这是第三个不相关的话题。";
		List<String> chunks = splitter.splitText(text);
		assertTrue(chunks.size() >= 2);
	}

	@Test
	void splitText_sentencesExceedMaxSize_forceSplits() {
		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.minChunkSize(10)
			.maxChunkSize(30)
			.similarityThreshold(0.1)
			.build();

		when(embeddingModel.dimensions()).thenReturn(3);
		List<Embedding> embeddings = List.of(new Embedding(new float[] { 1.0f, 0.0f, 0.0f }, 0),
				new Embedding(new float[] { 0.99f, 0.1f, 0.0f }, 1));
		when(embeddingModel.embedForResponse(anyList())).thenReturn(new EmbeddingResponse(embeddings));

		String text = "This is a fairly long sentence. Another fairly long sentence.";
		List<String> chunks = splitter.splitText(text);
		assertTrue(chunks.size() >= 2);
	}

	@Test
	void splitText_belowMinChunkSize_doesNotSplitOnSemantic() {
		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.minChunkSize(200)
			.maxChunkSize(500)
			.similarityThreshold(0.9)
			.build();

		when(embeddingModel.dimensions()).thenReturn(3);
		List<Embedding> embeddings = List.of(new Embedding(new float[] { 1.0f, 0.0f, 0.0f }, 0),
				new Embedding(new float[] { 0.0f, 1.0f, 0.0f }, 1));
		when(embeddingModel.embedForResponse(anyList())).thenReturn(new EmbeddingResponse(embeddings));

		String text = "Short. Different.";
		List<String> chunks = splitter.splitText(text);
		assertEquals(1, chunks.size());
	}

	@Test
	void splitText_largeBatch_batchesEmbeddings() {
		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.minChunkSize(5)
			.maxChunkSize(5000)
			.similarityThreshold(0.5)
			.embeddingBatchSize(3)
			.build();

		when(embeddingModel.dimensions()).thenReturn(2);

		List<Embedding> batch1 = List.of(new Embedding(new float[] { 1.0f, 0.0f }, 0),
				new Embedding(new float[] { 0.9f, 0.1f }, 1), new Embedding(new float[] { 0.8f, 0.2f }, 2));
		List<Embedding> batch2 = List.of(new Embedding(new float[] { 0.7f, 0.3f }, 0),
				new Embedding(new float[] { 0.6f, 0.4f }, 1));
		when(embeddingModel.embedForResponse(anyList())).thenReturn(new EmbeddingResponse(batch1))
			.thenReturn(new EmbeddingResponse(batch2));

		String text = "Sentence one. Sentence two. Sentence three. Sentence four. Sentence five.";
		List<String> chunks = splitter.splitText(text);
		assertFalse(chunks.isEmpty());
	}

	@Test
	void splitText_sentenceWithNewlines_extractsSentences() {
		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.minChunkSize(10)
			.maxChunkSize(500)
			.similarityThreshold(0.5)
			.build();

		when(embeddingModel.dimensions()).thenReturn(3);
		List<Embedding> embeddings = List.of(new Embedding(new float[] { 1.0f, 0.0f, 0.0f }, 0),
				new Embedding(new float[] { 0.9f, 0.1f, 0.0f }, 1));
		when(embeddingModel.embedForResponse(anyList())).thenReturn(new EmbeddingResponse(embeddings));

		String text = "First line\nSecond line\n";
		List<String> chunks = splitter.splitText(text);
		assertFalse(chunks.isEmpty());
	}

}
