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

import org.springframework.ai.document.Document;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Creates a stable fingerprint for the schema documents used by one analysis turn.
 */
public final class SchemaFingerprintUtil {

	private SchemaFingerprintUtil() {
	}

	@SafeVarargs
	public static String fingerprint(List<Document>... documentGroups) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (List<Document> documents : documentGroups) {
				if (documents == null) {
					continue;
				}
				documents.stream()
					.sorted(Comparator.comparing(Document::getId, Comparator.nullsFirst(String::compareTo)))
					.map(SchemaFingerprintUtil::canonicalDocument)
					.forEach(value -> digest.update(value.getBytes(StandardCharsets.UTF_8)));
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	private static String canonicalDocument(Document document) {
		Map<String, Object> sortedMetadata = new TreeMap<>(document.getMetadata());
		return document.getId() + '\n' + document.getText() + '\n' + sortedMetadata + '\n';
	}

}
