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

import com.alibaba.cloud.ai.dataagent.service.langfuse.LangfuseService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.TRACE_THREAD_ID;
import static org.junit.jupiter.api.Assertions.*;

class FluxUtilTest {

	@Test
	void cascadeFlux_simple_concatenatesFluxes() {
		Flux<String> origin = Flux.just("a", "b");
		Function<String, Flux<String>> nextFunc = combined -> Flux.just(combined.toUpperCase());
		Function<Flux<String>, Mono<String>> aggregator = flux -> flux
			.collect(StringBuilder::new, StringBuilder::append)
			.map(StringBuilder::toString);

		Flux<String> result = FluxUtil.cascadeFlux(origin, nextFunc, aggregator);

		StepVerifier.create(result).expectNext("a").expectNext("b").expectNext("AB").verifyComplete();
	}

	@Test
	void cascadeFlux_withPreMiddleEnd_concatenatesAll() {
		Flux<String> origin = Flux.just("data");
		Flux<String> pre = Flux.just("pre");
		Flux<String> middle = Flux.just("mid");
		Flux<String> end = Flux.just("end");

		Function<String, Flux<String>> nextFunc = combined -> Flux.just("next:" + combined);
		Function<Flux<String>, Mono<String>> aggregator = flux -> flux
			.collect(StringBuilder::new, StringBuilder::append)
			.map(StringBuilder::toString);

		Flux<String> result = FluxUtil.cascadeFlux(origin, nextFunc, aggregator, pre, middle, end);

		StepVerifier.create(result)
			.expectNext("pre")
			.expectNext("data")
			.expectNext("mid")
			.expectNext("next:data")
			.expectNext("end")
			.verifyComplete();
	}

	@Test
	void cascadeFlux_emptyOrigin_stillRunsPreMiddleEnd() {
		Flux<String> origin = Flux.empty();
		Flux<String> pre = Flux.just("pre");
		Flux<String> middle = Flux.just("mid");
		Flux<String> end = Flux.just("end");

		Function<String, Flux<String>> nextFunc = combined -> Flux.just("next:" + combined);
		Function<Flux<String>, Mono<String>> aggregator = flux -> flux
			.collect(StringBuilder::new, StringBuilder::append)
			.map(StringBuilder::toString);

		Flux<String> result = FluxUtil.cascadeFlux(origin, nextFunc, aggregator, pre, middle, end);

		StepVerifier.create(result)
			.expectNext("pre")
			.expectNext("mid")
			.expectNext("next:")
			.expectNext("end")
			.verifyComplete();
	}

	@Test
	void cascadeFlux_withEmptyPreMiddleEnd_justOriginAndNext() {
		Flux<Integer> origin = Flux.just(1, 2, 3);
		Function<Integer, Flux<Integer>> nextFunc = sum -> Flux.just(sum * 10);
		Function<Flux<Integer>, Mono<Integer>> aggregator = flux -> flux.reduce(0, Integer::sum);

		Flux<Integer> result = FluxUtil.cascadeFlux(origin, nextFunc, aggregator);

		StepVerifier.create(result).expectNext(1).expectNext(2).expectNext(3).expectNext(60).verifyComplete();
	}

	@Test
	void cascadeFlux_originError_propagatesError() {
		Flux<String> origin = Flux.error(new RuntimeException("origin error"));
		Function<String, Flux<String>> nextFunc = s -> Flux.just("next");
		Function<Flux<String>, Mono<String>> aggregator = flux -> flux
			.collect(StringBuilder::new, StringBuilder::append)
			.map(StringBuilder::toString);

		Flux<String> result = FluxUtil.cascadeFlux(origin, nextFunc, aggregator);

		StepVerifier.create(result).expectError(RuntimeException.class).verify();
	}

	@Test
	void cascadeFlux_nextFluxFuncError_propagatesError() {
		Flux<String> origin = Flux.just("a");
		Function<String, Flux<String>> nextFunc = s -> Flux.error(new RuntimeException("next error"));
		Function<Flux<String>, Mono<String>> aggregator = flux -> flux
			.collect(StringBuilder::new, StringBuilder::append)
			.map(StringBuilder::toString);

		Flux<String> result = FluxUtil.cascadeFlux(origin, nextFunc, aggregator);

		StepVerifier.create(result).expectNext("a").expectError(RuntimeException.class).verify();
	}

