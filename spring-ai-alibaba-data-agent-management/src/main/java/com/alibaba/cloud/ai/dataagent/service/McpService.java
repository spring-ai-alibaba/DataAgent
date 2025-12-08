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

package com.alibaba.cloud.ai.dataagent.service;

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class McpService {

	private final AgentMapper agentMapper;

	public record AgentListRequest(
			@JsonPropertyDescription("按状态过滤，例如 '状态：draft-待发布，published-已发布，offline-已下线") String status,

			@JsonPropertyDescription("按关键词搜索智能体名称或描述") String keyword) {
	}

	@Tool(description = "查询智能体列表，支持按状态和关键词过滤。可以根据智能体的状态（如已发布PUBLISHED、草稿DRAFT等）进行过滤，也可以通过关键词搜索智能体的名称、描述或标签。返回按创建时间降序排列的智能体列表。")
	public List<Agent> listAgentsToolCallback(AgentListRequest agentListRequest) {
		return agentMapper.findByConditions(agentListRequest.status(), agentListRequest.keyword());
	}

}
