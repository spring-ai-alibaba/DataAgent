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
import io.agentscope.runtime.sandbox.box.BaseSandbox;
import io.agentscope.runtime.sandbox.manager.ManagerConfig;
import io.agentscope.runtime.sandbox.manager.SandboxService;
import io.agentscope.runtime.sandbox.manager.client.container.docker.DockerClientStarter;
import io.agentscope.runtime.sandbox.manager.model.sandbox.SandboxConfig;
import io.agentscope.runtime.sandbox.manager.registry.SandboxRegistryService;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SaaSandboxRuntime {

	private final CodeExecutorProperties properties;

	private volatile SandboxService sandboxService;

	public SaaSandboxRuntime(CodeExecutorProperties properties) {
		this.properties = properties;
	}

	public SandboxService getOrStart() {
		SandboxService current = sandboxService;
		if (current != null) {
			return current;
		}
		synchronized (this) {
			if (sandboxService == null) {
				sandboxService = startSandboxService();
			}
			return sandboxService;
		}
	}

	private SandboxService startSandboxService() {
		CodeExecutorProperties.Sandbox sandboxProperties = properties.getSandbox();
		Map<String, Object> runtimeConfig = Map.of("mem_limit", properties.getLimitMemory() + "m", "nano_cpus",
				properties.getCpuCore() * 1_000_000_000L, "max_connections", sandboxProperties.getMaxConnections(),
				"privileged", false);
		SandboxConfig sandboxConfig = new SandboxConfig.Builder().imageName(sandboxProperties.getImageName())
			.sandboxType("base")
			.securityLevel("high")
			.timeout(sandboxTimeoutSeconds())
			.description("DataAgent generated Python sandbox")
			.runtimeConfig(runtimeConfig)
			.build();
		SandboxRegistryService.register(BaseSandbox.class, sandboxConfig);

		DockerClientStarter dockerClientStarter = createDockerClientStarter(sandboxProperties.getDockerHost());
		ManagerConfig managerConfig = ManagerConfig.builder()
			.containerPrefixKey(sandboxProperties.getContainerPrefix())
			.clientStarter(dockerClientStarter)
			.build();
		SandboxService service = new SandboxService(managerConfig);
		service.start();
		return service;
	}

	private DockerClientStarter createDockerClientStarter(String dockerHost) {
		if (dockerHost != null && dockerHost.startsWith("tcp://")) {
			String address = dockerHost.substring("tcp://".length());
			int separator = address.lastIndexOf(':');
			if (separator > 0) {
				return DockerClientStarter.builder()
					.host(address.substring(0, separator))
					.port(Integer.parseInt(address.substring(separator + 1)))
					.build();
			}
		}
		// AgentScope 1.0.2 falls back to docker-java's standard local socket
		// discovery when its default TCP endpoint is unavailable.
		return DockerClientStarter.builder().build();
	}

	int sandboxTimeoutSeconds() {
		return Math.toIntExact(properties.getSandbox()
			.getDependencyInstallTimeout()
			.plus(properties.getCodeTimeout())
			.plusSeconds(30)
			.toSeconds());
	}

	@PreDestroy
	public synchronized void close() {
		if (sandboxService != null) {
			sandboxService.cleanupAllSandboxes();
			sandboxService.close();
			sandboxService = null;
		}
	}

}
