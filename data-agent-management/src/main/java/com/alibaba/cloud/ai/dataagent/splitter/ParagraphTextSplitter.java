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

	/**
	 * 段落重叠字符数
	 */
	private final int paragraphOverlapChars;

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

		for (String paragraph : paragraphs) {
			String trimmedParagraph = paragraph.trim();
			if (trimmedParagraph.isEmpty()) {
				continue;
			}

			// 如果单个段落就超过 chunkSize，需要先切分
			if (trimmedParagraph.length() > chunkSize) {
				log.debug("Paragraph is too large ({}), splitting further", trimmedParagraph.length());

				// 如果当前块有内容，先保存并处理 overlap
				if (currentChunk.length() > 0) {
					chunks.add(currentChunk.toString().trim());
					currentChunk = extractOverlap(currentChunk.toString());
				}

				// 切分大段落
				List<String> subChunks = splitLargeParagraph(trimmedParagraph);
				if (!subChunks.isEmpty()) {
					// 将第一个子块添加到当前块
					if (currentChunk.length() > 0) {
						currentChunk.append("\n\n");
					}
					currentChunk.append(subChunks.get(0));

					// 其余子块作为独立块，并处理 overlap
					for (int i = 1; i < subChunks.size(); i++) {
						chunks.add(currentChunk.toString().trim());
						currentChunk = extractOverlap(currentChunk.toString());
						if (currentChunk.length() > 0) {
							currentChunk.append("\n\n");
						}
						currentChunk.append(subChunks.get(i));
					}
				}
				continue;
			}

			// 计算添加这个段落后的总长度（包括分隔符）
			int separatorLength = currentChunk.length() > 0 ? 2 : 0; // "\n\n"
			int potentialLength = currentChunk.length() + separatorLength + trimmedParagraph.length();

			// 如果加上当前段落会超过 chunkSize，先保存当前块
			if (potentialLength > chunkSize && currentChunk.length() > 0) {
				chunks.add(currentChunk.toString().trim());
				// 提取 overlap 内容作为新块的开始
				currentChunk = extractOverlap(currentChunk.toString());
			}

			// 添加当前段落
			if (currentChunk.length() > 0) {
				currentChunk.append("\n\n");
			}
			currentChunk.append(trimmedParagraph);
		}

		// 添加最后一个块
		if (currentChunk.length() > 0) {
			chunks.add(currentChunk.toString().trim());
		}

		log.info("Created {} paragraph chunks from {} paragraphs", chunks.size(), paragraphs.length);
		return chunks;
	}

	/**
	 * 从已完成的块中提取 overlap 内容（基于字符数）
	 */
	private StringBuilder extractOverlap(String chunk) {
		if (paragraphOverlapChars <= 0 || chunk == null || chunk.isEmpty()) {
			return new StringBuilder();
		}

		// 从块末尾提取指定字符数
		int overlapStart = Math.max(0, chunk.length() - paragraphOverlapChars);
		String overlapText = chunk.substring(overlapStart).trim();

		// 尝试从最后一个段落边界开始，避免截断段落
		int lastParagraphBreak = overlapText.indexOf("\n\n");
		if (lastParagraphBreak > 0) {
			overlapText = overlapText.substring(lastParagraphBreak + 2);
		}

		return new StringBuilder(overlapText);
	}

	/**
	 * 切分过大的段落（防丢失内容） 优先按句子切分，如果句子太大则按字符硬切
	 */
	private List<String> splitLargeParagraph(String paragraph) {
		List<String> subChunks = new ArrayList<>();

		// 1. 尝试按句子切分
		Pattern sentencePattern = Pattern.compile("[^。！？.!?\\n]+[。！？.!?\\n]*");
		Matcher matcher = sentencePattern.matcher(paragraph);

		StringBuilder currentChunk = new StringBuilder();
		int lastMatchEnd = 0;

		while (matcher.find()) {
			String sentence = matcher.group();
			lastMatchEnd = matcher.end();

			// 如果单个句子本身就超过 chunkSize，需要强制按字符硬切
			if (sentence.length() > chunkSize) {
				// 先保存当前块
				if (currentChunk.length() > 0) {
					subChunks.add(currentChunk.toString().trim());
					currentChunk = new StringBuilder();
				}
				// 对超长句子按字符硬切
				subChunks.addAll(splitByChars(sentence));
				continue;
			}

			// 检查添加这个句子后是否会超过 chunkSize
			if (currentChunk.length() + sentence.length() > chunkSize && currentChunk.length() > 0) {
				subChunks.add(currentChunk.toString().trim());
				currentChunk = new StringBuilder();
			}
			currentChunk.append(sentence);
		}

		// 2. 处理正则没匹配到的剩余部分（防止丢字）
		if (lastMatchEnd < paragraph.length()) {
			String remaining = paragraph.substring(lastMatchEnd);
			if (!remaining.trim().isEmpty()) {
				// 如果剩余部分太长，按字符硬切
				if (remaining.length() > chunkSize) {
					if (currentChunk.length() > 0) {
						subChunks.add(currentChunk.toString().trim());
						currentChunk = new StringBuilder();
					}
					subChunks.addAll(splitByChars(remaining));
				}
				else {
					// 检查是否可以添加到当前块
					if (currentChunk.length() + remaining.length() > chunkSize && currentChunk.length() > 0) {
						subChunks.add(currentChunk.toString().trim());
						currentChunk = new StringBuilder();
					}
					currentChunk.append(remaining);
				}
			}
		}

		// 3. 添加最后一个块
		if (currentChunk.length() > 0) {
			subChunks.add(currentChunk.toString().trim());
		}

		return subChunks;
	}

	/**
	 * 按固定字符数强制切分（保底逻辑）
	 */
	private List<String> splitByChars(String text) {
		List<String> chunks = new ArrayList<>();
		int start = 0;
		while (start < text.length()) {
			int end = Math.min(start + chunkSize, text.length());
			String chunk = text.substring(start, end).trim();
			if (!chunk.isEmpty()) {
				chunks.add(chunk);
			}
			start = end;
		}
		return chunks;
	}

}
