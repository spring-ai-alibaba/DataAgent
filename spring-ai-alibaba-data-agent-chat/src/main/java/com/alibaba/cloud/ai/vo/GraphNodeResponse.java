package com.alibaba.cloud.ai.vo;

import com.alibaba.cloud.ai.enums.TextType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GraphNodeResponse {

	private String agentId;

	private String threadId;

	// 使用Constant常量
	private String nodeName;

	private TextType textType;

	private String text;

	@Builder.Default
	private boolean error = false;

	@Builder.Default
	private boolean complete = false;

	public static GraphNodeResponse error(String agentId, String threadId, String text) {
		return GraphNodeResponse.builder()
			.agentId(agentId)
			.threadId(threadId)
			.text(text)
			.error(true)
			.textType(TextType.TEXT)
			.build();
	}

	public static GraphNodeResponse complete(String agentId, String threadId) {
		return GraphNodeResponse.builder()
			.agentId(agentId)
			.threadId(threadId)
			.complete(true)
			.textType(TextType.TEXT)
			.build();
	}

}
