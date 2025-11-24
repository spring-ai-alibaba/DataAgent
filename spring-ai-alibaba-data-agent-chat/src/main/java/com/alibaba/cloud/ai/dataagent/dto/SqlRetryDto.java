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
