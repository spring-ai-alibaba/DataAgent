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
package com.alibaba.cloud.ai.dataagent.agentscope.runtime;

import com.alibaba.cloud.ai.dataagent.agentscope.dto.GraphRequest;
import io.agentscope.core.tool.ToolExecutionContext;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

public final class ToolContextRequestResolver {

	private ToolContextRequestResolver() {
	}

	@Nullable
	public static GraphRequest resolveGraphRequest(@Nullable ToolContext toolContext) {
		if (toolContext == null || toolContext.getContext() == null) {
			return null;
		}
		Map<String, Object> context = toolContext.getContext();
		Object graphRequest = context.get("graphRequest");
		if (graphRequest instanceof GraphRequest request) {
			return request;
		}
		Object agentScopeContext = context.get("agentScopeContext");
		if (agentScopeContext instanceof ToolExecutionContext toolExecutionContext) {
			GraphRequest request = toolExecutionContext.get("graphRequest", GraphRequest.class);
			if (request != null) {
				return request;
			}
			GraphRequest metadataRequest = fromMetadata(toolExecutionContext.get(AgentRuntimeRequestMetadata.class));
			if (metadataRequest != null) {
				return metadataRequest;
			}
		}
		Object runtimeRequestMetadata = context.get("runtimeRequestMetadata");
		if (runtimeRequestMetadata instanceof AgentRuntimeRequestMetadata metadata) {
			return fromMetadata(metadata);
		}
		return null;
	}

	@Nullable
	private static GraphRequest fromMetadata(@Nullable AgentRuntimeRequestMetadata metadata) {
		if (metadata == null || !StringUtils.hasText(metadata.threadId())
				|| !StringUtils.hasText(metadata.runtimeRequestId())) {
			return null;
		}
		return GraphRequest.builder()
			.agentId(metadata.agentId())
			.threadId(metadata.threadId())
			.runtimeRequestId(metadata.runtimeRequestId())
			.nl2sqlOnly(metadata.nl2sqlOnly())
			.build();
	}

}
