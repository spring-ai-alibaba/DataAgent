/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.service.knowledge;

import com.alibaba.cloud.ai.dataagent.enums.SplitterType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class TextSplitterFactoryTest {

	@Test
	void testGetSplitter_found() {
		TextSplitter tokenSplitter = mock(TextSplitter.class);
		Map<String, TextSplitter> map = new HashMap<>();
		map.put("token", tokenSplitter);

		TextSplitterFactory factory = new TextSplitterFactory(map);

		TextSplitter result = factory.getSplitter("token");
		assertSame(tokenSplitter, result);
	}

	@Test
	void testGetSplitter_notFound_fallsBackToDefault() {
		TextSplitter tokenSplitter = mock(TextSplitter.class);
		Map<String, TextSplitter> map = new HashMap<>();
		map.put(SplitterType.TOKEN.getValue(), tokenSplitter);

		TextSplitterFactory factory = new TextSplitterFactory(map);

		TextSplitter result = factory.getSplitter("nonexistent");
		assertSame(tokenSplitter, result);
	}

	@Test
	void testGetSplitter_allTypes() {
		TextSplitter tokenSplitter = mock(TextSplitter.class);
		TextSplitter recursiveSplitter = mock(TextSplitter.class);
		Map<String, TextSplitter> map = new HashMap<>();
		map.put("token", tokenSplitter);
		map.put("recursive", recursiveSplitter);

		TextSplitterFactory factory = new TextSplitterFactory(map);

		assertSame(tokenSplitter, factory.getSplitter("token"));
		assertSame(recursiveSplitter, factory.getSplitter("recursive"));
	}

}
