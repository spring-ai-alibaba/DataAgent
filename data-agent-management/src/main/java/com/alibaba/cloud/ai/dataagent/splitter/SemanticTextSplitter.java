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
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义分块器（Semantic Text Splitter）
 * @author Zihenzzz
 * @since 2025/1/3
 */
@Slf4j
public class SemanticTextSplitter extends TextSplitter {

	/**
	 * 句子分隔符正则表达式
	 */
	private static final Pattern SENTENCE_PATTERN = Pattern.compile("([^。！？；.!?;]+[。！？；.!?;]+[\"'）\\)\\]]*\\s*)");

	/**
	 * 默认最小分块大小（字符数）
	 */
	private static final int DEFAULT_MIN_CHUNK_SIZE = 200;

	/**
	 * 默认最大分块大小（字符数）
	 */
	private static final int DEFAULT_MAX_CHUNK_SIZE = 1000;

	/**
	 * 默认语义相似度阈值（0-1之间，越低越容易分块）
	 */
	private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.7;

	/**
	 * Embedding 模型（用于计算句子向量）
	 */
	private final EmbeddingModel embeddingModel;

	/**
	 * 最小分块大小
	 */
	private final int minChunkSize;

	/**
	 * 最大分块大小
	 */
	private final int maxChunkSize;

	/**
	 * 语义相似度阈值（当相邻句子相似度低于此值时分块）
	 */
	private final double similarityThreshold;

	/**
	 * 私有构造函数，使用 Builder 构建
	 */
	private SemanticTextSplitter(Builder builder) {
		this.embeddingModel = builder.embeddingModel;
		this.minChunkSize = builder.minChunkSize > 0 ? builder.minChunkSize : DEFAULT_MIN_CHUNK_SIZE;
		this.maxChunkSize = builder.maxChunkSize > 0 ? builder.maxChunkSize : DEFAULT_MAX_CHUNK_SIZE;
		this.similarityThreshold = builder.similarityThreshold > 0 ? builder.similarityThreshold
				: DEFAULT_SIMILARITY_THRESHOLD;

		if (this.embeddingModel == null) {
			throw new IllegalArgumentException("EmbeddingModel is required for SemanticTextSplitter");
		}

		log.info("Initialized SemanticTextSplitter with minChunkSize={}, maxChunkSize={}, similarityThreshold={}",
				this.minChunkSize, this.maxChunkSize, this.similarityThreshold);
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

		private EmbeddingModel embeddingModel;

		private int minChunkSize = DEFAULT_MIN_CHUNK_SIZE;

		private int maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;

		private double similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;

		/**
		 * 设置 Embedding 模型（必需）
		 * @param embeddingModel Embedding 模型
		 * @return Builder 实例
		 */
		public Builder withEmbeddingModel(EmbeddingModel embeddingModel) {
			this.embeddingModel = embeddingModel;
			return this;
		}

		/**
		 * 设置最小分块大小
		 * @param minChunkSize 最小分块大小
		 * @return Builder 实例
		 */
		public Builder withMinChunkSize(int minChunkSize) {
			this.minChunkSize = minChunkSize;
			return this;
		}

		/**
		 * 设置最大分块大小
		 * @param maxChunkSize 最大分块大小
		 * @return Builder 实例
		 */
		public Builder withMaxChunkSize(int maxChunkSize) {
			this.maxChunkSize = maxChunkSize;
			return this;
		}

		/**
		 * 设置语义相似度阈值
		 * @param similarityThreshold 相似度阈值（0-1之间）
		 * @return Builder 实例
		 */
		public Builder withSimilarityThreshold(double similarityThreshold) {
			this.similarityThreshold = similarityThreshold;
			return this;
		}

		/**
		 * 构建 SemanticTextSplitter 实例
		 * @return 分块器实例
		 */
		public SemanticTextSplitter build() {
			return new SemanticTextSplitter(this);
		}

	}

	/**
	 * 实现 TextSplitter 的抽象方法 将单个文本切分为多个文本片段（按语义）
	 * @param text 输入文本
	 * @return 切分后的文本片段列表
	 */
	@Override
	protected List<String> splitText(String text) {
		return extractSentences(text);
	}

	/**
	 * 将文档列表按语义策略拆分为多个文档
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

		log.info("Split {} documents into {} chunks using semantic splitter", documents.size(), result.size());
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

		if (sentences.size() == 1) {
			// 只有一个句子，直接返回
			return List.of(createChunkDocument(sentences.get(0), document, 0));
		}

		log.debug("Extracted {} sentences from document", sentences.size());

		// 2. 计算句子的 embedding 向量
		List<float[]> sentenceEmbeddings = computeSentenceEmbeddings(sentences);

		// 3. 基于语义相似度进行分块
		List<Document> result = semanticChunking(sentences, sentenceEmbeddings, document);

		log.debug("Split document into {} semantic chunks", result.size());
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

		// 处理最后剩余的文本
		if (lastEnd < text.length()) {
			String remaining = text.substring(lastEnd).trim();
			if (StringUtils.hasText(remaining)) {
				sentences.add(remaining);
			}
		}

		return sentences;
	}

	/**
	 * 计算所有句子的 embedding 向量
	 * @param sentences 句子列表
	 * @return embedding 向量列表
	 */
	private List<float[]> computeSentenceEmbeddings(List<String> sentences) {
		List<float[]> embeddings = new ArrayList<>();

		try {
			// 批量计算 embedding（提高效率）
			EmbeddingResponse response = embeddingModel.embedForResponse(sentences);

			for (int i = 0; i < response.getResults().size(); i++) {
				float[] embedding = response.getResults().get(i).getOutput();
				embeddings.add(embedding);
			}

			log.debug("Computed embeddings for {} sentences", sentences.size());
		}
		catch (Exception e) {
			log.error("Failed to compute sentence embeddings, falling back to simple chunking", e);
			// 如果 embedding 失败，返回空列表，后续会使用简单分块策略
			return new ArrayList<>();
		}

		return embeddings;
	}

