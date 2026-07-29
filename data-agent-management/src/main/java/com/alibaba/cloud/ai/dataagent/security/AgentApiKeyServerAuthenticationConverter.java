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
package com.alibaba.cloud.ai.dataagent.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AgentApiKeyServerAuthenticationConverter implements ServerAuthenticationConverter {

	public static final String API_KEY_HEADER = "X-API-Key";

	private static final String BEARER_PREFIX = "Bearer ";

	private static final Pattern AGENT_MEMORY_PATH = Pattern.compile("^/api/agents/([^/]+)/memories(?:/.*)?$");

	@Override
	public Mono<Authentication> convert(ServerWebExchange exchange) {
		String agentId = exchange.getRequest().getQueryParams().getFirst("agentId");
		if (!StringUtils.hasText(agentId)) {
			Matcher matcher = AGENT_MEMORY_PATH.matcher(exchange.getRequest().getPath().value());
			if (matcher.matches()) {
				agentId = matcher.group(1);
			}
		}
		if (!StringUtils.hasText(agentId)) {
			return Mono.error(new BadCredentialsException("agentId is required"));
		}
		try {
			return Mono
				.just(AgentApiKeyAuthenticationToken.unauthenticated(Long.valueOf(agentId), extractApiKey(exchange)));
		}
		catch (NumberFormatException ex) {
			return Mono.error(new BadCredentialsException("Invalid agent API credentials", ex));
		}
	}

	private String extractApiKey(ServerWebExchange exchange) {
		String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
		if (StringUtils.hasText(apiKey)) {
			return apiKey.trim();
		}
		String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (StringUtils.hasText(authorization)
				&& authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			return authorization.substring(BEARER_PREFIX.length()).trim();
		}
		return null;
	}

}
