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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-100)
public class ApiKeyFilter implements WebFilter {

	private static final String API_KEY_HEADER = "X-API-Key";

	private static final String API_PATH_PREFIX = "/api/";

	private final String apiKey;

	public ApiKeyFilter(@Value("${app.api-key}") String apiKey) {
		this.apiKey = apiKey;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String path = exchange.getRequest().getURI().getPath();

		if (shouldSkipAuth(path)) {
			return chain.filter(exchange);
		}

		String requestApiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);

		if (requestApiKey == null || requestApiKey.isBlank() || !requestApiKey.equals(apiKey)) {
			exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
			return exchange.getResponse().setComplete();
		}

		return chain.filter(exchange);
	}

	private boolean shouldSkipAuth(String path) {
		return !path.startsWith(API_PATH_PREFIX);
	}

}
