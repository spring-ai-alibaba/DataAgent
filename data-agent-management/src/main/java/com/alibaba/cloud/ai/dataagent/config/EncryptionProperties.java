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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Encryption configuration properties for sensitive data storage.
 *
 * <p>
 * The encryption key must be a 32-byte AES key encoded as Base64. Set via environment
 * variable {@code DATA_AGENT_ENCRYPT_KEY} or configuration property
 * {@code spring.ai.alibaba.data-agent.encrypt-key}.
 * </p>
 *
 * <p>
 * <strong>Backward Compatibility:</strong> When {@code encrypt-key} is not configured
 * (empty/null), encryption is completely disabled and the system behaves identically to
 * the pre-encryption version. This allows:
 * </p>
 * <ul>
 * <li>Zero-impact upgrade: existing installations work without any configuration
 * change</li>
 * <li>Gradual migration: enable encryption when ready, historical data remains
 * readable</li>
 * <li>Rollback safety: removing the key disables encryption without data loss</li>
 * </ul>
 *
 * <p>
 * To generate a new key: {@code openssl rand -base64 32}
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "spring.ai.alibaba.data-agent")
public class EncryptionProperties {

	/**
	 * Base64-encoded 32-byte AES key for encrypting sensitive data. If not set,
	 * encryption is disabled (NOT recommended for production).
	 */
	private String encryptKey;

	/**
	 * Check if encryption is enabled (key is configured).
	 * @return true if encryptKey is set and non-empty
	 */
	public boolean isEncryptionEnabled() {
		return encryptKey != null && !encryptKey.isEmpty();
	}

}
