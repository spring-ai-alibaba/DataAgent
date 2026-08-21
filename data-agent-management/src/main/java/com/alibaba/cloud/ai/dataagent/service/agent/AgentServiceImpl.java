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
package com.alibaba.cloud.ai.dataagent.service.agent;

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.alibaba.cloud.ai.dataagent.security.ApiKeyCredentialService;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.service.knowledge.AgentKnowledgeResourceManager;
import com.alibaba.cloud.ai.dataagent.service.memory.lifecycle.MemoryLifecycleService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.util.ApiKeyUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

/**
 * Agent Service Class
 */
@Slf4j
@Service
@AllArgsConstructor
public class AgentServiceImpl implements AgentService {

	private final AgentMapper agentMapper;

	private final AgentVectorStoreService agentVectorStoreService;

	private final FileStorageService fileStorageService;

	private final ApiKeyCredentialService apiKeyCredentialService;

	private final AgentKnowledgeMapper agentKnowledgeMapper;

	private final AgentKnowledgeResourceManager agentKnowledgeResourceManager;

	private final MemoryLifecycleService memoryLifecycleService;

	@Override
	public List<Agent> findAll() {
		return agentMapper.findAll();
	}

	@Override
	public Agent findById(Long id) {
		return agentMapper.findById(id);
	}

	@Override
	public List<Agent> findByStatus(String status) {
		return agentMapper.findByStatus(status);
	}

	@Override
	public List<Agent> search(String keyword) {
		return agentMapper.searchByKeyword(keyword);
	}

	@Override
	public Agent save(Agent agent) {
		LocalDateTime now = LocalDateTime.now();

		if (agent.getId() == null) {
			// Add
			agent.setCreateTime(now);
			agent.setUpdateTime(now);
			if (agent.getApiKeyEnabled() == null) {
				agent.setApiKeyEnabled(0);
			}

			agentMapper.insert(agent);
		}
		else {
			// Update
			agent.setUpdateTime(now);
			if (agent.getApiKeyEnabled() == null) {
				agent.setApiKeyEnabled(0);
			}
			agentMapper.updateById(agent);
		}

		return agent;
	}

	@Override
	@Transactional
	public void deleteById(Long id) {
		Agent existing = requireAgent(id);
		memoryLifecycleService.forgetAgent(id.intValue());
		List<AgentKnowledge> knowledgeRecords = agentKnowledgeMapper.selectByAgentIdIncludeDeleted(id.intValue());
		for (AgentKnowledge knowledge : knowledgeRecords) {
			if (!agentKnowledgeResourceManager.cleanupResources(knowledge)) {
				throw new IllegalStateException("Failed to clean resources for agent knowledge: " + knowledge.getId());
			}
		}

		if (!Boolean.TRUE.equals(agentVectorStoreService.deleteDocumentsByMetadata(id.toString(), new HashMap<>()))) {
			throw new IllegalStateException("Failed to clean vector resources for agent: " + id);
		}
		if (existing.getAvatar() != null && !existing.getAvatar().isBlank()
				&& !fileStorageService.deleteFile(existing.getAvatar())) {
			throw new IllegalStateException("Failed to delete avatar for agent: " + id);
		}

		agentKnowledgeMapper.deleteByAgentId(id.intValue());
		if (agentMapper.deleteById(id) <= 0) {
			throw new IllegalStateException("Failed to delete agent: " + id);
		}
		log.info("Successfully deleted agent and owned resources: {}", id);
	}

	@Override
	public Agent generateApiKey(Long id) {
		Agent agent = requireAgent(id);
		String apiKey = ApiKeyUtil.generate();
		agentMapper.updateApiKey(id, apiKeyCredentialService.encode(apiKey), 1);
		agent.setApiKey(apiKey);
		agent.setApiKeyEnabled(1);
		return agent;
	}

	@Override
	public Agent resetApiKey(Long id) {
		return generateApiKey(id);
	}

	@Override
	public Agent deleteApiKey(Long id) {
		Agent agent = requireAgent(id);
		agentMapper.updateApiKey(id, null, 0);
		agent.setApiKey(null);
		agent.setApiKeyEnabled(0);
		return agent;
	}

	@Override
	public Agent toggleApiKey(Long id, boolean enabled) {
		Agent agent = requireAgent(id);
		if (enabled && (agent.getApiKey() == null || agent.getApiKey().isBlank())) {
			throw new IllegalStateException("Generate an API key before enabling API key authentication");
		}
		agentMapper.toggleApiKey(id, enabled ? 1 : 0);
		agent.setApiKeyEnabled(enabled ? 1 : 0);
		return agent;
	}

	@Override
	public String getApiKeyMasked(Long id) {
		Agent agent = requireAgent(id);
		String apiKey = agent.getApiKey();
		if (apiKey == null || apiKey.isBlank()) {
			return null;
		}
		return apiKeyCredentialService.mask(apiKey);
	}

	private Agent requireAgent(Long id) {
		Agent agent = agentMapper.findById(id);
		if (agent == null) {
			throw new IllegalArgumentException("Agent not found: " + id);
		}
		return agent;
	}

}
