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

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaFingerprintUtilTest {

	@Test
	void fingerprintIsStableAcrossDocumentAndMetadataOrdering() {
		Map<String, Object> firstMetadata = new LinkedHashMap<>();
		firstMetadata.put("name", "orders");
		firstMetadata.put("datasourceId", "3");
		Map<String, Object> reorderedMetadata = new LinkedHashMap<>();
		reorderedMetadata.put("datasourceId", "3");
		reorderedMetadata.put("name", "orders");
		Document first = new Document("a", "orders table", firstMetadata);
		Document firstReordered = new Document("a", "orders table", reorderedMetadata);
		Document second = new Document("b", "users table", Map.of("name", "users"));

		String original = SchemaFingerprintUtil.fingerprint(List.of(first, second));
		String reordered = SchemaFingerprintUtil.fingerprint(List.of(second, firstReordered));

		assertThat(reordered).isEqualTo(original);
		assertThat(SchemaFingerprintUtil.fingerprint(List.of(new Document("a", "changed", firstMetadata))))
			.isNotEqualTo(original);
	}

}
