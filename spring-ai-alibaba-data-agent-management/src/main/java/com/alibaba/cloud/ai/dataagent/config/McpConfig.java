/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.config;


import com.alibaba.cloud.ai.dataagent.mcp.McpServerTool;
import com.alibaba.cloud.ai.dataagent.service.AgentService;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import com.alibaba.cloud.ai.dataagent.service.McpService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// TODO 2025/12/08 合并包后移动到DataAgentConfiguration  中
@Configuration
public class McpConfig {

	@Bean
    @McpServerTool
	public ToolCallback listAgentsToolCallback(AgentService agentService) {
		return FunctionToolCallback.builder("listAgents", (AgentListRequest request) -> {
			// ---------------------------------------------------------
			// 关键点：只有当工具真正被调用时，才去容器里拿 AgentService
			// 这时候 Spring 容器早已启动完成，所有 Bean 都准备好了
			// ---------------------------------------------------------
			if (agentService == null) {
				throw new IllegalStateException("AgentService 未初始化");
			}

			// --- 业务逻辑 ---
			String keyword = request.keyword();
			String status = request.status();

			if (keyword != null && !keyword.trim().isEmpty()) {
				return agentService.search(keyword);
			}
			else if (status != null && !status.trim().isEmpty()) {
				return agentService.findByStatus(status);
			}
			else {
				return agentService.findAll();
			}
		})
			.description("查询智能体列表工具。可以根据状态(status)或关键词(keyword)筛选智能体。如果不传参数则返回所有。")
			.inputType(AgentListRequest.class)
			.build();

	public ToolCallbackProvider weatherTools(McpService mcpService) {
		return MethodToolCallbackProvider.builder().toolObjects(mcpService).build();
	}

}
