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
package com.alibaba.cloud.ai.dataagent.event;

import com.alibaba.cloud.ai.dataagent.entity.FileStorage;
import com.alibaba.cloud.ai.dataagent.mapper.FileStorageMapper;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class FileEventListener {

	private final FileStorageMapper fileStorageMapper;

	private final FileStorageService fileStorageService;

	@Async("dbOperationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleDeletionEvent(FileDeletionEvent event) {
		Long id = event.getFileId();
		log.info("Starting async resource cleanup for fileId: {}", id);

		// 1. 重新查询
		FileStorage fileStorage = fileStorageMapper.findById(id);
		if (fileStorage == null) {
			log.warn("File record physically missing, skipping cleanup. ID: {}", id);
			return;
		}

		try {
			// 3. 删除文件
			boolean fileDeleted = fileStorageService.deleteFileResource(fileStorage.getFilePath());

			// 4. 更新清理状态
			if (fileDeleted) {
				// 只有都成功了，才标记为资源已清理
				fileStorage.setIsCleaned(1);
				fileStorage.setUpdatedTime(LocalDateTime.now());
				fileStorageMapper.update(fileStorage);
				log.info("Resources cleaned up successfully. FileId: {}", id);
			}
			else {
				log.error("Cleanup incomplete. FileId: {}, FileDeleted: {}", id, fileDeleted);
				// isResourceCleaned=0，有定时任务兜底清理。
			}

		}
		catch (Exception e) {
			log.error("Exception during async cleanup for agentKnowledgeId: {}", id, e);
		}
	}

}