	/**
	 * 基于语义相似度进行分块
	 * @param sentences 句子列表
	 * @param embeddings embedding 向量列表
	 * @param originalDocument 原始文档
	 * @return 分块后的文档列表
	 */
	private List<Document> semanticChunking(List<String> sentences, List<float[]> embeddings,
			Document originalDocument) {

		List<Document> result = new ArrayList<>();

		// 如果 embedding 失败，使用简单的大小分块策略
		if (embeddings.isEmpty()) {
			return fallbackChunking(sentences, originalDocument);
		}

		List<String> currentChunk = new ArrayList<>();
		int currentSize = 0;

		for (int i = 0; i < sentences.size(); i++) {
			String sentence = sentences.get(i);
			int sentenceSize = sentence.length();

			boolean shouldSplit = false;

			// 检查是否需要分块
			if (!currentChunk.isEmpty()) {
				// 1. 如果超过最大分块大小，必须分块
				if (currentSize + sentenceSize > this.maxChunkSize) {
					shouldSplit = true;
					log.debug("Splitting due to max size: current={}, sentence={}", currentSize, sentenceSize);
				}
				// 2. 如果达到最小分块大小，检查语义相似度
				else if (currentSize >= this.minChunkSize && i < embeddings.size()) {
					double similarity = cosineSimilarity(embeddings.get(i - 1), embeddings.get(i));
					if (similarity < this.similarityThreshold) {
						shouldSplit = true;
						log.debug("Splitting due to low similarity: {}", similarity);
					}
				}
			}

			if (shouldSplit) {
				// 保存当前分块
				String chunkContent = String.join("", currentChunk);
				Document chunkDoc = createChunkDocument(chunkContent, originalDocument, result.size());
				result.add(chunkDoc);

				// 开始新分块
				currentChunk = new ArrayList<>();
				currentSize = 0;
			}

			// 添加当前句子
			currentChunk.add(sentence);
			currentSize += sentenceSize;
		}

		// 处理最后一个分块
		if (!currentChunk.isEmpty()) {
			String chunkContent = String.join("", currentChunk);
			Document chunkDoc = createChunkDocument(chunkContent, originalDocument, result.size());
			result.add(chunkDoc);
		}

		return result;
	}

	/**
	 * 降级分块策略（当 embedding 失败时使用）
	 * @param sentences 句子列表
	 * @param originalDocument 原始文档
	 * @return 分块后的文档列表
	 */
	private List<Document> fallbackChunking(List<String> sentences, Document originalDocument) {
		log.warn("Using fallback chunking strategy (simple size-based)");

		List<Document> result = new ArrayList<>();
		List<String> currentChunk = new ArrayList<>();
		int currentSize = 0;

		for (String sentence : sentences) {
			int sentenceSize = sentence.length();

			if (currentSize + sentenceSize > this.maxChunkSize && !currentChunk.isEmpty()) {
				String chunkContent = String.join("", currentChunk);
				result.add(createChunkDocument(chunkContent, originalDocument, result.size()));

				currentChunk = new ArrayList<>();
				currentSize = 0;
			}

			currentChunk.add(sentence);
			currentSize += sentenceSize;
		}

		if (!currentChunk.isEmpty()) {
			String chunkContent = String.join("", currentChunk);
			result.add(createChunkDocument(chunkContent, originalDocument, result.size()));
		}

		return result;
	}

	/**
	 * 计算两个向量的余弦相似度
	 * @param vec1 向量1
	 * @param vec2 向量2
	 * @return 余弦相似度（0-1之间，1表示完全相同）
	 */
	private double cosineSimilarity(float[] vec1, float[] vec2) {
		if (vec1.length != vec2.length) {
			throw new IllegalArgumentException("Vectors must have the same length");
		}

		double dotProduct = 0.0;
		double norm1 = 0.0;
		double norm2 = 0.0;

		for (int i = 0; i < vec1.length; i++) {
			dotProduct += vec1[i] * vec2[i];
			norm1 += vec1[i] * vec1[i];
			norm2 += vec2[i] * vec2[i];
		}

		if (norm1 == 0.0 || norm2 == 0.0) {
			return 0.0;
		}

		return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
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
		chunkDoc.getMetadata().put("splitter_type", "semantic");

		return chunkDoc;
	}

	@Override
	public String toString() {
		return String.format("SemanticTextSplitter(minChunkSize=%d, maxChunkSize=%d, similarityThreshold=%.2f)",
				this.minChunkSize, this.maxChunkSize, this.similarityThreshold);
	}

}
