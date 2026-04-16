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
package com.alibaba.cloud.ai.dataagent.agentscope.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/**
 * Minimal bridge that lets AgentScope reuse the project's Spring AI ChatModel.
 */
public class SpringAiAgentScopeModel extends ChatModelBase {

	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private static final String PARTIAL_TOOL_NAME = "__fragment__";

	private final org.springframework.ai.chat.model.ChatModel delegate;

	private final String modelName;

	private final Map<String, ToolCallback> toolCallbacks;

	private final ObjectMapper objectMapper;

	public SpringAiAgentScopeModel(org.springframework.ai.chat.model.ChatModel delegate, String modelName,
			Map<String, ToolCallback> toolCallbacks, ObjectMapper objectMapper) {
		this.delegate = delegate;
		this.modelName = modelName;
		this.toolCallbacks = toolCallbacks == null ? Collections.emptyMap() : Map.copyOf(toolCallbacks);
		this.objectMapper = objectMapper;
	}

	@Override
	protected Flux<ChatResponse> doStream(List<Msg> messages, List<ToolSchema> toolSchemas,
			GenerateOptions generateOptions) {
		List<Message> promptMessages = new ArrayList<>();
		for (Msg message : messages) {
			Message springMessage = toSpringMessage(message);
			if (springMessage != null) {
				promptMessages.add(springMessage);
			}
		}
		Prompt prompt = new Prompt(promptMessages, buildChatOptions(toolSchemas, generateOptions));
		return Flux.defer(() -> this.delegate.stream(prompt).map(this::toAgentScopeResponse));
	}

	@Override
	public String getModelName() {
		return this.modelName;
	}

	private Message toSpringMessage(Msg message) {
		if (message == null) {
			return null;
		}
		String text = extractMessageText(message);
		MsgRole role = message.getRole() == null ? MsgRole.USER : message.getRole();
		return switch (role) {
			case SYSTEM -> new SystemMessage(text);
			case ASSISTANT -> toAssistantMessage(message, text);
			case TOOL -> toToolResponseMessage(message, text);
			case USER -> new UserMessage(text);
		};
	}

	private AssistantMessage toAssistantMessage(Msg message, String text) {
		List<AssistantMessage.ToolCall> toolCalls = message.getContentBlocks(ToolUseBlock.class)
			.stream()
			.map(this::toSpringToolCall)
			.toList();
		Map<String, Object> metadata = message.getMetadata();
		if (toolCalls.isEmpty() && (metadata == null || metadata.isEmpty())) {
			return new AssistantMessage(text);
		}
		AssistantMessage.Builder builder = AssistantMessage.builder();
		if (StringUtils.hasText(text) || toolCalls.isEmpty()) {
			builder.content(text);
		}
		if (!toolCalls.isEmpty()) {
			builder.toolCalls(toolCalls);
		}
		if (metadata != null && !metadata.isEmpty()) {
			builder.properties(metadata);
		}
		return builder.build();
	}

	private ToolResponseMessage toToolResponseMessage(Msg message, String fallbackText) {
		List<ToolResponseMessage.ToolResponse> responses = message.getContentBlocks(ToolResultBlock.class)
			.stream()
			.map(this::toSpringToolResponse)
			.toList();
		if (responses.isEmpty()) {
			responses = List.of(new ToolResponseMessage.ToolResponse(defaultId(message.getId()),
					defaultToolName(message.getName()), fallbackText));
		}
		return ToolResponseMessage.builder().responses(responses).build();
	}

	private ToolCallingChatOptions buildChatOptions(List<ToolSchema> toolSchemas, GenerateOptions generateOptions) {
		ToolCallingChatOptions.Builder builder = ToolCallingChatOptions.builder();
		if (generateOptions != null) {
			if (StringUtils.hasText(generateOptions.getModelName())) {
				builder.model(generateOptions.getModelName());
			}
			if (generateOptions.getTemperature() != null) {
				builder.temperature(generateOptions.getTemperature().doubleValue());
			}
			if (generateOptions.getMaxTokens() != null) {
				builder.maxTokens(generateOptions.getMaxTokens());
			}
			if (generateOptions.getTopP() != null) {
				builder.topP(generateOptions.getTopP().doubleValue());
			}
			if (generateOptions.getTopK() != null) {
				builder.topK(generateOptions.getTopK());
			}
			if (generateOptions.getFrequencyPenalty() != null) {
				builder.frequencyPenalty(generateOptions.getFrequencyPenalty().doubleValue());
			}
			if (generateOptions.getPresencePenalty() != null) {
				builder.presencePenalty(generateOptions.getPresencePenalty().doubleValue());
			}
		}

		List<ToolCallback> selectedCallbacks = resolveToolCallbacks(toolSchemas);
		if (!selectedCallbacks.isEmpty()) {
			builder.toolCallbacks(selectedCallbacks.toArray(ToolCallback[]::new));
			builder.internalToolExecutionEnabled(false);
		}
		return builder.build();
	}

