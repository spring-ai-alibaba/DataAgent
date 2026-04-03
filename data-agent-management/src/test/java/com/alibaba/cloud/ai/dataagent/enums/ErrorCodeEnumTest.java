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
package com.alibaba.cloud.ai.dataagent.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeEnumTest {

	@Test
	void testGetCodeAndMessage() {
		assertEquals("0", ErrorCodeEnum.SUCCESS.getCode());
		assertEquals("操作成功", ErrorCodeEnum.SUCCESS.getMessage());
		assertEquals("10", ErrorCodeEnum.INVALID_PARAM.getCode());
		assertEquals("100", ErrorCodeEnum.OTHERS.getCode());
	}

	@Test
	void testFromCode_known() {
		assertEquals(ErrorCodeEnum.SUCCESS, ErrorCodeEnum.fromCode("0"));
		assertEquals(ErrorCodeEnum.INVALID_PARAM, ErrorCodeEnum.fromCode("10"));
		assertEquals(ErrorCodeEnum.DATASOURCE_CONNECTION_FAILURE_08001, ErrorCodeEnum.fromCode("08001"));
		assertEquals(ErrorCodeEnum.PASSWORD_ERROR_28P01, ErrorCodeEnum.fromCode("28P01"));
	}

	@Test
	void testFromCode_unknown() {
		assertEquals(ErrorCodeEnum.OTHERS, ErrorCodeEnum.fromCode("99999"));
	}

	@Test
	void testFromCodeWithSuccess_known() {
		assertEquals(ErrorCodeEnum.INVALID_PARAM, ErrorCodeEnum.fromCodeWithSuccess("10"));
	}

	@Test
	void testFromCodeWithSuccess_unknown() {
		assertEquals(ErrorCodeEnum.SUCCESS, ErrorCodeEnum.fromCodeWithSuccess("99999"));
	}

	@Test
	void testToString() {
		assertEquals("[0] 操作成功", ErrorCodeEnum.SUCCESS.toString());
		assertEquals("[100] 未知错误，请联系技术人员排查", ErrorCodeEnum.OTHERS.toString());
	}

	@Test
	void testValues() {
		assertEquals(17, ErrorCodeEnum.values().length);
	}

}
