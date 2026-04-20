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
import com.alibaba.cloud.ai.dataagent.exception.InternalServerException;
import com.alibaba.cloud.ai.dataagent.mapper.FileStorageMapper;
import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageProvider;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageProviderEnum;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.vo.FileStorageVo;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

	private final Map<String, FileStorageProvider> fileStorageProviders;

	private final FileStorageProperties fileStorageProperties;

	private final FileStorageMapper fileStorageMapper;

	private final ApplicationEventPublisher eventPublisher;

	public Mono<FileStorageVo> storeFile(FilePart file, String subPath) {

		// 1. 前置校验（轻量同步操作，可立即执行）
		if (file == null || file.headers().getContentLength() == 0 || !StringUtils.hasText(file.filename())) {
			log.warn("文件为空，无法上传");
			return Mono.error(new IllegalArgumentException("文件为空，无法上传")); // ✅ 响应式返回错误
		}

		// 2. 提取文件元数据
		String originalFilename = file.filename();
		String extension = originalFilename.contains(".")
				? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
		String filename = UUID.randomUUID() + extension;
		String filePath = buildFilePath(subPath, filename);

		MediaType contentType = file.headers().getContentType();
		String contentTypeStr = contentType != null ? contentType.toString() : "application/octet-stream";

		// 3. 构建 FileStorage 实体
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

		// 4. 核心响应式链：文件存储 → 数据库插入 → 结果转换
		return getStorageProvider(storage.getStorageType()).storeFile(file, storage)
			// 5. 文件存储成功后，执行数据库插入（阻塞 JDBC，需切换线程）
			.flatMap(storedStorage -> Mono.fromCallable(() -> {
				// ✅ 阻塞的 JDBC 操作包裹在 fromCallable 中
				storedStorage.setCreatedTime(LocalDateTime.now());
				storedStorage.setUpdatedTime(LocalDateTime.now());
				fileStorageMapper.insert(storedStorage);
				return storedStorage;
			}).subscribeOn(Schedulers.boundedElastic()))
			// 6. 数据库插入成功后，转换为 VO 返回给前端
			.map(storedStorage -> FileStorageVo.builder()
				.id(storedStorage.getId())
				.filePath(storedStorage.getFilePath())
				.url(getFileUrl(storedStorage))
				.filename(storedStorage.getFilename())
				.build())
			// 7. 可观测性：日志埋点
			.doOnSubscribe(sub -> log.debug("开始处理文件上传: {}, subPath: {}", originalFilename, subPath))
			.doOnSuccess(vo -> log.info("文件上传并入库成功: fileId={}, url={}", vo.getId(), vo.getUrl()))
			.doOnError(e -> log.error("文件上传流程失败: filename={}", originalFilename, e))
			// 8. 响应式错误处理：统一转换异常类型
			.onErrorMap(IllegalArgumentException.class, e -> e) // 参数错误直接抛出
			.onErrorMap(DataAccessException.class, e -> new InternalServerException("数据库操作失败: " + e.getMessage(), e))
			// 9. 可选：添加超时保护
			.timeout(Duration.ofSeconds(60));
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
