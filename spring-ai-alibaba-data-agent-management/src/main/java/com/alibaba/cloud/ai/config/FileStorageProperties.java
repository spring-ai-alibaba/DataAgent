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
package com.alibaba.cloud.ai.config;

import com.alibaba.cloud.ai.constant.Constant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储相关配置属性。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = Constant.PROJECT_PROPERTIES_PREFIX + ".file.storage")
public class FileStorageProperties {

	/**
	 * 存储类型：local（本地存储）、oss（阿里云OSS）
	 */
	private String type = "local";

	/**
	 * 对象存储路径前缀（通用配置，对OSS和本地存储都适用）
	 */
	private String pathPrefix = "";

}
