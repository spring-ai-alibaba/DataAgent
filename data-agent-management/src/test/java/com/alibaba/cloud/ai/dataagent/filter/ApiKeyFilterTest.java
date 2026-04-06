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

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class ApiKeyFilterTest {

	private static final String VALID_KEY = "sk-test-key";

	private ApiKeyFilter filterFor(boolean prod, Agent agentForKey) {
		Environment env = mock(Environment.class);
		when(env.getActiveProfiles()).thenReturn(prod ? new String[] { "prod" } : new String[] { "h2" });
		AgentMapper mapper = mock(AgentMapper.class);
		// findByApiKey returns the agent for the valid key, null for anything else (Mockito default)
		when(mapper.findByApiKey(VALID_KEY)).thenReturn(agentForKey);
		return new ApiKeyFilter(mapper, env);
	}

	private Agent validAgent() {
		return Agent.builder().id(1L).apiKey(VALID_KEY).apiKeyEnabled(1).build();
	}

	private WebFilterChain passingChain() {
		WebFilterChain chain = mock(WebFilterChain.class);
		when(chain.filter(any())).thenReturn(Mono.empty());
		return chain;
	}

	// --- prod: strictMode=true ---

	@Test
	void prod_shouldReject_whenNoApiKey() {
		ApiKeyFilter filter = filterFor(true, validAgent());
		WebFilterChain chain = passingChain();
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/api/sessions").build());

		StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
		assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
		verify(chain, never()).filter(any());
	}

	@Test
	void prod_shouldReject_whenInvalidApiKey() {
		ApiKeyFilter filter = filterFor(true, validAgent());
		WebFilterChain chain = passingChain();
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/api/sessions").header("X-API-Key", "wrong-key").build());

		StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
		assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
		verify(chain, never()).filter(any());
	}

	@Test
	void prod_shouldAllow_whenValidApiKey() {
		ApiKeyFilter filter = filterFor(true, validAgent());
		WebFilterChain chain = passingChain();
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/api/sessions").header("X-API-Key", VALID_KEY).build());

		StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
		verify(chain).filter(exchange);
	}

	// --- non-prod: strictMode=false ---

	@Test
	void nonProd_shouldAllow_whenNoApiKey() {
		ApiKeyFilter filter = filterFor(false, validAgent());
		WebFilterChain chain = passingChain();
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/api/sessions").build());

		StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
		assertNull(exchange.getResponse().getStatusCode());
		verify(chain).filter(exchange);
	}

	@Test
	void nonProd_shouldAllow_whenBlankApiKey() {
		ApiKeyFilter filter = filterFor(false, validAgent());
		WebFilterChain chain = passingChain();
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/api/sessions").header("X-API-Key", "   ").build());

		StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
		assertNull(exchange.getResponse().getStatusCode());
		verify(chain).filter(exchange);
	}

	@Test
	void nonProd_shouldReject_whenWrongApiKey() {
		ApiKeyFilter filter = filterFor(false, validAgent());
		WebFilterChain chain = passingChain();
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/api/sessions").header("X-API-Key", "bad-key").build());

		StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
		assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
		verify(chain, never()).filter(any());
	}

	@Test
	void nonProd_shouldAllow_whenValidApiKey() {
		ApiKeyFilter filter = filterFor(false, validAgent());
		WebFilterChain chain = passingChain();
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/api/sessions").header("X-API-Key", VALID_KEY).build());

		StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
		verify(chain).filter(exchange);
	}

	// --- path-based skip ---

	@Test
	void shouldSkip_whenNotApiPath() {
		ApiKeyFilter filter = filterFor(true, validAgent());
		WebFilterChain chain = passingChain();
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/swagger-ui/index.html").build());

		StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
		verify(chain).filter(exchange);
	}

	@Test
	void shouldSkip_whenV3ApiDocs() {
		ApiKeyFilter filter = filterFor(true, validAgent());
		WebFilterChain chain = passingChain();
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/v3/api-docs").build());

		StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
		verify(chain).filter(exchange);
	}

	@Test
	void shouldSkip_whenActuator() {
		ApiKeyFilter filter = filterFor(true, validAgent());
		WebFilterChain chain = passingChain();
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/actuator/health").build());

		StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
		verify(chain).filter(exchange);
	}

	@Test
	void shouldRequireAuth_whenApiSwaggerUi() {
		ApiKeyFilter filter = filterFor(true, validAgent());
		WebFilterChain chain = passingChain();
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/api/swagger-ui").build());

		StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
		assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
		verify(chain, never()).filter(any());
	}

}
