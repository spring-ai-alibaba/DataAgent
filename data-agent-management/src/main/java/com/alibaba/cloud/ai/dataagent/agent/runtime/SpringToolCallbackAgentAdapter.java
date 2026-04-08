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
package com.alibaba.cloud.ai.dataagent.agent.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@RequiredArgsConstructor
public class SpringToolCallbackAgentAdapter implements AgentTool {

	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final ToolCallback toolCallback;

	private final ObjectMapper objectMapper;

	@Override
	public String getName() {
		return toolCallback.getToolDefinition().name();
	}

	@Override
	public String getDescription() {
		return toolCallback.getToolDefinition().description();
	}

	@Override
	public Map<String, Object> getParameters() {
		try {
			return objectMapper.readValue(toolCallback.getToolDefinition().inputSchema(), MAP_TYPE);
		}
		catch (Exception ex) {
			log.warn("Failed to parse Spring AI tool schema, fallback to empty object. tool={}", getName(), ex);
			return Map.of("type", "object", "properties", Map.of());
		}
	}

	@Override
	public Mono<ToolResultBlock> callAsync(ToolCallParam toolCallParam) {
		return Mono.fromCallable(() -> invoke(toolCallParam)).subscribeOn(Schedulers.boundedElastic());
	}

	private ToolResultBlock invoke(ToolCallParam toolCallParam) throws Exception {
		String payload = objectMapper.writeValueAsString(toolCallParam.getInput());
		try {
			String result = toolCallback.call(payload);
			return ToolResultBlock.of(toolCallParam.getToolUseBlock().getId(), getName(),
					TextBlock.builder().text(result == null ? "" : result).build());
		}
		catch (Exception ex) {
			log.error("Spring AI tool callback execution failed. tool={}", getName(), ex);
			return ToolResultBlock.error(ex.getMessage() == null ? "Tool execution failed." : ex.getMessage())
				.withIdAndName(toolCallParam.getToolUseBlock().getId(), getName());
		}
	}

}
