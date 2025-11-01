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

package com.alibaba.cloud.ai.config;

import com.alibaba.cloud.ai.constant.Constant;
import com.alibaba.cloud.ai.service.llm.LlmServiceEnum;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = Constant.PROJECT_PROPERTIES_PREFIX)
public class DataAgentProperties {

	private LlmServiceEnum llmServiceType = LlmServiceEnum.STREAM;

	/**
	 * spring.ai.alibaba.nl2sql.embedding-batch.encoding-type=cl100k_base
	 * spring.ai.alibaba.nl2sql.embedding-batch.max-token-count=2000
	 * spring.ai.alibaba.nl2sql.embedding-batch.reserve-percentage=0.2
	 * spring.ai.alibaba.nl2sql.embedding-batch.max-text-count=10
	 */
	private EmbeddingBatch embeddingBatch = new EmbeddingBatch();

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

}
