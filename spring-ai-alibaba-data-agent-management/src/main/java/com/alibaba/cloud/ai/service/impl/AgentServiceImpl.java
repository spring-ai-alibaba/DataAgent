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
package com.alibaba.cloud.ai.service.impl;

import com.alibaba.cloud.ai.entity.Agent;
import com.alibaba.cloud.ai.service.AgentService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.alibaba.cloud.ai.mapper.AgentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent Service Class
 */
@Slf4j
@Service
@AllArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;

    private final AgentVectorService agentVectorService;

    private final com.alibaba.cloud.ai.config.FileUploadProperties fileUploadProperties;

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
    @Transactional
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
    @Transactional
    public void deleteById(Long id) {
		try {
            // 获取头像信息用于文件清理
            Agent existing = agentMapper.findById(id);
            String avatar = existing != null ? existing.getAvatar() : null;

			// Delete agent record from database
			agentMapper.deleteById(id);

			// Also clean up the agent's vector data
			if (agentVectorService != null) {
				try {
					agentVectorService.deleteAllVectorDataForAgent(id);
					log.info("Successfully deleted vector data for agent: {}", id);
				}
				catch (Exception vectorException) {
					log.warn("Failed to delete vector data for agent: {}, error: {}", id, vectorException.getMessage());
					// Vector data deletion failure does not affect the main process
				}
			}

            // 清理头像文件（为本地上传）
            try {
                cleanupAvatarFile(avatar);
            }
            catch (Exception avatarEx) {
                log.warn("Failed to cleanup avatar file for agent: {}, error: {}", id, avatarEx.getMessage());
            }

			log.info("Successfully deleted agent: {}", id);
		}
		catch (Exception e) {
			log.error("Failed to delete agent: {}", id, e);
			throw e;
		}
	}

    private void cleanupAvatarFile(String avatar) throws Exception {
        if (avatar == null || avatar.isBlank()) {
            return;
        }
        String urlPrefix = fileUploadProperties.getUrlPrefix();
        String filename = null;
        String normalized = avatar;

        // 如果是完整URL，提取 path 部分
        try {
            java.net.URI uri = new java.net.URI(avatar);
            if (uri.getScheme() != null) {
                normalized = uri.getPath();
            }
        }
        catch (Exception ignore) {
            // 非URL，按路径处理
        }

        if (normalized != null) {
            if (normalized.startsWith(urlPrefix + "/avatars/")) {
                filename = normalized.substring((urlPrefix + "/avatars/").length());
            }
            else if (normalized.startsWith("/avatars/")) {
                filename = normalized.substring("/avatars/".length());
            }
        }

        if (filename == null || filename.isBlank()) {
            return;
        }

        Path root = Paths.get(fileUploadProperties.getPath()).toAbsolutePath();
        Path avatarPath = root.resolve("avatars").resolve(filename);
        if (Files.exists(avatarPath)) {
            try {
                Files.delete(avatarPath);
                log.info("Deleted avatar file: {}", avatarPath);
            }
            catch (Exception e) {
                // 将异常抛出到调用方，由调用方决定是否忽略
                throw e;
            }
        }
    }
}
