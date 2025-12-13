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
package com.alibaba.cloud.ai.dataagent.service.file.impls;

import com.alibaba.cloud.ai.dataagent.config.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@AllArgsConstructor
public class LocalFileStorageServiceImpl implements FileStorageService {

	private final FileStorageProperties fileStorageProperties;

	@Override
	public String storeFile(MultipartFile file, String subPath) {
		try {
			String originalFilename = file.getOriginalFilename();
			String extension = "";
			if (originalFilename != null && originalFilename.contains(".")) {
				extension = originalFilename.substring(originalFilename.lastIndexOf("."));
			}
			String filename = UUID.randomUUID().toString() + extension;

			String storagePath = buildStoragePath(subPath, filename);

			Path uploadDir = Paths.get(fileStorageProperties.getPath(), storagePath).getParent();
			if (uploadDir != null && !Files.exists(uploadDir)) {
				Files.createDirectories(uploadDir);
			}

			Path filePath = Paths.get(fileStorageProperties.getPath(), storagePath);
			Files.copy(file.getInputStream(), filePath);

			log.info("文件存储成功: {}", storagePath);
			return storagePath;

		}
		catch (IOException e) {
			log.error("文件存储失败", e);
			throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
		}
	}

	@Override
	public boolean deleteFile(String filePath) {
		try {
			Path fullPath = Paths.get(fileStorageProperties.getPath(), filePath);
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
		try {
			ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
				.getRequestAttributes();
			if (attributes != null) {
				HttpServletRequest request = attributes.getRequest();
				return ServletUriComponentsBuilder.fromRequestUri(request)
					.replacePath(fileStorageProperties.getUrlPrefix() + "/" + filePath)
					.build()
					.toUriString();
			}
		}
		catch (Exception e) {
			log.warn("动态构建URL失败，使用相对路径", e);
		}
		return fileStorageProperties.getUrlPrefix() + "/" + filePath;
	}

	@Override
	public Resource getFileResource(String filePath) {
		Path fullPath = Paths.get(fileStorageProperties.getPath(), filePath);
		if (Files.exists(fullPath)) {
			return new FileSystemResource(fullPath);
		}
		else {
			throw new RuntimeException("File is not exist: " + filePath);
		}
	}

	/**
	 * 构建本地存储路径
	 */
	private String buildStoragePath(String subPath, String filename) {
		StringBuilder pathBuilder = new StringBuilder();

		if (StringUtils.hasText(fileStorageProperties.getPathPrefix())) {
			pathBuilder.append(fileStorageProperties.getPathPrefix()).append("/");
		}

		if (StringUtils.hasText(subPath)) {
			pathBuilder.append(subPath).append("/");
		}

		pathBuilder.append(filename);

		return pathBuilder.toString();
	}

}
