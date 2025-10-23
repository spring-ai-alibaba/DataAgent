/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.util;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * @author vlsmb
 * @since 2025/10/22
 */
public final class FluxUtil {

	private FluxUtil() {

	}

	/**
	 * 级联两个具有前后关系的Flux
	 * @param originFlux 第一个Flux
	 * @param nextFluxFunc 根据第一个Flux的聚合结果生成第二个Flux
	 * @param aggregator 聚合第一个Flux的所有数据
	 * @param preFlux 在第一个Flux前添加的信息Flux
	 * @param middleFlux 在第一个Flux和第二个Flux之间添加的信息Flux
	 * @param endFlux 在第二个Flux后添加的信息Flux
	 */
	public static <T, R> Flux<T> cascadeFlux(Flux<T> originFlux, Function<R, Flux<T>> nextFluxFunc,
			Function<Flux<T>, Mono<R>> aggregator, Flux<T> preFlux, Flux<T> middleFlux, Flux<T> endFlux) {
		// 缓存原始流避免重复执行
		Flux<T> cachedOrigin = originFlux.cache();

		// 聚合结果
		Mono<R> aggregatedResult = aggregator.apply(cachedOrigin).cache();

		// 构建完整流
		Flux<T> secondFlux = aggregatedResult.flatMapMany(nextFluxFunc);

		return preFlux.concatWith(cachedOrigin).concatWith(middleFlux).concatWith(secondFlux).concatWith(endFlux);
	}

	/**
	 * 级联两个具有前后关系的Flux
	 * @param originFlux 第一个Flux
	 * @param nextFluxFunc 根据第一个Flux的聚合结果生成第二个Flux
	 * @param aggregator 聚合第一个Flux的所有数据
	 */
	public static <T, R> Flux<T> cascadeFlux(Flux<T> originFlux, Function<R, Flux<T>> nextFluxFunc,
			Function<Flux<T>, Mono<R>> aggregator) {
		return cascadeFlux(originFlux, nextFluxFunc, aggregator, Flux.empty(), Flux.empty(), Flux.empty());
	}

}
