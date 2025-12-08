package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.service.AgentService;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
@Configuration
// @Service
public class McpConfig {

	// 1. 定义入参结构
	// // 这里的字段名 status 和 keyword 会被 AI 自动识别
	public record AgentListRequest(@JsonPropertyDescription("按状态过滤，例如 'PUBLISHED'(已发布) 或 'DRAFT'(草稿)") String status,

			@JsonPropertyDescription("按关键词搜索智能体名称或描述") String keyword) {
	}

	@Bean
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
	}

}