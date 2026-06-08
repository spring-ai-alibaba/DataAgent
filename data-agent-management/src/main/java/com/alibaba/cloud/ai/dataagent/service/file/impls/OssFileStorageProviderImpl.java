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
import com.alibaba.cloud.ai.dataagent.properties.OssStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageProvider;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 阿里云OSS文件存储服务实现
 */
@Slf4j
public class OssFileStorageProviderImpl implements FileStorageProvider {

	private final OssStorageProperties ossProperties;

	private OSS ossClient;

	public OssFileStorageProviderImpl(OssStorageProperties ossProperties) {
		this.ossProperties = ossProperties;
	}

	@PostConstruct
	public void init() {
		this.ossClient = new OSSClientBuilder().build(ossProperties.getEndpoint(), ossProperties.getAccessKeyId(),
				ossProperties.getAccessKeySecret());
		log.info("OSS客户端初始化完成，endpoint: {}, bucket: {}", ossProperties.getEndpoint(), ossProperties.getBucketName());
	}

	@PreDestroy
	public void destroy() {
		if (ossClient != null) {
			ossClient.shutdown();
			log.info("OSS客户端已关闭");
		}
	}

	@Override
	public Mono<FileStorage> storeFile(FilePart file, FileStorage fileStorage) {

		// 1. 准备 OSS 元数据（纯内存操作，可立即执行）
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(fileStorage.getFileSize());
		metadata.setContentType(fileStorage.getFileType());
		metadata.setCacheControl("no-cache");

		// 2. 定义临时文件路径
		Path tempFile = Path.of("/tmp", fileStorage.getFilePath());

		return Mono.defer(() -> {
			// 确保每次订阅时重新执行（避免临时文件路径冲突）

			// 3. 第一步：将上传文件保存到本地临时文件（响应式 API）
			return file.transferTo(tempFile)

				// 4. 第二步：上传到 OSS（阻塞操作，需切换线程）
				.then(Mono.fromCallable(() -> {
					// 确保父目录存在
					Files.createDirectories(tempFile.getParent());

					// 阻塞 IO：读取本地文件 + 上传 OSS
					try (InputStream is = Files.newInputStream(tempFile)) {
						ossClient.putObject(ossProperties.getBucketName(), fileStorage.getFilePath(), is, metadata);
						log.info("文件上传 OSS 成功: {}", fileStorage);
						return fileStorage; // 返回业务对象
					}
				}).subscribeOn(Schedulers.boundedElastic()))

				.publishOn(Schedulers.boundedElastic())

				// 5. 第三步：无论成功失败，清理临时文件
				.doFinally(signal -> {
					try {
						Files.deleteIfExists(tempFile);
						log.debug("临时文件已清理: {}", tempFile);
					}
					catch (IOException e) {
						log.warn("清理临时文件失败: {}", tempFile, e);
						// 注意：doFinally 中抛异常会影响主流程，建议只记录日志
					}
				})

				// 6. 响应式错误处理：转换异常类型
				.onErrorMap(IOException.class, e -> new InternalServerException("文件处理失败: " + e.getMessage(), e))
				// 7. 可选：添加超时保护，防止大文件卡死
				.timeout(Duration.ofSeconds(60));
		})
			// 8. 日志埋点（可观测性）
			.doOnSubscribe(sub -> log.debug("开始处理文件上传: {}", fileStorage.getFilename()))
			.doOnSuccess(stored -> log.info("文件上传流程完成: {}", stored))
			.doOnError(e -> log.error("文件上传流程异常: {}", fileStorage.getFilename(), e));
	}

	@Override
	public boolean deleteFile(String filePath) {
		if (!StringUtils.hasText(filePath)) {
			log.info("删除文件失败，路径为空");
			return false;
		}
		try {
			if (ossClient.doesObjectExist(ossProperties.getBucketName(), filePath)) {
				ossClient.deleteObject(ossProperties.getBucketName(), filePath);
				log.info("成功从OSS删除文件: {}", filePath);
			}
			else {
				// 删除是个等幂的操作，不存在也是当做被删除了
				log.info("OSS中文件不存在，跳过删除，视为成功: {}", filePath);
			}
			return true;
		}
		catch (Exception e) {
			log.error("从OSS删除文件失败: {}", filePath, e);
			return false;
		}
	}

	@Override
	public String getFileUrl(String filePath) {
		try {
			if (StringUtils.hasText(ossProperties.getCustomDomain())) {
				return ossProperties.getCustomDomain() + "/" + filePath;
			}

			String bucketDomain = String.format("https://%s.%s", ossProperties.getBucketName(),
					ossProperties.getEndpoint().replace("https://", "").replace("http://", ""));
			return bucketDomain + "/" + filePath;

		}
		catch (Exception e) {
			log.error("生成OSS文件URL失败: {}", filePath, e);
			return filePath;
		}
	}

	/**
	 * This implementation throws IllegalStateException if attempting to read the
	 * underlying stream multiple times.
	 */
	@Override
	public Resource getFileResource(String filePath) {

		if (!StringUtils.hasText(filePath)) {
			log.info("获取文件失败，路径为空");
			return null;
		}

		OSSObject result = ossClient.getObject(ossProperties.getBucketName(), filePath);
		// todo: 临时处理,只能读取一次,不能重复读
		return new InputStreamResource(result.getObjectContent()) {
			@Override
			public long contentLength() {
				return result.getObjectMetadata().getContentLength();
			}
		};
	}

}
