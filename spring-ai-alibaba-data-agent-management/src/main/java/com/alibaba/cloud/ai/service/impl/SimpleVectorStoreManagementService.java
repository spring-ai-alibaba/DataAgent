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
package com.alibaba.cloud.ai.service.impl;

import com.alibaba.cloud.ai.connector.accessor.Accessor;
import com.alibaba.cloud.ai.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.service.AbstractVectorStoreManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SimpleVectorStoreManagementService extends AbstractVectorStoreManagementService {

	private final SimpleVectorStore vectorStore;

	private final Accessor dbAccessor;

	private final DbConfig dbConfig;

	@Autowired
	public SimpleVectorStoreManagementService(@Value("${spring.ai.dashscope.api-key:default_api_key}") String apiKey,
			AccessorFactory accessorFactory, DbConfig dbConfig) {
		this.dbAccessor = accessorFactory.getAccessorByDbConfig(dbConfig);
		this.dbConfig = dbConfig;

		DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(apiKey).build();
		DashScopeEmbeddingModel dashScopeEmbeddingModel = new DashScopeEmbeddingModel(dashScopeApi, MetadataMode.EMBED,
				DashScopeEmbeddingOptions.builder()
					.withModel(DashScopeApi.EmbeddingModel.EMBEDDING_V4.getValue())
					.build());
		this.vectorStore = SimpleVectorStore.builder(dashScopeEmbeddingModel).build();
	}

	@Override
	protected VectorStore getVectorStore() {
		return vectorStore;
	}

}
