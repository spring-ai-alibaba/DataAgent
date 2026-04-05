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
package com.alibaba.cloud.ai.dataagent.service.code;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodePoolExecutorServiceTest {

	@Test
	void taskResponse_success_hasCorrectFields() {
		CodePoolExecutorService.TaskResponse response = CodePoolExecutorService.TaskResponse.success("output");

		assertTrue(response.isSuccess());
		assertFalse(response.executionSuccessButResultFailed());
		assertEquals("output", response.stdOut());
		assertNull(response.stdErr());
		assertNull(response.exceptionMsg());
	}

	@Test
	void taskResponse_failure_hasCorrectFields() {
		CodePoolExecutorService.TaskResponse response = CodePoolExecutorService.TaskResponse.failure("stdout",
				"stderr msg");

		assertFalse(response.isSuccess());
		assertTrue(response.executionSuccessButResultFailed());
		assertEquals("stdout", response.stdOut());
		assertEquals("stderr msg", response.stdErr());
		assertTrue(response.exceptionMsg().contains("stderr msg"));
	}

	@Test
	void taskResponse_exception_hasCorrectFields() {
		CodePoolExecutorService.TaskResponse response = CodePoolExecutorService.TaskResponse
			.exception("connection timeout");

		assertFalse(response.isSuccess());
		assertFalse(response.executionSuccessButResultFailed());
		assertNull(response.stdOut());
		assertNull(response.stdErr());
		assertTrue(response.exceptionMsg().contains("connection timeout"));
	}

	@Test
	void taskResponse_toString_containsFields() {
		CodePoolExecutorService.TaskResponse response = CodePoolExecutorService.TaskResponse.success("hello");

		String str = response.toString();
		assertTrue(str.contains("isSuccess=true"));
		assertTrue(str.contains("hello"));
	}

	@Test
	void taskRequest_fieldsAccessible() {
		CodePoolExecutorService.TaskRequest request = new CodePoolExecutorService.TaskRequest("print('hi')",
				"{\"key\": 1}", "analyze data");

		assertEquals("print('hi')", request.code());
		assertEquals("{\"key\": 1}", request.input());
		assertEquals("analyze data", request.requirement());
	}

	@Test
	void taskResponse_success_nullStdErr() {
		CodePoolExecutorService.TaskResponse response = CodePoolExecutorService.TaskResponse.success(null);

		assertTrue(response.isSuccess());
		assertNull(response.stdOut());
	}

	@Test
	void stateEnum_values() {
		assertEquals(3, CodePoolExecutorService.State.values().length);
		assertNotNull(CodePoolExecutorService.State.READY);
		assertNotNull(CodePoolExecutorService.State.RUNNING);
		assertNotNull(CodePoolExecutorService.State.REMOVING);
	}

}
