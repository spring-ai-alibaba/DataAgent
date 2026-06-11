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

import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.ContentPart;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.IncompleteDetails;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.OutputItem;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.ResponsesRequest;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.ResponsesResponse;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.ResponsesUsage;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.StreamEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ResponsesApiChatModel 协议转换单元测试。 覆盖 Prompt → Responses 请求映射、非流式响应转换、
 * 流式事件转换（重点回归：终包必须为空文本，避免上层聚合时全文重复）。
 */
class ResponsesApiChatModelTest {

	private ResponsesApi responsesApi;

	private ResponsesApiChatModel chatModel;

	@BeforeEach
	void setUp() {
		responsesApi = mock(ResponsesApi.class);
		OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
			.model("default-model")
			.temperature(0.1)
			.maxTokens(1000)
			.build();
		chatModel = new ResponsesApiChatModel(responsesApi, defaultOptions);
	}

	/**
	 * 构造一个携带文本输出和 usage 的完整响应
	 */
	private ResponsesResponse completedResponse(String text) {
		OutputItem item = new OutputItem("message", "assistant", List.of(new ContentPart("output_text", text)));
		return new ResponsesResponse("resp_1", "gpt-test", "completed", List.of(item), new ResponsesUsage(10, 5, 15),
				null, null);
	}

	// ======================== 请求映射 ========================

	@Test
	void call_mapsSystemToInstructionsAndUserToInput() {
		when(responsesApi.call(any())).thenReturn(completedResponse("ok"));

		Prompt prompt = new Prompt(List.of(new SystemMessage("you are helpful"), new UserMessage("hi")));
		chatModel.call(prompt);

		ArgumentCaptor<ResponsesRequest> captor = ArgumentCaptor.forClass(ResponsesRequest.class);
		verify(responsesApi).call(captor.capture());
		ResponsesRequest request = captor.getValue();

		assertEquals("you are helpful", request.instructions());
		assertEquals(1, request.input().size());
		assertEquals("user", request.input().get(0).role());
		assertEquals("hi", request.input().get(0).content().get(0).text());
		// 未指定运行时 options 时使用默认配置
		assertEquals("default-model", request.model());
		assertEquals(0.1, request.temperature());
		assertEquals(1000, request.maxOutputTokens());
	}

	@Test
	void call_runtimeOptionsOverrideDefaults_viaChatOptionsInterface() {
		when(responsesApi.call(any())).thenReturn(completedResponse("ok"));

		// 使用通用 ChatOptions 实现（非 OpenAiChatOptions），验证按接口取值不会被静默忽略
		ChatOptions runtimeOptions = ChatOptions.builder()
			.model("runtime-model")
			.temperature(0.9)
			.maxTokens(123)
			.build();
		Prompt prompt = new Prompt(List.of(new UserMessage("hi")), runtimeOptions);
		chatModel.call(prompt);

		ArgumentCaptor<ResponsesRequest> captor = ArgumentCaptor.forClass(ResponsesRequest.class);
		verify(responsesApi).call(captor.capture());
		ResponsesRequest request = captor.getValue();

		assertEquals("runtime-model", request.model());
		assertEquals(0.9, request.temperature());
		assertEquals(123, request.maxOutputTokens());
	}

	// ======================== 非流式响应转换 ========================

	@Test
	void call_extractsTextAndUsage() {
		when(responsesApi.call(any())).thenReturn(completedResponse("hello world"));

		ChatResponse response = chatModel.call(new Prompt(List.of(new UserMessage("hi"))));

		assertEquals("hello world", response.getResult().getOutput().getText());
		assertEquals("STOP", response.getResult().getMetadata().getFinishReason());
		assertEquals(10, response.getMetadata().getUsage().getPromptTokens());
		assertEquals(5, response.getMetadata().getUsage().getCompletionTokens());
	}

	@Test
	void call_incompleteWithMaxTokens_mapsToLengthFinishReason() {
		OutputItem item = new OutputItem("message", "assistant", List.of(new ContentPart("output_text", "partial")));
		ResponsesResponse incomplete = new ResponsesResponse("resp_2", "gpt-test", "incomplete", List.of(item), null,
				new IncompleteDetails("max_output_tokens"), null);
		when(responsesApi.call(any())).thenReturn(incomplete);

		ChatResponse response = chatModel.call(new Prompt(List.of(new UserMessage("hi"))));

		assertEquals("partial", response.getResult().getOutput().getText());
		assertEquals("LENGTH", response.getResult().getMetadata().getFinishReason());
	}

