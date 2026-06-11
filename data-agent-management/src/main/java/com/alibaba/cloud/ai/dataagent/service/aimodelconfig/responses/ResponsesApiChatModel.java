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
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.InputItem;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.OutputItem;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.ResponsesRequest;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.ResponsesResponse;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.ResponsesUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.RetryUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 OpenAI Responses API 的 ChatModel 实现。 将 Spring AI 的 Prompt 转换为 Responses API 请求， 将
 * Responses API 的响应转换回 Spring AI 的 ChatResponse。
 * <p>
 * 设计要点：实现 ChatModel 接口后，对上层 AiModelRegistry / LlmService / 全部 16 个图节点完全透明，无需任何改动。
 */
@Slf4j
public class ResponsesApiChatModel implements ChatModel {

	private final ResponsesApi responsesApi;

	private final OpenAiChatOptions defaultOptions;

	public ResponsesApiChatModel(ResponsesApi responsesApi, OpenAiChatOptions defaultOptions) {
		this.responsesApi = responsesApi;
		this.defaultOptions = defaultOptions;
	}

	// ======================== 非流式调用 ========================

	@Override
	public ChatResponse call(Prompt prompt) {
		ResponsesRequest request = buildRequest(prompt, false);
		// 与 OpenAiChatModel 的容错行为对齐：同步调用使用统一重试模板，
		// 网络抖动或服务端瞬时错误时自动重试，避免两种协议容错能力不一致
		ResponsesResponse response = RetryUtils.DEFAULT_RETRY_TEMPLATE.execute(ctx -> responsesApi.call(request));
		return convertToChatResponse(response);
	}

	// ======================== 流式调用 ========================

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		ResponsesRequest request = buildRequest(prompt, true);

		// 将 SSE 事件转为 ChatResponse 流，与 OpenAiChatModel 的流式行为对齐：
		// - delta 事件 → 包含文本块的 ChatResponse（逐 token 推送）
		// - completed 事件 → 终包：空文本 + usage 元数据（Langfuse token 统计依赖此包）
		// - incomplete 事件 → 终包：finishReason=LENGTH
		// - error 事件 → Flux.error 向上传播，由节点重试机制接管
		return responsesApi.stream(request).concatMap(event -> switch (event.type()) {
			case DELTA -> {
				// 文本增量：构建只含文本的 ChatResponse；delta 为 null 时按空串处理，避免 NPE
				String delta = event.delta() != null ? event.delta() : "";
				Generation generation = new Generation(new AssistantMessage(delta));
				yield Flux.just(new ChatResponse(List.of(generation)));
			}
			case COMPLETED -> {
				// 完成事件：构建携带 usage 元数据的终包
				yield Flux.just(buildCompletedResponse(event.response(), "STOP"));
			}
			case INCOMPLETE -> {
				// 截断事件：finishReason 为 LENGTH
				yield Flux.just(buildCompletedResponse(event.response(), "LENGTH"));
			}
			case ERROR -> {
				// 错误事件：转为异常向上传播
				yield Flux.error(new RuntimeException("Responses API 错误: " + event.errorMessage()));
			}
		});
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	// ======================== 请求构建 ========================

	/**
	 * 将 Spring AI Prompt 转换为 Responses API 请求。 映射规则：SystemMessage → instructions
	 * 字段，UserMessage → input 数组。
	 */
	private ResponsesRequest buildRequest(Prompt prompt, boolean stream) {
		String instructions = null;
		List<InputItem> inputItems = new ArrayList<>();

		// 从 Prompt 中提取消息，分类映射到 Responses API 字段
		for (Message message : prompt.getInstructions()) {
			if (message.getMessageType() == MessageType.SYSTEM) {
				// SystemMessage 映射为 instructions（Responses API 推荐的系统提示词方式）
				instructions = message.getText();
			}
			else if (message.getMessageType() == MessageType.USER) {
				// UserMessage 映射为 input 数组项
				inputItems.add(InputItem.userMessage(message.getText()));
			}
			// AssistantMessage 和其他类型在 DataAgent 当前链路中不会出现，忽略
		}

		// 合并运行时 options 与默认 options，运行时优先。
		// 使用 ChatOptions 接口取值而非 instanceof 具体实现类：
		// ChatClient 可能传入 DefaultChatOptions 等任意实现，按接口读取保证运行时参数不被静默忽略
		String model = defaultOptions.getModel();
		Double temperature = defaultOptions.getTemperature();
		Integer maxTokens = defaultOptions.getMaxTokens();

		ChatOptions runtimeOptions = prompt.getOptions();
		if (runtimeOptions != null) {
			if (runtimeOptions.getModel() != null) {
				model = runtimeOptions.getModel();
			}
			if (runtimeOptions.getTemperature() != null) {
				temperature = runtimeOptions.getTemperature();
			}
			if (runtimeOptions.getMaxTokens() != null) {
				maxTokens = runtimeOptions.getMaxTokens();
			}
		}

		return new ResponsesRequest(model, instructions, inputItems.isEmpty() ? null : inputItems, temperature,
				maxTokens, stream);
	}

