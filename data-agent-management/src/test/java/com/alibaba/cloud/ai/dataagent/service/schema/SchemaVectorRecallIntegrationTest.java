/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.schema;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreServiceImpl;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.MetadataAwareSimpleVectorStore;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.DynamicFilterService;
import com.alibaba.cloud.ai.dataagent.service.vector.MetadataDocumentRetriever;
import com.alibaba.cloud.ai.dataagent.support.KeywordEmbeddingModel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaVectorRecallIntegrationTest {

	private ExecutorService executorService;

	private AgentVectorStoreService vectorStoreService;

	private SchemaServiceImpl schemaService;

	@BeforeEach
	void setUp() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getVectorStore().setTableTopkLimit(1);
		properties.getVectorStore().setTableSimilarityThreshold(0.8);
		SimpleVectorStore vectorStore = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
		vectorStoreService = new AgentVectorStoreServiceImpl(vectorStore, Optional.empty(), properties,
				new DynamicFilterService(null, null), new MetadataDocumentRetriever(new StandardEnvironment()));
		executorService = Executors.newSingleThreadExecutor();
		schemaService = new SchemaServiceImpl(executorService, null, null, null, null, properties, vectorStoreService,
				null, null);

		vectorStoreService.addDocuments("7",
				List.of(tableDocument("orders", "订单销售数据"), tableDocument("users", "用户注册信息")));
	}

	@AfterEach
	void tearDown() {
		executorService.shutdownNow();
	}

	@Test
	void schemaRecallUsesTheUserQueryInsteadOfAConstantProbe() {
		List<Document> result = schemaService.getTableDocumentsByDatasource(7, "查询订单销售数据", "schema-v1");

		assertThat(result).extracting(document -> document.getMetadata().get(DocumentMetadataConstant.NAME))
			.containsExactly("orders");
	}

	@Test
	void initializationCheckUsesDatasourceAndSelectedTableScope() {
		assertThat(vectorStoreService.hasTableDocuments(7, List.of("orders", "users"))).isTrue();
		assertThat(vectorStoreService.hasTableDocuments(7, List.of("orders", "missing"))).isFalse();
	}

	private Document tableDocument(String name, String text) {
		return new Document(text,
				Map.of(Constant.DATASOURCE_ID, "7", DocumentMetadataConstant.VECTOR_TYPE,
						DocumentMetadataConstant.TABLE, DocumentMetadataConstant.NAME, name,
						DocumentMetadataConstant.SCHEMA_REVISION, "schema-v1"));
	}

}
