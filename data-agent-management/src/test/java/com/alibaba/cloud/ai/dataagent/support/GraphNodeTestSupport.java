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
package com.alibaba.cloud.ai.dataagent.support;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

public final class GraphNodeTestSupport {

	private GraphNodeTestSupport() {
	}

	@SuppressWarnings("unchecked")
	public static NodeExecution execute(Map<String, Object> nodeResult, String outputKey) {
		assertThat(nodeResult).containsKey(outputKey);
		assertThat(nodeResult.get(outputKey)).isInstanceOf(Flux.class);

		Flux<GraphResponse<StreamingOutput>> generator = (Flux<GraphResponse<StreamingOutput>>) nodeResult
			.get(outputKey);
		List<GraphResponse<StreamingOutput>> responses = generator.collectList().block(Duration.ofSeconds(2));
		assertThat(responses).isNotNull();

		String streamedText = responses.stream()
			.filter(response -> !response.isDone() && !response.isError())
			.map(response -> response.getOutput().join().chunk())
			.collect(Collectors.joining());
		Map<String, Object> finalResult = responses.stream()
			.filter(GraphResponse::isDone)
			.findFirst()
			.flatMap(GraphResponse::resultValue)
			.map(value -> (Map<String, Object>) value)
			.orElseThrow(() -> new AssertionError("Node did not emit a final result for " + outputKey));
		return new NodeExecution(streamedText, finalResult);
	}

	public record NodeExecution(String streamedText, Map<String, Object> finalResult) {
	}

}
