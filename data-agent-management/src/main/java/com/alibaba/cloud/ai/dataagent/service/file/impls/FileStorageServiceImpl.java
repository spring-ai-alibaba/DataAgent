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
import com.alibaba.cloud.ai.dataagent.event.FileDeletionEvent;
import com.alibaba.cloud.ai.dataagent.mapper.FileStorageMapper;
import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageProvider;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageProviderEnum;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.vo.FileStorageVo;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

	private final Map<String, FileStorageProvider> fileStorageProviders;

	private final FileStorageProperties fileStorageProperties;

	private final FileStorageMapper fileStorageMapper;

	private final ApplicationEventPublisher eventPublisher;

	public Mono<FileStorageVo> storeFile(FilePart file, String subPath) {

		if (file == null || file.headers().getContentLength() == 0 || !StringUtils.hasText(file.filename())) {
			log.warn("文件为空，无法上传");
			throw new IllegalArgumentException("文件为空，无法上传");
		}

		String originalFilename = file.filename();
		String extension = "";
		if (originalFilename.contains(".")) {
			extension = originalFilename.substring(originalFilename.lastIndexOf("."));
		}
		String filename = UUID.randomUUID() + extension;

		String filePath = buildFilePath(subPath, filename);

		// 获取 Content-Type
		MediaType contentType = file.headers().getContentType();
		String contentTypeStr = contentType != null ? contentType.toString() : "application/octet-stream";

		FileStorage storage = FileStorage.builder()
			.filename(originalFilename)
			.filePath(filePath)
			.fileSize(file.headers().getContentLength())
			.fileType(contentTypeStr)
			.fileExtension(extension)
			.storageType(fileStorageProperties.getType())
			.isDeleted(0)
			.isCleaned(0)
			.build();

		getStorageProvider(fileStorageProperties.getType()).storeFile(file, storage);

		storage.setCreatedTime(LocalDateTime.now());
		storage.setUpdatedTime(LocalDateTime.now());

		fileStorageMapper.insert(storage);

		return Mono.just(FileStorageVo.builder()
			.id(storage.getId())
			.filePath(storage.getFilePath())
			.url(getFileUrl(storage))
			.filename(storage.getFilename())
			.build());
	}

	private FileStorageProvider getStorageProvider(FileStorageProviderEnum storageProviderEnum) {
		if (storageProviderEnum == null) {
			storageProviderEnum = fileStorageProperties.getType();
		}
		if (!fileStorageProviders.containsKey(storageProviderEnum.name().toLowerCase(Locale.ROOT))) {
			throw new IllegalArgumentException("Invalid storage provider: " + storageProviderEnum);
		}
		return fileStorageProviders.get(storageProviderEnum.name().toLowerCase(Locale.ROOT));
	}

	@Override
	public FileStorage getFileById(Long id) {
		return fileStorageMapper.findById(id);
	}

	public boolean deleteFileResource(String filePath) {
		return getStorageProvider(fileStorageProperties.getType()).deleteFile(filePath);
	}

	public boolean deleteFileResource(FileStorage fileStorage) {
		// 1. 删除文件
		boolean fileDeleted = getStorageProvider(fileStorage.getStorageType()).deleteFile(fileStorage.getFilePath());

		// 2. 更新清理状态
		if (fileDeleted) {
			fileStorage.setIsCleaned(1);
			fileStorage.setUpdatedTime(LocalDateTime.now());
			fileStorageMapper.update(fileStorage);
		}
		return fileDeleted;
	}

	public boolean deleteFileById(Long id) {
		FileStorage fileStorage = fileStorageMapper.findById(id);
		return deleteFile(fileStorage);
	}

	private boolean deleteFile(FileStorage fileStorage) {
		fileStorage.setIsDeleted(1);
		fileStorage.setUpdatedTime(LocalDateTime.now());
		fileStorageMapper.update(fileStorage);
		eventPublisher.publishEvent(new FileDeletionEvent(this, fileStorage.getId()));
		return true;
	}

	@Override
	public String getFileUrl(FileStorage fileStorage) {
		return getStorageProvider(fileStorage.getStorageType()).getFileUrl(fileStorage.getFilePath());
	}

	@Override
	public Resource getFileResource(FileStorage fileStorage) {
		return getStorageProvider(fileStorage.getStorageType()).getFileResource(fileStorage.getFilePath());
	}

	/**
	 * 构建存储路径
	 */
	private String buildFilePath(String subPath, String filename) {
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
