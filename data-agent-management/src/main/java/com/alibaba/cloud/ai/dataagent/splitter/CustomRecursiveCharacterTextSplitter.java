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

/**
 * 自定义递归字符文本分块器
 * @author Zihenzzz
 * @since 2025/1/2
 */
@Slf4j
public class CustomRecursiveCharacterTextSplitter extends TextSplitter {

	/**
	 * 默认分隔符列表，按优先级排序
	 *
	 * <p>
	 * 优先级从高到低：双换行(段落) > 单换行 > 中文标点 > 英文标点 > 空格
	 * </p>
	 */
	private static final String[] DEFAULT_SEPARATORS = new String[] { "\n\n", "\n", "\r\n", "\r", "。", "；", "，", "！",
			"？", ".", ";", ",", "!", "?", " ", "" };

	/**
	 * 默认分块大小
	 */
	private static final int DEFAULT_CHUNK_SIZE = 1000;

	/**
	 * 默认最小分块字符数
	 */
	private static final int DEFAULT_MIN_CHUNK_SIZE_CHARS = 400;

	/**
	 * 默认重叠字符数
	 */
	private static final int DEFAULT_CHUNK_OVERLAP = 200;

	/**
	 * 分块大小（基于字符数）
	 */
	private final int chunkSize;

	/**
	 * 最小分块字符数
	 */
	private final int minChunkSizeChars;

	/**
	 * 重叠区域字符数，用于保持上下文连贯性
	 */
	private final int chunkOverlap;

	/**
	 * 分隔符列表
	 */
	private final String[] separators;

	/**
	 * 私有构造函数，使用 Builder 构建
	 */
	private CustomRecursiveCharacterTextSplitter(Builder builder) {
		this.chunkSize = builder.chunkSize > 0 ? builder.chunkSize : DEFAULT_CHUNK_SIZE;
		this.minChunkSizeChars = builder.minChunkSizeChars > 0 ? builder.minChunkSizeChars
				: DEFAULT_MIN_CHUNK_SIZE_CHARS;
		this.chunkOverlap = builder.chunkOverlap >= 0 ? builder.chunkOverlap : DEFAULT_CHUNK_OVERLAP;

		if (builder.separators != null && builder.separators.length > 0) {
			this.separators = builder.separators;
		}
		else {
			this.separators = DEFAULT_SEPARATORS;
		}

		log.info(
				"Initialized CustomRecursiveCharacterTextSplitter with chunkSize={}, minChunkSizeChars={}, "
						+ "chunkOverlap={}, separators count={}",
				this.chunkSize, this.minChunkSizeChars, this.chunkOverlap, this.separators.length);
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

		private int minChunkSizeChars = DEFAULT_MIN_CHUNK_SIZE_CHARS;

		private int chunkOverlap = DEFAULT_CHUNK_OVERLAP;

		private String[] separators;

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
		 * 设置最小分块字符数
		 * @param minChunkSizeChars 最小分块字符数
		 * @return Builder 实例
		 */
		public Builder withMinChunkSizeChars(int minChunkSizeChars) {
			this.minChunkSizeChars = minChunkSizeChars;
			return this;
		}

		/**
		 * 设置重叠区域字符数
		 * @param chunkOverlap 重叠区域字符数
		 * @return Builder 实例
		 */
		public Builder withChunkOverlap(int chunkOverlap) {
			this.chunkOverlap = chunkOverlap;
			return this;
		}

		/**
		 * 设置分隔符列表
		 * @param separators 分隔符数组
		 * @return Builder 实例
		 */
		public Builder withSeparators(String[] separators) {
			this.separators = separators;
			return this;
		}

		/**
		 * 构建 CustomRecursiveCharacterTextSplitter 实例
		 * @return 分块器实例
		 */
		public CustomRecursiveCharacterTextSplitter build() {
			return new CustomRecursiveCharacterTextSplitter(this);
		}

	}

	/**
	 * 实现 TextSplitter 的抽象方法 将单个文本切分为多个文本片段
	 * @param text 输入文本
	 * @return 切分后的文本片段列表
	 */
	@Override
	protected List<String> splitText(String text) {
		return splitTextRecursively(text);
	}

