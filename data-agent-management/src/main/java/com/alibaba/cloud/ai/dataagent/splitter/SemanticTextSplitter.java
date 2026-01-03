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
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 语义文本分块器
 *
 * @author zihenzzz
 * @since 2025-01-03
 */
@Slf4j
@Builder
public class SemanticTextSplitter extends TextSplitter {

	private final EmbeddingModel embeddingModel;

	private final int minChunkSize;

	private final int maxChunkSize;

	private final double similarityThreshold;

	/**
	 * Embedding API 每批次最大句子数（阿里 text-embedding-v4 最多支持 10 个）
	 */
	private static final int EMBEDDING_BATCH_SIZE = 10;

	/**
	 * 改进的句子分隔正则：支持标点符号、换行符、列表项等
	 */
	private static final Pattern SENTENCE_PATTERN = Pattern.compile("([^。！？；.!?;\\n]+[。！？；.!?;]?|[^。！？；.!?;\\n]*\\n)");

	/**
	 * 覆盖 splitText 方法，返回 List<String>
	 */
	public List<String> splitText(String text) {
		// 1. 提取句子
		List<String> sentences = extractSentences(text);
		if (sentences.isEmpty()) {
			return List.of(text);
		}

		log.debug("Extracted {} sentences from text", sentences.size());

		// 2. 使用滑动窗口构建上下文句子（用于计算 embedding）
		List<String> contextSentences = buildContextSentences(sentences);

		// 3. 分批调用 Embedding API
		List<float[]> embeddings = batchEmbed(contextSentences);

		// 4. 计算相似度并确定切分点
		List<Integer> splitIndices = findSplitIndices(embeddings);

		// 5. 根据切分点生成最终的文本块
		return createChunks(sentences, splitIndices);
	}

	/**
	 * 提取句子（支持标点符号、换行符等多种分隔符）
	 */
	private List<String> extractSentences(String text) {
		List<String> sentences = new ArrayList<>();
		Matcher matcher = SENTENCE_PATTERN.matcher(text);

		while (matcher.find()) {
			String sentence = matcher.group().trim();
			if (!sentence.isEmpty()) {
				sentences.add(sentence);
			}
		}

		return sentences;
	}

	/**
	 * 使用滑动窗口构建上下文句子 Vector(i) = Embed(Sent[i-1] + Sent[i] + Sent[i+1])
	 */
	private List<String> buildContextSentences(List<String> sentences) {
		List<String> contextSentences = new ArrayList<>();

		for (int i = 0; i < sentences.size(); i++) {
			StringBuilder context = new StringBuilder();

			// 前一句（如果存在）
			if (i > 0) {
				context.append(sentences.get(i - 1)).append(" ");
			}

			// 当前句
			context.append(sentences.get(i));

			// 后一句（如果存在）
			if (i < sentences.size() - 1) {
				context.append(" ").append(sentences.get(i + 1));
			}

			contextSentences.add(context.toString());
		}

		return contextSentences;
	}

	/**
	 * 分批调用 Embedding API
	 */
	private List<float[]> batchEmbed(List<String> texts) {
		List<float[]> allEmbeddings = new ArrayList<>();

		// 分批处理
		for (int i = 0; i < texts.size(); i += EMBEDDING_BATCH_SIZE) {
			int endIdx = Math.min(i + EMBEDDING_BATCH_SIZE, texts.size());
			List<String> batch = texts.subList(i, endIdx);

			log.debug("Processing embedding batch {}/{} (size: {})", (i / EMBEDDING_BATCH_SIZE) + 1,
					(texts.size() + EMBEDDING_BATCH_SIZE - 1) / EMBEDDING_BATCH_SIZE, batch.size());

			try {
				// 调用 Embedding API
				EmbeddingResponse response = embeddingModel.embedForResponse(batch);
				List<float[]> batchEmbeddings = response.getResults()
					.stream()
					.map(embedding -> embedding.getOutput())
					.collect(Collectors.toList());

				allEmbeddings.addAll(batchEmbeddings);
			}
			catch (Exception e) {
				log.error("Failed to get embeddings for batch starting at index {}", i, e);
				// 失败时使用零向量作为占位符
				for (int j = 0; j < batch.size(); j++) {
					allEmbeddings.add(new float[768]); // 假设向量维度为 768
				}
			}
		}

		return allEmbeddings;
	}

	/**
	 * 计算余弦相似度
	 */
	private double cosineSimilarity(float[] vec1, float[] vec2) {
		if (vec1.length != vec2.length) {
			log.warn("Vector dimensions mismatch: {} vs {}", vec1.length, vec2.length);
			return 0.0;
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
	 * 找到语义切分点
	 */
	private List<Integer> findSplitIndices(List<float[]> embeddings) {
		List<Integer> splitIndices = new ArrayList<>();
		splitIndices.add(0); // 起始位置

		for (int i = 1; i < embeddings.size(); i++) {
			double similarity = cosineSimilarity(embeddings.get(i - 1), embeddings.get(i));

			// 相似度低于阈值，说明语义发生了较大变化，需要切分
			if (similarity < similarityThreshold) {
				log.debug("Found semantic boundary at sentence {} (similarity: {:.3f})", i, similarity);
				splitIndices.add(i);
			}
		}

		return splitIndices;
	}

	/**
	 * 根据切分点生成文本块
	 */
	private List<String> createChunks(List<String> sentences, List<Integer> splitIndices) {
		List<String> chunks = new ArrayList<>();

		for (int i = 0; i < splitIndices.size(); i++) {
			int startIdx = splitIndices.get(i);
			int endIdx = (i + 1 < splitIndices.size()) ? splitIndices.get(i + 1) : sentences.size();

			StringBuilder chunkText = new StringBuilder();
			for (int j = startIdx; j < endIdx; j++) {
				chunkText.append(sentences.get(j));
				if (j < endIdx - 1) {
					chunkText.append(" ");
				}
			}

			String chunk = chunkText.toString().trim();

			// 检查块大小是否符合要求
			if (chunk.length() >= minChunkSize) {
				// 如果超过最大大小，进一步切分
				if (chunk.length() > maxChunkSize) {
					chunks.addAll(splitLargeChunk(chunk));
				}
				else {
					chunks.add(chunk);
				}
			}
			else if (!chunk.isEmpty()) {
				// 太小的块，如果不是最后一个，尝试合并到下一个
				if (i == splitIndices.size() - 1) {
					chunks.add(chunk); // 最后一个块，即使很小也保留
				}
			}
		}

		log.info("Created {} semantic chunks from {} sentences", chunks.size(), sentences.size());
		return chunks;
	}

	/**
	 * 切分过大的块
	 */
	private List<String> splitLargeChunk(String chunk) {
		List<String> subChunks = new ArrayList<>();
		int start = 0;

		while (start < chunk.length()) {
			int end = Math.min(start + maxChunkSize, chunk.length());

			// 尝试在句子边界处切分
			if (end < chunk.length()) {
				int lastPeriod = chunk.lastIndexOf('。', end);
				int lastNewline = chunk.lastIndexOf('\n', end);
				int splitPoint = Math.max(lastPeriod, lastNewline);

				if (splitPoint > start + minChunkSize) {
					end = splitPoint + 1;
				}
			}

			subChunks.add(chunk.substring(start, end).trim());
			start = end;
		}

		return subChunks;
	}

}
