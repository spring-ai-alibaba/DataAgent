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

import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.vo.UploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileUploadControllerTest {

	@Mock
	private FileStorageProperties fileStorageProperties;

	@Mock
	private FileStorageService fileStorageService;

	private FileUploadController controller;

	@BeforeEach
	void setUp() {
		controller = new FileUploadController(fileStorageProperties, fileStorageService);
	}

	@Test
	void uploadAvatar_nonImageFile_returnsBadRequest() {
		FilePart filePart = mock(FilePart.class);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		when(filePart.headers()).thenReturn(headers);

		Mono<ResponseEntity<UploadResponse>> result = controller.uploadAvatar(filePart);

		StepVerifier.create(result).assertNext(response -> {
			assertEquals(400, response.getStatusCode().value());
			assertFalse(response.getBody().isSuccess());
		}).verifyComplete();
	}

	@Test
	void uploadAvatar_nullContentType_returnsBadRequest() {
		FilePart filePart = mock(FilePart.class);
		HttpHeaders headers = new HttpHeaders();
		when(filePart.headers()).thenReturn(headers);

		Mono<ResponseEntity<UploadResponse>> result = controller.uploadAvatar(filePart);

		StepVerifier.create(result).assertNext(response -> {
			assertEquals(400, response.getStatusCode().value());
		}).verifyComplete();
	}

	@Test
	void uploadAvatar_exceedsMaxSize_returnsBadRequest() {
		FilePart filePart = mock(FilePart.class);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.IMAGE_PNG);
		headers.setContentLength(10 * 1024 * 1024);
		when(filePart.headers()).thenReturn(headers);
		when(fileStorageProperties.getImageSize()).thenReturn(2L * 1024 * 1024);

		Mono<ResponseEntity<UploadResponse>> result = controller.uploadAvatar(filePart);

		StepVerifier.create(result).assertNext(response -> {
			assertEquals(400, response.getStatusCode().value());
			assertFalse(response.getBody().isSuccess());
		}).verifyComplete();
	}

	@Test
	void uploadAvatar_validImage_returnsSuccess() {
		FilePart filePart = mock(FilePart.class);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.IMAGE_PNG);
		headers.setContentLength(1024);
		when(filePart.headers()).thenReturn(headers);
		when(fileStorageProperties.getImageSize()).thenReturn(2L * 1024 * 1024);
		when(fileStorageService.storeFile(filePart, "avatars")).thenReturn(Mono.just("avatars/test.png"));
		when(fileStorageService.getFileUrl("avatars/test.png")).thenReturn("/uploads/avatars/test.png");

		Mono<ResponseEntity<UploadResponse>> result = controller.uploadAvatar(filePart);

		StepVerifier.create(result).assertNext(response -> {
			assertEquals(200, response.getStatusCode().value());
			assertTrue(response.getBody().isSuccess());
			assertEquals("/uploads/avatars/test.png", response.getBody().getUrl());
			assertEquals("test.png", response.getBody().getFilename());
		}).verifyComplete();
	}

	@Test
	void getFile_nonExistentFile_returnsNotFound(@TempDir Path tempDir) {
		when(fileStorageProperties.getPath()).thenReturn(tempDir.toString());
		when(fileStorageProperties.getUrlPrefix()).thenReturn("/uploads");

		org.springframework.http.server.reactive.ServerHttpRequest request = mock(
				org.springframework.http.server.reactive.ServerHttpRequest.class);
		org.springframework.http.server.RequestPath requestPath = mock(
				org.springframework.http.server.RequestPath.class);
		when(request.getPath()).thenReturn(requestPath);
		when(requestPath.value()).thenReturn("/api/upload/uploads/avatars/missing.png");

		ResponseEntity<byte[]> result = controller.getFile(request);

		assertEquals(404, result.getStatusCode().value());
	}

	@Test
	void getFile_existingFile_returnsFileContent(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("test.txt");
		Files.writeString(file, "hello");
		when(fileStorageProperties.getPath()).thenReturn(tempDir.toString());
		when(fileStorageProperties.getUrlPrefix()).thenReturn("/uploads");

		org.springframework.http.server.reactive.ServerHttpRequest request = mock(
				org.springframework.http.server.reactive.ServerHttpRequest.class);
		org.springframework.http.server.RequestPath requestPath = mock(
				org.springframework.http.server.RequestPath.class);
		when(request.getPath()).thenReturn(requestPath);
		when(requestPath.value()).thenReturn("/api/upload/uploads/test.txt");

		ResponseEntity<byte[]> result = controller.getFile(request);

		assertEquals(200, result.getStatusCode().value());
		assertEquals("hello", new String(result.getBody()));
	}

}
