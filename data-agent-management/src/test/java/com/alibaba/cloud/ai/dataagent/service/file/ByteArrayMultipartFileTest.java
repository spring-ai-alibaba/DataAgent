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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class ByteArrayMultipartFileTest {

	@Test
	void testGetName() {
		ByteArrayMultipartFile file = new ByteArrayMultipartFile("hello".getBytes(), "test.txt", "text/plain");
		assertEquals("file", file.getName());
	}

	@Test
	void testGetOriginalFilename() {
		ByteArrayMultipartFile file = new ByteArrayMultipartFile("hello".getBytes(), "test.txt", "text/plain");
		assertEquals("test.txt", file.getOriginalFilename());
	}

	@Test
	void testGetContentType() {
		ByteArrayMultipartFile file = new ByteArrayMultipartFile("hello".getBytes(), "test.txt", "text/plain");
		assertEquals("text/plain", file.getContentType());
	}

	@Test
	void testIsEmpty_false() {
		ByteArrayMultipartFile file = new ByteArrayMultipartFile("hello".getBytes(), "test.txt", "text/plain");
		assertFalse(file.isEmpty());
	}

	@Test
	void testIsEmpty_true_nullContent() {
		ByteArrayMultipartFile file = new ByteArrayMultipartFile(null, "test.txt", "text/plain");
		assertTrue(file.isEmpty());
	}

	@Test
	void testIsEmpty_true_emptyContent() {
		ByteArrayMultipartFile file = new ByteArrayMultipartFile(new byte[0], "test.txt", "text/plain");
		assertTrue(file.isEmpty());
	}

	@Test
	void testGetSize() {
		byte[] content = "hello world".getBytes();
		ByteArrayMultipartFile file = new ByteArrayMultipartFile(content, "test.txt", "text/plain");
		assertEquals(content.length, file.getSize());
	}

	@Test
	void testGetBytes() {
		byte[] content = "hello".getBytes();
		ByteArrayMultipartFile file = new ByteArrayMultipartFile(content, "test.txt", "text/plain");
		assertArrayEquals(content, file.getBytes());
	}

	@Test
	void testGetInputStream() throws IOException {
		byte[] content = "hello".getBytes();
		ByteArrayMultipartFile file = new ByteArrayMultipartFile(content, "test.txt", "text/plain");

		try (InputStream is = file.getInputStream()) {
			byte[] read = is.readAllBytes();
			assertArrayEquals(content, read);
		}
	}

	@Test
	void testTransferTo_throwsUnsupported() {
		ByteArrayMultipartFile file = new ByteArrayMultipartFile("hello".getBytes(), "test.txt", "text/plain");
		assertThrows(UnsupportedOperationException.class, () -> file.transferTo(new java.io.File("/tmp/test")));
	}

}
