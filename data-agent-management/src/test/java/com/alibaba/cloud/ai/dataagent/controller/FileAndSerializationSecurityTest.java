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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAndSerializationSecurityTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@TempDir
	Path tempDir;

	@Test
	void agentSerializationShouldHideApiKey() throws Exception {
		Agent agent = Agent.builder().id(1L).name("demo").apiKey("sk-test-secret").apiKeyEnabled(1).build();

		String json = objectMapper.writeValueAsString(agent);

		assertFalse(json.contains("\"apiKey\":"));
		assertFalse(json.contains("sk-test-secret"));
		assertTrue(json.contains("apiKeyEnabled"));
	}

	@Test
	void datasourceSerializationShouldHideCredentials() throws Exception {
		Datasource datasource = Datasource.builder()
			.id(1)
			.name("mysql")
			.username("root")
			.password("secret")
			.connectionUrl("jdbc:mysql://127.0.0.1:3306/test")
			.build();

		String json = objectMapper.writeValueAsString(datasource);

		assertTrue(json.contains("username"));
		assertFalse(json.contains("\"password\":"));
		assertFalse(json.contains("\"connectionUrl\":"));
		assertFalse(json.contains("jdbc:mysql://127.0.0.1:3306/test"));
	}

	@Test
	void getFileShouldReturnFileWithinBasePath() throws Exception {
		Path file = tempDir.resolve("avatars").resolve("ok.txt");
		Files.createDirectories(file.getParent());
		byte[] expected = "safe-content".getBytes(StandardCharsets.UTF_8);
		Files.write(file, expected);

		FileUploadController controller = new FileUploadController(buildFileStorageProperties(),
				new NoopFileStorageService());
		MockServerHttpRequest request = MockServerHttpRequest.get("/api/upload/uploads/avatars/ok.txt").build();

		ResponseEntity<byte[]> response = controller.getFile(request);

		assertEquals(200, response.getStatusCode().value());
		assertArrayEquals(expected, response.getBody());
	}

	@Test
	void getFileShouldBlockPathTraversal() throws Exception {
		Path parentSecret = tempDir.getParent().resolve("secret.txt");
		Files.write(parentSecret, "top-secret".getBytes(StandardCharsets.UTF_8));

		FileUploadController controller = new FileUploadController(buildFileStorageProperties(),
				new NoopFileStorageService());
		MockServerHttpRequest request = MockServerHttpRequest.get("/api/upload/uploads/../secret.txt").build();

		ResponseEntity<byte[]> response = controller.getFile(request);

		assertEquals(403, response.getStatusCode().value());
	}

	private FileStorageProperties buildFileStorageProperties() {
		FileStorageProperties properties = new FileStorageProperties();
		properties.setPath(tempDir.toString());
		properties.setUrlPrefix("/uploads");
		return properties;
	}

	private static final class NoopFileStorageService implements FileStorageService {

		@Override
		public Mono<String> storeFile(FilePart filePart, String subPath) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String storeFile(MultipartFile file, String subPath) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean deleteFile(String filePath) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getFileUrl(String filePath) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Resource getFileResource(String filePath) {
			throw new UnsupportedOperationException();
		}

	}

}