	/**
	 * 将单个文档按分块策略拆分为多个文档
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

		log.info("Split {} documents into {} chunks", documents.size(), result.size());
		return result;
	}

	/**
	 * 拆分单个文档
	 * @param document 原始文档
	 * @return 分块后的文档列表
	 */
	private List<Document> splitDocument(Document document) {
		String text = document.getText();
		List<String> splits = splitTextRecursively(text);

		List<Document> result = new ArrayList<>();
		StringBuilder currentChunk = new StringBuilder();
		int currentSize = 0;

		for (int i = 0; i < splits.size(); i++) {
			String part = splits.get(i);
			int partSize = part.length();

			// 如果当前部分加上已有内容超过分块大小，且当前chunk不为空，则保存当前chunk
			if (currentSize + partSize > this.chunkSize && currentSize > 0) {
				Document chunkDoc = createChunkDocument(currentChunk.toString(), document, i);
				result.add(chunkDoc);

				// 处理重叠区域
				if (this.chunkOverlap > 0 && i < splits.size() - 1) {
					currentChunk = new StringBuilder(getOverlapText(splits, i, this.chunkOverlap));
					currentSize = currentChunk.length();
				}
				else {
					currentChunk = new StringBuilder();
					currentSize = 0;
				}
			}

			currentChunk.append(part);
			currentSize += partSize;
		}

		// 处理最后一个chunk
		if (currentSize > 0) {
			Document chunkDoc = createChunkDocument(currentChunk.toString(), document, splits.size());
			result.add(chunkDoc);
		}

		log.debug("Split document into {} chunks", result.size());
		return result;
	}

	/**
	 * 递归按分隔符拆分文本
	 * @param text 原始文本
	 * @return 按分隔符拆分后的文本片段列表
	 */
	private List<String> splitTextRecursively(String text) {
		return splitTextRecursively(text, 0);
	}

	/**
	 * 递归按分隔符拆分文本（内部方法）
	 * @param text 原始文本
	 * @param separatorIndex 当前使用的分隔符索引
	 * @return 拆分后的文本片段列表
	 */
	private List<String> splitTextRecursively(String text, int separatorIndex) {
		// 递归终止条件：已尝试完所有分隔符或文本长度小于最小分块大小
		if (separatorIndex >= this.separators.length) {
			List<String> result = new ArrayList<>();
			if (StringUtils.hasText(text)) {
				result.add(text);
			}
			return result;
		}

		String separator = this.separators[separatorIndex];
		List<String> splits = splitBySeparator(text, separator);

		// 如果拆分后所有片段都小于最小分块大小，则返回结果
		if (areAllSplitsSmallEnough(splits)) {
			return splits;
		}

		// 否则，对每个大于最小分块大小的片段尝试使用下一个分隔符
		List<String> result = new ArrayList<>();
		for (String split : splits) {
			if (split.length() > this.minChunkSizeChars) {
				// 递归尝试下一个分隔符
				result.addAll(splitTextRecursively(split, separatorIndex + 1));
			}
			else {
				result.add(split);
			}
		}

		return result;
	}

	/**
	 * 按指定分隔符拆分文本
	 * @param text 原始文本
	 * @param separator 分隔符
	 * @return 拆分后的文本片段列表
	 */
	private List<String> splitBySeparator(String text, String separator) {
		List<String> result = new ArrayList<>();

		if (!StringUtils.hasText(separator)) {
			// 空分隔符按字符拆分
			for (int i = 0; i < text.length(); i++) {
				result.add(String.valueOf(text.charAt(i)));
			}
			return result;
		}

		int start = 0;
		int idx = text.indexOf(separator);

		while (idx != -1) {
			if (idx > start) {
				result.add(text.substring(start, idx));
			}
			start = idx + separator.length();
			idx = text.indexOf(separator, start);
		}

		// 添加最后一段
		if (start < text.length()) {
			result.add(text.substring(start));
		}

		return result;
	}

	/**
	 * 检查所有拆分后的片段是否都小于最小分块大小
	 * @param splits 文本片段列表
	 * @return true 表示都小于最小分块大小
	 */
	private boolean areAllSplitsSmallEnough(List<String> splits) {
		for (String split : splits) {
			if (split.length() > this.minChunkSizeChars) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 获取重叠区域的文本
	 * @param splits 所有文本片段
	 * @param currentIndex 当前处理到的位置
	 * @param overlapSize 重叠大小
	 * @return 重叠区域的文本
	 */
	private String getOverlapText(List<String> splits, int currentIndex, int overlapSize) {
		StringBuilder overlap = new StringBuilder();

		// 从当前位置向前收集文本，直到达到重叠大小
		for (int i = currentIndex - 1; i >= 0 && overlap.length() < overlapSize; i--) {
			String part = splits.get(i);
			if (overlap.length() + part.length() <= overlapSize) {
				overlap.insert(0, part);
			}
			else {
				// 需要截取部分内容
				int remaining = overlapSize - overlap.length();
				overlap.insert(0, part.substring(part.length() - remaining));
				break;
			}
		}

		return overlap.toString();
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
		chunkDoc.getMetadata().put("splitter_type", "recursive");

		return chunkDoc;
	}

	@Override
	public String toString() {
		return String.format(
				"CustomRecursiveCharacterTextSplitter(chunkSize=%d, minChunkSizeChars=%d, chunkOverlap=%d, separators=%d)",
				this.chunkSize, this.minChunkSizeChars, this.chunkOverlap, this.separators.length);

	}

}
