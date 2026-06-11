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
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.FunctionTool;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.InputItem;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.OutputItem;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.ResponsesRequest;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.ResponsesResponse;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi.ResponsesUsage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 OpenAI Responses API 的 ChatModel 实现。 将 Spring AI 的 Prompt 转换为 Responses API 请求， 将
 * Responses API 的响应转换回 Spring AI 的 ChatResponse。
 * <p>
 * 设计要点：实现 ChatModel 接口后，对上层 AiModelRegistry / LlmService / 全部 16 个图节点完全透明，无需任何改动。
 * <p>
 * Tool calling 支持（AgentScope 链路依赖）：识别 ToolCallingChatOptions 中的工具声明并映射为 tools
 * 字段；助手历史中的工具调用与工具执行结果分别映射为 function_call / function_call_output 输入项。 注意：本实现不在框架内部执行工具
 * （等价于 internalToolExecutionEnabled=false），工具调用以 AssistantMessage.toolCalls
 * 形式返回给调用方，由调用方（AgentScope runtime）负责执行并回传结果。
 */
@Slf4j
public class ResponsesApiChatModel implements ChatModel {

	/** 用于解析工具 inputSchema JSON 字符串，无状态可安全共享 */
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

		// 标记本次流是否收到过文本增量事件：个别兼容实现可能不推送 output_text.delta，
		// 文本只出现在 completed 事件的完整 output 中，此时终包需降级携带全文，否则文本整体丢失。
		// 每次订阅独立创建（defer），避免同一 Flux 被多次订阅时状态串扰
		return Flux.defer(() -> {
			AtomicBoolean deltaReceived = new AtomicBoolean(false);

			// 将 SSE 事件转为 ChatResponse 流，与 OpenAiChatModel 的流式行为对齐：
			// - delta 事件 → 包含文本块的 ChatResponse（逐 token 推送）
			// - completed 事件 → 终包：空文本 + usage 元数据（Langfuse token 统计依赖此包）
			// - incomplete 事件 → 终包：finishReason=LENGTH
			// - error 事件 → Flux.error 向上传播，由节点重试机制接管
			return responsesApi.stream(request).concatMap(event -> switch (event.type()) {
				case DELTA -> {
					// 文本增量：构建只含文本的 ChatResponse；delta 为 null 时按空串处理，避免 NPE
					deltaReceived.set(true);
					String delta = event.delta() != null ? event.delta() : "";
					Generation generation = new Generation(new AssistantMessage(delta));
					yield Flux.just(new ChatResponse(List.of(generation)));
				}
				case COMPLETED -> {
					// 完成事件：构建携带 usage 元数据的终包
					yield Flux.just(buildCompletedResponse(event.response(), "STOP", deltaReceived.get()));
				}
				case INCOMPLETE -> {
					// 截断事件：finishReason 为 LENGTH
					yield Flux.just(buildCompletedResponse(event.response(), "LENGTH", deltaReceived.get()));
				}
				case ERROR -> {
					// 错误事件：转为异常向上传播
					yield Flux.error(new RuntimeException("Responses API 错误: " + event.errorMessage()));
				}
			});
		});
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	// ======================== 请求构建 ========================

