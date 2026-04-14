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

import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 保留管理侧仍在使用的通用 Flux 编排能力。
 */
public final class FluxUtil {

	private FluxUtil() {
	}

	public static <T, R> Flux<T> cascadeFlux(Flux<T> originFlux, Function<R, Flux<T>> nextFluxFunc,
			Function<Flux<T>, Mono<R>> aggregator, Flux<T> preFlux, Flux<T> middleFlux, Flux<T> endFlux) {
		Flux<T> cachedOrigin = originFlux.cache();
		Mono<R> aggregatedResult = aggregator.apply(cachedOrigin).cache();
		Flux<T> secondFlux = aggregatedResult.flatMapMany(nextFluxFunc);
		return preFlux.concatWith(cachedOrigin).concatWith(middleFlux).concatWith(secondFlux).concatWith(endFlux);
	}

	public static <T, R> Flux<T> cascadeFlux(Flux<T> originFlux, Function<R, Flux<T>> nextFluxFunc,
			Function<Flux<T>, Mono<R>> aggregator) {
		return cascadeFlux(originFlux, nextFluxFunc, aggregator, Flux.empty(), Flux.empty(), Flux.empty());
	}

}
