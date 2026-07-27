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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PythonDependencyMetadataParserTest {

	private PythonDependencyMetadataParser parser;

	@BeforeEach
	void setUp() {
		CodeExecutorProperties properties = new CodeExecutorProperties();
		parser = new PythonDependencyMetadataParser(new PythonDependencyPolicy(properties));
	}

	@Test
	void parse_readsCanonicalPep723ScriptBlock() {
		PythonDependencyMetadata metadata = parser.parse("""
				# /// script
				# requires-python = ">=3.10"
				# dependencies = ["pandas>=2,<3", "numpy==2.2.1"]
				# ///
				import pandas
				""");

		assertThat(metadata.requiresPython()).isEqualTo(">=3.10");
		assertThat(metadata.dependencies()).containsExactly("pandas>=2,<3", "numpy==2.2.1");
	}

	@Test
	void parse_returnsEmptyMetadataWhenBlockIsAbsent() {
		assertThat(parser.parse("import json").dependencies()).isEmpty();
	}

	@Test
	void parse_rejectsUrlsPathsMarkersAndPipFlags() {
		for (String dependency : new String[] { "pkg @ https://example.com/pkg.whl", "../local-package",
				"pandas; python_version > '3.10'", "--extra-index-url=https://example.com" }) {
			String code = "# /// script\n# dependencies = [\"" + dependency + "\"]\n# ///\n";
			assertThatThrownBy(() -> parser.parse(code)).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void parse_rejectsMultipleOrUnclosedBlocks() {
		assertThatThrownBy(() -> parser.parse("""
				# /// script
				# dependencies = []
				# ///
				# /// script
				# dependencies = []
				# ///
				""")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> parser.parse("# /// script\n# dependencies = []\n"))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
