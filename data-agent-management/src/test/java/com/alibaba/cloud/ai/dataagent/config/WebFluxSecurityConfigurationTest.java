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

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.alibaba.cloud.ai.dataagent.security.AgentApiKeyReactiveAuthenticationManager;
import com.alibaba.cloud.ai.dataagent.security.AgentApiKeyServerAuthenticationConverter;
import com.alibaba.cloud.ai.dataagent.security.ApiKeyCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunctions;

import static org.mockito.Mockito.when;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@ExtendWith(MockitoExtension.class)
class WebFluxSecurityConfigurationTest {

	@Mock
	private AgentMapper agentMapper;

	private ApiKeyCredentialService credentialService;

	private WebTestClient webTestClient;

	@BeforeEach
	void setUp() {
		WebFluxSecurityConfiguration configuration = new WebFluxSecurityConfiguration();
		PasswordEncoder encoder = configuration.apiKeyPasswordEncoder();
		credentialService = new ApiKeyCredentialService(encoder);
		AgentApiKeyReactiveAuthenticationManager manager = new AgentApiKeyReactiveAuthenticationManager(agentMapper,
				credentialService);
		var securityChain = configuration.agentApiSecurityWebFilterChain(
				org.springframework.security.config.web.server.ServerHttpSecurity.http(), manager,
				new AgentApiKeyServerAuthenticationConverter());
		var router = RouterFunctions.route(GET("/api/stream/search"), request -> ok().bodyValue("stream"))
			.andRoute(POST("/api/agents/{agentId}/memories"), request -> ok().bodyValue("memory"))
			.andRoute(GET("/api/agent/list"), request -> ok().bodyValue("management"));
		webTestClient = WebTestClient.bindToRouterFunction(router)
			.webFilter(new WebFilterChainProxy(securityChain))
			.build();
	}

	@Test
	void streamSearch_apiKeyDisabled_allowsExistingInternalFlow() {
		when(agentMapper.findById(1L)).thenReturn(Agent.builder().id(1L).apiKeyEnabled(0).build());

		webTestClient.get().uri("/api/stream/search?agentId=1").exchange().expectStatus().isOk();
	}

	@Test
	void streamSearch_apiKeyEnabledWithoutCredential_returnsUnauthorized() {
		when(agentMapper.findById(1L))
			.thenReturn(Agent.builder().id(1L).apiKeyEnabled(1).apiKey(credentialService.encode("sk-valid")).build());

		webTestClient.get().uri("/api/stream/search?agentId=1").exchange().expectStatus().isUnauthorized();
	}

	@Test
	void streamSearch_validHeaderCredential_isAuthenticated() {
		when(agentMapper.findById(1L))
			.thenReturn(Agent.builder().id(1L).apiKeyEnabled(1).apiKey(credentialService.encode("sk-valid")).build());

		webTestClient.get()
			.uri("/api/stream/search?agentId=1")
			.header(AgentApiKeyServerAuthenticationConverter.API_KEY_HEADER, "sk-valid")
			.exchange()
			.expectStatus()
			.isOk();
	}

	@Test
	void streamSearch_wrongBearerCredential_returnsUnauthorized() {
		when(agentMapper.findById(1L))
			.thenReturn(Agent.builder().id(1L).apiKeyEnabled(1).apiKey(credentialService.encode("sk-valid")).build());

		webTestClient.get()
			.uri("/api/stream/search?agentId=1")
			.headers(headers -> headers.setBearerAuth("sk-wrong"))
			.exchange()
			.expectStatus()
			.isUnauthorized();
	}

	@Test
	void managementEndpoint_isNotClaimedByAgentApiKeyAuthentication() {
		webTestClient.get().uri("/api/agent/list").exchange().expectStatus().isOk();
	}

	@Test
	void memoryMutationUsesAgentIdFromPathAndRequiresItsCredential() {
		when(agentMapper.findById(7L))
			.thenReturn(Agent.builder().id(7L).apiKeyEnabled(1).apiKey(credentialService.encode("sk-seven")).build());

		webTestClient.post()
			.uri("/api/agents/7/memories")
			.header(AgentApiKeyServerAuthenticationConverter.API_KEY_HEADER, "sk-seven")
			.exchange()
			.expectStatus()
			.isOk();
	}

	@Test
	void memoryMutationWithoutCredentialIsUnauthorized() {
		when(agentMapper.findById(7L))
			.thenReturn(Agent.builder().id(7L).apiKeyEnabled(1).apiKey(credentialService.encode("sk-seven")).build());

		webTestClient.post().uri("/api/agents/7/memories").exchange().expectStatus().isUnauthorized();
	}

	@Test
	void memoryMutationCannotBypassAuthenticationWhenAgentApiKeyIsDisabled() {
		when(agentMapper.findById(7L)).thenReturn(Agent.builder().id(7L).apiKeyEnabled(0).build());

		webTestClient.post().uri("/api/agents/7/memories").exchange().expectStatus().isUnauthorized();
	}

	@Test
	void memoryPathIdentityCannotBeOverriddenByQueryParameter() {
		when(agentMapper.findById(7L)).thenReturn(Agent.builder().id(7L).apiKeyEnabled(0).build());

		webTestClient.post().uri("/api/agents/7/memories?agentId=8").exchange().expectStatus().isUnauthorized();
	}

}
