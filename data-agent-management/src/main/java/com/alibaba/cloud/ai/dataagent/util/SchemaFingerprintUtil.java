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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Creates a stable fingerprint for a complete datasource schema document set.
 */
public final class SchemaFingerprintUtil {

	private static final Set<String> SCHEMA_METADATA_KEYS = Set.of("datasourceId", "vectorType", "schema", "name",
			"tableName", "type", "primary", "notnull", "foreignKey", "primaryKey");

	private SchemaFingerprintUtil() {
	}

	@SafeVarargs
	public static String fingerprint(List<Document>... documentGroups) {
		return fingerprintWithPrefix("", documentGroups);
	}

	/**
	 * Includes the durable datasource generation so a connection or selected-table change
	 * invalidates schema-sensitive memories even when the resulting physical DDL happens
	 * to be identical.
	 */
	@SafeVarargs
	public static String fingerprint(long schemaGeneration, List<Document>... documentGroups) {
		return fingerprintWithPrefix("generation=" + schemaGeneration + '\n', documentGroups);
	}

	@SafeVarargs
	private static String fingerprintWithPrefix(String prefix, List<Document>... documentGroups) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(prefix.getBytes(StandardCharsets.UTF_8));
			for (List<Document> documents : documentGroups) {
				if (documents == null) {
					continue;
				}
				documents.stream().map(SchemaFingerprintUtil::canonicalDocument).sorted().forEach(value -> {
					digest.update(value.getBytes(StandardCharsets.UTF_8));
					digest.update((byte) 0);
				});
				digest.update((byte) 1);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	private static String canonicalDocument(Document document) {
		Map<String, String> sortedMetadata = document.getMetadata()
			.entrySet()
			.stream()
			.filter(entry -> SCHEMA_METADATA_KEYS.contains(entry.getKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> canonicalValue(entry.getKey(), entry.getValue()),
					(left, right) -> left, TreeMap::new));
		// Spring AI creates a random ID for Document(text, metadata). The ID is a
		// vector-storage concern. Enriched descriptions and sampled values are also
		// intentionally excluded because they can change without a DDL change.
		return sortedMetadata + "\n";
	}

	private static String canonicalValue(String key, Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof Iterable<?> iterable) {
			List<String> values = new ArrayList<>();
			iterable.forEach(item -> values.add(String.valueOf(item)));
			return values.stream().sorted().collect(Collectors.joining(","));
		}
		if ("foreignKey".equals(key)) {
			return Arrays.stream(String.valueOf(value).split("、"))
				.map(String::trim)
				.filter(part -> !part.isEmpty())
				.sorted()
				.collect(Collectors.joining("、"));
		}
		return String.valueOf(value);
	}

}
