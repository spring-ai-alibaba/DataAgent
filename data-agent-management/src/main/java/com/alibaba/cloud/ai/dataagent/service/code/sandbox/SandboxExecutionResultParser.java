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
package com.alibaba.cloud.ai.dataagent.service.code.sandbox;

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SandboxExecutionResultParser {

	private static final Pattern ENCODED_RESULT = Pattern
		.compile(Pattern.quote(PythonSandboxBootstrapBuilder.RESULT_MARKER) + "([A-Za-z0-9+/=]+)");

	private final CodeExecutorProperties properties;

	public SandboxExecutionResultParser(CodeExecutorProperties properties) {
		this.properties = properties;
	}

	public SandboxExecutionResult parse(String rawResult) {
		if (rawResult == null) {
			throw new IllegalArgumentException("Sandbox returned an empty response");
		}
		Matcher matcher = ENCODED_RESULT.matcher(rawResult);
		String encodedResult = null;
		while (matcher.find()) {
			encodedResult = matcher.group(1);
		}
		if (encodedResult == null) {
			throw new IllegalArgumentException("Sandbox response does not contain the DataAgent result marker");
		}

		byte[] decoded;
		try {
			decoded = Base64.getDecoder().decode(encodedResult);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Sandbox returned an invalid encoded result", ex);
		}
		int maximumBytes = properties.getSandbox().getMaxOutputBytes() + properties.getSandbox().getMaxErrorBytes();
		if (decoded.length > maximumBytes + 4096) {
			throw new IllegalArgumentException("Sandbox result exceeds the configured size limit");
		}

		try {
			return JsonUtil.getObjectMapper()
				.readValue(new String(decoded, StandardCharsets.UTF_8), SandboxExecutionResult.class);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("Sandbox returned an invalid result envelope", ex);
		}
	}

}
