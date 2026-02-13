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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.vo.UploadResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

/**
 * 文件上传控制器
 *
 * @author Makoto
 * @since 2025/9/19
 */
@Slf4j
@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class FileUploadController {

	private final FileStorageProperties fileStorageProperties;

	private final FileStorageService fileStorageService;

	/**
	 * 上传头像图片
	 */
	@PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Mono<ResponseEntity<UploadResponse>> uploadAvatar(@RequestPart("file") FilePart file) {
		// 验证文件类型
		String contentType = file.headers().getContentType() != null ? file.headers().getContentType().toString()
				: null;
		if (contentType == null || !contentType.startsWith("image/")) {
			return Mono.just(ResponseEntity.badRequest().body(UploadResponse.error("只支持图片文件")));
		}

		if (file.headers().getContentLength() > fileStorageProperties.getImageSize()) {
			return Mono.just(ResponseEntity.badRequest().body(UploadResponse.error("图片大小超过最大限制")));
		}

		// 使用文件存储服务存储文件
		return fileStorageService.storeFile(file, "avatars").map(filePath -> {
			String fileUrl = fileStorageService.getFileUrl(filePath);
			// 提取文件名
			String filename = filePath.substring(filePath.lastIndexOf("/") + 1);
			return ResponseEntity.ok(UploadResponse.ok("上传成功", fileUrl, filename));
		}).onErrorResume(e -> {
			log.error("头像上传失败", e);
			return Mono
				.just(ResponseEntity.internalServerError().body(UploadResponse.error("上传失败: " + e.getMessage())));
		});
	}

	/**
	 * 获取文件
	 */
	@GetMapping("/**")
	public ResponseEntity<byte[]> getFile(ServerHttpRequest request) {
		try {
			String requestMapPath = this.getClass().getAnnotation(RequestMapping.class).value()[0];
			String requestPath = request.getPath().value();
			String urlPrefix = fileStorageProperties.getUrlPrefix();
			String filePath = requestPath.substring(requestMapPath.length() + urlPrefix.length());

			Path fullPath = Paths.get(fileStorageProperties.getPath(), filePath);

			if (!Files.exists(fullPath) || Files.isDirectory(fullPath)) {
				return ResponseEntity.notFound().build();
			}

			byte[] fileContent = Files.readAllBytes(fullPath);
			String contentType = Files.probeContentType(fullPath);

			return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"))
				.body(fileContent);

		}
		catch (IOException e) {
			log.error("文件读取失败", e);
			return ResponseEntity.internalServerError().build();
		}
	}

}
