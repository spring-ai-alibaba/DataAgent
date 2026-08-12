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
package com.alibaba.cloud.ai.dataagent.service.integration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.vector.MetadataDocumentRetriever;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreServiceImpl;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.DynamicFilterService;
import com.alibaba.cloud.ai.dataagent.support.KeywordEmbeddingModel;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.collection.DropCollectionParam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "dataagent.milvus.integration", matches = "true")
class MilvusVectorStoreIT {

	private MilvusServiceClient client;

	private MilvusVectorStore vectorStore;

	private String collectionName;

	@BeforeEach
	void setUp() throws Exception {
		String host = System.getProperty("dataagent.milvus.host", "127.0.0.1");
		int port = Integer.getInteger("dataagent.milvus.port", 19530);
		collectionName = "dataagent_it_" + UUID.randomUUID().toString().replace("-", "");
		client = new MilvusServiceClient(ConnectParam.newBuilder().withHost(host).withPort(port).build());
		vectorStore = MilvusVectorStore.builder(client, new KeywordEmbeddingModel())
			.collectionName(collectionName)
			.embeddingDimension(3)
			.initializeSchema(true)
			.build();
		vectorStore.afterPropertiesSet();
	}

	@AfterEach
	void tearDown() {
		if (client != null && collectionName != null) {
			client.dropCollection(DropCollectionParam.newBuilder().withCollectionName(collectionName).build());
			client.close();
		}
	}

	@Test
	void addSearchFilterAndDeleteUseTheRealMilvusServer() {
		Document order = new Document("订单销售数据", Map.of(Constant.AGENT_ID, "42", DocumentMetadataConstant.VECTOR_TYPE,
				DocumentMetadataConstant.AGENT_KNOWLEDGE));
		Document user = new Document("用户注册信息", Map.of(Constant.AGENT_ID, "99", DocumentMetadataConstant.VECTOR_TYPE,
				DocumentMetadataConstant.AGENT_KNOWLEDGE));
		vectorStore.add(List.of(order, user));

		FilterExpressionBuilder filters = new FilterExpressionBuilder();
		SearchRequest request = SearchRequest.builder()
			.query("查询订单")
			.topK(5)
			.similarityThreshold(0.8)
			.filterExpression(filters.eq(Constant.AGENT_ID, "42").build())
			.build();

		assertThat(vectorStore.similaritySearch(request)).extracting(Document::getText).containsExactly("订单销售数据");
		AgentVectorStoreService service = new AgentVectorStoreServiceImpl(vectorStore, Optional.empty(),
				new DataAgentProperties(), new DynamicFilterService(null, null),
				new MetadataDocumentRetriever(new StandardEnvironment()));
		assertThat(service.getDocumentsOnlyByFilter(filters.eq(Constant.AGENT_ID, "42").build(), 5))
			.extracting(Document::getText)
			.containsExactly("订单销售数据");

		vectorStore.delete("agentId == '42'");
		assertThat(vectorStore.similaritySearch(request)).isEmpty();
	}

}
