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

import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.properties.OssStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.file.impls.LocalFileStorageServiceImpl;
import com.alibaba.cloud.ai.dataagent.service.file.impls.OssFileStorageServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class FileStorageServiceFactory implements FactoryBean<FileStorageService> {

	private final FileStorageProperties properties;

	private final OssStorageProperties ossProperties;

	@Override
	public FileStorageService getObject() {
		if (FileStorageServiceEnum.OSS.equals(properties.getType())) {
			return new OssFileStorageServiceImpl(properties, ossProperties);
		}
		else {
			return new LocalFileStorageServiceImpl(properties);
		}
	}

	@Override
	public Class<?> getObjectType() {
		return FileStorageService.class;
	}

}
