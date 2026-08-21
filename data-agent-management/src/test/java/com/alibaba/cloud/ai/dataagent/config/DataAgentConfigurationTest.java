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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.service.file.FileStorageServiceFactory;
import com.alibaba.cloud.ai.dataagent.service.langfuse.LangfuseService;
import com.alibaba.cloud.ai.dataagent.service.langfuse.NodeTracingLifecycleListener;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.alibaba.cloud.ai.graph.store.stores.MemoryStore;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.HUMAN_FEEDBACK_NODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataAgentConfigurationTest {

	@Test
	void fileStorageServiceFactory_isPlainSelectorInsteadOfSpringFactoryBean() {
		assertThat(FactoryBean.class.isAssignableFrom(FileStorageServiceFactory.class)).isFalse();
	}

	@Test
	void graphMemoryStore_usesFrameworkMemoryStoreAsRebuildableProjection() {
		DataAgentConfiguration configuration = new DataAgentConfiguration();
		List<String> namespace = List.of("data-agent", "conversation-summary", "conversation-1");

		Store store = configuration.graphMemoryStore();
		Store summaryStore = configuration.conversationSummaryStore();
		store.putItem(StoreItem.of(namespace, "rolling-summary", Map.of("summaryText", "verified summary")));

		assertThat(store).isInstanceOf(MemoryStore.class);
		assertThat(summaryStore).isInstanceOf(MemoryStore.class).isNotSameAs(store);
		assertThat(store.getItem(namespace, "rolling-summary")).isPresent()
			.get()
			.satisfies(item -> assertThat(item.getValue()).containsEntry("summaryText", "verified summary"));
	}

	@Test
	void nl2sqlGraphCompileConfig_usesProvidedFrameworkSaver() throws Exception {
		DataAgentConfiguration configuration = new DataAgentConfiguration();
		BaseCheckpointSaver configuredSaver = configuration.memoryCheckpointSaver();
		Store configuredStore = new MemoryStore();
		CompileConfig compileConfig = configuration.nl2sqlGraphCompileConfig(configuredSaver, configuredStore,
				new NodeTracingLifecycleListener(mock(Tracer.class), mock(LangfuseService.class)));
		BaseCheckpointSaver checkpointSaver = compileConfig.checkpointSaver().orElseThrow();
		RunnableConfig runnableConfig = RunnableConfig.builder().threadId("chat-session-1").build();
		Checkpoint checkpoint = Checkpoint.builder()
			.id(UUID.randomUUID().toString())
			.nodeId("planner")
			.nextNodeId(HUMAN_FEEDBACK_NODE)
			.state(Map.of("question", "analyse orders"))
			.build();

		checkpointSaver.put(runnableConfig, checkpoint);

		assertThat(checkpointSaver).isInstanceOf(MemorySaver.class);
		assertThat(compileConfig.getStore()).isSameAs(configuredStore);
		assertThat(compileConfig.interruptsBefore()).contains(HUMAN_FEEDBACK_NODE);
		assertThat(checkpointSaver.get(runnableConfig)).isPresent();
		checkpointSaver.release(runnableConfig);
		assertThat(checkpointSaver.get(runnableConfig)).isEmpty();
	}

	@Test
	void mysqlCheckpointSaver_usesFrameworkSaverWithNodeLocalCacheDisabled() throws ReflectiveOperationException {
		DataAgentConfiguration configuration = new DataAgentConfiguration();
		String databaseName = "checkpoint-" + UUID.randomUUID();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				"jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
		StateGraph graph = mock(StateGraph.class);
		when(graph.getStateSerializer()).thenReturn(StateGraph.DEFAULT_JACKSON_SERIALIZER);

		BaseCheckpointSaver saver = configuration.mysqlCheckpointSaver(graph, dataSource);
		var maxCachedThreads = MysqlSaver.class.getDeclaredField("maxCachedThreads");
		maxCachedThreads.setAccessible(true);

		assertThat(saver).isInstanceOf(MysqlSaver.class);
		assertThat(maxCachedThreads.getInt(saver)).isZero();
	}

}
