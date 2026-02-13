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
package com.alibaba.cloud.ai.dataagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

	private Jwt jwt = new Jwt();

	private Lockout lockout = new Lockout();

	@Data
	public static class Jwt {

		private String secret;

		private long accessTokenExpiration = 3600000; // 1 hour

		private long refreshTokenExpiration = 604800000; // 7 days

	}

	@Data
	public static class Lockout {

		private int maxAttempts = 5;

		private long lockDuration = 1800000; // 30 minutes

	}

}
