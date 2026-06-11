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
package com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses;

import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.StreamEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResponsesApi SSE 事件解析单元测试。 覆盖 delta / completed / incomplete / failed / error
 * 各事件分支，以及畸形事件的容错行为。
 */
class ResponsesApiTest {

	private ResponsesApi responsesApi;

	@BeforeEach
	void setUp() {
		// 仅测试解析逻辑，不发起真实 HTTP 请求，baseUrl 等参数使用占位值
		responsesApi = new ResponsesApi("http://localhost", "test-key", null, RestClient.builder(),
				WebClient.builder());
	}

	private StreamEvent parse(String data) {
		return responsesApi.parseStreamEvent(ServerSentEvent.builder(data).build());
	}

	@Test
	void parseDeltaEvent_returnsDeltaText() {
		StreamEvent event = parse("{\"type\":\"response.output_text.delta\",\"delta\":\"Hello\"}");
		assertNotNull(event);
		assertEquals(StreamEvent.Type.DELTA, event.type());
		assertEquals("Hello", event.delta());
	}

	@Test
	void parseCompletedEvent_returnsResponseWithUsage() {
		String data = """
				{"type":"response.completed","response":{"id":"resp_1","model":"gpt-test","status":"completed",
				"output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"hi"}]}],
				"usage":{"input_tokens":10,"output_tokens":5,"total_tokens":15}}}
				""";
		StreamEvent event = parse(data);
		assertNotNull(event);
		assertEquals(StreamEvent.Type.COMPLETED, event.type());
		assertEquals("resp_1", event.response().id());
		assertEquals(10, event.response().usage().inputTokens());
		assertEquals(5, event.response().usage().outputTokens());
	}

	@Test
	void parseCompletedEvent_parsesFunctionCallOutputItem() {
		// completed 事件的 output 中包含 function_call 项时，call_id/name/arguments 必须正确反序列化，
		// 否则 tool calling 链路无法将调用与结果配对
		String data = """
				{"type":"response.completed","response":{"id":"resp_fc","status":"completed",
				"output":[{"type":"function_call","id":"fc_1","call_id":"call_1","name":"search",
				"arguments":"{\\"q\\":\\"天气\\"}"}]}}
				""";
		StreamEvent event = parse(data);
		assertNotNull(event);
		assertEquals(StreamEvent.Type.COMPLETED, event.type());
		assertEquals("function_call", event.response().output().get(0).type());
		assertEquals("call_1", event.response().output().get(0).callId());
		assertEquals("search", event.response().output().get(0).name());
		assertEquals("{\"q\":\"天气\"}", event.response().output().get(0).arguments());
	}

	@Test
	void parseIncompleteEvent_returnsIncompleteType() {
		String data = """
				{"type":"response.incomplete","response":{"id":"resp_2","status":"incomplete",
				"incomplete_details":{"reason":"max_output_tokens"}}}
				""";
		StreamEvent event = parse(data);
		assertNotNull(event);
		assertEquals(StreamEvent.Type.INCOMPLETE, event.type());
		assertEquals("max_output_tokens", event.response().incompleteDetails().reason());
	}

	@Test
	void parseFailedEvent_returnsErrorWithMessage() {
		String data = """
				{"type":"response.failed","response":{"id":"resp_3","status":"failed",
				"error":{"type":"server_error","message":"boom","code":"500"}}}
				""";
		StreamEvent event = parse(data);
		assertNotNull(event);
		assertEquals(StreamEvent.Type.ERROR, event.type());
		assertEquals("boom", event.errorMessage());
	}

	@Test
	void parseErrorEvent_returnsErrorWithMessage() {
		StreamEvent event = parse("{\"type\":\"error\",\"error\":{\"message\":\"rate limited\"}}");
		assertNotNull(event);
		assertEquals(StreamEvent.Type.ERROR, event.type());
		assertEquals("rate limited", event.errorMessage());
	}

	@Test
	void parseUnknownEventType_returnsNull() {
		// 未知事件类型（如 response.created）应静默忽略，保证向前兼容
		assertNull(parse("{\"type\":\"response.created\",\"response\":{\"id\":\"resp_4\"}}"));
	}

	@Test
	void parseMalformedJson_returnsNull() {
		// 畸形 JSON 不应抛异常中断流，应按单条事件损坏跳过
		assertNull(parse("{not valid json"));
	}

	@Test
	void parseCompletedEventWithWrongStructure_returnsNull() {
		// response 字段结构不匹配时 convertValue 抛 IllegalArgumentException，
		// 必须被捕获并跳过，不能让一条畸形事件中断整个 SSE 流（回归用例）
		assertNull(parse("{\"type\":\"response.completed\",\"response\":\"not-an-object\"}"));
	}

	@Test
	void parseEventWithoutType_returnsNull() {
		assertNull(parse("{\"delta\":\"orphan\"}"));
	}

}
