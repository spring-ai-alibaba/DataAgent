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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses API 低层 HTTP 客户端。 负责构建请求、解析非流式响应与流式 SSE 事件。 协议 DTO 以内嵌 record/class
 * 形式定义，覆盖 DataAgent 实际用到的字段子集。
 * <p>
 * 不传 store、previous_response_id、tools 等字段，DataAgent 不使用这些特性。
 */
@Slf4j
public class ResponsesApi {

	private final RestClient restClient;

	private final WebClient webClient;

	private final String responsesPath;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	public ResponsesApi(String baseUrl, String apiKey, String responsesPath, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder) {
		this.responsesPath = StringUtils.hasText(responsesPath) ? responsesPath : "/v1/responses";

		// 构建同步 RestClient（用于阻塞调用）
		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeader("Authorization", "Bearer " + apiKey)
			.defaultHeader("Content-Type", "application/json")
			.build();

		// 构建异步 WebClient（用于 SSE 流式调用）
		this.webClient = webClientBuilder.baseUrl(baseUrl)
			.defaultHeader("Authorization", "Bearer " + apiKey)
			.defaultHeader("Content-Type", "application/json")
			.build();
	}

	// ======================== 同步调用 ========================

	/**
	 * 非流式调用 Responses API，返回完整响应。
	 * @param request 请求体（stream 字段会被忽略/覆盖为 false）
	 * @return 完整的 Responses 响应
	 */
	public ResponsesResponse call(ResponsesRequest request) {
		// 确保非流式
		ResponsesRequest syncRequest = new ResponsesRequest(request.model(), request.instructions(), request.input(),
				request.temperature(), request.maxOutputTokens(), false);

		return restClient.post().uri(this.responsesPath).body(syncRequest).retrieve().body(ResponsesResponse.class);
	}

	// ======================== 流式调用 ========================

