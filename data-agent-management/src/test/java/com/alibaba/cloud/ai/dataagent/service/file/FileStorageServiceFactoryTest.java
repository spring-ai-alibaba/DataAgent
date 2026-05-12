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
package com.alibaba.cloud.ai.dataagent.service.file;

import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.properties.OssStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.file.impls.LocalFileStorageServiceImpl;
import com.alibaba.cloud.ai.dataagent.service.file.impls.OssFileStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceFactoryTest {

	@Mock
	private FileStorageProperties properties;

	@Mock
	private OssStorageProperties ossProperties;

	private FileStorageServiceFactory factory;

	@BeforeEach
	void setUp() {
		factory = new FileStorageServiceFactory(properties, ossProperties);
	}

	@Test
	void testGetObject_oss() {
		when(properties.getType()).thenReturn(FileStorageServiceEnum.OSS);

		FileStorageService result = factory.getObject();
		assertInstanceOf(OssFileStorageServiceImpl.class, result);
	}

	@Test
	void testGetObject_local() {
		when(properties.getType()).thenReturn(FileStorageServiceEnum.LOCAL);

		FileStorageService result = factory.getObject();
		assertInstanceOf(LocalFileStorageServiceImpl.class, result);
	}

	@Test
	void testGetObject_null_defaultsToLocal() {
		when(properties.getType()).thenReturn(null);

		FileStorageService result = factory.getObject();
		assertInstanceOf(LocalFileStorageServiceImpl.class, result);
	}

	@Test
	void testGetObjectType() {
		assertEquals(FileStorageService.class, factory.getObjectType());
	}

}
