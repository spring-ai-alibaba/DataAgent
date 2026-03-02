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
package com.alibaba.cloud.ai.dataagent.service.file.impls;

import com.alibaba.cloud.ai.dataagent.entity.FileStorage;
import com.alibaba.cloud.ai.dataagent.exception.InternalServerException;
import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@AllArgsConstructor
public class LocalFileStorageProviderImpl implements FileStorageProvider {

	private final FileStorageProperties fileStorageProperties;

	@Override
	public Mono<FileStorage> storeFile(FilePart file, FileStorage fileStorage) {
		return Mono.fromCallable(() -> {
			// 1. 执行所有同步/阻塞的 IO 操作
			Path storagePath = fileStorageProperties.getLocalBasePath().resolve(fileStorage.getFilePath());

			checkPathSecurity(storagePath);

			Path uploadDir = storagePath.getParent();
			if (!Files.exists(uploadDir)) {
				Files.createDirectories(uploadDir);
			}
			return storagePath; // 返回计算结果给下一步
		})
			// 2. 关键：切换到 boundedElastic 线程池，避免阻塞 I/O 线程
			.subscribeOn(Schedulers.boundedElastic())
			// 3. 执行响应式文件传输（file.transferTo 返回 Mono<Void>）
			.flatMap(storagePath -> file.transferTo(storagePath).thenReturn(fileStorage))
			// 4. 成功日志（可选）
			.doOnSuccess(stored -> log.info("文件存储成功: {}", stored))
			// 5. 响应式错误处理
			.doOnError(e -> log.error("文件存储失败", e))
			.onErrorMap(IOException.class, e -> new InternalServerException("文件存储失败: " + e.getMessage(), e));
	}

	@Override
	public boolean deleteFile(String filePath) {
		try {
			Path fullPath = fileStorageProperties.getLocalBasePath().resolve(filePath);
			checkPathSecurity(fullPath);
			if (Files.exists(fullPath)) {
				Files.deleteIfExists(fullPath);
				log.info("成功删除文件: {}", filePath);
			}
			else {
				// 删除是个等幂的操作，不存在也是当做被删除了
				log.info("文件不存在，跳过删除，视为成功: {}", filePath);
			}
			return true;
		}
		catch (IOException e) {
			log.error("删除文件失败: {}", filePath, e);
			return false;
		}
	}

	@Override
	public String getFileUrl(String filePath) {
		checkPathSecurity(fileStorageProperties.getLocalBasePath().resolve(filePath));
		// 返回相对路径，前端会自动基于当前域名访问
		return fileStorageProperties.getUrlPrefix() + "/" + filePath;
	}

	@Override
	public Resource getFileResource(String filePath) {
		Path fullPath = fileStorageProperties.getLocalBasePath().resolve(filePath);
		checkPathSecurity(fullPath);
		if (Files.exists(fullPath)) {
			return new FileSystemResource(fullPath);
		}
		else {
			throw new RuntimeException("File is not exist: " + filePath);
		}
	}

	/**
	 * 检查文件访问路径是否安全
	 * @param filePath 文件访问路径
	 */
	private void checkPathSecurity(Path filePath) {
		if (!filePath.normalize().startsWith(fileStorageProperties.getLocalBasePath())) {
			throw new SecurityException("Invalid file path");
		}
	}

}
