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

import com.alibaba.cloud.ai.dataagent.exception.InternalServerException;
import com.alibaba.cloud.ai.dataagent.exception.InvalidInputException;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

	private GlobalExceptionHandler handler;

	@BeforeEach
	void setUp() {
		handler = new GlobalExceptionHandler();
	}

	@Test
	void handleInvalidInputException_returnsErrorResponse() {
		InvalidInputException ex = new InvalidInputException("bad input");

		ApiResponse<Object> response = handler.handleInvalidInputException(ex);

		assertFalse(response.isSuccess());
		assertEquals("bad input", response.getMessage());
		assertNull(response.getData());
	}

	@Test
	void handleInvalidInputException_withData_returnsErrorWithData() {
		InvalidInputException ex = new InvalidInputException("bad input", "detail");

		ApiResponse<Object> response = handler.handleInvalidInputException(ex);

		assertFalse(response.isSuccess());
		assertEquals("bad input", response.getMessage());
		assertEquals("detail", response.getData());
	}

	@Test
	void handleInternalServerException_returnsErrorResponse() {
		InternalServerException ex = new InternalServerException("server error");

		ApiResponse<Object> response = handler.handleInternalServerException(ex);

		assertFalse(response.isSuccess());
		assertEquals("server error", response.getMessage());
	}

	@Test
	void handleGenericException_returnsFixedErrorMessage() {
		Exception ex = new RuntimeException("unexpected");

		ApiResponse<Object> response = handler.handleGenericException(ex);

		assertFalse(response.isSuccess());
		assertEquals("\u670d\u52a1\u5668\u5185\u90e8\u9519\u8bef", response.getMessage());
	}

}
