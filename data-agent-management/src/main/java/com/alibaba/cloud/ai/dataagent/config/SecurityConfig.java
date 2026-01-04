/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.filter.ApiKeyAuthenticationFilter;
import com.alibaba.cloud.ai.dataagent.service.agent.AgentService;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatSessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final String[] PROTECTED_PATHS = { "/api/agent/*/sessions", "/api/agent/*/sessions/**",
			"/api/sessions/*", "/api/sessions/**" };

	@Bean
	public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(AgentService agentService,
			ChatSessionService chatSessionService) {
		List<RequestMatcher> matchers = List.of(new AntPathRequestMatcher(PROTECTED_PATHS[0]),
				new AntPathRequestMatcher(PROTECTED_PATHS[1]), new AntPathRequestMatcher(PROTECTED_PATHS[2]),
				new AntPathRequestMatcher(PROTECTED_PATHS[3]));
		return new ApiKeyAuthenticationFilter(matchers, agentService, chatSessionService);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyAuthenticationFilter apiKeyFilter)
			throws Exception {
		return http.cors(Customizer.withDefaults())
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.OPTIONS, "/api/**")
				.permitAll()
				.requestMatchers(PROTECTED_PATHS)
				.authenticated()
				.anyRequest()
				.permitAll())
			.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
			.build();
	}

}
