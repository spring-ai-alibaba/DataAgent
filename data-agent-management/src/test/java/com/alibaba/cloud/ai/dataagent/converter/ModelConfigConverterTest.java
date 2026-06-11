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
package com.alibaba.cloud.ai.dataagent.converter;

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ModelConfigConverter 转换逻辑单元测试。 重点回归 chatApiProtocol 的默认值兜底： INSERT 显式写入 NULL
 * 会绕过数据库列默认值，必须在转换层回填。
 */
class ModelConfigConverterTest {

	private ModelConfigDTO buildDto(String chatApiProtocol) {
		return ModelConfigDTO.builder()
			.provider("openai")
			.baseUrl("https://api.example.com")
			.apiKey("sk-test")
			.modelName("test-model")
			.modelType("CHAT")
			.chatApiProtocol(chatApiProtocol)
			.build();
	}

	@Test
	void toEntity_backfillsDefaultProtocolWhenNull() {
		ModelConfig entity = ModelConfigConverter.toEntity(buildDto(null));
		assertEquals("CHAT_COMPLETIONS", entity.getChatApiProtocol());
	}

	@Test
	void toEntity_backfillsDefaultProtocolWhenBlank() {
		ModelConfig entity = ModelConfigConverter.toEntity(buildDto("  "));
		assertEquals("CHAT_COMPLETIONS", entity.getChatApiProtocol());
	}

	@Test
	void toEntity_keepsExplicitProtocol() {
		ModelConfig entity = ModelConfigConverter.toEntity(buildDto("RESPONSES"));
		assertEquals("RESPONSES", entity.getChatApiProtocol());
	}

}
