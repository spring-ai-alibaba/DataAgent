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

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class AgentApiKeyReactiveAuthenticationManager implements ReactiveAuthenticationManager {

	private static final String AGENT_API_AUTHORITY = "ROLE_AGENT_API";

	private final AgentMapper agentMapper;

	private final ApiKeyCredentialService credentialService;

	@Override
	public Mono<Authentication> authenticate(Authentication authentication) {
		if (!(authentication instanceof AgentApiKeyAuthenticationToken token)) {
			return Mono.empty();
		}
		return Mono.fromCallable(() -> authenticate(token)).subscribeOn(Schedulers.boundedElastic());
	}

	private Authentication authenticate(AgentApiKeyAuthenticationToken token) {
		Long agentId = (Long) token.getPrincipal();
		Agent agent = agentMapper.findById(agentId);
		if (agent == null) {
			throw new BadCredentialsException("Invalid agent API credentials");
		}
		if (!Integer.valueOf(1).equals(agent.getApiKeyEnabled())) {
			if (token.isCredentialRequired()) {
				throw new BadCredentialsException("Agent API key must be enabled for memory management");
			}
			return authenticated(agentId);
		}
		String rawApiKey = (String) token.getCredentials();
		if (!credentialService.matches(rawApiKey, agent.getApiKey())) {
			throw new BadCredentialsException("Invalid agent API credentials");
		}
		return authenticated(agentId);
	}

	private Authentication authenticated(Long agentId) {
		return AgentApiKeyAuthenticationToken.authenticated(agentId,
				AuthorityUtils.createAuthorityList(AGENT_API_AUTHORITY));
	}

}
