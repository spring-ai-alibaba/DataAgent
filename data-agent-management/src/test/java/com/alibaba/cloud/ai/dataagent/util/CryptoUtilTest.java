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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CryptoUtil}.
 */
class CryptoUtilTest {

	private String testKey;

	@BeforeEach
	void setUp() {
		// Generate a fresh key for each test
		testKey = CryptoUtil.generateKey();
	}

	@Test
	void testEncryptDecryptRoundTrip() {
		String plaintext = "mySecretPassword123!";
		String encrypted = CryptoUtil.encrypt(plaintext, testKey);
		String decrypted = CryptoUtil.decrypt(encrypted, testKey);

		assertNotEquals(plaintext, encrypted, "Ciphertext should differ from plaintext");
		assertEquals(plaintext, decrypted, "Decrypted text should match original");
	}

	@Test
	void testEncryptNullReturnsNull() {
		assertNull(CryptoUtil.encrypt(null, testKey));
	}

	@Test
	void testEncryptEmptyReturnsEmpty() {
		assertEquals("", CryptoUtil.encrypt("", testKey));
	}

	@Test
	void testDecryptNullReturnsNull() {
		assertNull(CryptoUtil.decrypt(null, testKey));
	}

	@Test
	void testDecryptEmptyReturnsEmpty() {
		assertEquals("", CryptoUtil.decrypt("", testKey));
	}

	@Test
	void testEncryptProducesDifferentCiphertextEachTime() {
		String plaintext = "samePassword";
		String encrypted1 = CryptoUtil.encrypt(plaintext, testKey);
		String encrypted2 = CryptoUtil.encrypt(plaintext, testKey);

		// IV is random, so ciphertexts should differ
		assertNotEquals(encrypted1, encrypted2, "Same plaintext should produce different ciphertexts due to random IV");
	}

	@Test
	void testDecryptWithWrongKeyFails() {
		String plaintext = "myPassword";
		String encrypted = CryptoUtil.encrypt(plaintext, testKey);
		String wrongKey = CryptoUtil.generateKey();

		assertThrows(RuntimeException.class, () -> CryptoUtil.decrypt(encrypted, wrongKey));
	}

	@Test
	void testDecryptTamperedCiphertextFails() {
		String plaintext = "myPassword";
		String encrypted = CryptoUtil.encrypt(plaintext, testKey);
		// Tamper with the ciphertext
		String tampered = encrypted.substring(0, encrypted.length() - 2) + "XX";

		assertThrows(RuntimeException.class, () -> CryptoUtil.decrypt(tampered, testKey));
	}

	@Test
	void testEncryptWithNullKeyThrows() {
		assertThrows(IllegalArgumentException.class, () -> CryptoUtil.encrypt("test", null));
	}

	@Test
	void testEncryptWithEmptyKeyThrows() {
		assertThrows(IllegalArgumentException.class, () -> CryptoUtil.encrypt("test", ""));
	}

	@Test
	void testDecryptWithNullKeyThrows() {
		assertThrows(IllegalArgumentException.class, () -> CryptoUtil.decrypt("test", null));
	}

	@Test
	void testGenerateKeyProducesValidBase64() {
		String key = CryptoUtil.generateKey();
		// Base64 encoded 32 bytes should be 44 characters
		assertEquals(44, key.length());
		// Should not throw when decoding
		java.util.Base64.getDecoder().decode(key);
	}

	@Test
	void testIsEncryptedWithValidCiphertext() {
		String encrypted = CryptoUtil.encrypt("test", testKey);
		assertTrue(CryptoUtil.isEncrypted(encrypted));
	}

	@Test
	void testIsEncryptedWithPlaintext() {
		assertFalse(CryptoUtil.isEncrypted("plaintext"));
		assertFalse(CryptoUtil.isEncrypted("password123"));
	}

	@Test
	void testIsEncryptedWithNull() {
		assertFalse(CryptoUtil.isEncrypted(null));
	}

	@Test
	void testIsEncryptedWithEmpty() {
		assertFalse(CryptoUtil.isEncrypted(""));
	}

	@Test
	void testEncryptDecryptSpecialCharacters() {
		String plaintext = "p@$$w0rd!#$%^&*()_+-=[]{}|;':\",./<>?";
		String encrypted = CryptoUtil.encrypt(plaintext, testKey);
		String decrypted = CryptoUtil.decrypt(encrypted, testKey);
		assertEquals(plaintext, decrypted);
	}

	@Test
	void testEncryptDecryptUnicode() {
		String plaintext = "密码测试🔐🔑";
		String encrypted = CryptoUtil.encrypt(plaintext, testKey);
		String decrypted = CryptoUtil.decrypt(encrypted, testKey);
		assertEquals(plaintext, decrypted);
	}

	@Test
	void testEncryptDecryptLongString() {
		String plaintext = "A".repeat(10000);
		String encrypted = CryptoUtil.encrypt(plaintext, testKey);
		String decrypted = CryptoUtil.decrypt(encrypted, testKey);
		assertEquals(plaintext, decrypted);
	}

	@Test
	void testEncryptDecryptConnectionString() {
		String url = "jdbc:mysql://192.168.1.100:3306/mydb?useSSL=true&password=secret123";
		String encrypted = CryptoUtil.encrypt(url, testKey);
		String decrypted = CryptoUtil.decrypt(encrypted, testKey);
		assertEquals(url, decrypted);
	}

	@Test
	void testEncryptDecryptApiKey() {
		String apiKey = "sk-abcdefghijklmnopqrstuvwxyz123456";
		String encrypted = CryptoUtil.encrypt(apiKey, testKey);
		String decrypted = CryptoUtil.decrypt(encrypted, testKey);
		assertEquals(apiKey, decrypted);
	}

	// Tests for decryptOrReturn (backward compatibility)

	@Test
	void testDecryptOrReturn_withEncryptedData_decrypts() {
		String plaintext = "mySecretPassword";
		String encrypted = CryptoUtil.encrypt(plaintext, testKey);

		String result = CryptoUtil.decryptOrReturn(encrypted, testKey);
		assertEquals(plaintext, result);
	}

	@Test
	void testDecryptOrReturn_withPlaintext_returnsAsIs() {
		String plaintext = "oldPlainTextPassword";

		String result = CryptoUtil.decryptOrReturn(plaintext, testKey);
		assertEquals(plaintext, result);
	}

	@Test
	void testDecryptOrReturn_withNull_returnsNull() {
		assertNull(CryptoUtil.decryptOrReturn(null, testKey));
	}

	@Test
	void testDecryptOrReturn_withEmpty_returnsEmpty() {
		assertEquals("", CryptoUtil.decryptOrReturn("", testKey));
	}

	@Test
	void testDecryptOrReturn_withConnectionString() {
		String url = "jdbc:mysql://localhost:3306/test?user=root&password=123456";

		// Plaintext URL should pass through
		String result = CryptoUtil.decryptOrReturn(url, testKey);
		assertEquals(url, result);
	}

	@Test
	void testDecryptOrReturn_withEncryptedConnectionString() {
		String url = "jdbc:mysql://localhost:3306/test?user=root&password=123456";
		String encrypted = CryptoUtil.encrypt(url, testKey);

		String result = CryptoUtil.decryptOrReturn(encrypted, testKey);
		assertEquals(url, result);
	}

}