	// ======================== 流式响应转换 ========================

	@Test
	void stream_terminalChunkMustBeEmptyText_toAvoidDuplication() {
		// completed 事件的 response 中携带完整全文；上层 FluxUtil 会对每个 chunk 的文本做 append 聚合，
		// 因此终包文本必须为空串，否则聚合结果为"全部 delta + 整段全文"的重复（P0 回归用例）
		when(responsesApi.stream(any())).thenReturn(Flux.just(new StreamEvent(StreamEvent.Type.DELTA, "Hello", null, null),
				new StreamEvent(StreamEvent.Type.DELTA, " world", null, null),
				new StreamEvent(StreamEvent.Type.COMPLETED, null, completedResponse("Hello world"), null)));

		List<ChatResponse> chunks = chatModel.stream(new Prompt(List.of(new UserMessage("hi")))).collectList().block();

		assertNotNull(chunks);
		assertEquals(3, chunks.size());
		assertEquals("Hello", chunks.get(0).getResult().getOutput().getText());
		assertEquals(" world", chunks.get(1).getResult().getOutput().getText());
		// 终包：空文本 + finishReason + usage
		assertEquals("", chunks.get(2).getResult().getOutput().getText());
		assertEquals("STOP", chunks.get(2).getResult().getMetadata().getFinishReason());
		assertEquals(10, chunks.get(2).getMetadata().getUsage().getPromptTokens());

		// 模拟上层聚合行为：全部 chunk 文本拼接后应恰好等于一遍全文
		StringBuilder aggregated = new StringBuilder();
		chunks.forEach(c -> aggregated.append(c.getResult().getOutput().getText()));
		assertEquals("Hello world", aggregated.toString());
	}

	@Test
	void stream_incompleteEvent_mapsToLengthTerminal() {
		ResponsesResponse incomplete = new ResponsesResponse("resp_3", "gpt-test", "incomplete", null, null,
				new IncompleteDetails("max_output_tokens"), null);
		when(responsesApi.stream(any()))
			.thenReturn(Flux.just(new StreamEvent(StreamEvent.Type.DELTA, "part", null, null),
					new StreamEvent(StreamEvent.Type.INCOMPLETE, null, incomplete, null)));

		List<ChatResponse> chunks = chatModel.stream(new Prompt(List.of(new UserMessage("hi")))).collectList().block();

		assertNotNull(chunks);
		assertEquals(2, chunks.size());
		assertEquals("", chunks.get(1).getResult().getOutput().getText());
		assertEquals("LENGTH", chunks.get(1).getResult().getMetadata().getFinishReason());
	}

	@Test
	void stream_nullDelta_treatedAsEmptyText() {
		// delta 字段缺失时按空串处理，不应抛 NPE
		when(responsesApi.stream(any()))
			.thenReturn(Flux.just(new StreamEvent(StreamEvent.Type.DELTA, null, null, null)));

		List<ChatResponse> chunks = chatModel.stream(new Prompt(List.of(new UserMessage("hi")))).collectList().block();

		assertNotNull(chunks);
		assertEquals("", chunks.get(0).getResult().getOutput().getText());
	}

	@Test
	void stream_errorEvent_propagatesAsFluxError() {
		when(responsesApi.stream(any()))
			.thenReturn(Flux.just(new StreamEvent(StreamEvent.Type.ERROR, null, null, "boom")));

		Flux<ChatResponse> flux = chatModel.stream(new Prompt(List.of(new UserMessage("hi"))));

		RuntimeException ex = assertThrows(RuntimeException.class, flux::blockLast);
		assertTrue(ex.getMessage().contains("boom"));
	}

	@Test
	void stream_requestHasStreamFlagTrue() {
		when(responsesApi.stream(any())).thenReturn(Flux.empty());

		chatModel.stream(new Prompt(List.of(new UserMessage("hi")))).collectList().block();

		ArgumentCaptor<ResponsesRequest> captor = ArgumentCaptor.forClass(ResponsesRequest.class);
		verify(responsesApi).stream(captor.capture());
		assertTrue(captor.getValue().stream());
	}

}
