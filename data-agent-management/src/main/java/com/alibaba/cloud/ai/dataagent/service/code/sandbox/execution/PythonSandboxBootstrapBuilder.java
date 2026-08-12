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
package com.alibaba.cloud.ai.dataagent.service.code.sandbox.execution;

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Component
public class PythonSandboxBootstrapBuilder {

	static final String RESULT_MARKER = "__DATAAGENT_RESULT_V1__:";

	private final CodeExecutorProperties properties;

	public PythonSandboxBootstrapBuilder(CodeExecutorProperties properties) {
		this.properties = properties;
	}

	public String buildDependencyInstaller(List<String> dependencies) {
		String dependenciesJson;
		try {
			dependenciesJson = JsonUtil.getObjectMapper().writeValueAsString(dependencies);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("Failed to serialize Python dependencies", ex);
		}
		String encodedDependencies = encode(dependenciesJson);
		String encodedIndex = encode(properties.getSandbox().getPackageIndexUrl());
		long timeoutSeconds = properties.getSandbox().getDependencyInstallTimeout().toSeconds();
		int maxOutputCharacters = properties.getSandbox().getMaxOutputBytes();
		int maxErrorCharacters = properties.getSandbox().getMaxErrorBytes();
		return """
				import base64
				import json
				import subprocess
				import sys

				_dependencies = json.loads(base64.b64decode("%s").decode("utf-8"))
				_index_url = base64.b64decode("%s").decode("utf-8")
				_envelope = {"success": True, "stdout": "", "stderr": "", "errorType": None, "errorMessage": None}
				try:
				    _command = [
				        sys.executable, "-m", "pip", "install",
				        "--disable-pip-version-check", "--no-input", "--no-cache-dir",
				        "--target", "/tmp/dataagent-deps", "--index-url", _index_url,
				        *_dependencies
				    ]
				    _completed = subprocess.run(
				        _command, capture_output=True, text=True, timeout=%d, check=False
				    )
				    _envelope["stdout"] = _completed.stdout[-%d:]
				    _envelope["stderr"] = _completed.stderr[-%d:]
				    if _completed.returncode != 0:
				        _envelope["success"] = False
				        _envelope["errorType"] = "DependencyInstallError"
				        _envelope["errorMessage"] = "pip exited with status " + str(_completed.returncode)
				except subprocess.TimeoutExpired as _error:
				    _envelope["success"] = False
				    _envelope["errorType"] = "DependencyInstallTimeout"
				    _envelope["errorMessage"] = str(_error)
				except BaseException as _error:
				    _envelope["success"] = False
				    _envelope["errorType"] = type(_error).__name__
				    _envelope["errorMessage"] = str(_error)
				_payload = base64.b64encode(
				    json.dumps(_envelope, ensure_ascii=False).encode("utf-8")
				).decode("ascii")
				print("%s" + _payload)
				""".formatted(encodedDependencies, encodedIndex, timeoutSeconds, maxOutputCharacters,
				maxErrorCharacters, RESULT_MARKER);
	}

	public String buildCodeExecution(String code, String input) {
		String encodedCode = encode(code);
		String encodedInput = encode(input);
		long timeoutSeconds = properties.getCodeTimeout().toSeconds();
		int maxOutputCharacters = properties.getSandbox().getMaxOutputBytes();
		int maxErrorCharacters = properties.getSandbox().getMaxErrorBytes();
		return """
				import base64
				import json
				import os
				import subprocess
				import sys

				_code = base64.b64decode("%s").decode("utf-8")
				_input = base64.b64decode("%s").decode("utf-8")
				_envelope = {"success": True, "stdout": "", "stderr": "", "errorType": None, "errorMessage": None}

				def _dataagent_as_text(_value):
				    if isinstance(_value, bytes):
				        return _value.decode("utf-8", errors="replace")
				    return _value or ""

				try:
				    _environment = dict(os.environ)
				    _environment["PYTHONPATH"] = "/tmp/dataagent-deps"
				    _completed = subprocess.run(
				        [sys.executable, "-c", _code],
				        input=_input,
				        capture_output=True,
				        text=True,
				        timeout=%d,
				        check=False,
				        env=_environment
				    )
				    _envelope["stdout"] = _completed.stdout[-%d:]
				    _envelope["stderr"] = _completed.stderr[-%d:]
				    if _completed.returncode != 0:
				        _envelope["success"] = False
				        _envelope["errorType"] = "PythonProcessError"
				        _envelope["errorMessage"] = "Python exited with status " + str(_completed.returncode)
				except subprocess.TimeoutExpired as _error:
				    _envelope["success"] = False
				    _envelope["errorType"] = "PythonTimeout"
				    _envelope["errorMessage"] = "Python code execution exceeded %d seconds"
				    _envelope["stdout"] = _dataagent_as_text(_error.stdout)[-%d:]
				    _envelope["stderr"] = _dataagent_as_text(_error.stderr)[-%d:]
				except BaseException as _error:
				    _envelope["success"] = False
				    _envelope["errorType"] = type(_error).__name__
				    _envelope["errorMessage"] = str(_error)
				_payload = base64.b64encode(
				    json.dumps(_envelope, ensure_ascii=False).encode("utf-8")
				).decode("ascii")
				print("%s" + _payload)
				""".formatted(encodedCode, encodedInput, timeoutSeconds, maxOutputCharacters, maxErrorCharacters,
				timeoutSeconds, maxOutputCharacters, maxErrorCharacters, RESULT_MARKER);
	}

	private String encode(String value) {
		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

}