	@Test
	void cascadeFlux_singleElement_worksCorrectly() {
		Flux<String> origin = Flux.just("single");
		Function<String, Flux<String>> nextFunc = s -> Flux.just("processed:" + s);
		Function<Flux<String>, Mono<String>> aggregator = flux -> flux.next();

		Flux<String> result = FluxUtil.cascadeFlux(origin, nextFunc, aggregator);

		StepVerifier.create(result).expectNext("single").expectNext("processed:single").verifyComplete();
	}

	@Nested
	@DisplayName("Token accumulation with completion-id dedup")
	class TokenAccumulation {

		private static final String THREAD_ID = "flux-util-test-thread";

		@Test
		@DisplayName("Duplicate completion id counts usage only once (STOP chunk + usage-only chunk)")
		void duplicateCompletionId_countsOnce() {
			long[] tokens = runAndTake(THREAD_ID, contentChunk("hello"), usageChunk("chatcmpl-1", 10, 5),
					usageChunk("chatcmpl-1", 10, 5));

			assertArrayEquals(new long[] { 10, 5 }, tokens);
		}

		@Test
		@DisplayName("Distinct completion ids (e.g. a new model call after tool execution) are each counted once")
		void distinctCompletionIds_countEachOnce() {
			long[] tokens = runAndTake(THREAD_ID, usageChunk("chatcmpl-1", 10, 5), usageChunk("chatcmpl-1", 10, 5),
					usageChunk("chatcmpl-2", 20, 8), usageChunk("chatcmpl-2", 20, 8));

			assertArrayEquals(new long[] { 30, 13 }, tokens);
		}

		@Test
		@DisplayName("Chunks without a completion id fall back to counting every usage-bearing chunk")
		void nullCompletionId_alwaysCounted() {
			long[] tokens = runAndTake(THREAD_ID, usageChunk(null, 10, 5), usageChunk(null, 10, 5));

			assertArrayEquals(new long[] { 20, 10 }, tokens);
		}

		@Test
		@DisplayName("Chunks with zero-token usage are ignored")
		void zeroUsage_isIgnored() {
			long[] tokens = runAndTake(THREAD_ID, usageChunk("chatcmpl-1", 0, 0));

			assertArrayEquals(new long[] { 0, 0 }, tokens);
		}

		@Test
		@DisplayName("Missing TRACE_THREAD_ID in state neither accumulates nor throws")
		void missingThreadId_doesNotAccumulate() {
			OverAllState state = new OverAllState();
			Flux<ChatResponse> source = Flux.just(usageChunk("chatcmpl-1", 10, 5), usageChunk("chatcmpl-1", 10, 5));

			assertDoesNotThrow(() -> FluxUtil
				.createStreamingGenerator(DummyNode.class, state, source, Flux.empty(), Flux.empty(),
						text -> Map.of("result", text))
				.collectList()
				.block());
		}

	}

	static class DummyNode implements NodeAction {

		@Override
		public Map<String, Object> apply(OverAllState state) {
			return Map.of();
		}

	}

	/**
	 * 将 chunk 流跑过 {@link FluxUtil#createStreamingGenerator}，并取出节点级 token 累加器的计数
	 */
	private static long[] runAndTake(String threadId, ChatResponse... chunks) {
		OverAllState state = new OverAllState();
		state.updateState(Map.of(TRACE_THREAD_ID, threadId));
		LangfuseService.registerActiveAccumulator(threadId);
		try {
			FluxUtil
				.createStreamingGenerator(DummyNode.class, state, Flux.just(chunks), Flux.empty(), Flux.empty(),
						text -> Map.of("result", text))
				.collectList()
				.block();
			long[] tokens = LangfuseService.takeActiveAccumulator(threadId);
			assertNotNull(tokens);
			return tokens;
		}
		finally {
			LangfuseService.discardAccumulators(threadId);
		}
	}

	private static ChatResponse contentChunk(String text) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
	}

	private static ChatResponse usageChunk(String id, int promptTokens, int completionTokens) {
		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
			.id(id)
			.usage(new DefaultUsage(promptTokens, completionTokens))
			.build();
		return new ChatResponse(List.of(new Generation(new AssistantMessage(""))), metadata);
	}

}
