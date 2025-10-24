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

import com.alibaba.cloud.ai.config.FileUploadProperties;
import com.alibaba.cloud.ai.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.alibaba.file.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageServiceImpl implements FileStorageService {

	private final FileUploadProperties fileUploadProperties;

	public LocalFileStorageServiceImpl(FileUploadProperties fileUploadProperties) {
		this.fileUploadProperties = fileUploadProperties;
	}

	@Override
	public String uploadFile(MultipartFile file, String folder) {
		try {
			// 创建上传目录
			Path uploadDir = Paths.get(fileUploadProperties.getPath(), folder);
			if (!Files.exists(uploadDir)) {
				Files.createDirectories(uploadDir);
			}

			// 生成文件名
			String originalFilename = file.getOriginalFilename();
			String extension = "";
			if (originalFilename != null && originalFilename.contains(".")) {
				extension = originalFilename.substring(originalFilename.lastIndexOf("."));
			}
			String filename = UUID.randomUUID().toString() + extension;

			// 保存文件
			Path filePath = uploadDir.resolve(filename);
			Files.copy(file.getInputStream(), filePath);

			// 生成访问URL
			return ServletUriComponentsBuilder.fromCurrentContextPath()
				.path(fileUploadProperties.getUrlPrefix())
				.path("/" + folder + "/")
				.path(filename)
				.toUriString();

		}
		catch (IOException e) {
			log.error("本地文件上传失败", e);
			throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
		}
	}

	@Override
	public String uploadFile(InputStream inputStream, String filename, String folder, String contentType) {
		try {
			// 创建上传目录
			Path uploadDir = Paths.get(fileUploadProperties.getPath(), folder);
			if (!Files.exists(uploadDir)) {
				Files.createDirectories(uploadDir);
			}

			// 生成唯一文件名
			String extension = "";
			if (filename != null && filename.contains(".")) {
				extension = filename.substring(filename.lastIndexOf("."));
			}
			String uniqueFilename = UUID.randomUUID().toString() + extension;

			// 保存文件
			Path filePath = uploadDir.resolve(uniqueFilename);
			Files.copy(inputStream, filePath);

			// 生成访问URL
			return ServletUriComponentsBuilder.fromCurrentContextPath()
				.path(fileUploadProperties.getUrlPrefix())
				.path("/" + folder + "/")
				.path(uniqueFilename)
				.toUriString();

		}
		catch (IOException e) {
			log.error("本地文件流上传失败", e);
			throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
		}
	}

	@Override
	public boolean deleteFile(String fileUrl) {
		try {
			String filename = extractFilename(fileUrl);
			if (filename == null) {
				return false;
			}

			String folder = extractFolder(fileUrl);
			Path filePath = Paths.get(fileUploadProperties.getPath(), folder, filename);

			if (Files.exists(filePath)) {
				Files.delete(filePath);
				log.info("删除本地文件成功: {}", filePath);
				return true;
			}
			return false;
		}
		catch (Exception e) {
			log.error("删除本地文件失败: {}", fileUrl, e);
			return false;
		}
	}

	@Override
	public String getFileUrl(String filePath) {
		return ServletUriComponentsBuilder.fromCurrentContextPath()
			.path(fileUploadProperties.getUrlPrefix())
			.path("/" + filePath)
			.toUriString();
	}

	@Override
	public boolean fileExists(String fileUrl) {
		try {
			String filename = extractFilename(fileUrl);
			if (filename == null) {
				return false;
			}

			String folder = extractFolder(fileUrl);
			Path filePath = Paths.get(fileUploadProperties.getPath(), folder, filename);
			return Files.exists(filePath);
		}
		catch (Exception e) {
			log.error("检查本地文件存在性失败: {}", fileUrl, e);
			return false;
		}
	}

	@Override
	public String getStorageType() {
		return "local";
	}

	/**
	 * 从URL中提取文件名
	 */
	private String extractFilename(String fileUrl) {
		try {
			String normalized = fileUrl;

			// 如果是完整URL，提取path部分
			if (fileUrl.startsWith("http")) {
				URI uri = new URI(fileUrl);
				normalized = uri.getPath();
			}

			// 提取文件名
			if (normalized.contains("/")) {
				return normalized.substring(normalized.lastIndexOf("/") + 1);
			}
			return normalized;
		}
		catch (Exception e) {
			log.error("提取文件名失败: {}", fileUrl, e);
			return null;
		}
	}

	/**
	 * 从URL中提取文件夹路径
	 */
	private String extractFolder(String fileUrl) {
		try {
			String normalized = fileUrl;
			String urlPrefix = fileUploadProperties.getUrlPrefix();

			// 如果是完整URL，提取path部分
			if (fileUrl.startsWith("http")) {
				URI uri = new URI(fileUrl);
				normalized = uri.getPath();
			}

			// 移除URL前缀
			if (normalized.startsWith(urlPrefix + "/")) {
				normalized = normalized.substring((urlPrefix + "/").length());
			}

			// 提取文件夹部分
			if (normalized.contains("/")) {
				return normalized.substring(0, normalized.lastIndexOf("/"));
			}
			return "";
		}
		catch (Exception e) {
			log.error("提取文件夹路径失败: {}", fileUrl, e);
			return "";
		}
	}

}
