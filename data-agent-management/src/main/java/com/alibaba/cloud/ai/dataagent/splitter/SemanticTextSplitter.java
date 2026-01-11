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
 * 生产级语义文本分块器 策略：滑动窗口Embedding + 语义相似度切分 + 最大长度强制切分
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

	private final double similarityThreshold; // 建议 0.7 - 0.8

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

		// 记录当前块包含的句子数量，用于后续相似度比较的索引对齐
		int sentencesInCurrentChunk = 0;

		for (int i = 0; i < sentences.size(); i++) {
			String sentence = sentences.get(i);

			// 第一句直接加入
			if (currentChunk.length() == 0) {
				currentChunk.append(sentence);
				sentencesInCurrentChunk++;
				continue;
			}

			// --- 决策：是否需要在当前句子之前切一刀？ ---
			boolean shouldSplit = false;

			// 1. 长度检查：加上这句是否超长？
			if (currentChunk.length() + sentence.length() > maxChunkSize) {
				log.debug("Splitting at index {} due to max size limit", i);
				shouldSplit = true;
			}
			// 2. 语义检查：和上一句（或当前块的主题）相比，语义是否突变？
			else if (i < embeddings.size()) {
				// 比较：当前句子的窗口向量 vs 上一句子的窗口向量
				// 注意：embeddings 索引与 sentences 索引是一一对应的
				double similarity = cosineSimilarity(embeddings.get(i - 1), embeddings.get(i));

				if (similarity < similarityThreshold) {
					// 只有当当前块已经达到最小长度时，才允许按语义切分
					// 否则即使语义变了，为了保证块不太碎，也强行合并
					if (currentChunk.length() >= minChunkSize) {
						log.debug("Splitting at index {} due to semantic shift (sim={})", i, similarity);
						shouldSplit = true;
					}
				}
			}

			// --- 执行动作 ---
			if (shouldSplit) {
				chunks.add(currentChunk.toString().trim());
				currentChunk.setLength(0); // 清空
				sentencesInCurrentChunk = 0;
			}

			// 如果不是第一句（或刚清空过），加个空格（英文）或不加（中文视情况）
			// 简单起见，这里假设需要空格或者直接拼接。中文通常直接拼，英文要空格。
			// 更加严谨的做法是判断字符类型。
			if (currentChunk.length() > 0 && !isChinese(sentence)) {
				currentChunk.append(" ");
			}

			currentChunk.append(sentence);
			sentencesInCurrentChunk++;
		}

		// 处理最后一个块
		if (currentChunk.length() > 0) {
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
		while (matcher.find()) {
			String s = matcher.group().trim();
			if (!s.isEmpty())
				sentences.add(s);
		}
		return sentences;
	}

	/**
	 * 滑动窗口构建上下文
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
		List<float[]> allEmbeddings = new ArrayList<>();
		// 获取向量维度的占位符（需要知道模型维度，通常第一次请求成功后可获知，或者硬编码 1536/768/1024）
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
				// 填充零向量，长度假设 768，生产环境最好动态获取或配置
				for (int k = 0; k < batch.size(); k++)
					allEmbeddings.add(new float[768]);
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
