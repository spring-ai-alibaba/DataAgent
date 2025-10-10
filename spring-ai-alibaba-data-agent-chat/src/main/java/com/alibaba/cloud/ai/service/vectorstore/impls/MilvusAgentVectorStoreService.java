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

package com.alibaba.cloud.ai.service.vectorstore.impls;

import com.alibaba.cloud.ai.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.service.vectorstore.AbstractAgentVectorStoreService;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;

public class MilvusAgentVectorStoreService extends AbstractAgentVectorStoreService {

	private final MilvusVectorStore milvusVectorStore;

	private final EmbeddingModel embeddingModel;

	public MilvusAgentVectorStoreService(AccessorFactory accessorFactory, MilvusVectorStore milvusVectorStore,
			EmbeddingModel embeddingModel, BatchingStrategy batchingStrategy) {
		super(batchingStrategy, accessorFactory);
		this.milvusVectorStore = milvusVectorStore;
		this.embeddingModel = embeddingModel;
	}

	@Override
	protected EmbeddingModel getEmbeddingModel() {
		return this.embeddingModel;
	}

	@Override
	protected VectorStore getVectorStore() {
		return this.milvusVectorStore;
	}

}
