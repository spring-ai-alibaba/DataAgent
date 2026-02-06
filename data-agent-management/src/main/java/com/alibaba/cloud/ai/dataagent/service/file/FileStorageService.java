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
package com.alibaba.cloud.ai.dataagent.service.file;

import com.alibaba.cloud.ai.dataagent.entity.FileStorage;
import com.alibaba.cloud.ai.dataagent.vo.FileStorageVo;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface FileStorageService {

	/**
	 * 存储文件（响应式版本，用于 WebFlux Controller）
	 * @param filePart 上传的文件
	 * @param subPath 子路径（如 "avatars"）
	 * @return 存储后的文件路径
	 */
	Mono<FileStorageVo> storeFile(FilePart filePart, String subPath);

	/**
	 * 通过文件Id获取文件存储信息
	 * @param id 文件ID
	 * @return 文件存储信息
	 */
	FileStorage getFileById(Long id);

	/**
	 * 删除文件
	 * @param filePath 文件路径
	 * @return 是否删除成功
	 */
	boolean deleteFileResource(String filePath);

	/**
	 * 删除文件
	 * @param fileStorage 文件信息
	 * @return 是否删除成功
	 */
	boolean deleteFileResource(FileStorage fileStorage);

	/**
	 * 删除文件
	 * @param id 文件Id
	 * @return 是否删除成功
	 */
	boolean deleteFileById(Long id);

	/**
	 * 获取文件访问URL
	 * @param fileStorage 文件存储信息
	 * @return 访问URL
	 */
	String getFileUrl(FileStorage fileStorage);

	/**
	 * 获取文件资源对象
	 * @param fileStorage 文件路径
	 * @return 文件资源对象
	 */
	Resource getFileResource(FileStorage fileStorage);

}
