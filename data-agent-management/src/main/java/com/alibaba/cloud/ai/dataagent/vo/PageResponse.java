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
package com.alibaba.cloud.ai.dataagent.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 分页响应类
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PageResponse<T> extends ApiResponse<T> {

	/**
	 * 总记录数
	 */
	private Long total;

	/**
	 * 当前页码
	 */
	private Integer pageNum;

	/**
	 * 每页大小
	 */
	private Integer pageSize;

	/**
	 * 总页数
	 */
	private Integer totalPages;

	public PageResponse(boolean success, String message) {
		super(success, message);
	}

	@SuppressWarnings("unchecked")
	public PageResponse(boolean success, String message, T data) {
		super(success, message, data);
	}

	@SuppressWarnings("unchecked")
	public PageResponse(boolean success, String message, T data, Long total, Integer pageNum, Integer pageSize,
			Integer totalPages) {
		super(success, message, data);
		this.total = total;
		this.pageNum = pageNum;
		this.pageSize = pageSize;
		this.totalPages = totalPages;
	}

	public static <T> PageResponse<T> success(T data, Long total, Integer pageNum, Integer pageSize,
			Integer totalPages) {
		return new PageResponse<>(true, "查询成功", data, total, pageNum, pageSize, totalPages);
	}

	public static <T> PageResponse<T> success(String message, T data, Long total, Integer pageNum, Integer pageSize,
			Integer totalPages) {
		return new PageResponse<>(true, message, data, total, pageNum, pageSize, totalPages);
	}

	public static <T> PageResponse<T> pageError(String message) {
		return new PageResponse<>(false, message);
	}

	public static <T> PageResponse<T> pageError(String message, T data) {
		return new PageResponse<>(false, message, data);
	}

}
