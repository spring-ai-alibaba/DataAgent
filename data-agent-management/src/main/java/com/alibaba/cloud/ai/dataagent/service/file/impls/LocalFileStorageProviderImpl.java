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

@Slf4j
@AllArgsConstructor
public class LocalFileStorageProviderImpl implements FileStorageProvider {

	private final FileStorageProperties fileStorageProperties;

	@Override
	public void storeFile(FilePart file, FileStorage fileStorage) {
		try {

			Path storagePath = fileStorageProperties.getLocalBasePath().resolve(fileStorage.getFilePath());

			checkPathSecurity(storagePath);

			Path uploadDir = storagePath.getParent();
			if (!Files.exists(uploadDir)) {
				Files.createDirectories(uploadDir);
			}
			file.transferTo(storagePath).block();

			log.info("文件存储成功: {}", fileStorage);

		}
		catch (IOException e) {
			log.error("文件存储失败", e);
			throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
		}
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
