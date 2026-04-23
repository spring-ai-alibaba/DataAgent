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
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(InvalidInputException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Object> handleInvalidInputException(InvalidInputException e) {
		log.warn("Invalid input: {}", e.getMessage());
		return ApiResponse.error(e.getMessage(), e.getData());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
		String message = buildValidationMessage(e.getBindingResult().getFieldErrors().stream()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.collect(Collectors.toList()));
		log.warn("Method argument not valid: {}", message);
		return ApiResponse.error(message);
	}

	@ExceptionHandler(WebExchangeBindException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Object> handleWebExchangeBindException(WebExchangeBindException e) {
		String message = buildValidationMessage(e.getFieldErrors().stream()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.collect(Collectors.toList()));
		log.warn("Web exchange bind not valid: {}", message);
		return ApiResponse.error(message);
	}

	@ExceptionHandler(InternalServerException.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public ApiResponse<Object> handleInternalServerException(InternalServerException e) {
		log.error("Internal server error: {}", e.getMessage(), e);
		return ApiResponse.error(e.getMessage());
	}

	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public ApiResponse<Object> handleGenericException(Exception e) {
		log.error("Unexpected error: {}", e.getMessage(), e);
		return ApiResponse.error("Internal server error");
	}

	private String buildValidationMessage(java.util.List<String> messages) {
		String message = messages.stream().filter(item -> item != null && !item.isBlank()).collect(Collectors.joining("; "));
		if (message.isBlank()) {
			return "Request validation failed";
		}
		return message;
	}

}
