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
package com.alibaba.cloud.ai.dataagent.management.util;

import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;

import static org.junit.jupiter.api.Assertions.*;

class ChatResponseUtilTest {

	@Test
	void createResponse_addsNewline() {
		ChatResponse response = ChatResponseUtil.createResponse("hello");
		String text = ChatResponseUtil.getText(response);
		assertEquals("hello\n", text);
	}

	@Test
	void createPureResponse_noNewline() {
		ChatResponse response = ChatResponseUtil.createPureResponse("hello");
		String text = ChatResponseUtil.getText(response);
		assertEquals("hello", text);
	}

	@Test
	void getText_nullResult_returnsEmptyString() {
		ChatResponse response = new ChatResponse(java.util.List.of());
		String text = ChatResponseUtil.getText(response);
		assertEquals("", text);
	}

	@Test
	void getText_validResponse_returnsText() {
		ChatResponse response = ChatResponseUtil.createPureResponse("test message");
		assertEquals("test message", ChatResponseUtil.getText(response));
	}

}
