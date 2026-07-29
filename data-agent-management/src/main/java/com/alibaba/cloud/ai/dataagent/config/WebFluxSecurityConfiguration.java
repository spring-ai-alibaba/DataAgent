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

import com.alibaba.cloud.ai.dataagent.security.AgentApiKeyReactiveAuthenticationManager;
import com.alibaba.cloud.ai.dataagent.security.AgentApiKeyServerAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class WebFluxSecurityConfiguration {

	private static final String STREAM_SEARCH_PATH = "/api/stream/search";

	private static final String MEMORY_API_PATH = "/api/agents/*/memories/**";

	@Bean
	PasswordEncoder apiKeyPasswordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	SecurityWebFilterChain agentApiSecurityWebFilterChain(ServerHttpSecurity http,
			AgentApiKeyReactiveAuthenticationManager authenticationManager,
			AgentApiKeyServerAuthenticationConverter authenticationConverter) {
		AuthenticationWebFilter apiKeyFilter = new AuthenticationWebFilter(authenticationManager);
		apiKeyFilter.setRequiresAuthenticationMatcher(new OrServerWebExchangeMatcher(
				new PathPatternParserServerWebExchangeMatcher(STREAM_SEARCH_PATH, HttpMethod.GET),
				new PathPatternParserServerWebExchangeMatcher(MEMORY_API_PATH)));
		apiKeyFilter.setServerAuthenticationConverter(authenticationConverter);
		apiKeyFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
		apiKeyFilter
			.setAuthenticationFailureHandler(new ServerAuthenticationEntryPointFailureHandler((exchange, ex) -> {
				exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
				return exchange.getResponse().setComplete();
			}));

		return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
			.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
			.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
			.logout(ServerHttpSecurity.LogoutSpec::disable)
			.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
			.authorizeExchange(exchange -> exchange.pathMatchers(HttpMethod.GET, STREAM_SEARCH_PATH)
				.authenticated()
				.pathMatchers(MEMORY_API_PATH)
				.authenticated()
				.anyExchange()
				.permitAll())
			.addFilterAt(apiKeyFilter,
					org.springframework.security.config.web.server.SecurityWebFiltersOrder.AUTHENTICATION)
			.build();
	}

}
