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
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxExecutionResultParserTest {

	private final SandboxExecutionResultParser parser = new SandboxExecutionResultParser(new CodeExecutorProperties());

	@Test
	void parse_extractsLastEnvelopeFromEscapedSandboxProtocol() throws Exception {
		SandboxExecutionResult expected = new SandboxExecutionResult(true, "{\"ok\":true}", "", null, null);
		String json = JsonUtil.getObjectMapper().writeValueAsString(expected);
		String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
		String response = "{\"content\":[{\"text\":\"cell output\\\\n" + PythonSandboxBootstrapBuilder.RESULT_MARKER
				+ encoded + "\\\\n\"}]}";

		SandboxExecutionResult actual = parser.parse(response);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	void parse_rejectsResponsesWithoutResultMarker() {
		assertThatThrownBy(() -> parser.parse("{\"isError\":true}")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("result marker");
	}

}
