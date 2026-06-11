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
import org.springframework.ai.retry.RetryUtils;
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
 * 形式定义，覆盖 DataAgent 实际用到的字段子集（含 function calling）。
 * <p>
 * 不传 store、previous_response_id 等会话状态字段，DataAgent 自行管理对话历史。
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

		// 构建同步 RestClient（用于阻塞调用）。
		// 必须挂载 DEFAULT_RESPONSE_ERROR_HANDLER：它把 4xx 映射为 NonTransientAiException、
		// 5xx 映射为 TransientAiException，而上层 DEFAULT_RETRY_TEMPLATE 只对 TransientAiException
		// 重试——
		// 若不挂载，HTTP 错误抛出的是 RestClientResponseException，重试模板会直接放行，重试形同虚设
		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeader("Authorization", "Bearer " + apiKey)
			.defaultHeader("Content-Type", "application/json")
			.defaultStatusHandler(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)
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
		ResponsesRequest syncRequest = request.withStream(false);

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
		ResponsesRequest streamRequest = request.withStream(true);

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
			log.warn("解析 Responses API SSE 事件失败，跳过: {}", abbreviate(data), e);
			return null;
		}
	}

	/** 日志输出时保留的 SSE 负载最大长度 */
	private static final int LOG_PAYLOAD_MAX_LENGTH = 500;

	/**
	 * 截断过长的 SSE 负载用于日志输出。 事件体可能携带完整模型输出，全量打印会造成日志膨胀并把对话内容写进日志， 截断后保留的前缀足够定位事件类型与结构问题。
	 */
	private static String abbreviate(String data) {
		if (data == null || data.length() <= LOG_PAYLOAD_MAX_LENGTH) {
			return data;
		}
		return data.substring(0, LOG_PAYLOAD_MAX_LENGTH) + "...(已截断，总长度 " + data.length() + ")";
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
	 * Responses API 请求体。 只包含 DataAgent 实际需要的字段，不传 store/previous_response_id 等。 tools
	 * 字段用于声明可调用的 function 工具（AgentScope 链路依赖）。
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ResponsesRequest(String model, String instructions, List<InputItem> input, List<FunctionTool> tools,
			Double temperature, @JsonProperty("max_output_tokens") Integer maxOutputTokens, Boolean stream) {

		/**
		 * 返回仅覆盖 stream 标志的副本。 call/stream 入口统一经此方法强制流式开关， 避免在多处手工重建
		 * record——后续给请求体加字段时只需改这一处，防止字段漏拷
		 */
		public ResponsesRequest withStream(boolean stream) {
			return new ResponsesRequest(model, instructions, input, tools, temperature, maxOutputTokens, stream);
		}
	}

	/**
	 * function 工具声明：对应请求 tools 数组元素。 Responses API 采用扁平结构（name/description/parameters 与
	 * type 平级），与 Chat Completions 的嵌套 function 结构不同。
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FunctionTool(String type, String name, String description, Map<String, Object> parameters) {

		/**
		 * 构造 function 类型工具声明
		 */
		public static FunctionTool function(String name, String description, Map<String, Object> parameters) {
			return new FunctionTool("function", name, description, parameters);
		}
	}

	/**
	 * 输入项：对应 Responses API 的 input 数组元素。 一个 record 承载三种形态（按 type 区分）： 普通消息（type 为空，含
	 * role+content）、function_call（助手历史中的工具调用）、 function_call_output（工具执行结果，模型靠 callId
	 * 与调用配对）。
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record InputItem(String type, String role, List<ContentPart> content, @JsonProperty("call_id") String callId,
			String name, String arguments, String output) {

		/**
		 * 构造 user 消息（content 类型为 input_text）
		 */
		public static InputItem userMessage(String text) {
			return new InputItem(null, "user", List.of(new ContentPart("input_text", text)), null, null, null, null);
		}

		/**
		 * 构造 assistant 历史消息（content 类型为 output_text，与 user 消息不同）
		 */
		public static InputItem assistantMessage(String text) {
			return new InputItem(null, "assistant", List.of(new ContentPart("output_text", text)), null, null, null,
					null);
		}

		/**
		 * 构造助手历史中的工具调用项，多轮 agent 循环回放历史时必须携带， 否则模型无法将 function_call_output 与调用配对
		 */
		public static InputItem functionCall(String callId, String name, String arguments) {
			return new InputItem("function_call", null, null, callId, name, arguments, null);
		}

		/**
		 * 构造工具执行结果项
		 */
		public static InputItem functionCallOutput(String callId, String output) {
			return new InputItem("function_call_output", null, null, callId, null, null, output);
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
	 * 输出项：对应 output 数组元素。 type 为 "message" 时包含 content 数组（文本回复）； type 为 "function_call"
	 * 时包含 callId/name/arguments（模型发起的工具调用）。
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record OutputItem(String type, String role, List<ContentPart> content,
			@JsonProperty("call_id") String callId, String name, String arguments) {
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
