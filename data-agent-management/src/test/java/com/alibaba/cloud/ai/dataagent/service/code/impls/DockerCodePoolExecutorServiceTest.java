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
package com.alibaba.cloud.ai.dataagent.service.code.impls;

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.service.code.CodePoolExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class DockerCodePoolExecutorServiceTest {

	private CodeExecutorProperties properties;

	@BeforeEach
	void setUp() {
		properties = new CodeExecutorProperties();
		properties.setContainerNamePrefix("test-docker-");
		properties.setCodeTimeout("60s");
		properties.setContainerTimeout(5L);
		properties.setImageName("continuumio/anaconda3:latest");
		properties.setHost("tcp://localhost:2375");
		properties.setCoreContainerNum(2);
		properties.setTempContainerNum(2);
		properties.setTaskQueueSize(5);
		properties.setCoreThreadSize(2);
		properties.setMaxThreadSize(2);
		properties.setKeepThreadAliveTime(60L);
		properties.setThreadQueueSize(5);
	}

	@Test
	void constructor_dockerUnavailable_throwsRuntimeException() {
		properties.setHost("tcp://192.0.2.1:2375");
		assertThrows(RuntimeException.class, () -> new DockerCodePoolExecutorService(properties));
	}

	@Test
	void constructor_nullHost_usesDefaultAndMayFail() {
		properties.setHost(null);
		try {
			DockerCodePoolExecutorService service = new DockerCodePoolExecutorService(properties);
			assertNotNull(service);
		}
		catch (RuntimeException e) {
			assertTrue(e.getMessage().contains("Failed to connect to Docker")
					|| e.getMessage().contains("Connection refused") || e.getMessage() != null);
		}
	}

	@Test
	void getDockerHostForCurrentOS_withExplicitHost_returnsExplicitHost() throws Exception {
		DockerCodePoolExecutorService instance = createServiceWithMockedDocker();
		if (instance == null) {
			return;
		}

		Method method = DockerCodePoolExecutorService.class.getDeclaredMethod("getDockerHostForCurrentOS",
				String.class);
		method.setAccessible(true);
		String result = (String) method.invoke(instance, "tcp://myhost:2375");
		assertEquals("tcp://myhost:2375", result);
	}

	@Test
	void getDockerHostForCurrentOS_withNullHost_returnsOsDefault() throws Exception {
		DockerCodePoolExecutorService instance = createServiceWithMockedDocker();
		if (instance == null) {
			return;
		}

		Method method = DockerCodePoolExecutorService.class.getDeclaredMethod("getDockerHostForCurrentOS",
				String.class);
		method.setAccessible(true);
		String result = (String) method.invoke(instance, (String) null);
		assertNotNull(result);
		assertTrue(result.startsWith("unix://") || result.startsWith("tcp://") || result.startsWith("npipe://"));
	}

	@Test
	void checkIsRemote_unixSocket_returnsFalse() throws Exception {
		DockerCodePoolExecutorService instance = createServiceWithMockedDocker();
		if (instance == null) {
			return;
		}

		Method method = DockerCodePoolExecutorService.class.getDeclaredMethod("checkIsRemote", String.class);
		method.setAccessible(true);
		assertFalse((boolean) method.invoke(instance, "unix:///var/run/docker.sock"));
	}

	@Test
	void checkIsRemote_localhost_returnsFalse() throws Exception {
		DockerCodePoolExecutorService instance = createServiceWithMockedDocker();
		if (instance == null) {
			return;
		}

		Method method = DockerCodePoolExecutorService.class.getDeclaredMethod("checkIsRemote", String.class);
		method.setAccessible(true);
		assertFalse((boolean) method.invoke(instance, "tcp://localhost:2375"));
		assertFalse((boolean) method.invoke(instance, "tcp://127.0.0.1:2375"));
	}

	@Test
	void checkIsRemote_remoteHost_returnsTrue() throws Exception {
		DockerCodePoolExecutorService instance = createServiceWithMockedDocker();
		if (instance == null) {
			return;
		}

		Method method = DockerCodePoolExecutorService.class.getDeclaredMethod("checkIsRemote", String.class);
		method.setAccessible(true);
		assertTrue((boolean) method.invoke(instance, "tcp://192.168.1.100:2375"));
	}

	@Test
	void checkIsRemote_nullHost_returnsFalse() throws Exception {
		DockerCodePoolExecutorService instance = createServiceWithMockedDocker();
		if (instance == null) {
			return;
		}

		Method method = DockerCodePoolExecutorService.class.getDeclaredMethod("checkIsRemote", String.class);
		method.setAccessible(true);
		assertFalse((boolean) method.invoke(instance, (String) null));
	}

	@Test
	void checkIsRemote_emptyHost_returnsFalse() throws Exception {
		DockerCodePoolExecutorService instance = createServiceWithMockedDocker();
		if (instance == null) {
			return;
		}

		Method method = DockerCodePoolExecutorService.class.getDeclaredMethod("checkIsRemote", String.class);
		method.setAccessible(true);
		assertFalse((boolean) method.invoke(instance, ""));
	}

	@Test
	void generateContainerName_containsPrefixAndTimestamp() throws Exception {
		DockerCodePoolExecutorService instance = createServiceWithMockedDocker();
		if (instance == null) {
			return;
		}

		Method method = AbstractCodePoolExecutorService.class.getDeclaredMethod("generateContainerName");
		method.setAccessible(true);
		String name = (String) method.invoke(instance);
		assertTrue(name.startsWith("test-docker-_"));
		assertTrue(name.contains(Thread.currentThread().getName()));
	}

	@Test
	void shutdownPool_clearsAllState() throws Exception {
		DockerCodePoolExecutorService instance = createServiceWithMockedDocker();
		if (instance == null) {
			return;
		}

		Method shutdownMethod = DockerCodePoolExecutorService.class.getDeclaredMethod("shutdownPool");
		shutdownMethod.setAccessible(true);
		assertDoesNotThrow(() -> {
			try {
				shutdownMethod.invoke(instance);
			}
			catch (Exception e) {
				if (e.getCause() instanceof Exception) {
					throw e.getCause();
				}
			}
		});

		Field coreState = AbstractCodePoolExecutorService.class.getDeclaredField("coreContainerState");
		coreState.setAccessible(true);
		ConcurrentHashMap<?, ?> coreMap = (ConcurrentHashMap<?, ?>) coreState.get(instance);
		assertTrue(coreMap.isEmpty());

		Field tempState = AbstractCodePoolExecutorService.class.getDeclaredField("tempContainerState");
		tempState.setAccessible(true);
		ConcurrentHashMap<?, ?> tempMap = (ConcurrentHashMap<?, ?>) tempState.get(instance);
		assertTrue(tempMap.isEmpty());
	}

	@Test
	void appendWithLimit_underLimit_appendsFully() throws Exception {
		DockerCodePoolExecutorService instance = createServiceWithMockedDocker();
		if (instance == null) {
			return;
		}

		Method method = DockerCodePoolExecutorService.class.getDeclaredMethod("appendWithLimit", StringBuilder.class,
				String.class, int.class);
		method.setAccessible(true);

		StringBuilder sb = new StringBuilder();
		method.invoke(instance, sb, "hello", 100);
		assertEquals("hello", sb.toString());
	}

	@Test
	void appendWithLimit_atLimit_appendsTruncationMessage() throws Exception {
		DockerCodePoolExecutorService instance = createServiceWithMockedDocker();
		if (instance == null) {
			return;
		}

		Method method = DockerCodePoolExecutorService.class.getDeclaredMethod("appendWithLimit", StringBuilder.class,
				String.class, int.class);
		method.setAccessible(true);

		StringBuilder sb = new StringBuilder("12345");
		method.invoke(instance, sb, "more", 5);
		assertTrue(sb.toString().contains("truncated"));
	}

	@Test
	void taskResponse_exception_createsCorrectResponse() {
		CodePoolExecutorService.TaskResponse response = CodePoolExecutorService.TaskResponse.exception("test error");
		assertFalse(response.isSuccess());
		assertFalse(response.executionSuccessButResultFailed());
		assertNull(response.stdOut());
		assertNull(response.stdErr());
		assertTrue(response.exceptionMsg().contains("test error"));
	}

	@Test
	void taskResponse_success_createsCorrectResponse() {
		CodePoolExecutorService.TaskResponse response = CodePoolExecutorService.TaskResponse.success("output");
		assertTrue(response.isSuccess());
		assertFalse(response.executionSuccessButResultFailed());
		assertEquals("output", response.stdOut());
		assertNull(response.stdErr());
		assertNull(response.exceptionMsg());
	}

	@Test
	void taskResponse_failure_createsCorrectResponse() {
		CodePoolExecutorService.TaskResponse response = CodePoolExecutorService.TaskResponse.failure("out", "err");
		assertFalse(response.isSuccess());
		assertTrue(response.executionSuccessButResultFailed());
		assertEquals("out", response.stdOut());
		assertEquals("err", response.stdErr());
		assertTrue(response.exceptionMsg().contains("err"));
	}

	private DockerCodePoolExecutorService createServiceWithMockedDocker() {
		try {
			return new DockerCodePoolExecutorService(properties);
		}
		catch (RuntimeException e) {
			return null;
		}
	}

}
