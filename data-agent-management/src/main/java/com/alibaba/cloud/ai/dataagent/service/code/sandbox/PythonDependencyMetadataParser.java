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

import org.springframework.stereotype.Component;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class PythonDependencyMetadataParser {

	private static final String BLOCK_START = "# /// script";

	private static final String BLOCK_END = "# ///";

	private static final Set<String> SUPPORTED_KEYS = Set.of("dependencies", "requires-python");

	private final PythonDependencyPolicy dependencyPolicy;

	public PythonDependencyMetadataParser(PythonDependencyPolicy dependencyPolicy) {
		this.dependencyPolicy = dependencyPolicy;
	}

	public PythonDependencyMetadata parse(String code) {
		String metadata = extractMetadata(code);
		if (metadata == null) {
			return PythonDependencyMetadata.empty();
		}
		dependencyPolicy.validateMetadataSize(metadata);

		TomlParseResult result = Toml.parse(metadata);
		if (result.hasErrors()) {
			throw new IllegalArgumentException("Invalid PEP 723 dependency metadata: " + result.errors().get(0));
		}
		if (!SUPPORTED_KEYS.containsAll(result.keySet())) {
			throw new IllegalArgumentException("PEP 723 metadata contains unsupported fields");
		}

		List<String> dependencies = new ArrayList<>();
		TomlArray dependencyArray = result.getArray("dependencies");
		if (dependencyArray != null) {
			for (int index = 0; index < dependencyArray.size(); index++) {
				Object dependency = dependencyArray.get(index);
				if (!(dependency instanceof String dependencyString)) {
					throw new IllegalArgumentException("PEP 723 dependencies must be strings");
				}
				dependencies.add(dependencyString);
			}
		}
		String requiresPython = result.getString("requires-python");
		return new PythonDependencyMetadata(dependencyPolicy.validateDependencies(dependencies), requiresPython);
	}

	private String extractMetadata(String code) {
		String[] lines = code.split("\\R", -1);
		StringBuilder metadata = null;
		boolean insideBlock = false;
		boolean foundBlock = false;
		for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
			String line = lines[lineIndex];
			if (BLOCK_START.equals(line.strip())) {
				if (insideBlock || foundBlock) {
					throw new IllegalArgumentException("Python code must contain at most one PEP 723 script block");
				}
				metadata = new StringBuilder();
				insideBlock = true;
				foundBlock = true;
				continue;
			}
			if (!insideBlock) {
				continue;
			}
			if (BLOCK_END.equals(line.strip())) {
				insideBlock = false;
				continue;
			}
			String stripped = line.stripLeading();
			if (stripped.isEmpty() && lineIndex == lines.length - 1) {
				continue;
			}
			if (!stripped.startsWith("#")) {
				throw new IllegalArgumentException("Each PEP 723 metadata line must be a Python comment");
			}
			String value = stripped.substring(1);
			if (value.startsWith(" ")) {
				value = value.substring(1);
			}
			metadata.append(value).append('\n');
		}
		if (insideBlock) {
			throw new IllegalArgumentException("PEP 723 script block is not closed");
		}
		return foundBlock ? metadata.toString() : null;
	}

}
