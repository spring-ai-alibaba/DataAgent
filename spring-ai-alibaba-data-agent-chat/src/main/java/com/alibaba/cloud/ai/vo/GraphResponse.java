package com.alibaba.cloud.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GraphResponse {

	private String agentId;

	private String threadId;

	// 使用Constant常量
	private String nodeName;

	private TextType textType;

	private String text;

	private boolean error;

	private boolean complete;

	public enum TextType {

		TEXT, JSON, PYTHON, SQL;

	}

	public static GraphResponse error(String agentId, String threadId, String text) {
		return GraphResponse.builder()
			.agentId(agentId)
			.threadId(threadId)
			.text(text)
			.error(true)
			.textType(TextType.TEXT)
			.build();
	}

	public static GraphResponse complete(String agentId, String threadId) {
		return GraphResponse.builder()
			.agentId(agentId)
			.threadId(threadId)
			.complete(true)
			.textType(TextType.TEXT)
			.build();
	}

}
