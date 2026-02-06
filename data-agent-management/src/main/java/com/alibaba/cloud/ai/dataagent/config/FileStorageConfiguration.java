/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.properties.OssStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageProvider;
import com.alibaba.cloud.ai.dataagent.service.file.impls.LocalFileStorageProviderImpl;
import com.alibaba.cloud.ai.dataagent.service.file.impls.OssFileStorageProviderImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties({ FileStorageProperties.class, OssStorageProperties.class })
@RequiredArgsConstructor
public class FileStorageConfiguration {

	private final FileStorageProperties fileStorageProperties;

	private final OssStorageProperties ossStorageProperties;

	@Bean("local")
	public FileStorageProvider localFileStorageProvider() {
		return new LocalFileStorageProviderImpl(fileStorageProperties);
	}

	@Bean("oss")
	@ConditionalOnProperty(name = Constant.PROJECT_PROPERTIES_PREFIX + ".file.oss.enabled", havingValue = "true")
	public FileStorageProvider ossFileStorageProvider() {
		return new OssFileStorageProviderImpl(fileStorageProperties, ossStorageProperties);
	}

}