	// ======================== 响应转换 ========================

	/**
	 * 将 Responses API 完整响应转换为 ChatResponse（非流式场景）。 从 output 数组中提取 type=message 的文本内容，拼接为
	 * AssistantMessage。
	 */
	private ChatResponse convertToChatResponse(ResponsesResponse response) {
		String text = extractOutputText(response);
		String finishReason = mapFinishReason(response);

		Generation generation = new Generation(new AssistantMessage(text),
				ChatGenerationMetadata.builder().finishReason(finishReason).build());

		ChatResponseMetadata metadata = buildResponseMetadata(response);
		return new ChatResponse(List.of(generation), metadata);
	}

	/**
	 * 构建流式完成/截断事件的终包 ChatResponse。 携带 usage 元数据，与 OpenAiChatModel streamUsage(true)
	 * 的末包行为对齐。
	 * <p>
	 * 关键约束：终包文本必须为空串。completed/incomplete 事件的 response 中携带完整 output 全文， 而上层 FluxUtil
	 * 会对流中每个 chunk 的文本做 append 聚合——若此处再返回全文， 聚合结果会出现"全部 delta + 整段全文"的重复，破坏 SQL/JSON 解析。
	 */
	private ChatResponse buildCompletedResponse(ResponsesResponse response, String finishReason) {
		Generation generation = new Generation(new AssistantMessage(""),
				ChatGenerationMetadata.builder().finishReason(finishReason).build());

		ChatResponseMetadata metadata = buildResponseMetadata(response);
		return new ChatResponse(List.of(generation), metadata);
	}

	/**
	 * 从 ResponsesResponse 的 output 数组中提取所有文本内容并拼接。 只处理 type=message 且 content 中
	 * type=output_text 的内容块。
	 */
	private String extractOutputText(ResponsesResponse response) {
		if (response == null || response.output() == null) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		for (OutputItem outputItem : response.output()) {
			// 只处理 message 类型的输出项
			if ("message".equals(outputItem.type()) && outputItem.content() != null) {
				for (ContentPart part : outputItem.content()) {
					// 只提取 output_text 类型的文本内容
					if ("output_text".equals(part.type()) && part.text() != null) {
						sb.append(part.text());
					}
				}
			}
		}
		return sb.toString();
	}

	/**
	 * 根据 Responses API 的 status 映射 finishReason。 completed → STOP；incomplete +
	 * max_output_tokens → LENGTH；failed → ERROR
	 */
	private String mapFinishReason(ResponsesResponse response) {
		if (response == null || response.status() == null) {
			return "STOP";
		}

		return switch (response.status()) {
			case "completed" -> "STOP";
			case "incomplete" -> {
				// 检查截断原因
				if (response.incompleteDetails() != null
						&& "max_output_tokens".equals(response.incompleteDetails().reason())) {
					yield "LENGTH";
				}
				yield "STOP";
			}
			case "failed" -> "ERROR";
			default -> "STOP";
		};
	}

	/**
	 * 构建包含 usage 统计的响应元数据。 将 Responses API 的 input_tokens/output_tokens 映射为 Spring AI 的
	 * promptTokens/completionTokens。
	 */
	private ChatResponseMetadata buildResponseMetadata(ResponsesResponse response) {
		ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder();
		if (response != null) {
			if (response.id() != null) {
				builder.id(response.id());
			}
			if (response.model() != null) {
				builder.model(response.model());
			}
			// usage 映射：input_tokens → promptTokens，output_tokens → completionTokens
			if (response.usage() != null) {
				ResponsesUsage u = response.usage();
				builder.usage(new DefaultUsage(u.inputTokens() != null ? u.inputTokens() : 0,
						u.outputTokens() != null ? u.outputTokens() : 0,
						u.totalTokens() != null ? u.totalTokens() : 0));
			}
		}
		return builder.build();
	}

}
