/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.dataagent.dto;

import lombok.Getter;

@Getter
public class SqlRetryDto {

	private String reason;

	private boolean semanticFail;

	private boolean sqlExecuteFail;

	public static SqlRetryDto semantic(String reason) {
		SqlRetryDto retryDto = new SqlRetryDto();
		retryDto.reason = reason;
		retryDto.semanticFail = true;
		return retryDto;
	}

	public static SqlRetryDto sqlExecute(String reason) {
		SqlRetryDto retryDto = new SqlRetryDto();
		retryDto.reason = reason;
		retryDto.sqlExecuteFail = true;
		return retryDto;
	}

	public static SqlRetryDto empty() {
		SqlRetryDto retryDto = new SqlRetryDto();
		retryDto.reason = "";
		return retryDto;
	}

}
