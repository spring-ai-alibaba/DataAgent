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
package com.alibaba.cloud.ai.dataagent.service.knowledge;

import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class AgentKnowledgeResourceCleanerTask {

	private final AgentKnowledgeMapper mapper;

	private final AgentKnowledgeResourceManager resourceManager;

	/**
	 * 每隔 1 小时执行一次兜底清理 cron = "0 0 * * * ?" (整点执行)
	 */
	@Scheduled(cron = "0 0 * * * ?")
	public void cleanupZombieResources() {
		log.info("Starting zombie resources cleanup task...");

		// 1. 定义时间缓冲：只处理 30 分钟前删除的数据
		// 这样不会跟用户刚刚操作的异步任务冲突
		LocalDateTime timeBuffer = LocalDateTime.now().minusMinutes(30);
		int batchSize = 100;

		// 2. 查询脏数据
		List<AgentKnowledge> dirtyRecords = mapper.selectDirtyRecords(timeBuffer, batchSize);

		if (dirtyRecords.isEmpty()) {
			log.info("No zombie resources found. Task finished.");
			return;
		}

		log.info("Found {} zombie records to clean.", dirtyRecords.size());

		// 3. 逐条清理
		for (AgentKnowledge knowledge : dirtyRecords) {
			try {
				cleanupSingleRecord(knowledge);
			}
			catch (Exception e) {
				// 单条失败不影响其他记录，只记录日志，等下个周期再试
				log.error("Failed to clean resources for ID: {}", knowledge.getId(), e);
			}
		}
	}

	private void cleanupSingleRecord(AgentKnowledge knowledge) {
		Integer id = knowledge.getId();

		// A. 删除向量 (复用 ResourceManager 的逻辑)
		boolean vectorDeleted = resourceManager.deleteFromVectorStore(knowledge.getAgentId(), id);

		// B. 删除文件
		boolean fileDeleted = resourceManager.deleteKnowledgeFile(knowledge);

		// C. 如果都清理干净了，更新数据库状态
		if (vectorDeleted && fileDeleted) {
			knowledge.setIsResourceCleaned(1);
			knowledge.setUpdatedTime(LocalDateTime.now());
			mapper.update(knowledge);
			log.info("Zombie resource cleaned: ID={}", id);
		}
		else {
			log.warn("Partial cleanup for ID={}. VectorDel={}, FileDel={}", id, vectorDeleted, fileDeleted);
		}
	}

}
