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
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class PythonDependencyPolicy {

	private static final Pattern PACKAGE_SPECIFIER = Pattern.compile(
			"^[A-Za-z0-9][A-Za-z0-9._-]*(?:\\[[A-Za-z0-9._,-]+])?(?:\\s*[!<>=~]{1,2}\\s*[A-Za-z0-9.*+!_-]+(?:\\s*,\\s*[!<>=~]{1,2}\\s*[A-Za-z0-9.*+!_-]+)*)?$");

	private final CodeExecutorProperties properties;

	public PythonDependencyPolicy(CodeExecutorProperties properties) {
		this.properties = properties;
	}

	public void validateMetadataSize(String metadata) {
		int bytes = metadata.getBytes(StandardCharsets.UTF_8).length;
		if (bytes > properties.getSandbox().getMaxMetadataBytes()) {
			throw new IllegalArgumentException("Python dependency metadata exceeds the configured size limit");
		}
	}

	public List<String> validateDependencies(List<String> dependencies) {
		if (dependencies.size() > properties.getSandbox().getMaxDependencies()) {
			throw new IllegalArgumentException("Too many Python dependencies");
		}
		for (String dependency : dependencies) {
			if (dependency == null || !PACKAGE_SPECIFIER.matcher(dependency).matches()) {
				throw new IllegalArgumentException("Unsupported Python dependency specifier: " + dependency);
			}
		}
		return List.copyOf(dependencies);
	}

}
