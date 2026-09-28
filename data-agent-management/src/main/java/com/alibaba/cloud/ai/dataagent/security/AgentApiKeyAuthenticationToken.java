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

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

final class AgentApiKeyAuthenticationToken extends AbstractAuthenticationToken {

	private final Long agentId;

	private String apiKey;

	private final boolean credentialRequired;

	private AgentApiKeyAuthenticationToken(Long agentId, String apiKey,
			Collection<? extends GrantedAuthority> authorities, boolean credentialRequired) {
		super(authorities);
		this.agentId = agentId;
		this.apiKey = apiKey;
		this.credentialRequired = credentialRequired;
		setAuthenticated(!authorities.isEmpty());
	}

	static AgentApiKeyAuthenticationToken unauthenticated(Long agentId, String apiKey, boolean credentialRequired) {
		return new AgentApiKeyAuthenticationToken(agentId, apiKey, java.util.List.of(), credentialRequired);
	}

	static AgentApiKeyAuthenticationToken authenticated(Long agentId,
			Collection<? extends GrantedAuthority> authorities) {
		return new AgentApiKeyAuthenticationToken(agentId, null, authorities, false);
	}

	boolean isCredentialRequired() {
		return credentialRequired;
	}

	@Override
	public Object getCredentials() {
		return apiKey;
	}

	@Override
	public Object getPrincipal() {
		return agentId;
	}

	@Override
	public void eraseCredentials() {
		super.eraseCredentials();
		apiKey = null;
	}

}
