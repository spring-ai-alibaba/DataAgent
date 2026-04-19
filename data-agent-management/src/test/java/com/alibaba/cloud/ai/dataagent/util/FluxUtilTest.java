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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.Function;

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

}
