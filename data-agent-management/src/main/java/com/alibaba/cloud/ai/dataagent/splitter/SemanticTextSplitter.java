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

/**
 * 生产级语义文本分块器 策略：滑动窗口Embedding + 语义相似度切分 + 最大长度强制切分 它尽可能地把句子往一个块里塞（为了上下文完整），直到 塞满了（超长） 或者
 * 发现话题变了（语义突变） 才会停下来切一刀。
 *
 * @author zihenzzz
 * @since 2025-01-03
 */
@Slf4j
@Builder
public class SemanticTextSplitter extends TextSplitter {

	private final EmbeddingModel embeddingModel;

	private final int minChunkSize; // 建议 200

	private final int maxChunkSize; // 建议 1000

	private final double similarityThreshold;

	/**
	 * Embedding API 每批次最大句子数 阿里 text-embedding-v4 支持 10，OpenAI 支持更多。建议可配置。
	 */
	@Builder.Default
	private int embeddingBatchSize = 10;

	/**
	 * 句子正则：匹配标点或换行
	 */
	private static final Pattern SENTENCE_PATTERN = Pattern.compile("([^。！？；.!?;\\n]+[。！？；.!?;]?|[^。！？；.!?;\\n]*\\n)");

	@Override
	public List<String> splitText(String text) {
		if (text == null || text.trim().isEmpty()) {
			return List.of();
		}

		// 1. 提取句子
		List<String> sentences = extractSentences(text);
		if (sentences.isEmpty()) {
			return List.of(text);
		}
		log.debug("Extracted {} sentences", sentences.size());

		// 2. 只有一句，直接返回（或者检查长度）
		if (sentences.size() == 1) {
			return splitLargeChunk(sentences.get(0));
		}

		// 3. 构建滑动窗口上下文 (Windowed Context)
		List<String> contextSentences = buildContextSentences(sentences);

		// 4. 计算 Embeddings
		List<float[]> embeddings = batchEmbed(contextSentences);

		// 5. 核心：基于 语义+长度 双重约束进行合并
		return combineSentences(sentences, embeddings);
	}

	/**
	 * 核心逻辑：合并句子
	 */
	private List<String> combineSentences(List<String> sentences, List<float[]> embeddings) {
		List<String> chunks = new ArrayList<>();
		StringBuilder currentChunk = new StringBuilder();

		for (int i = 0; i < sentences.size(); i++) {
			String sentence = sentences.get(i);

			// 先检查单句是否本身就是超出最大长度
			if (sentence.length() > maxChunkSize) {
				// 1. 先把当前的结算了
				if (!currentChunk.isEmpty()) {
					chunks.add(currentChunk.toString().trim());
					currentChunk.setLength(0);
				}
				// 2. 巨无霸单独切分
				chunks.addAll(splitLargeChunk(sentence));
				// 处理下一句
				continue;
			}

			// --- 决策：是否需要在当前句子之前切一刀？ ---
			boolean shouldSplit = false;

			// 如果 currentChunk 为空，说明是新的一块，不需要 split，直接 add 即可
			if (!currentChunk.isEmpty()) {
				// 1. 长度检查：加上这句是否超长？
				if (currentChunk.length() + sentence.length() > maxChunkSize) {
					log.debug("Splitting at index {} due to max size limit", i);
					shouldSplit = true;
				}
				// 2. 语义检查：语义突变？
				else if (i < embeddings.size()) {
					double similarity = cosineSimilarity(embeddings.get(i - 1), embeddings.get(i));
					// 只有当当前块已经达到最小长度时，才允许按语义切分
					// 否则即使语义变了，为了保证块不太碎，也强行合并
					if (similarity < similarityThreshold && currentChunk.length() >= minChunkSize) {
						log.debug("Splitting at index {} due to semantic shift (sim={})", i, similarity);
						shouldSplit = true;
					}
				}
			}

			// --- 执行动作 ---
			if (shouldSplit) {
				chunks.add(currentChunk.toString().trim());
				currentChunk.setLength(0);
			}

			// 拼接空格逻辑
			if (!currentChunk.isEmpty() && !isChinese(sentence)) {
				currentChunk.append(" ");
			}

			currentChunk.append(sentence);
		}

		// 处理最后一个块
		if (!currentChunk.isEmpty()) {
			chunks.add(currentChunk.toString().trim());
		}

		return chunks;
	}

