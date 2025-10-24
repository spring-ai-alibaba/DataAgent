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
package com.alibaba.cloud.ai.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.cloud.ai.config.FileUploadProperties;
import com.alibaba.cloud.ai.service.FileStorageService;
import com.alibaba.cloud.ai.vo.UploadResponse;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class FileUploadController {

	private final FileUploadProperties fileUploadProperties;

	@Autowired
	private FileStorageService fileStorageService;

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
			long maxImageSize = fileUploadProperties.getImageSize();
			if (file.getSize() > maxImageSize) {
				return ResponseEntity.badRequest().body(UploadResponse.error("图片大小超限，最大允许：" + maxImageSize + " 字节"));
			}

			// 使用文件存储服务上传到avatars文件夹
			String fileUrl = fileStorageService.uploadFile(file, "avatars");

			// 提取文件名
			String filename = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);

			log.info("头像上传成功: {} -> {}", file.getOriginalFilename(), fileUrl);
			return ResponseEntity.ok(UploadResponse.ok("上传成功", fileUrl, filename));

		}
		catch (Exception e) {
			log.error("头像上传失败", e);
			return ResponseEntity.internalServerError().body(UploadResponse.error("上传失败: " + e.getMessage()));
		}
	}

	/**
	 * 通用文件上传接口 支持其他文件上传：POST /api/upload/file?folder=documents
	 */
	@PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<UploadResponse> uploadFile(@RequestParam("file") MultipartFile file,
			@RequestParam(value = "folder", defaultValue = "files") String folder) {
		try {
			// 基本文件验证
			if (file.isEmpty()) {
				return ResponseEntity.badRequest().body(UploadResponse.error("文件不能为空"));
			}

			// 使用文件存储服务上传
			String fileUrl = fileStorageService.uploadFile(file, folder);

			// 提取文件名
			String filename = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);

			log.info("文件上传成功: {} -> {} (folder: {})", file.getOriginalFilename(), fileUrl, folder);
			return ResponseEntity.ok(UploadResponse.ok("上传成功", fileUrl, filename));

		}
		catch (Exception e) {
			log.error("文件上传失败", e);
			return ResponseEntity.internalServerError().body(UploadResponse.error("上传失败: " + e.getMessage()));
		}
	}

}
