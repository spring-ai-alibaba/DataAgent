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

package com.alibaba.cloud.ai.dataagent.common.util;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.FluxConverter;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
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

	/**
	 * Quickly create streaming generator with start and end messages
	 * @param nodeClass node class
	 * @param state state
	 * @param startMessage start message
	 * @param completionMessage completion message
	 * @param resultMapper result mapping function
	 * @param sourceFlux source data stream
	 * @return Flux instance
	 */
	public static Flux<GraphResponse<StreamingOutput>> createStreamingGeneratorWithMessages(
			Class<? extends NodeAction> nodeClass, OverAllState state, String startMessage, String completionMessage,
			Function<String, Map<String, Object>> resultMapper, Flux<ChatResponse> sourceFlux) {
		String nodeName = nodeClass.getSimpleName();

		// Used to collect actual processing results
		final StringBuilder collectedResult = new StringBuilder();

		// wrapperFlux
		Flux<ChatResponse> startFlux = (startMessage == null ? Flux.empty()
				: Flux.just(ChatResponseUtil.createResponse(startMessage)));
		Flux<ChatResponse> wrapperFlux = startFlux.concatWith(sourceFlux.doOnNext(chatResponse -> {
			String text = ChatResponseUtil.getText(chatResponse);
			collectedResult.append(text);
		}));
		if (completionMessage != null) {
			wrapperFlux = wrapperFlux.concatWith(Flux.just(ChatResponseUtil.createResponse(completionMessage)));
		}
		return FluxConverter.builder()
			.startingNode(nodeName)
			.startingState(state)
			.mapResult(r -> resultMapper.apply(collectedResult.toString()))
			.build(wrapperFlux);
	}

	public static Flux<GraphResponse<StreamingOutput>> createStreamingGeneratorWithMessages(
			Class<? extends NodeAction> nodeClass, OverAllState state,
			Function<String, Map<String, Object>> resultMapper, Flux<ChatResponse> sourceFlux) {
		return createStreamingGeneratorWithMessages(nodeClass, state, null, null, resultMapper, sourceFlux);
	}

	/**
	 * create streaming generator with start and end flux
	 * @param nodeClass node class
	 * @param state state
	 * @param sourceFlux source data stream
	 * @param preFlux preFlux
	 * @param sufFlux sufFlux
	 * @param sourceMapper result of <code>sourceFlux</code> mapping function
	 * @return Flux instance
	 */
	public static Flux<GraphResponse<StreamingOutput>> createStreamingGenerator(Class<? extends NodeAction> nodeClass,
			OverAllState state, Flux<ChatResponse> sourceFlux, Flux<ChatResponse> preFlux, Flux<ChatResponse> sufFlux,
			Function<String, Map<String, Object>> sourceMapper) {
		String nodeName = nodeClass.getSimpleName();
		// Used to collect actual processing results
		final StringBuilder collectedResult = new StringBuilder();
		sourceFlux = sourceFlux.doOnNext(r -> collectedResult.append(ChatResponseUtil.getText(r)));
		return FluxConverter.builder()
			.startingNode(nodeName)
			.startingState(state)
			.mapResult(r -> sourceMapper.apply(collectedResult.toString()))
			.build(Flux.concat(preFlux, sourceFlux, sufFlux));
	}

}
