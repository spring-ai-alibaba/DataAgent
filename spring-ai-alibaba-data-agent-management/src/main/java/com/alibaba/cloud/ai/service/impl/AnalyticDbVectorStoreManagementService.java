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

import com.alibaba.cloud.ai.annotation.ConditionalOnADBEnabled;
import com.alibaba.cloud.ai.connector.accessor.Accessor;
import com.alibaba.cloud.ai.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.service.AbstractVectorStoreManagementService;
import com.alibaba.cloud.ai.vectorstore.analyticdb.AnalyticDbVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Core vector database operation service, providing vector writing, querying, deletion,
 * schema initialization and other functions.
 */

@Service
@ConditionalOnADBEnabled
public class AnalyticDbVectorStoreManagementService extends AbstractVectorStoreManagementService {

	@Autowired
	private AnalyticDbVectorStore vectorStore;

	private final Accessor dbAccessor;

	public AnalyticDbVectorStoreManagementService(AccessorFactory accessorFactory, DbConfig dbConfig) {
		this.dbAccessor = accessorFactory.getAccessorByDbConfig(dbConfig);
	}

	@Override
	protected VectorStore getVectorStore() {
		return vectorStore;
	}

}