	private List<ToolCallback> resolveToolCallbacks(List<ToolSchema> toolSchemas) {
		if (toolSchemas == null || toolSchemas.isEmpty()) {
			return List.of();
		}
		List<ToolCallback> selectedCallbacks = new ArrayList<>();
		LinkedHashSet<String> selectedNames = new LinkedHashSet<>();
		for (ToolSchema toolSchema : toolSchemas) {
			if (toolSchema == null || !StringUtils.hasText(toolSchema.getName())) {
				continue;
			}
			String toolName = toolSchema.getName();
			if (!selectedNames.add(toolName)) {
				continue;
			}
			ToolCallback toolCallback = toolCallbacks.get(toolName);
			selectedCallbacks.add(toolCallback != null ? toolCallback : new ToolSchemaBackedToolCallback(toolSchema,
					objectMapper));
		}
		return selectedCallbacks;
	}

	private static final class ToolSchemaBackedToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		private ToolSchemaBackedToolCallback(ToolSchema toolSchema, ObjectMapper objectMapper) {
			this.toolDefinition = ToolDefinition.builder()
				.name(toolSchema.getName())
				.description(defaultDescription(toolSchema.getDescription()))
				.inputSchema(serializeSchema(toolSchema, objectMapper))
				.build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			return "{\"error\":\"Tool execution is delegated to AgentScope runtime.\"}";
		}

		private static String serializeSchema(ToolSchema toolSchema, ObjectMapper objectMapper) {
			Map<String, Object> parameters = toolSchema.getParameters() == null ? Map.of("type", "object",
					"properties", Map.of()) : toolSchema.getParameters();
			try {
				return objectMapper.writeValueAsString(parameters);
			}
			catch (Exception ex) {
				return "{\"type\":\"object\",\"properties\":{}}";
			}
		}

