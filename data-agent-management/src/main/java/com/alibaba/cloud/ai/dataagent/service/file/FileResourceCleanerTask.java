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
package com.alibaba.cloud.ai.dataagent.service.file;

import com.alibaba.cloud.ai.dataagent.entity.FileStorage;
import com.alibaba.cloud.ai.dataagent.mapper.FileStorageMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class FileResourceCleanerTask {

	private final FileStorageMapper mapper;

	private final FileStorageService fileStorageService;

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
		List<FileStorage> dirtyRecords = mapper.selectDirtyRecords(timeBuffer, batchSize);

		if (dirtyRecords.isEmpty()) {
			log.info("No zombie resources found. Task finished.");
			return;
		}

		log.info("Found {} zombie records to clean.", dirtyRecords.size());

		// 3. 逐条清理
		for (FileStorage fileStorage : dirtyRecords) {
			try {
				cleanupSingleRecord(fileStorage);
			}
			catch (Exception e) {
				// 单条失败不影响其他记录，只记录日志，等下个周期再试
				log.error("Failed to clean resources for ID: {}", fileStorage.getId(), e);
			}
		}
	}

	private void cleanupSingleRecord(FileStorage fileStorage) {
		
		Long id = fileStorage.getId();
		boolean fileDeleted = fileStorageService.deleteFileResource(fileStorage);

		if (fileDeleted) {
			log.info("Zombie resource cleaned: ID={}", id);
		}
		else {
			log.warn("Partial cleanup for ID={}, FileDel={}", id, fileDeleted);
		}
	}

}
