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
package com.alibaba.cloud.ai.dataagent.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/**
 * Minimal bridge that lets AgentScope reuse the project's Spring AI ChatModel.
 */
public class SpringAiAgentScopeModel extends ChatModelBase {

	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

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
	protected Flux<ChatResponse> doStream(List<Msg> messages, List<ToolSchema> toolSchemas, GenerateOptions generateOptions) {
		List<Message> promptMessages = new ArrayList<>();
		for (Msg message : messages) {
			Message springMessage = toSpringMessage(message);
			if (springMessage != null) {
				promptMessages.add(springMessage);
			}
		}
		Prompt prompt = new Prompt(promptMessages, buildChatOptions(toolSchemas, generateOptions));
		return Flux.defer(() -> Flux.just(toAgentScopeResponse(this.delegate.call(prompt))));
	}

	@Override
	public String getModelName() {
		return this.modelName;
	}

	private Message toSpringMessage(Msg message) {
		if (message == null) {
			return null;
		}
		String text = defaultText(message.getTextContent());
		MsgRole role = message.getRole() == null ? MsgRole.USER : message.getRole();
		return switch (role) {
			case SYSTEM -> new SystemMessage(text);
			case ASSISTANT -> new AssistantMessage(text);
			case TOOL -> ToolResponseMessage.builder()
				.responses(List.of(new ToolResponseMessage.ToolResponse(defaultId(message.getId()),
						defaultToolName(message.getName()), text)))
				.build();
			case USER -> new UserMessage(text);
		};
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
		for (ToolSchema toolSchema : toolSchemas) {
			if (toolSchema == null || !StringUtils.hasText(toolSchema.getName())) {
				continue;
			}
			ToolCallback toolCallback = toolCallbacks.get(toolSchema.getName());
			if (toolCallback != null) {
				selectedCallbacks.add(toolCallback);
			}
		}
		return selectedCallbacks;
	}

	private ChatResponse toAgentScopeResponse(org.springframework.ai.chat.model.ChatResponse response) {
		org.springframework.ai.chat.model.Generation generation = response == null ? null : response.getResult();
		AssistantMessage output = generation == null ? null : generation.getOutput();
		String text = output == null ? "" : defaultText(output.getText());
		List<ContentBlock> contentBlocks = new ArrayList<>();
		if (StringUtils.hasText(text)) {
			contentBlocks.add(TextBlock.builder().text(text).build());
		}
		if (output != null && output.hasToolCalls()) {
			output.getToolCalls().stream().map(this::toToolUseBlock).forEach(contentBlocks::add);
		}
		if (contentBlocks.isEmpty()) {
			contentBlocks.add(TextBlock.builder().text(text).build());
		}
		Map<String, Object> metadata = new LinkedHashMap<>();
		String responseId = null;
		String finishReason = null;
		if (response != null && response.getMetadata() != null) {
			responseId = response.getMetadata().getId();
			if (StringUtils.hasText(response.getMetadata().getModel())) {
				metadata.put("springAiModel", response.getMetadata().getModel());
			}
		}
		if (generation != null && generation.getMetadata() != null) {
			finishReason = generation.getMetadata().getFinishReason();
		}
		return ChatResponse.builder()
			.id(responseId)
			.content(contentBlocks)
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
			.id(defaultId(toolCall.id()))
			.name(defaultToolName(toolCall.name()))
			.input(input)
			.content(defaultText(toolCall.arguments()))
			.metadata(metadata)
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

}
