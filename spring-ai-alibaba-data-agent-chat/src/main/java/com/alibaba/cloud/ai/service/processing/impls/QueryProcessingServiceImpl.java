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

package com.alibaba.cloud.ai.service.processing.impls;

import com.alibaba.cloud.ai.service.DatasourceService;
import com.alibaba.cloud.ai.service.LlmService;
import com.alibaba.cloud.ai.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.service.schema.SchemaService;
import com.alibaba.cloud.ai.service.processing.AbstractQueryProcessingService;
import com.alibaba.cloud.ai.service.vectorstore.AgentVectorStoreService;
import org.springframework.stereotype.Service;

@Service
public class QueryProcessingServiceImpl extends AbstractQueryProcessingService {

	private final AgentVectorStoreService vectorStoreService;

	private final SchemaService schemaService;

	private final Nl2SqlService nl2SqlService;

	public QueryProcessingServiceImpl(LlmService aiService, AgentVectorStoreService vectorStoreService,
			SchemaService schemaService, Nl2SqlService nl2SqlService, DatasourceService datasourceService) {
		super(aiService, datasourceService);
		this.vectorStoreService = vectorStoreService;
		this.schemaService = schemaService;
		this.nl2SqlService = nl2SqlService;
	}

	@Override
	protected AgentVectorStoreService getVectorStoreService() {
		return this.vectorStoreService;
	}

	@Override
	protected SchemaService getSchemaService() {
		return this.schemaService;
	}

	@Override
	protected Nl2SqlService getNl2SqlService() {
		return this.nl2SqlService;
	}

}
