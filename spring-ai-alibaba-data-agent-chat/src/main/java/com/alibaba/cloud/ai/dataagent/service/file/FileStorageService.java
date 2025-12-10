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

package com.alibaba.cloud.ai.dataagent.service.file;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

	/**
	 * 存储文件
	 * @param file 上传的文件
	 * @param subPath 子路径（如 "avatars"）
	 * @return 存储后的文件路径
	 */
	String storeFile(MultipartFile file, String subPath);

	/**
	 * 删除文件
	 * @param filePath 文件路径
	 * @return 是否删除成功
	 */
	boolean deleteFile(String filePath);

	/**
	 * 获取文件访问URL
	 * @param filePath 文件路径
	 * @return 访问URL
	 */
	String getFileUrl(String filePath);

	/**
	 * 获取文件资源对象
	 * @param filePath 文件路径
	 * @return 文件资源对象
	 */
	Resource getFileResource(String filePath);

}
