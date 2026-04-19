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
package com.alibaba.cloud.ai.dataagent.service.file.impls;

import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalFileStorageServiceImplTest {

	@TempDir
	Path tempDir;

	@Mock
	private MultipartFile multipartFile;

	private FileStorageProperties fileStorageProperties;

	private LocalFileStorageServiceImpl service;

	@BeforeEach
	void setUp() {
		fileStorageProperties = new FileStorageProperties();
		fileStorageProperties.setPath(tempDir.toAbsolutePath().toString());
		fileStorageProperties.setUrlPrefix("/uploads");
		fileStorageProperties.setPathPrefix("");
		service = new LocalFileStorageServiceImpl(fileStorageProperties);
	}

	@Test
	void storeFile_multipart_createsFileInCorrectLocation() throws IOException {
		when(multipartFile.getOriginalFilename()).thenReturn("test.txt");
		when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("content".getBytes()));

		String storagePath = service.storeFile(multipartFile, "docs");

		assertNotNull(storagePath);
		assertTrue(storagePath.contains("docs/"));
		assertTrue(storagePath.endsWith(".txt"));

		Path storedFile = tempDir.resolve(storagePath);
		assertTrue(Files.exists(storedFile));
		assertEquals("content", Files.readString(storedFile));
	}

	@Test
	void storeFile_multipart_noExtension_createsFileWithoutExtension() throws IOException {
		when(multipartFile.getOriginalFilename()).thenReturn("noext");
		when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));

		String storagePath = service.storeFile(multipartFile, "files");

		assertNotNull(storagePath);
		assertFalse(storagePath.contains("."));
		assertTrue(storagePath.contains("files/"));

		Path storedFile = tempDir.resolve(storagePath);
		assertTrue(Files.exists(storedFile));
	}

	@Test
	void storeFile_multipart_nullFilename_handledGracefully() throws IOException {
		when(multipartFile.getOriginalFilename()).thenReturn(null);
		when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));

		String storagePath = service.storeFile(multipartFile, "files");

		assertNotNull(storagePath);
		Path storedFile = tempDir.resolve(storagePath);
		assertTrue(Files.exists(storedFile));
	}

	@Test
	void storeFile_multipart_ioException_throwsRuntimeException() throws IOException {
		when(multipartFile.getOriginalFilename()).thenReturn("fail.txt");
		when(multipartFile.getInputStream()).thenThrow(new IOException("disk full"));

		assertThrows(RuntimeException.class, () -> service.storeFile(multipartFile, "docs"));
	}

	@Test
	void storeFile_withPathPrefix_includesPrefixInPath() throws IOException {
		fileStorageProperties.setPathPrefix("prefix");
		when(multipartFile.getOriginalFilename()).thenReturn("test.csv");
		when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("csv data".getBytes()));

		String storagePath = service.storeFile(multipartFile, "reports");

		assertTrue(storagePath.startsWith("prefix/"));
		assertTrue(storagePath.contains("reports/"));
		assertTrue(storagePath.endsWith(".csv"));
	}

	@Test
	void storeFile_nullSubPath_createsFileAtRoot() throws IOException {
		when(multipartFile.getOriginalFilename()).thenReturn("test.pdf");
		when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream("pdf data".getBytes()));

		String storagePath = service.storeFile(multipartFile, null);

		assertNotNull(storagePath);
		assertTrue(storagePath.endsWith(".pdf"));
		Path storedFile = tempDir.resolve(storagePath);
		assertTrue(Files.exists(storedFile));
	}

	@Test
	void deleteFile_existingFile_returnsTrue() throws IOException {
		Path testFile = tempDir.resolve("deleteme.txt");
		Files.writeString(testFile, "to delete");

		boolean result = service.deleteFile("deleteme.txt");

		assertTrue(result);
		assertFalse(Files.exists(testFile));
	}

	@Test
	void deleteFile_nonExistingFile_returnsTrue() {
		boolean result = service.deleteFile("nonexistent.txt");
		assertTrue(result);
	}

	@Test
	void getFileUrl_returnsUrlWithPrefix() {
		String url = service.getFileUrl("docs/file.txt");

		assertEquals("/uploads/docs/file.txt", url);
	}

	@Test
	void getFileUrl_customUrlPrefix_returnsCorrectUrl() {
		fileStorageProperties.setUrlPrefix("/api/files");

		String url = service.getFileUrl("report.pdf");

		assertEquals("/api/files/report.pdf", url);
	}

	@Test
	void getFileResource_existingFile_returnsAccessibleResource() throws IOException {
		Path testFile = tempDir.resolve("resource.txt");
		Files.writeString(testFile, "resource content");

		Resource resource = service.getFileResource("resource.txt");

		assertNotNull(resource);
		assertTrue(resource.exists());
		assertTrue(resource.isReadable());
	}

	@Test
	void getFileResource_nonExistingFile_throwsRuntimeException() {
		assertThrows(RuntimeException.class, () -> service.getFileResource("missing.txt"));
	}

	@Test
	void getFileResource_subdirectoryFile_returnsResource() throws IOException {
		Path subDir = tempDir.resolve("sub");
		Files.createDirectories(subDir);
		Path testFile = subDir.resolve("nested.txt");
		Files.writeString(testFile, "nested content");

		Resource resource = service.getFileResource("sub/nested.txt");

		assertNotNull(resource);
		assertTrue(resource.exists());
	}

	@Test
	void pathTraversal_throwsSecurityException() {
		assertThrows(SecurityException.class, () -> service.getFileUrl("../../etc/passwd"));
	}

	@Test
	void pathTraversal_delete_throwsSecurityException() {
		assertThrows(SecurityException.class, () -> service.deleteFile("../../../etc/passwd"));
	}

	@Test
	void pathTraversal_getResource_throwsSecurityException() {
		assertThrows(SecurityException.class, () -> service.getFileResource("../../etc/shadow"));
	}

}
