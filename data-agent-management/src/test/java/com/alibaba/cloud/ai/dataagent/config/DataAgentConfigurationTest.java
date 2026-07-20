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

import com.alibaba.cloud.ai.dataagent.service.code.CodePoolExecutorServiceFactory;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageServiceFactory;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmServiceFactory;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.FactoryBean;

import java.util.Map;
import java.util.UUID;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.HUMAN_FEEDBACK_NODE;
import static org.assertj.core.api.Assertions.assertThat;

class DataAgentConfigurationTest {

	@Test
	void runtimeServiceFactories_arePlainSelectorsInsteadOfSpringFactoryBeans() {
		assertThat(FactoryBean.class.isAssignableFrom(LlmServiceFactory.class)).isFalse();
		assertThat(FactoryBean.class.isAssignableFrom(FileStorageServiceFactory.class)).isFalse();
		assertThat(FactoryBean.class.isAssignableFrom(CodePoolExecutorServiceFactory.class)).isFalse();
	}

	@Test
	void nl2sqlGraphCompileConfig_usesProvidedFrameworkSaver() throws Exception {
		DataAgentConfiguration configuration = new DataAgentConfiguration();
		BaseCheckpointSaver configuredSaver = configuration.memoryCheckpointSaver();
		CompileConfig compileConfig = configuration.nl2sqlGraphCompileConfig(configuredSaver);
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
		assertThat(compileConfig.interruptsBefore()).contains(HUMAN_FEEDBACK_NODE);
		assertThat(checkpointSaver.get(runnableConfig)).isPresent();
		checkpointSaver.release(runnableConfig);
		assertThat(checkpointSaver.get(runnableConfig)).isEmpty();
	}

}
