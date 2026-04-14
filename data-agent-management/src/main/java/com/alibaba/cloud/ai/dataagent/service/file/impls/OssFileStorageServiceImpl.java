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

import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.properties.OssStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 阿里云OSS文件存储服务实现
 */
@Slf4j
public class OssFileStorageServiceImpl implements FileStorageService {

	private final FileStorageProperties fileStorageProperties;

	private final OssStorageProperties ossProperties;

	private OSS ossClient;

	public OssFileStorageServiceImpl(FileStorageProperties fileStorageProperties, OssStorageProperties ossProperties) {
		this.fileStorageProperties = fileStorageProperties;
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
	public Mono<String> storeFile(FilePart file, String subPath) {
		if (file == null || !StringUtils.hasText(file.filename())) {
			log.warn("文件为空，无法上传到OSS");
			return Mono.error(new IllegalArgumentException("文件为空，无法上传到OSS"));
		}

		String originalFilename = file.filename();
		String extension = "";
		if (originalFilename.contains(".")) {
			extension = originalFilename.substring(originalFilename.lastIndexOf("."));
		}
		String filename = UUID.randomUUID() + extension;
		String objectKey = buildObjectKey(subPath, filename);

		// 获取 Content-Type
		MediaType contentType = file.headers().getContentType();
		String contentTypeStr = contentType != null ? contentType.toString() : "application/octet-stream";

		// 使用 DataBufferUtils 收集文件内容，然后在 boundedElastic 线程池上执行 OSS 上传
		return DataBufferUtils.join(file.content()).flatMap(dataBuffer -> {
			byte[] bytes = new byte[dataBuffer.readableByteCount()];
			dataBuffer.read(bytes);
			DataBufferUtils.release(dataBuffer);

			return Mono.fromCallable(() -> {
				ObjectMetadata metadata = new ObjectMetadata();
				metadata.setContentLength(bytes.length);
				metadata.setContentType(contentTypeStr);
				metadata.setCacheControl("no-cache");

				try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
					ossClient.putObject(ossProperties.getBucketName(), objectKey, inputStream, metadata);
					log.info("文件上传成功: {}", objectKey);
					return objectKey;
				}
			}).subscribeOn(Schedulers.boundedElastic());
		}).onErrorMap(e -> {
			log.error("文件存储失败，上传OSS失败", e);
			return new RuntimeException("文件存储失败: " + e.getMessage(), e);
		});
	}

	@Override
	public String storeFile(MultipartFile file, String subPath) {
		try {
			if (file == null || file.isEmpty()) {
				log.warn("文件为空，无法上传到OSS");
				throw new IllegalArgumentException("文件为空，无法上传到OSS");
			}

			String originalFilename = file.getOriginalFilename();
			String extension = "";
			if (originalFilename != null && originalFilename.contains(".")) {
				extension = originalFilename.substring(originalFilename.lastIndexOf("."));
			}
			String filename = UUID.randomUUID() + extension;

			String objectKey = buildObjectKey(subPath, filename);

			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(file.getSize());
			metadata.setContentType(file.getContentType());
			metadata.setCacheControl("no-cache");

			try (InputStream inputStream = file.getInputStream()) {
				ossClient.putObject(ossProperties.getBucketName(), objectKey, inputStream, metadata);
				log.info("文件上传成功: {}", objectKey);
				return objectKey;
			}
		}
		catch (IOException e) {
			log.error("文件存储失败，获取输入流错误", e);
			throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
		}
		catch (Exception e) {
			log.error("文件存储失败，上传OSS失败", e);
			throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
		}
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

	/**
	 * 构建OSS对象键
	 */
	private String buildObjectKey(String subPath, String filename) {
		StringBuilder keyBuilder = new StringBuilder();

		if (StringUtils.hasText(fileStorageProperties.getPathPrefix())) {
			keyBuilder.append(fileStorageProperties.getPathPrefix()).append("/");
		}

		if (StringUtils.hasText(subPath)) {
			keyBuilder.append(subPath).append("/");
		}

		keyBuilder.append(filename);

		return keyBuilder.toString();
	}

}