	/**
	 * 将 Spring AI Prompt 转换为 Responses API 请求。 映射规则：SystemMessage → instructions
	 * 字段；UserMessage → input 数组消息项；AssistantMessage（含历史工具调用）→ assistant 消息项 +
	 * function_call 项；ToolResponseMessage → function_call_output 项。 多轮 agent
	 * 循环回放历史时，function_call 与 function_call_output 必须成对出现，模型靠 callId 配对。
	 */
	private ResponsesRequest buildRequest(Prompt prompt, boolean stream) {
		String instructions = null;
		List<InputItem> inputItems = new ArrayList<>();

		// 从 Prompt 中提取消息，按类型分类映射到 Responses API 字段
		for (Message message : prompt.getInstructions()) {
			switch (message.getMessageType()) {
				// SystemMessage 映射为 instructions（Responses API 推荐的系统提示词方式）。
				// 多条系统消息按出现顺序拼接：instructions 只有一个字段，直接赋值覆盖会静默丢失前文
				case SYSTEM -> instructions = mergeInstructions(instructions, message.getText());
				// UserMessage 映射为 input 数组消息项
				case USER -> inputItems.add(InputItem.userMessage(message.getText()));
				// 助手历史消息：文本和工具调用分别映射
				case ASSISTANT -> appendAssistantItems((AssistantMessage) message, inputItems);
				// 工具执行结果：映射为 function_call_output 项
				case TOOL -> appendToolOutputItems((ToolResponseMessage) message, inputItems);
			}
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

		return new ResponsesRequest(model, instructions, inputItems.isEmpty() ? null : inputItems,
				buildTools(runtimeOptions), temperature, maxTokens, stream);
	}

	/**
	 * 合并多条 SystemMessage 为单个 instructions。 Responses API 只有一个 instructions 字段， 上游（如
	 * AgentScope 链路）可能注入多条系统消息，这里用空行拼接保证语义完整、顺序不变。
	 */
	private String mergeInstructions(String existing, String text) {
		if (!StringUtils.hasText(text)) {
			return existing;
		}
		return existing == null ? text : existing + "\n\n" + text;
	}

	/**
	 * 将助手历史消息拆解为 input 项。 文本部分映射为 assistant 消息项；工具调用部分逐个映射为 function_call 项，
	 * 缺失任何一个都会导致模型无法将后续的 function_call_output 与调用配对而报错。
	 */
	private void appendAssistantItems(AssistantMessage message, List<InputItem> inputItems) {
		if (StringUtils.hasText(message.getText())) {
			inputItems.add(InputItem.assistantMessage(message.getText()));
		}
		if (message.hasToolCalls()) {
			for (AssistantMessage.ToolCall toolCall : message.getToolCalls()) {
				// arguments 为空时降级为空对象，Responses API 要求该字段必须是合法 JSON 字符串
				String arguments = StringUtils.hasText(toolCall.arguments()) ? toolCall.arguments() : "{}";
				inputItems.add(InputItem.functionCall(toolCall.id(), toolCall.name(), arguments));
			}
		}
	}

	/**
	 * 将工具执行结果消息映射为 function_call_output 项，callId 必须与历史 function_call 一致
	 */
	private void appendToolOutputItems(ToolResponseMessage message, List<InputItem> inputItems) {
		for (ToolResponseMessage.ToolResponse toolResponse : message.getResponses()) {
			// responseData 为 null 时兜底空串：output 字段若被 NON_NULL 序列化策略整体省略，
			// 部分服务端会因缺少必填字段拒绝请求
			String output = toolResponse.responseData() != null ? toolResponse.responseData() : "";
			inputItems.add(InputItem.functionCallOutput(toolResponse.id(), output));
		}
	}

	/**
	 * 从 ToolCallingChatOptions 中提取工具声明并映射为 Responses API 的 tools 字段。 AgentScope 链路通过
	 * ToolCallingChatOptions 传入 toolCallbacks；非工具场景（普通 ChatOptions）返回 null，请求中不出现 tools
	 * 字段。
	 */
	private List<FunctionTool> buildTools(ChatOptions options) {
		if (!(options instanceof ToolCallingChatOptions toolOptions)) {
			return null;
		}
		// Spring AI 约定 internalToolExecutionEnabled 为 null 时默认 true（框架内部执行工具并自动循环），
		// 但本实现不支持内部执行，工具调用一律以 AssistantMessage.toolCalls 返回给调用方。
		// 对依赖内部执行语义的调用方显式告警，避免"拿到原始 toolCalls 却无人执行"的静默行为偏差
		if (ToolCallingChatOptions.isInternalToolExecutionEnabled(toolOptions)
				&& !CollectionUtils.isEmpty(toolOptions.getToolCallbacks())) {
			log.warn("ResponsesApiChatModel 不支持框架内部执行工具（internalToolExecutionEnabled={}），"
					+ "工具调用将以 toolCalls 返回给调用方自行执行", toolOptions.getInternalToolExecutionEnabled());
		}
		// 仅支持 toolCallbacks 方式声明工具；toolNames + ToolCallbackResolver 方式无法在此解析，
		// 显式告警避免工具被静默丢弃后难以排查
		if (!CollectionUtils.isEmpty(toolOptions.getToolNames())) {
			log.warn("ResponsesApiChatModel 不支持 toolNames 方式注册的工具，已忽略: {}", toolOptions.getToolNames());
		}
		List<FunctionTool> tools = new ArrayList<>();
		for (ToolCallback toolCallback : toolOptions.getToolCallbacks()) {
			ToolDefinition definition = toolCallback.getToolDefinition();
			tools.add(FunctionTool.function(definition.name(), definition.description(),
					parseInputSchema(definition.inputSchema())));
		}
		return tools.isEmpty() ? null : tools;
	}

	/**
	 * 将 ToolDefinition 的 inputSchema（JSON 字符串）解析为 Map。 Responses API 的 parameters 字段要求
	 * JSON 对象而非字符串；解析失败时降级为空参数 schema，保证工具声明本身不缺失。
	 */
	private Map<String, Object> parseInputSchema(String inputSchema) {
		if (!StringUtils.hasText(inputSchema)) {
			return Map.of("type", "object", "properties", Map.of());
		}
		try {
			return OBJECT_MAPPER.readValue(inputSchema, new TypeReference<Map<String, Object>>() {
			});
		}
		catch (JsonProcessingException e) {
			log.warn("解析工具 inputSchema 失败，降级为空参数 schema: {}", inputSchema, e);
			return Map.of("type", "object", "properties", Map.of());
		}
	}

	// ======================== 响应转换 ========================

	/**
	 * 将 Responses API 完整响应转换为 ChatResponse（非流式场景）。 从 output 数组中提取 type=message 的文本内容拼接为
	 * AssistantMessage，并提取 type=function_call 的工具调用。 存在工具调用时 finishReason 为 TOOL_CALLS，与
	 * OpenAiChatModel 行为对齐。
	 */
	private ChatResponse convertToChatResponse(ResponsesResponse response) {
		String text = extractOutputText(response);
		List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(response);
		String finishReason = toolCalls.isEmpty() ? mapFinishReason(response) : "TOOL_CALLS";

		AssistantMessage assistantMessage = AssistantMessage.builder().content(text).toolCalls(toolCalls).build();
		Generation generation = new Generation(assistantMessage,
				ChatGenerationMetadata.builder().finishReason(finishReason).build());

		ChatResponseMetadata metadata = buildResponseMetadata(response);
		return new ChatResponse(List.of(generation), metadata);
	}

	/**
	 * 构建流式完成/截断事件的终包 ChatResponse。 携带 usage 元数据，与 OpenAiChatModel streamUsage(true)
	 * 的末包行为对齐。
	 * <p>
	 * 关键约束一：正常路径下终包文本必须为空串。completed/incomplete 事件的 response 中携带完整 output 全文， 而上层
	 * FluxUtil 会对流中每个 chunk 的文本做 append 聚合——若此处再返回全文， 聚合结果会出现"全部 delta + 整段全文"的重复，破坏
	 * SQL/JSON 解析。 例外：若整个流从未收到 delta 事件（个别兼容实现不推送增量），说明文本只存在于终包的完整 output 中，
	 * 此时降级在终包携带全文，否则文本会整体丢失。
	 * <p>
	 * 关键约束二：工具调用必须在终包携带。文本会通过 delta 事件逐块推送，但 function_call 项只在 completed 事件的完整 output
	 * 中出现一次（参数增量事件被适配层忽略）；调用方（AgentScope 桥接层）对每个 chunk 都读取 toolCalls，终包单次携带完整调用即可驱动工具执行。
	 * @param deltaReceived 本次流是否已收到过文本增量事件，决定终包是否需要降级携带全文
	 */
	private ChatResponse buildCompletedResponse(ResponsesResponse response, String finishReason,
			boolean deltaReceived) {
		List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(response);
		String resolvedFinishReason = toolCalls.isEmpty() ? finishReason : "TOOL_CALLS";

		// 正常路径终包为空文本（见约束一）；从未收到 delta 且完整 output 中有文本时降级携带全文
		String text = "";
		if (!deltaReceived) {
			String fullText = extractOutputText(response);
			if (StringUtils.hasText(fullText)) {
				log.warn("Responses API 流式响应未收到任何文本增量事件，终包降级携带完整文本（长度 {}）", fullText.length());
				text = fullText;
			}
		}

		AssistantMessage assistantMessage = AssistantMessage.builder().content(text).toolCalls(toolCalls).build();
		Generation generation = new Generation(assistantMessage,
				ChatGenerationMetadata.builder().finishReason(resolvedFinishReason).build());

		ChatResponseMetadata metadata = buildResponseMetadata(response);
		return new ChatResponse(List.of(generation), metadata);
	}

	/**
	 * 从 output 数组中提取 type=function_call 的工具调用项，转为 Spring AI 的 ToolCall。 id 取
	 * call_id（而非输出项自身 id），后续 function_call_output 回传依赖它配对。
	 */
	private List<AssistantMessage.ToolCall> extractToolCalls(ResponsesResponse response) {
		if (response == null || response.output() == null) {
			return List.of();
		}
		List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
		for (OutputItem outputItem : response.output()) {
			if ("function_call".equals(outputItem.type())) {
				// arguments 缺失时降级为空对象，保证调用方 JSON 解析不报错
				String arguments = outputItem.arguments() != null ? outputItem.arguments() : "{}";
				toolCalls
					.add(new AssistantMessage.ToolCall(outputItem.callId(), "function", outputItem.name(), arguments));
			}
		}
		return toolCalls;
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
