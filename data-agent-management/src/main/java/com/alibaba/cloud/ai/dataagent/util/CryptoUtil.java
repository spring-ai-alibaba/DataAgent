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
package com.alibaba.cloud.ai.dataagent.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM encryption/decryption utility for sensitive data storage.
 *
 * <p>
 * Encryption key must be 32 bytes, provided as Base64-encoded string via environment
 * variable {@code DATA_AGENT_ENCRYPT_KEY}.
 * </p>
 *
 * <p>
 * Ciphertext format: {@code Base64(iv + encryptedData + authTag)}, where iv is 12 bytes,
 * authTag is 16 bytes.
 * </p>
 */
public final class CryptoUtil {

	private static final String ALGORITHM = "AES";

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private static final int GCM_IV_LENGTH = 12;

	private static final int GCM_TAG_LENGTH = 128;

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private CryptoUtil() {
	}

	/**
	 * Encrypt plaintext using AES-256-GCM.
	 * @param plaintext the text to encrypt
	 * @param keyBase64 Base64-encoded 32-byte AES key
	 * @return Base64-encoded ciphertext, or null/empty if input is null/empty
	 */
	public static String encrypt(String plaintext, String keyBase64) {
		if (plaintext == null || plaintext.isEmpty()) {
			return plaintext;
		}
		if (keyBase64 == null || keyBase64.isEmpty()) {
			throw new IllegalArgumentException("Encryption key must not be null or empty");
		}
		try {
			byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
			SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

			byte[] iv = new byte[GCM_IV_LENGTH];
			SECURE_RANDOM.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

			byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

			// Pack iv + ciphertext into single byte array
			ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
			buffer.put(iv);
			buffer.put(encrypted);

			return Base64.getEncoder().encodeToString(buffer.array());
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to encrypt data", e);
		}
	}

	/**
	 * Decrypt ciphertext using AES-256-GCM.
	 * @param ciphertext Base64-encoded ciphertext from {@link #encrypt(String, String)}
	 * @param keyBase64 Base64-encoded 32-byte AES key
	 * @return decrypted plaintext, or null/empty if input is null/empty
	 */
	public static String decrypt(String ciphertext, String keyBase64) {
		if (ciphertext == null || ciphertext.isEmpty()) {
			return ciphertext;
		}
		if (keyBase64 == null || keyBase64.isEmpty()) {
			throw new IllegalArgumentException("Encryption key must not be null or empty");
		}
		try {
			byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
			SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

			byte[] decoded = Base64.getDecoder().decode(ciphertext);
			ByteBuffer buffer = ByteBuffer.wrap(decoded);

			byte[] iv = new byte[GCM_IV_LENGTH];
			buffer.get(iv);

			byte[] encrypted = new byte[buffer.remaining()];
			buffer.get(encrypted);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
			cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

			byte[] decrypted = cipher.doFinal(encrypted);
			return new String(decrypted, StandardCharsets.UTF_8);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to decrypt data", e);
		}
	}

	/**
	 * Generate a random 32-byte AES key, returned as Base64 string. Useful for initial
	 * setup.
	 * @return Base64-encoded 32-byte key
	 */
	public static String generateKey() {
		byte[] key = new byte[32];
		SECURE_RANDOM.nextBytes(key);
		return Base64.getEncoder().encodeToString(key);
	}

	/**
	 * Check if a ciphertext string is already encrypted (Base64-encoded with valid
	 * length). This is a heuristic check, not cryptographic verification.
	 * @param value the string to check
	 * @return true if the value appears to be encrypted
	 */
	public static boolean isEncrypted(String value) {
		if (value == null || value.isEmpty()) {
			return false;
		}
		try {
			byte[] decoded = Base64.getDecoder().decode(value);
			// Minimum: 12 (iv) + 16 (tag) + 1 (data) = 29 bytes
			return decoded.length >= GCM_IV_LENGTH + GCM_TAG_LENGTH / 8 + 1;
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * Decrypt ciphertext if it's encrypted, otherwise return as-is. This method provides
	 * backward compatibility for data that was stored before encryption was enabled.
	 *
	 * <p>
	 * <strong>Backward Compatibility Strategy:</strong>
	 * </p>
	 * <ul>
	 * <li>When encryption is first enabled, existing plaintext data in database remains
	 * readable</li>
	 * <li>On read: encrypted data is decrypted, plaintext data passes through
	 * unchanged</li>
	 * <li>On write: all new data is encrypted (if key is configured)</li>
	 * <li>Over time, as data is updated, plaintext data is gradually replaced with
	 * encrypted data</li>
	 * </ul>
	 *
	 * <p>
	 * <strong>Detection Logic:</strong> Uses {@link #isEncrypted(String)} to check if the
	 * value is valid Base64-encoded ciphertext with minimum GCM structure (12-byte IV +
	 * 16-byte auth tag + at least 1 byte data = 29 bytes minimum).
	 * </p>
	 * @param value the value to decrypt (may be encrypted or plaintext), null-safe
	 * @param keyBase64 Base64-encoded 32-byte AES key
	 * @return decrypted value if encrypted, or original value if plaintext/null/empty
	 */
	public static String decryptOrReturn(String value, String keyBase64) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		// Check if the value looks like encrypted data before attempting decryption
		// This prevents decryption failures on legacy plaintext data
		if (isEncrypted(value)) {
			try {
				return decrypt(value, keyBase64);
			}
			catch (RuntimeException e) {
				// If decryption fails (e.g., wrong key), return original value
				// This handles edge cases where plaintext data happens to pass
				// isEncrypted check
				return value;
			}
		}
		// Not encrypted, return as-is (backward compatible with historical plaintext
		// data)
		return value;
	}

	/**
	 * Main method for generating encryption key from command line.
	 * @param args unused
	 */
	public static void main(String[] args) {
		System.out.println("=== DataAgent Encryption Key Generator ===");
		System.out.println();
		String key = generateKey();
		System.out.println("Generated AES-256 Key (Base64): " + key);
		System.out.println();
		System.out.println("Set this as environment variable:");
		System.out.println("  Linux/Mac: export DATA_AGENT_ENCRYPT_KEY=" + key);
		System.out.println("  Windows:   set DATA_AGENT_ENCRYPT_KEY=" + key);
		System.out.println("  PowerShell: $env:DATA_AGENT_ENCRYPT_KEY=\"" + key + "\"");
	}

}
