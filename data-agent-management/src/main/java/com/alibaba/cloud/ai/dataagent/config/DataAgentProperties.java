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

package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.common.constant.Constant;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmServiceEnum;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = Constant.PROJECT_PROPERTIES_PREFIX)
public class DataAgentProperties {

	private LlmServiceEnum llmServiceType = LlmServiceEnum.STREAM;

	/**
	 * spring.ai.alibaba.data-agent.embedding-batch.encoding-type=cl100k_base
	 * spring.ai.alibaba.data-agent.embedding-batch.max-token-count=2000
	 * spring.ai.alibaba.data-agent.embedding-batch.reserve-percentage=0.2
	 * spring.ai.alibaba.data-agent.embedding-batch.max-text-count=10
	 */
	private EmbeddingBatch embeddingBatch = new EmbeddingBatch();

	private VectorStoreProperties vectorStore = new VectorStoreProperties();

	/**
	 * sql执行失败重试次数
	 */
	private int maxSqlRetryCount = 10;

	/**
	 * sql优化最多次数
	 */
	private int maxSqlOptimizeCount = 10;

	/**
	 * sql优化分数阈值
	 */
	private double sqlScoreThreshold = 0.95;

	private TextSplitter textSplitter = new TextSplitter();

	/**
	 * 最多保留的对话轮数
	 */
	private int maxturnhistory = 5;

	/**
	 * 单次规划最大长度限制
	 */
	private int maxplanlength = 2000;

	// 每张表的最大预估列数
	private int maxColumnsPerTable = 50;

	@Getter
	@Setter
	public static class TextSplitter {

		// --- 通用参数 ---
		/**
		 * 默认分块大小，基于token数量 默认值：1000
		 */
		private int chunkSize = 1000;

		// --- TokenTextSplitter 专用 ---
		/**
		 * 最小分块字符数 默认值：400
		 */
		private int minChunkSizeChars = 400;

		/**
		 * 嵌入最小分块长度 默认值：10
		 */
		private int minChunkLengthToEmbed = 10;

		/**
		 * 最大分块数量 默认值：5000
		 */
		private int maxNumChunks = 5000;

		/**
		 * 是否保留分隔符 默认值：true
		 */
		private boolean keepSeparator = true;

		// --- RecursiveCharacterTextSplitter 专用 ---
		// 如果为 null，该类内部有默认的分隔符列表
		private String[] separators = null;

	}

	@Getter
	@Setter
	public static class EmbeddingBatch {

		/**
		 * encodingType 默认值：cl100k_base，适用于OpenAI等模型
		 */
		private String encodingType = "cl100k_base";

		/**
		 * 每批次最大令牌数 值越小，每批次文档越少，但更安全 值越大，处理效率越高，但可能超出API限制 建议值：2000-8000，根据实际API限制调整
		 */
		private int maxTokenCount = 8000;

		/**
		 * 预留百分比 用于预留缓冲空间，避免超出限制 建议值：0.1-0.2（10%-20%）
		 */
		private double reservePercentage = 0.2;

		/**
		 * 每批次最大文本数量 适用于DashScope等有文本数量限制的API DashScope限制为10
		 */
		private int maxTextCount = 10;

	}

	@Getter
	@Setter
	public static class VectorStoreProperties {

		// 专门给召回Table 用的配置
		private int tableTopkLimit = 10;

		// 设置低尽可能保证表不会召回漏掉
		private double tableSimilarityThreshold = 0.2;

		// 全局默认配置（给 BusinessTerm, AgentKnowledge 等使用）
		/**
		 * 相似度阈值配置，用于过滤相似度分数大于等于此阈值的文档
		 */
		private double defaultSimilarityThreshold = 0.4;

		/**
		 * 查询时返回的最大文档数量
		 */
		private int defaultTopkLimit = 8;

		/**
		 * 一次删除操作中，最多删除的文档数量
		 */
		private int batchDelTopkLimit = 5000;

		/**
		 * 是否启用混合搜索
		 */
		private boolean enableHybridSearch = false;

		/**
		 * Elasticsearch最小分数阈值，用于es执行关键词搜索时过滤相关性较低的文档
		 */
		private double elasticsearchMinScore = 0.5;

	}

}
