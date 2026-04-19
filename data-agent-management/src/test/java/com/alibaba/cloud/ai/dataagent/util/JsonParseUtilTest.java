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

import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JsonParseUtilTest {

	@Mock
	private LlmService llmService;

	private JsonParseUtil jsonParseUtil;

	@BeforeEach
	void setUp() {
		jsonParseUtil = new JsonParseUtil(llmService);
	}

	@Test
	void tryConvertToObject_validJson_returnsObject() {
		String json = "{\"name\":\"test\",\"value\":42}";
		Map<String, Object> result = jsonParseUtil.tryConvertToObject(json, new TypeReference<>() {
		});

		assertEquals("test", result.get("name"));
		assertEquals(42, result.get("value"));
	}

	@Test
	void tryConvertToObject_validJsonWithClass_returnsObject() {
		String json = "{\"name\":\"test\"}";
		Map result = jsonParseUtil.tryConvertToObject(json, Map.class);
		assertEquals("test", result.get("name"));
	}

	@Test
	void tryConvertToObject_nullInput_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> jsonParseUtil.tryConvertToObject(null, Map.class));
	}

	@Test
	void tryConvertToObject_emptyInput_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> jsonParseUtil.tryConvertToObject("", Map.class));
	}

	@Test
	void tryConvertToObject_nullClass_throwsException() {
		assertThrows(IllegalArgumentException.class, () -> jsonParseUtil.tryConvertToObject("{}", (Class<?>) null));
	}

	@Test
	void tryConvertToObject_nullTypeReference_throwsException() {
		assertThrows(IllegalArgumentException.class,
				() -> jsonParseUtil.tryConvertToObject("{}", (TypeReference<?>) null));
	}

	@Test
	void tryConvertToObject_withThinkTags_removesTagsAndParses() {
		String json = "<think>some reasoning</think>{\"name\":\"test\"}";
		Map result = jsonParseUtil.tryConvertToObject(json, Map.class);
		assertEquals("test", result.get("name"));
	}

	@Test
	void tryConvertToObject_withMultipleThinkTags_removesLastTagAndAfter() {
		String json = "<think>first</think><think>second</think>{\"name\":\"test\"}";
		Map result = jsonParseUtil.tryConvertToObject(json, Map.class);
		assertEquals("test", result.get("name"));
	}

	@Test
	void tryConvertToObject_invalidJson_callsLlmToFix() {
		String invalidJson = "{invalid json}";
		String fixedJson = "{\"name\":\"fixed\"}";

		Flux<ChatResponse> responseFlux = Flux.just(ChatResponseUtil.createPureResponse(fixedJson));
		when(llmService.callUser(anyString())).thenReturn(responseFlux);
		when(llmService.toStringFlux(any())).thenReturn(Flux.just(fixedJson));

		Map result = jsonParseUtil.tryConvertToObject(invalidJson, Map.class);
		assertEquals("fixed", result.get("name"));
		verify(llmService).callUser(anyString());
	}

	@Test
	void tryConvertToObject_invalidJson_allRetriesFail_throwsException() {
		String invalidJson = "{invalid json}";

		Flux<ChatResponse> responseFlux = Flux.just(ChatResponseUtil.createPureResponse("still invalid"));
		when(llmService.callUser(anyString())).thenReturn(responseFlux);
		when(llmService.toStringFlux(any())).thenReturn(Flux.just("still invalid"));

		assertThrows(IllegalArgumentException.class, () -> jsonParseUtil.tryConvertToObject(invalidJson, Map.class));
		verify(llmService, times(3)).callUser(anyString());
	}

	@Test
	void tryConvertToObject_invalidJson_llmReturnsNull_usesOriginal() {
		String invalidJson = "{invalid}";

		when(llmService.callUser(anyString())).thenReturn(Flux.empty());
		when(llmService.toStringFlux(any())).thenReturn(Flux.empty());

		assertThrows(IllegalArgumentException.class, () -> jsonParseUtil.tryConvertToObject(invalidJson, Map.class));
	}

	@Test
	void tryConvertToObject_invalidJson_llmThrowsException_usesOriginal() {
		String invalidJson = "{invalid}";

		when(llmService.callUser(anyString())).thenThrow(new RuntimeException("LLM error"));

		assertThrows(IllegalArgumentException.class, () -> jsonParseUtil.tryConvertToObject(invalidJson, Map.class));
	}

	@Test
	void tryConvertToObject_jsonWithMarkdownCodeBlock_extractsContent() {
		String invalidJson = "{bad}";
		String fixedWithMarkdown = "```json\n{\"name\":\"fixed\"}\n```";

		when(llmService.callUser(anyString())).thenReturn(Flux.empty());
		when(llmService.toStringFlux(any())).thenReturn(Flux.just(fixedWithMarkdown));

		Map result = jsonParseUtil.tryConvertToObject(invalidJson, Map.class);
		assertEquals("fixed", result.get("name"));
	}

	@Test
	void tryConvertToObject_validListJson_returnsListWithTypeReference() {
		String json = "[\"a\",\"b\",\"c\"]";
		List<String> result = jsonParseUtil.tryConvertToObject(json, new TypeReference<>() {
		});
		assertEquals(3, result.size());
		assertEquals("a", result.get(0));
	}

	@Test
	void tryConvertToObject_jsonWithWhitespaceAndThinkTag_trims() {
		String json = "  <think>reasoning</think>  {\"key\":\"val\"}  ";
		Map result = jsonParseUtil.tryConvertToObject(json, Map.class);
		assertEquals("val", result.get("key"));
	}

}
