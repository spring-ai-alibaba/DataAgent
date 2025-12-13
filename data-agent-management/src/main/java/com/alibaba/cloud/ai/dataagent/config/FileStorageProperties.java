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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.common.constant.Constant;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageServiceEnum;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 文件存储相关配置属性。
 */
@Getter
@Setter
@EnableConfigurationProperties({ OssStorageProperties.class })
@ConfigurationProperties(prefix = Constant.PROJECT_PROPERTIES_PREFIX + ".file")
public class FileStorageProperties {

	/**
	 * 存储类型：local（本地存储）、oss（阿里云OSS）
	 */
	private FileStorageServiceEnum type = FileStorageServiceEnum.LOCAL;

	/**
	 * 对象存储路径前缀（通用配置，对OSS和本地存储都适用）
	 */
	private String pathPrefix = "";

	/**
	 * 本地上传目录路径。
	 */
	private String path = "./uploads";

	/**
	 * 对外暴露的访问前缀。
	 */
	private String urlPrefix = "/uploads";

	/**
	 * 头像图片大小上限（字节）。默认 2MB。
	 */
	private long imageSize = 2L * 1024 * 1024;

}
