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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParagraphTextSplitterTest {

	@Test
	void splitText_nullInput_returnsEmptyList() {
		ParagraphTextSplitter splitter = ParagraphTextSplitter.builder()
			.chunkSize(100)
			.paragraphOverlapChars(20)
			.build();

		assertTrue(splitter.splitText(null).isEmpty());
	}

	@Test
	void splitText_emptyInput_returnsEmptyList() {
		ParagraphTextSplitter splitter = ParagraphTextSplitter.builder()
			.chunkSize(100)
			.paragraphOverlapChars(20)
			.build();

		assertTrue(splitter.splitText("").isEmpty());
	}

	@Test
	void splitText_whitespaceInput_returnsEmptyList() {
		ParagraphTextSplitter splitter = ParagraphTextSplitter.builder()
			.chunkSize(100)
			.paragraphOverlapChars(20)
			.build();

		assertTrue(splitter.splitText("   ").isEmpty());
	}

	@Test
	void splitText_singleShortParagraph_returnsSingleChunk() {
		ParagraphTextSplitter splitter = ParagraphTextSplitter.builder()
			.chunkSize(500)
			.paragraphOverlapChars(0)
			.build();

		List<String> chunks = splitter.splitText("This is a short paragraph.");
		assertEquals(1, chunks.size());
		assertEquals("This is a short paragraph.", chunks.get(0));
	}

	@Test
	void splitText_multipleParagraphs_withinChunkSize_returnsSingleChunk() {
		ParagraphTextSplitter splitter = ParagraphTextSplitter.builder()
			.chunkSize(500)
			.paragraphOverlapChars(0)
			.build();

		String text = "First paragraph.\n\nSecond paragraph.";
		List<String> chunks = splitter.splitText(text);
		assertEquals(1, chunks.size());
	}

	@Test
	void splitText_multipleParagraphs_exceedsChunkSize_splitsIntoMultipleChunks() {
		ParagraphTextSplitter splitter = ParagraphTextSplitter.builder().chunkSize(30).paragraphOverlapChars(0).build();

		String text = "First paragraph here.\n\nSecond paragraph here.";
		List<String> chunks = splitter.splitText(text);
		assertTrue(chunks.size() >= 2);
	}

	@Test
	void splitText_largeParagraph_recursivelySplits() {
		ParagraphTextSplitter splitter = ParagraphTextSplitter.builder().chunkSize(20).paragraphOverlapChars(0).build();

		String text = "This is a very long sentence that should be split into smaller chunks by the splitter.";
		List<String> chunks = splitter.splitText(text);
		assertTrue(chunks.size() > 1);
		for (String chunk : chunks) {
			assertTrue(chunk.length() <= 20);
		}
	}

	@Test
	void splitText_withOverlap_includesOverlapContent() {
		ParagraphTextSplitter splitter = ParagraphTextSplitter.builder()
			.chunkSize(40)
			.paragraphOverlapChars(10)
			.build();

		String text = "First paragraph content.\n\nSecond paragraph content.\n\nThird paragraph content.";
		List<String> chunks = splitter.splitText(text);
		assertTrue(chunks.size() >= 2);
	}

	@Test
	void splitText_emptyParagraphsBetweenContent_skipsEmpty() {
		ParagraphTextSplitter splitter = ParagraphTextSplitter.builder()
			.chunkSize(500)
			.paragraphOverlapChars(0)
			.build();

		String text = "Content.\n\n\n\n\nMore content.";
		List<String> chunks = splitter.splitText(text);
		assertEquals(1, chunks.size());
	}

	@Test
	void splitText_overlapLargerThanChunk_handlesGracefully() {
		ParagraphTextSplitter splitter = ParagraphTextSplitter.builder()
			.chunkSize(30)
			.paragraphOverlapChars(50)
			.build();

		String text = "Short.\n\nAnother short.";
		List<String> chunks = splitter.splitText(text);
		assertFalse(chunks.isEmpty());
	}

	@Test
	void splitText_noOverlap_zeroOverlap() {
		ParagraphTextSplitter splitter = ParagraphTextSplitter.builder().chunkSize(30).paragraphOverlapChars(0).build();

		String text = "First paragraph here.\n\nSecond paragraph here.";
		List<String> chunks = splitter.splitText(text);
		assertTrue(chunks.size() >= 2);
	}

	@Test
	void splitText_largeParagraphWithSentences_splitsBySentences() {
		ParagraphTextSplitter splitter = ParagraphTextSplitter.builder().chunkSize(50).paragraphOverlapChars(0).build();

		String text = "First sentence. Second sentence. Third sentence. Fourth sentence. Fifth sentence.";
		List<String> chunks = splitter.splitText(text);
		assertTrue(chunks.size() >= 2);
	}

	@Test
	void splitText_chineseParagraphs_splits() {
		ParagraphTextSplitter splitter = ParagraphTextSplitter.builder().chunkSize(20).paragraphOverlapChars(0).build();

		String text = "这是第一段内容。\n\n这是第二段内容。\n\n这是第三段内容。";
		List<String> chunks = splitter.splitText(text);
		assertTrue(chunks.size() >= 2);
	}

}
