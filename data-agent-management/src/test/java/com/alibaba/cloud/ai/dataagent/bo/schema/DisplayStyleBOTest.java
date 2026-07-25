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
package com.alibaba.cloud.ai.dataagent.bo.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayStyleBOTest {

	private final BeanOutputConverter<DisplayStyleBO> converter = new BeanOutputConverter<>(DisplayStyleBO.class);

	@Test
	void tableOutputDoesNotRequireChartAxes() throws Exception {
		JsonNode schema = new ObjectMapper().readTree(JsonSchemaGenerator.generateForType(DisplayStyleBO.class));

		assertThat(schema.path("required").isArray()).isTrue();
		assertThat(schema.path("required").toString()).contains("type", "title").doesNotContain("\"x\"", "\"y\"");

		DisplayStyleBO displayStyle = converter.convert("""
				{"type":"table","title":"订单统计"}
				""");

		assertThat(displayStyle).isNotNull();
		assertThat(displayStyle.getType()).isEqualTo("table");
		assertThat(displayStyle.getTitle()).isEqualTo("订单统计");
		assertThat(displayStyle.getX()).isNull();
		assertThat(displayStyle.getY()).isNull();
	}

}