	// 简单的中文判断，用于决定拼接时加不加空格
	private boolean isChinese(String str) {
		return str.codePoints()
			.anyMatch(codepoint -> Character.UnicodeScript.of(codepoint) == Character.UnicodeScript.HAN);
	}

	/**
	 * 提取句子
	 */
	private List<String> extractSentences(String text) {
		List<String> sentences = new ArrayList<>();
		Matcher matcher = SENTENCE_PATTERN.matcher(text);
		int lastEnd = 0;

		while (matcher.find()) {
			String s = matcher.group().trim();
			if (!s.isEmpty())
				sentences.add(s);
			lastEnd = matcher.end();
		}

		// 兜底：防止正则漏掉最后一段没有标点的文本
		if (lastEnd < text.length()) {
			String tail = text.substring(lastEnd).trim();
			if (!tail.isEmpty()) {
				sentences.add(tail);
			}
		}

		return sentences;
	}

	/**
	 * 滑动窗口构建上下文 在计算第 i 句的向量时，我们实际送给模型的文本是：[第 i-1 句] + [第 i 句] + [第 i+1 句]。 目的：让 Embedding
	 * 向量包含上下文信息，算出来的相似度更准。
	 */
	private List<String> buildContextSentences(List<String> sentences) {
		List<String> contextSentences = new ArrayList<>();
		for (int i = 0; i < sentences.size(); i++) {
			StringBuilder context = new StringBuilder();
			if (i > 0)
				context.append(sentences.get(i - 1)).append(" ");
			context.append(sentences.get(i));
			if (i < sentences.size() - 1)
				context.append(" ").append(sentences.get(i + 1));
			contextSentences.add(context.toString());
		}
		return contextSentences;
	}

	/**
	 * 批量 Embedding (带容错)
	 */
	private List<float[]> batchEmbed(List<String> texts) {
		// 获取向量维度的占位符
		int dimensions = embeddingModel.dimensions();
		List<float[]> allEmbeddings = new ArrayList<>();
		// 这里为了安全，建议 catch 异常时填入 new float[0]，计算相似度时做空检查

		for (int i = 0; i < texts.size(); i += embeddingBatchSize) {
			int endIdx = Math.min(i + embeddingBatchSize, texts.size());
			List<String> batch = texts.subList(i, endIdx);
			try {
				EmbeddingResponse response = embeddingModel.embedForResponse(batch);
				// 假设 Spring AI 的 EmbeddingResponse 结构
				for (var result : response.getResults()) {
					allEmbeddings.add(result.getOutput());
				}
			}
			catch (Exception e) {
				log.error("Embedding failed for batch {}-{}", i, endIdx, e);
				// 填充零向量
				for (int k = 0; k < batch.size(); k++)
					allEmbeddings.add(new float[dimensions]);
			}
		}
		return allEmbeddings;
	}

	private double cosineSimilarity(float[] vec1, float[] vec2) {
		if (vec1 == null || vec2 == null || vec1.length != vec2.length)
			return 0.0;
		double dot = 0.0, norm1 = 0.0, norm2 = 0.0;
		for (int i = 0; i < vec1.length; i++) {
			dot += vec1[i] * vec2[i];
			norm1 += vec1[i] * vec1[i];
			norm2 += vec2[i] * vec2[i];
		}
		if (norm1 == 0 || norm2 == 0)
			return 0.0; // 零向量处理
		return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
	}

	/**
	 * 保底策略：如果单个句子本身就超长，还是得硬切
	 */
	private List<String> splitLargeChunk(String text) {
		List<String> result = new ArrayList<>();
		for (int i = 0; i < text.length(); i += maxChunkSize) {
			result.add(text.substring(i, Math.min(i + maxChunkSize, text.length())));
		}
		return result;
	}

}
