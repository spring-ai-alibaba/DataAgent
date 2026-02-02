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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 代理配置属性类
 *
 * @author Darlingxxx
 * @since 2026.1.30
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "spring.ai.alibaba.data-agent.ai-proxy")
public class AiProxyProperties {

	/**
	 * 是否启用代理，默认为 false
	 */
	private boolean enabled = false;

	/**
	 * 代理主机地址
	 */
	private String host;

	/**
	 * 代理端口
	 */
	private Integer port;

	/**
	 * 代理用户名（可选）
	 */
	private String username;

	/**
	 * 代理密码（可选）
	 */
	private String password;

}
