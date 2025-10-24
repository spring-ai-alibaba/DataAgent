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
package com.alibaba.cloud.ai.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileStorageService {

	/**
	 * 上传文件
	 * @param file 文件
	 * @param folder 文件夹路径 (如: "avatars", "documents")
	 * @return 文件访问URL
	 */
	String uploadFile(MultipartFile file, String folder);

	/**
	 * 上传文件流
	 * @param inputStream 文件输入流
	 * @param filename 文件名
	 * @param folder 文件夹路径
	 * @param contentType 文件类型
	 * @return 文件访问URL
	 */
	String uploadFile(InputStream inputStream, String filename, String folder, String contentType);

	/**
	 * 删除文件
	 * @param fileUrl 文件URL
	 * @return 是否删除成功
	 */
	boolean deleteFile(String fileUrl);

	/**
	 * 获取文件访问URL
	 * @param filePath 文件路径
	 * @return 完整的访问URL
	 */
	String getFileUrl(String filePath);

	/**
	 * 检查文件是否存在
	 * @param fileUrl 文件URL
	 * @return 是否存在
	 */
	boolean fileExists(String fileUrl);

	/**
	 * 获取存储类型
	 * @return 存储类型 (local, oss, s3等)
	 */
	String getStorageType();

}
