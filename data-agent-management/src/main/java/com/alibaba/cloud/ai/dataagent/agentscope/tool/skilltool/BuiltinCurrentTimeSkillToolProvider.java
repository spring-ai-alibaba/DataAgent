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
package com.alibaba.cloud.ai.dataagent.agentscope.tool.skilltool;

import com.alibaba.cloud.ai.dataagent.service.skill.LocalSkillService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class BuiltinCurrentTimeSkillToolProvider implements SkillBoundToolProvider {

	private static final String TOOL_NAME = "skill.current_time.now";

	private static final String INPUT_SCHEMA = """
			{
			  "type": "object",
			  "properties": {
			    "timezone": {
			      "type": "string",
			      "description": "IANA 时区名，例如 Asia/Shanghai、UTC、America/New_York。默认 Asia/Shanghai。"
			    },
			    "pattern": {
			      "type": "string",
			      "description": "可选的时间格式，例如 yyyy-MM-dd HH:mm:ss。默认 yyyy-MM-dd HH:mm:ss z。"
			    }
			  }
			}
			""";

	private final ObjectMapper objectMapper;

	@Override
	public String getSkillId() {
		return LocalSkillService.BUILTIN_CURRENT_TIME_SKILL_ID;
	}

	@Override
	public Map<String, ToolCallback> getToolCallbacks(String agentId) {
		ToolDefinition toolDefinition = ToolDefinition.builder()
			.name(TOOL_NAME)
			.description("返回指定时区的当前时间。需要精确时间信息时使用。")
			.inputSchema(INPUT_SCHEMA)
			.build();
		return Map.of(TOOL_NAME, new CurrentTimeToolCallback(toolDefinition, objectMapper));
	}

	private static final class CurrentTimeToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		private final ObjectMapper objectMapper;

		private CurrentTimeToolCallback(ToolDefinition toolDefinition, ObjectMapper objectMapper) {
			this.toolDefinition = toolDefinition;
			this.objectMapper = objectMapper;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			try {
				JsonNode jsonNode = StringUtils.hasText(toolInput) ? objectMapper.readTree(toolInput)
						: objectMapper.createObjectNode();
				String timezone = jsonNode.path("timezone").asText("Asia/Shanghai");
				String pattern = jsonNode.path("pattern").asText("yyyy-MM-dd HH:mm:ss z");
				ZoneId zoneId = ZoneId.of(timezone);
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
				ZonedDateTime now = ZonedDateTime.now(zoneId);
				return objectMapper.createObjectNode()
					.put("timezone", zoneId.getId())
					.put("formattedTime", now.format(formatter))
					.put("isoTime", now.toOffsetDateTime().toString())
					.toString();
			}
			catch (IllegalArgumentException ex) {
				throw new IllegalStateException("Invalid timezone or time format: " + ex.getMessage(), ex);
			}
			catch (Exception ex) {
				throw new IllegalStateException("Failed to get current time: " + ex.getMessage(), ex);
			}
		}

		@Override
		public String call(String toolInput, ToolContext toolContext) {
			return call(toolInput);
		}

	}

}
