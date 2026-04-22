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
package com.alibaba.cloud.ai.dataagent.properties;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import java.nio.file.Path;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = Constant.PROJECT_PROPERTIES_PREFIX + ".skills")
public class AgentSkillProperties {

	/**
	 * 本地 skill 根目录。默认相对后端启动目录创建。
	 */
	private String localPath = "./agent-skills";

	public Path getLocalBasePath() {
		Path configuredPath = Path.of(localPath);
		if (configuredPath.isAbsolute()) {
			return configuredPath.normalize();
		}
		Path workingDirectory = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
		if ("data-agent-management".equalsIgnoreCase(String.valueOf(workingDirectory.getFileName()))) {
			Path parent = workingDirectory.getParent();
			if (parent != null) {
				return parent.resolve(configuredPath).normalize();
			}
		}
		return workingDirectory.resolve(configuredPath).normalize();
	}

}
