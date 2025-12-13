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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.config.FileStorageProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.vo.UploadResponse;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
	public ResponseEntity<UploadResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
		try {
			// 验证文件类型
			String contentType = file.getContentType();
			if (contentType == null || !contentType.startsWith("image/")) {
				return ResponseEntity.badRequest().body(UploadResponse.error("只支持图片文件"));
			}

			// 校验文件大小
			long maxImageSize = fileStorageProperties.getImageSize();
			if (file.getSize() > maxImageSize) {
				return ResponseEntity.badRequest().body(UploadResponse.error("图片大小超限，最大允许：" + maxImageSize + " 字节"));
			}

			// 使用文件存储服务存储文件
			String filePath = fileStorageService.storeFile(file, "avatars");
			String fileUrl = fileStorageService.getFileUrl(filePath);

			// 提取文件名
			String filename = filePath.substring(filePath.lastIndexOf("/") + 1);

			return ResponseEntity.ok(UploadResponse.ok("上传成功", fileUrl, filename));

		}
		catch (Exception e) {
			log.error("头像上传失败", e);
			return ResponseEntity.internalServerError().body(UploadResponse.error("上传失败: " + e.getMessage()));
		}
	}

	/**
	 * 获取文件
	 */
	@GetMapping("/**")
	public ResponseEntity<byte[]> getFile(HttpServletRequest request) {
		try {
			String requestPath = request.getRequestURI();
			String urlPrefix = fileStorageProperties.getUrlPrefix();
			String filePath = requestPath.substring(urlPrefix.length());

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
