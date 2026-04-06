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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ApiKeyFilterTest {

    private ApiKeyFilter filter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyFilter("sk-test-key");
        chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void shouldReject_whenNoApiKey() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/sessions").build()
        );

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
            .verifyComplete();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void shouldReject_whenInvalidApiKey() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/sessions")
                .header("X-API-Key", "wrong-key")
                .build()
        );

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
            .verifyComplete();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void shouldAllow_whenValidApiKey() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/sessions")
                .header("X-API-Key", "sk-test-key")
                .build()
        );

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
            .verifyComplete();
        verify(chain).filter(exchange);
    }

    @Test
    void shouldSkip_whenNotApiPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/swagger-ui/index.html").build()
        );

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
            .verifyComplete();
        verify(chain).filter(exchange);
    }
}
