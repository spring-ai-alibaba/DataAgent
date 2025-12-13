/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.agent;

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
			// 确保 humanReviewEnabled 不为 null
			if (agent.getHumanReviewEnabled() == null) {
				agent.setHumanReviewEnabled(0);
			}

			agentMapper.insert(agent);
		}
		else {
			// Update
			agent.setUpdateTime(now);
			// 确保 humanReviewEnabled 不为 null
			if (agent.getHumanReviewEnabled() == null) {
				agent.setHumanReviewEnabled(0);
			}
			agentMapper.updateById(agent);
		}

		return agent;
	}

	@Override
	public void deleteById(Long id) {
		try {
			// 获取头像信息用于文件清理
			Agent existing = agentMapper.findById(id);
			String avatar = existing != null ? existing.getAvatar() : null;

			// Delete agent record from database
			agentMapper.deleteById(id);

			// Also clean up the agent's vector data
			if (agentVectorStoreService != null) {
				try {
					agentVectorStoreService.deleteDocumentsByMetedata(id.toString(), new HashMap<>());
					log.info("Successfully deleted vector data for agent: {}", id);
				}
				catch (Exception vectorException) {
					log.warn("Failed to delete vector data for agent: {}, error: {}", id, vectorException.getMessage());
					// Vector data deletion failure does not affect the main process
				}
			}

			// 清理头像文件
			try {
				if (avatar != null && !avatar.isBlank()) {
					fileStorageService.deleteFile(avatar);
					log.info("Successfully deleted avatar file: {} for agent: {}", avatar, id);
				}
			}
			catch (Exception avatarEx) {
				log.warn("Failed to cleanup avatar file: {} for agent: {}, error: {}", avatar, id,
						avatarEx.getMessage());
			}

			log.info("Successfully deleted agent: {}", id);
		}
		catch (Exception e) {
			log.error("Failed to delete agent: {}", id, e);
			throw e;
		}
	}

}
