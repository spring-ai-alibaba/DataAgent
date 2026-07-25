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

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.SimpleVectorStoreInitialization;
import com.alibaba.cloud.ai.dataagent.support.KeywordEmbeddingModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleVectorStorePersistenceIntegrationTest {

	@TempDir
	Path tempDirectory;

	@Test
	void saveAndLoadRoundTripUsesACompleteReplacementFile() throws Exception {
		Path storeFile = tempDirectory.resolve("nested/vectorstore.json");
		DataAgentProperties properties = new DataAgentProperties();
		properties.getVectorStore().setFilePath(storeFile.toString());

		SimpleVectorStore original = SimpleVectorStore.builder(new KeywordEmbeddingModel()).build();
		original.add(List.of(new Document("订单销售数据")));
		new SimpleVectorStoreInitialization(original, properties).save();

		assertThat(storeFile).exists();
		try (Stream<Path> paths = Files.list(storeFile.getParent())) {
			assertThat(paths.map(path -> path.getFileName().toString())).noneMatch(name -> name.endsWith(".tmp"));
		}

		SimpleVectorStore restored = SimpleVectorStore.builder(new KeywordEmbeddingModel()).build();
		new SimpleVectorStoreInitialization(restored, properties).load();

		assertThat(
				restored.similaritySearch(SearchRequest.builder().query("订单").topK(1).similarityThreshold(0.8).build()))
			.extracting(Document::getText)
			.containsExactly("订单销售数据");
	}

}
