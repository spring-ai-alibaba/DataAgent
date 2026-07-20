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
package com.alibaba.cloud.ai.dataagent.service.code.impls;

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.HostConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DockerCodePoolExecutorServiceTest {

	@Mock
	private DockerClient dockerClient;

	private CodeExecutorProperties properties;

	@BeforeEach
	void setUp() {
		properties = new CodeExecutorProperties();
		properties.setContainerNamePrefix("test-docker-");
		properties.setTaskQueueSize(2);
		properties.setCoreContainerNum(1);
		properties.setTempContainerNum(1);
		properties.setCoreThreadSize(1);
		properties.setMaxThreadSize(1);
		properties.setThreadQueueSize(2);
	}

	@Test
	void constructor_withInjectedClient_hasNoDockerSideEffects() {
		DockerCodePoolExecutorService service = new DockerCodePoolExecutorService(properties, dockerClient, false);

		verifyNoInteractions(dockerClient);

		service.close();
	}

	@Test
	void createHostConfig_withSecurityOpt_appliesIt() {
		properties.setSecurityOpt("seccomp=unconfined");
		DockerCodePoolExecutorService service = new DockerCodePoolExecutorService(properties, dockerClient, false);

		HostConfig hostConfig = service.createHostConfig();

		assertEquals(List.of("seccomp=unconfined"), hostConfig.getSecurityOpts());
		service.close();
	}

	@Test
	void createHostConfig_doesNotBindSharedTaskFiles() {
		DockerCodePoolExecutorService service = new DockerCodePoolExecutorService(properties, dockerClient, false);

		HostConfig hostConfig = service.createHostConfig();

		assertEquals(0, hostConfig.getBinds().length);
		service.close();
	}

	@Test
	void singletonContainerName_isStableAcrossTasks() {
		DockerCodePoolExecutorService service = new DockerCodePoolExecutorService(properties, dockerClient, false);

		assertEquals("test-docker-singleton", service.singletonContainerName());
		service.close();
	}

	@Test
	void singletonContainerStartupCommand_createsTaskRootInsideTmpfs() {
		DockerCodePoolExecutorService service = new DockerCodePoolExecutorService(properties, dockerClient, false);

		assertEquals("mkdir -p /tmp/dataagent-tasks && while true; do sleep 3600; done",
				service.singletonContainerStartupCommand());
		service.close();
	}

	@Test
	void taskCommand_installsDynamicDependenciesIntoTaskDirectory() {
		DockerCodePoolExecutorService service = new DockerCodePoolExecutorService(properties, dockerClient, false);

		String command = service.buildTaskCommand();

		assertTrue(command.contains("--target packages"));
		assertTrue(command.contains("PYTHONPATH=\"$PWD/packages"));
		assertTrue(command.contains("python3 -u script.py < input_data.txt"));
		service.close();
	}

	@Test
	void close_calledTwice_closesDockerClientExactlyOnce() throws IOException {
		DockerCodePoolExecutorService service = new DockerCodePoolExecutorService(properties, dockerClient, false);

		service.close();
		service.close();

		verify(dockerClient).close();
	}

}
