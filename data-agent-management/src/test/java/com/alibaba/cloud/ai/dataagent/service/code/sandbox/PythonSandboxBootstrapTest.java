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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PythonSandboxBootstrapTest {

	private PythonSandboxBootstrapBuilder builder;

	private SandboxExecutionResultParser parser;

	@BeforeEach
	void setUp() {
		CodeExecutorProperties properties = new CodeExecutorProperties();
		builder = new PythonSandboxBootstrapBuilder(properties);
		parser = new SandboxExecutionResultParser(properties);
	}

	@Test
	void dependencyInstaller_usesFixedPipCommandAndDoesNotInterpolateDependency() {
		String dependency = "pandas>=2,<3";
		String code = builder.buildDependencyInstaller(List.of(dependency));

		assertThat(code).contains("sys.executable, \"-m\", \"pip\", \"install\"");
		assertThat(code).contains("\"--target\", \"/tmp/dataagent-deps\"");
		assertThat(code).doesNotContain(dependency);
	}

	@Test
	void codeExecution_encodesUntrustedCodeAndInput() {
		String code = "print('secret marker')";
		String input = "[{\"value\":\"input marker\"}]";
		String wrapper = builder.buildCodeExecution(code, input);

		assertThat(wrapper).doesNotContain(code).doesNotContain("input marker");
		assertThat(wrapper).contains("[sys.executable, \"-c\", _code]");
	}

	@Test
	void resultParser_extractsLastEnvelopeFromEscapedSandboxResponse() throws Exception {
		SandboxExecutionResult expected = new SandboxExecutionResult(true, "{\"ok\":true}", "", null, null);
		String json = JsonUtil.getObjectMapper().writeValueAsString(expected);
		String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
		String raw = "{\"content\":[{\"text\":\"old " + PythonSandboxBootstrapBuilder.RESULT_MARKER
				+ "aW52YWxpZA==\\\\n" + PythonSandboxBootstrapBuilder.RESULT_MARKER + encoded + "\\\\n\"}]}";

		SandboxExecutionResult actual = parser.parse(raw);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	void resultParser_rejectsUnstructuredSandboxOutput() {
		assertThatThrownBy(() -> parser.parse("{\"isError\":true}")).hasMessageContaining("result marker");
	}

}
