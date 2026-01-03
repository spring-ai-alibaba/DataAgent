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

package com.alibaba.cloud.ai.dataagent.splitter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 句子分块器
 *
 * @author Zihenzzz
 * @since 2025/1/3
 */
@Slf4j
public class SentenceSplitter extends TextSplitter {

	/**
	 * 句子分隔符正则表达式 匹配中文和英文的句子结束符，包括：。！？；.!?; 同时考虑后面可能跟随的引号、括号等
	 */
	private static final Pattern SENTENCE_PATTERN = Pattern.compile("([^。！？；.!?;]+[。！？；.!?;]+[\"'）\\)\\]]*\\s*)");

	/**
	 * 默认分块大小（字符数）
	 */
	private static final int DEFAULT_CHUNK_SIZE = 1000;

	/**
	 * 默认句子重叠数量
	 */
	private static final int DEFAULT_SENTENCE_OVERLAP = 1;

	/**
	 * 分块大小（基于字符数）
	 */
	private final int chunkSize;

	/**
	 * 句子重叠数量（保留前面几个句子作为上下文）
	 */
	private final int sentenceOverlap;

	/**
	 * 私有构造函数，使用 Builder 构建
	 */
	private SentenceSplitter(Builder builder) {
		this.chunkSize = builder.chunkSize > 0 ? builder.chunkSize : DEFAULT_CHUNK_SIZE;
		this.sentenceOverlap = builder.sentenceOverlap >= 0 ? builder.sentenceOverlap : DEFAULT_SENTENCE_OVERLAP;

		log.info("Initialized SentenceSplitter with chunkSize={}, sentenceOverlap={}", this.chunkSize,
				this.sentenceOverlap);
	}

	/**
	 * 获取 Builder 实例
	 * @return Builder 实例
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder 类，支持流式 API
	 */
	public static class Builder {

		private int chunkSize = DEFAULT_CHUNK_SIZE;

		private int sentenceOverlap = DEFAULT_SENTENCE_OVERLAP;

		/**
		 * 设置分块大小
		 * @param chunkSize 分块大小
		 * @return Builder 实例
		 */
		public Builder withChunkSize(int chunkSize) {
			this.chunkSize = chunkSize;
			return this;
		}

		/**
		 * 设置句子重叠数量
		 * @param sentenceOverlap 句子重叠数量
		 * @return Builder 实例
		 */
		public Builder withSentenceOverlap(int sentenceOverlap) {
			this.sentenceOverlap = sentenceOverlap;
			return this;
		}

		/**
		 * 构建 SentenceSplitter 实例
		 * @return 分块器实例
		 */
		public SentenceSplitter build() {
			return new SentenceSplitter(this);
		}

	}

	/**
	 * 实现 TextSplitter 的抽象方法 将单个文本切分为多个文本片段（按句子）
	 * @param text 输入文本
	 * @return 切分后的句子列表
	 */
	@Override
	protected List<String> splitText(String text) {
		return extractSentences(text);
	}

	/**
	 * 将文档列表按句子策略拆分为多个文档
	 * @param documents 输入文档列表
	 * @return 分块后的文档列表
	 */
	@Override
	public List<Document> apply(List<Document> documents) {
		if (CollectionUtils.isEmpty(documents)) {
			return new ArrayList<>();
		}

		List<Document> result = new ArrayList<>();

		for (Document document : documents) {
			String text = document.getText();
			if (!StringUtils.hasText(text)) {
				log.warn("Document content is empty, skipping");
				continue;
			}

			List<Document> splitDocs = splitDocument(document);
			result.addAll(splitDocs);
		}

		log.info("Split {} documents into {} chunks using sentence splitter", documents.size(), result.size());
		return result;
	}

	/**
	 * 拆分单个文档
	 * @param document 原始文档
	 * @return 分块后的文档列表
	 */
	private List<Document> splitDocument(Document document) {
		String text = document.getText();

		// 1. 提取所有句子
		List<String> sentences = extractSentences(text);

		if (sentences.isEmpty()) {
			log.warn("No sentences extracted from document, returning original");
			return List.of(document);
		}

		log.debug("Extracted {} sentences from document", sentences.size());

		// 2. 将句子组合成分块
		List<Document> result = new ArrayList<>();
		List<String> currentChunk = new ArrayList<>();
		int currentSize = 0;

		for (int i = 0; i < sentences.size(); i++) {
			String sentence = sentences.get(i);
			int sentenceSize = sentence.length();

			// 如果加入当前句子会超过分块大小，且当前分块不为空，则保存当前分块
			if (currentSize + sentenceSize > this.chunkSize && !currentChunk.isEmpty()) {
				// 保存当前分块
				String chunkContent = String.join("", currentChunk);
				Document chunkDoc = createChunkDocument(chunkContent, document, result.size());
				result.add(chunkDoc);

				// 处理句子重叠：保留最后几个句子作为下一个分块的开始
				if (this.sentenceOverlap > 0 && currentChunk.size() > this.sentenceOverlap) {
					List<String> overlapSentences = currentChunk.subList(currentChunk.size() - this.sentenceOverlap,
							currentChunk.size());
					currentChunk = new ArrayList<>(overlapSentences);
					currentSize = overlapSentences.stream().mapToInt(String::length).sum();
				}
				else {
					currentChunk = new ArrayList<>();
					currentSize = 0;
				}
			}

			// 添加当前句子
			currentChunk.add(sentence);
			currentSize += sentenceSize;
		}

		// 处理最后一个分块
		if (!currentChunk.isEmpty()) {
			String chunkContent = String.join("", currentChunk);
			Document chunkDoc = createChunkDocument(chunkContent, document, result.size());
			result.add(chunkDoc);
		}

		log.debug("Split document into {} chunks", result.size());
		return result;
	}

	/**
	 * 从文本中提取所有句子
	 * @param text 原始文本
	 * @return 句子列表
	 */
	private List<String> extractSentences(String text) {
		List<String> sentences = new ArrayList<>();

		Matcher matcher = SENTENCE_PATTERN.matcher(text);
		int lastEnd = 0;

		while (matcher.find()) {
			String sentence = matcher.group(1).trim();
			if (StringUtils.hasText(sentence)) {
				sentences.add(sentence);
			}
			lastEnd = matcher.end();
		}

		// 处理最后剩余的文本（可能没有句子结束符）
		if (lastEnd < text.length()) {
			String remaining = text.substring(lastEnd).trim();
			if (StringUtils.hasText(remaining)) {
				sentences.add(remaining);
			}
		}

		return sentences;
	}

	/**
	 * 创建分块文档
	 * @param content 分块内容
	 * @param originalDocument 原始文档
	 * @param chunkIndex 分块索引
	 * @return 分块后的文档
	 */
	private Document createChunkDocument(String content, Document originalDocument, int chunkIndex) {
		Document chunkDoc = new Document(content);

		// 复制元数据
		if (originalDocument.getMetadata() != null) {
			chunkDoc.getMetadata().putAll(originalDocument.getMetadata());
		}

		// 添加分块相关信息到元数据
		chunkDoc.getMetadata().put("chunk_index", chunkIndex);
		chunkDoc.getMetadata().put("chunk_size", content.length());
		chunkDoc.getMetadata().put("splitter_type", "sentence");

		return chunkDoc;
	}

	@Override
	public String toString() {
		return String.format("SentenceSplitter(chunkSize=%d, sentenceOverlap=%d)", this.chunkSize,
				this.sentenceOverlap);
	}

}
