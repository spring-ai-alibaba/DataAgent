/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.splitter;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 段落文本分块器
 *
 * @author zihenzzz
 * @since 2025-01-03
 */
@Slf4j
@Builder
public class ParagraphTextSplitter extends TextSplitter {

	private final int chunkSize;

	private final int paragraphOverlap;

	/**
	 * 段落分隔符：至少两个连续的换行符（可能包含空白字符）
	 */
	private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\\n\\s*\\n+");

	/**
	 * 分割文本为段落块
	 */
	@Override
	public List<String> splitText(String text) {
		if (text == null || text.trim().isEmpty()) {
			return List.of();
		}

		// 1. 按段落切分
		String[] paragraphs = PARAGRAPH_PATTERN.split(text);
		log.debug("Split text into {} paragraphs", paragraphs.length);

		List<String> chunks = new ArrayList<>();
		StringBuilder currentChunk = new StringBuilder();
		List<String> recentParagraphs = new ArrayList<>(); // 用于 overlap

		for (String paragraph : paragraphs) {
			String trimmedParagraph = paragraph.trim();
			if (trimmedParagraph.isEmpty()) {
				continue;
			}

			// 计算添加这个段落后的总长度
			int potentialLength = currentChunk.length() + (currentChunk.length() > 0 ? 2 : 0)
					+ trimmedParagraph.length();

			// 如果加上当前段落会超过 chunkSize，先保存当前块
			if (potentialLength > chunkSize && currentChunk.length() > 0) {
				chunks.add(currentChunk.toString().trim());

				// 准备新块，加入 overlap 段落
				currentChunk = new StringBuilder();
				if (paragraphOverlap > 0 && !recentParagraphs.isEmpty()) {
					int overlapCount = Math.min(paragraphOverlap, recentParagraphs.size());
					for (int i = recentParagraphs.size() - overlapCount; i < recentParagraphs.size(); i++) {
						if (currentChunk.length() > 0) {
							currentChunk.append("\n\n");
						}
						currentChunk.append(recentParagraphs.get(i));
					}
				}
				recentParagraphs.clear();
			}

			// 添加当前段落
			if (currentChunk.length() > 0) {
				currentChunk.append("\n\n");
			}
			currentChunk.append(trimmedParagraph);
			recentParagraphs.add(trimmedParagraph);

			// 如果单个段落就超过 chunkSize，直接切分
			if (trimmedParagraph.length() > chunkSize) {
				log.debug("Paragraph is too large ({}), splitting further", trimmedParagraph.length());
				chunks.addAll(splitLargeParagraph(trimmedParagraph));
				currentChunk = new StringBuilder();
				recentParagraphs.clear();
			}
		}

		// 添加最后一个块
		if (currentChunk.length() > 0) {
			chunks.add(currentChunk.toString().trim());
		}

		log.info("Created {} paragraph chunks from {} paragraphs", chunks.size(), paragraphs.length);
		return chunks;
	}

	/**
	 * 切分过大的段落（按句子切分）
	 */
	private List<String> splitLargeParagraph(String paragraph) {
		List<String> subChunks = new ArrayList<>();

		// 简单按句子切分
		Pattern sentencePattern = Pattern.compile("[^。！？.!?]+[。！？.!?]+");
		Matcher matcher = sentencePattern.matcher(paragraph);

		StringBuilder currentChunk = new StringBuilder();
		while (matcher.find()) {
			String sentence = matcher.group();
			if (currentChunk.length() + sentence.length() > chunkSize && currentChunk.length() > 0) {
				subChunks.add(currentChunk.toString().trim());
				currentChunk = new StringBuilder();
			}
			currentChunk.append(sentence);
		}

		if (currentChunk.length() > 0) {
			subChunks.add(currentChunk.toString().trim());
		}

		return subChunks;
	}

}
