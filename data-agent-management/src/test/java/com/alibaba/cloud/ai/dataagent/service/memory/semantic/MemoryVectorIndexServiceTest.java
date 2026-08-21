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
package com.alibaba.cloud.ai.dataagent.service.memory.semantic;

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.MemoryItem;
import com.alibaba.cloud.ai.dataagent.enums.MemoryScopeType;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryVectorIndexServiceTest {

	@Mock
	private VectorStore vectorStore;

	private DataAgentProperties properties;

	private MemoryVectorIndexService service;

	@BeforeEach
	void setUp() {
		properties = new DataAgentProperties();
		properties.getMemory().setVectorIndexEnabled(true);
		service = new MemoryVectorIndexService(vectorStore, properties);
	}

	@Test
	void longTermSearchFiltersAgentAndDatasourceScopesBeforeTopK() {
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		service.recallMemoryItemIds("sales", null, 7, 3, 5);

		ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
		verify(vectorStore).similaritySearch(captor.capture());
		String filter = captor.getValue().getFilterExpression().toString();
		assertThat(filter).contains("agentId", "7", "memoryScopeType", "AGENT", "DATASOURCE", "datasourceId", "3");
	}

	@Test
	void userScopedMemoryIsNotSharedUntilTrustedOwnerScopeIsEnabled() {
		service.indexMemoryItem(MemoryItem.builder()
			.id(11L)
			.scopeType(MemoryScopeType.USER_AGENT)
			.ownerId(99L)
			.agentId(7)
			.memoryKey("currency")
			.valueJson("\"CNY\"")
			.build());

		verifyNoInteractions(vectorStore);
	}

	@Test
	void trustedUserScopeIsIndexedAndOwnerFilteredByFrameworkVectorStore() {
		properties.getMemory().setUserScopeEnabled(true);
		MemoryItem item = MemoryItem.builder()
			.id(11L)
			.scopeType(MemoryScopeType.USER_AGENT)
			.ownerId(99L)
			.agentId(7)
			.memoryKey("currency")
			.valueJson("\"CNY\"")
			.build();
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		service.indexMemoryItem(item);
		service.recallMemoryItemIds("currency", 99L, 7, null, 5);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Document>> documents = ArgumentCaptor.forClass(List.class);
		verify(vectorStore).add(documents.capture());
		assertThat(documents.getValue()).singleElement()
			.satisfies(document -> assertThat(document.getMetadata())
				.containsEntry(DocumentMetadataConstant.MEMORY_OWNER_ID, "99"));
		ArgumentCaptor<SearchRequest> search = ArgumentCaptor.forClass(SearchRequest.class);
		verify(vectorStore).similaritySearch(search.capture());
		assertThat(search.getValue().getFilterExpression().toString()).contains("memoryScopeType", "USER_AGENT",
				"memoryOwnerId", "99");
	}

	@Test
	void disablingIndexingStillDeletesDocumentsWrittenBeforeTheConfigurationChange() {
		properties.getMemory().setVectorIndexEnabled(false);

		service.deleteTurn("turn-1");
		service.deleteMemoryItem(11L);

		verify(vectorStore).delete(List.of("memory-turn-turn-1"));
		verify(vectorStore).delete(List.of("memory-item-11"));
	}

	@Test
	void malformedVectorMetadataCannotBreakRelationalFallback() {
		Document malformed = new Document("bad", "memory",
				java.util.Map.of(DocumentMetadataConstant.MEMORY_ITEM_ID, "not-a-number"));
		Document valid = new Document("good", "memory",
				java.util.Map.of(DocumentMetadataConstant.MEMORY_ITEM_ID, "12"));
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(malformed, valid));

		assertThat(service.recallMemoryItemIds("currency", null, 7, null, 5)).containsExactly(12L);
	}

}