		private static String defaultDescription(String description) {
			return StringUtils.hasText(description) ? description : "";
		}

	}

	private ChatResponse toAgentScopeResponse(org.springframework.ai.chat.model.ChatResponse response) {
		org.springframework.ai.chat.model.Generation generation = response == null ? null : response.getResult();
		AssistantMessage output = generation == null ? null : generation.getOutput();
		String text = output == null ? "" : defaultText(output.getText());
		List<ContentBlock> contentBlocks = new ArrayList<>();
		String reasoningContent = extractReasoningContent(output);
		Map<String, Object> thinkingMetadata = extractThinkingMetadata(output);
		if (StringUtils.hasText(reasoningContent) || thinkingMetadata != null) {
			contentBlocks.add(
					ThinkingBlock.builder().thinking(defaultText(reasoningContent)).metadata(thinkingMetadata).build());
		}
		if (StringUtils.hasText(text)) {
			contentBlocks.add(TextBlock.builder().text(text).build());
		}
		if (output != null && output.hasToolCalls()) {
			output.getToolCalls().stream().map(this::toToolUseBlock).forEach(contentBlocks::add);
		}
		Map<String, Object> metadata = new LinkedHashMap<>();
		String responseId = null;
		String finishReason = null;
		ChatUsage usage = null;
		if (response != null && response.getMetadata() != null) {
			responseId = response.getMetadata().getId();
			if (StringUtils.hasText(response.getMetadata().getModel())) {
				metadata.put("springAiModel", response.getMetadata().getModel());
			}
			usage = toChatUsage(response.getMetadata().getUsage());
		}
		if (generation != null && generation.getMetadata() != null) {
			finishReason = generation.getMetadata().getFinishReason();
		}
		return ChatResponse.builder()
			.id(responseId)
			.content(contentBlocks)
			.usage(usage)
			.metadata(metadata)
			.finishReason(finishReason)
			.build();
	}

	private ToolUseBlock toToolUseBlock(AssistantMessage.ToolCall toolCall) {
		Map<String, Object> input = parseArguments(toolCall.arguments());
		Map<String, Object> metadata = new HashMap<>();
		if (StringUtils.hasText(toolCall.type())) {
			metadata.put("type", toolCall.type());
		}
		if (StringUtils.hasText(toolCall.arguments())) {
			metadata.put("arguments", toolCall.arguments());
		}
		return ToolUseBlock.builder()
			.id(toolCall.id())
			.name(resolveChunkToolName(toolCall))
			.input(input)
			.content(defaultText(toolCall.arguments()))
			.metadata(metadata.isEmpty() ? null : metadata)
			.build();
	}

	private AssistantMessage.ToolCall toSpringToolCall(ToolUseBlock toolUseBlock) {
		return new AssistantMessage.ToolCall(defaultId(toolUseBlock.getId()), resolveToolCallType(toolUseBlock),
				defaultToolName(toolUseBlock.getName()), resolveToolCallArguments(toolUseBlock));
	}

	private ToolResponseMessage.ToolResponse toSpringToolResponse(ToolResultBlock toolResultBlock) {
		return new ToolResponseMessage.ToolResponse(defaultId(toolResultBlock.getId()),
				defaultToolName(toolResultBlock.getName()), extractToolResultOutput(toolResultBlock));
	}

	private String extractReasoningContent(AssistantMessage output) {
		if (output == null || output.getMetadata() == null) {
			return null;
		}
		Object reasoningContent = output.getMetadata().get("reasoningContent");
		if (reasoningContent == null) {
			return null;
		}
		return reasoningContent instanceof String text ? text : reasoningContent.toString();
	}

	private Map<String, Object> extractThinkingMetadata(AssistantMessage output) {
		if (output == null || output.getMetadata() == null || output.getMetadata().isEmpty()) {
			return null;
		}
		Object reasoningDetails = output.getMetadata().get(ThinkingBlock.METADATA_REASONING_DETAILS);
		if (reasoningDetails == null) {
			return null;
		}
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put(ThinkingBlock.METADATA_REASONING_DETAILS, reasoningDetails);
		return metadata;
	}

	private ChatUsage toChatUsage(Usage usage) {
		if (usage == null) {
			return null;
		}
		return ChatUsage.builder()
			.inputTokens(usage.getPromptTokens())
			.outputTokens(usage.getCompletionTokens())
			.build();
	}

	private Map<String, Object> parseArguments(String arguments) {
		if (!StringUtils.hasText(arguments)) {
			return Map.of();
		}
		try {
			return objectMapper.readValue(arguments, MAP_TYPE);
		}
		catch (Exception ex) {
			Map<String, Object> fallback = new LinkedHashMap<>();
			fallback.put("rawArguments", arguments);
			return fallback;
		}
	}

	private String defaultText(String text) {
		return text == null ? "" : text;
	}

	private String defaultId(String id) {
		return StringUtils.hasText(id) ? id : "tool-response";
	}

	private String defaultToolName(String name) {
		return StringUtils.hasText(name) ? name : "tool";
	}

	private String extractMessageText(Msg message) {
		String text = message.getTextContent();
		if (StringUtils.hasText(text)) {
			return text;
		}
		return message.getContentBlocks(TextBlock.class)
			.stream()
			.map(TextBlock::getText)
			.filter(StringUtils::hasText)
			.findFirst()
			.orElse("");
	}

	private String resolveToolCallType(ToolUseBlock toolUseBlock) {
		Map<String, Object> metadata = toolUseBlock.getMetadata();
		if (metadata == null) {
			return "function";
		}
		Object type = metadata.get("type");
		return type instanceof String value && StringUtils.hasText(value) ? value : "function";
	}

	private String resolveToolCallArguments(ToolUseBlock toolUseBlock) {
		if (StringUtils.hasText(toolUseBlock.getContent())) {
			return toolUseBlock.getContent();
		}
		try {
			return objectMapper
				.writeValueAsString(toolUseBlock.getInput() == null ? Map.of() : toolUseBlock.getInput());
		}
		catch (Exception ex) {
			return "{}";
		}
	}

	private String extractToolResultOutput(ToolResultBlock toolResultBlock) {
		List<ContentBlock> output = toolResultBlock.getOutput();
		if (output == null || output.isEmpty()) {
			return "";
		}
		return output.stream()
			.filter(TextBlock.class::isInstance)
			.map(TextBlock.class::cast)
			.map(TextBlock::getText)
			.filter(StringUtils::hasText)
			.findFirst()
			.orElse("");
	}

	private String resolveChunkToolName(AssistantMessage.ToolCall toolCall) {
		if (toolCall == null) {
			return PARTIAL_TOOL_NAME;
		}
		return StringUtils.hasText(toolCall.name()) ? toolCall.name() : PARTIAL_TOOL_NAME;
	}

}