	/**
	 * 流式调用 Responses API，返回 SSE 事件流。 解析 Responses API 特有的事件类型（response.output_text.delta、
	 * response.completed 等）。 对于未知事件类型一律忽略，保证向前兼容。
	 * @param request 请求体（stream 字段会被覆盖为 true）
	 * @return SSE 事件流，每个事件已解析为 StreamEvent
	 */
	public Flux<StreamEvent> stream(ResponsesRequest request) {
		// 确保流式
		ResponsesRequest streamRequest = new ResponsesRequest(request.model(), request.instructions(), request.input(),
				request.temperature(), request.maxOutputTokens(), true);

		return webClient.post()
			.uri(this.responsesPath)
			.bodyValue(streamRequest)
			.accept(MediaType.TEXT_EVENT_STREAM)
			.retrieve()
			.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
			})
			.filter(sse -> StringUtils.hasText(sse.data()))
			.mapNotNull(this::parseStreamEvent);
	}

	/**
	 * 解析单个 SSE 事件的 data 负载。 按事件 type 分发：delta / completed / incomplete / failed / error。
	 * 未知事件类型静默忽略（返回 null 被 mapNotNull 过滤掉）。 包私有可见性用于单元测试直接覆盖各事件分支。
	 */
	StreamEvent parseStreamEvent(ServerSentEvent<String> sse) {
		String data = sse.data();
		if (!StringUtils.hasText(data)) {
			return null;
		}
		try {
			Map<String, Object> eventMap = OBJECT_MAPPER.readValue(data, Map.class);
			String type = (String) eventMap.get("type");
			if (type == null) {
				return null;
			}

			return switch (type) {
				// 文本增量：产出一个 delta 文本块
				case "response.output_text.delta" -> {
					String delta = (String) eventMap.get("delta");
					yield new StreamEvent(StreamEvent.Type.DELTA, delta, null, null);
				}
				// 响应完成：携带完整响应（含 usage）
				case "response.completed" -> {
					ResponsesResponse response = OBJECT_MAPPER.convertValue(eventMap.get("response"),
							ResponsesResponse.class);
					yield new StreamEvent(StreamEvent.Type.COMPLETED, null, response, null);
				}
				// 响应被截断（如达到 max_output_tokens）
				case "response.incomplete" -> {
					ResponsesResponse response = OBJECT_MAPPER.convertValue(eventMap.get("response"),
							ResponsesResponse.class);
					yield new StreamEvent(StreamEvent.Type.INCOMPLETE, null, response, null);
				}
				// 响应失败
				case "response.failed" -> {
					ResponsesResponse response = OBJECT_MAPPER.convertValue(eventMap.get("response"),
							ResponsesResponse.class);
					String errorMsg = extractErrorMessage(response);
					yield new StreamEvent(StreamEvent.Type.ERROR, null, response, errorMsg);
				}
				// 全局错误事件
				case "error" -> {
					String errorMsg = extractErrorFromMap(eventMap);
					yield new StreamEvent(StreamEvent.Type.ERROR, null, null, errorMsg);
				}
				// 其余事件（created、in_progress、output_item.added、output_text.done 等）一律忽略
				default -> null;
			};
		}
		catch (JsonProcessingException | IllegalArgumentException e) {
			// readValue 抛 JsonProcessingException；convertValue 结构不匹配时抛
			// IllegalArgumentException。
			// 两者都按"单条事件损坏"处理：记录日志后跳过，不让一条畸形事件中断整个 SSE 流
			log.warn("解析 Responses API SSE 事件失败，跳过: {}", data, e);
			return null;
		}
	}

	/**
	 * 从 ResponsesResponse 的 error 字段提取错误消息
	 */
	private String extractErrorMessage(ResponsesResponse response) {
		if (response != null && response.error() != null) {
			return response.error().message();
		}
		return "Responses API 调用失败（未知原因）";
	}

	/**
	 * 从原始事件 map 的 error 字段提取错误消息
	 */
	@SuppressWarnings("unchecked")
	private String extractErrorFromMap(Map<String, Object> eventMap) {
		Object error = eventMap.get("error");
		if (error instanceof Map<?, ?> errorMap) {
			Object message = errorMap.get("message");
			if (message instanceof String msg) {
				return msg;
			}
		}
		return "Responses API 返回错误事件";
	}

	// ======================== 协议 DTO ========================

	/**
	 * Responses API 请求体。 只包含 DataAgent 实际需要的字段，不传 tools/store/previous_response_id 等。
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ResponsesRequest(String model, String instructions, List<InputItem> input, Double temperature,
			@JsonProperty("max_output_tokens") Integer maxOutputTokens, Boolean stream) {
	}

	/**
	 * 输入消息项：对应 Responses API 的 input 数组元素。 DataAgent 只使用 user 角色的文本消息。
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record InputItem(String role, List<ContentPart> content) {

		/**
		 * 构造 user 消息
		 */
		public static InputItem userMessage(String text) {
			return new InputItem("user", List.of(new ContentPart("input_text", text)));
		}
	}

	/**
	 * 内容块：对应 input/output 中的 content 数组元素
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ContentPart(String type, String text) {
	}

	/**
	 * Responses API 完整响应体
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ResponsesResponse(String id, String model, String status, List<OutputItem> output,
			ResponsesUsage usage, @JsonProperty("incomplete_details") IncompleteDetails incompleteDetails,
			ResponsesError error) {
	}

	/**
	 * 输出项：对应 output 数组元素。 type 为 "message" 时包含 content 数组；DataAgent 只关注 output_text
	 * 类型的文本内容。
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record OutputItem(String type, String role, List<ContentPart> content) {
	}

	/**
	 * token 用量统计
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ResponsesUsage(@JsonProperty("input_tokens") Integer inputTokens,
			@JsonProperty("output_tokens") Integer outputTokens, @JsonProperty("total_tokens") Integer totalTokens) {
	}

	/**
	 * 截断详情（status=incomplete 时附带）
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record IncompleteDetails(String reason) {
	}

	/**
	 * 错误信息
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ResponsesError(String type, String message, String code) {
	}

	/**
	 * 流式 SSE 事件的统一封装
	 */
	public record StreamEvent(Type type, String delta, ResponsesResponse response, String errorMessage) {

		public enum Type {

			/** 文本增量 */
			DELTA,
			/** 响应完成（携带 usage） */
			COMPLETED,
			/** 响应被截断 */
			INCOMPLETE,
			/** 错误 */
			ERROR

		}
	}

}
