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
package com.alibaba.cloud.ai.dataagent.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ChatApiProtocol 协议解析单元测试。 重点回归：非法值必须抛异常拦截在保存入口， 不能静默落入默认协议分支导致用户配置悄悄失效。
 */
class ChatApiProtocolTest {

	@Test
	void fromCode_resolvesStandardValues() {
		assertEquals(ChatApiProtocol.CHAT_COMPLETIONS, ChatApiProtocol.fromCode("CHAT_COMPLETIONS"));
		assertEquals(ChatApiProtocol.RESPONSES, ChatApiProtocol.fromCode("RESPONSES"));
	}

	@Test
	void fromCode_ignoresCase() {
		// 与 DynamicModelFactory 的 equalsIgnoreCase 容忍口径一致
		assertEquals(ChatApiProtocol.RESPONSES, ChatApiProtocol.fromCode("responses"));
		assertEquals(ChatApiProtocol.CHAT_COMPLETIONS, ChatApiProtocol.fromCode("chat_completions"));
	}

	@Test
	void fromCode_rejectsUnknownValue() {
		// 拼写错误（如漏掉 S）必须报错，而不是静默回退默认协议
		assertThrows(IllegalArgumentException.class, () -> ChatApiProtocol.fromCode("RESPONSE"));
		assertThrows(IllegalArgumentException.class, () -> ChatApiProtocol.fromCode(""));
		assertThrows(IllegalArgumentException.class, () -> ChatApiProtocol.fromCode(null));
	}

}
