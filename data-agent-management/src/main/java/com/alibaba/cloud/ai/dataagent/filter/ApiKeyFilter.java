/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;

@Slf4j
@Component
@Order(-100)
public class ApiKeyFilter implements WebFilter {

	private static final String API_KEY_HEADER = "X-API-Key";

	private static final String API_PATH_PREFIX = "/api/";

	private final String apiKey;

	private final boolean strictMode;

	public ApiKeyFilter(@Value("${app.api-key}") String apiKey, Environment environment) {
		this.apiKey = apiKey;
		this.strictMode = Arrays.asList(environment.getActiveProfiles()).contains("prod");
		log.info("ApiKeyFilter initialized: strictMode={}", this.strictMode);
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String path = exchange.getRequest().getURI().getPath();

		if (shouldSkipAuth(path)) {
			return chain.filter(exchange);
		}

		String requestApiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);

		if (requestApiKey != null && !requestApiKey.isBlank()) {
			// Key provided — always validate it regardless of profile
			if (!requestApiKey.equals(apiKey)) {
				log.warn("Rejected request to {}: invalid API key", path);
				exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
				return exchange.getResponse().setComplete();
			}
			return chain.filter(exchange);
		}

		// No key provided — only enforce in prod
		if (strictMode) {
			log.warn("Rejected request to {}: missing API key (prod mode)", path);
			exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
			return exchange.getResponse().setComplete();
		}

		return chain.filter(exchange);
	}

	private boolean shouldSkipAuth(String path) {
		return !path.startsWith(API_PATH_PREFIX);
	}

}
