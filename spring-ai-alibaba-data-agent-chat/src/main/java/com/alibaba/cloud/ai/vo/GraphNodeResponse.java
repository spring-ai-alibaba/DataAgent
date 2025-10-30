package com.alibaba.cloud.ai.vo;

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

	public enum TextType {

		JSON("```json"),

		PYTHON("```python"),

		SQL("```sql"),

		HTML("```html"),

		MARK_DOWN("```"),

		TEXT(null);

		private final String startSign;

		TextType(String startSign) {
			this.startSign = startSign;
		}

		public static TextType getType(TextType origin, String chuck) {
			for (TextType type : TextType.values()) {
				if (type == TEXT || type == MARK_DOWN) {
					continue;
				}
				if (chuck.startsWith(type.startSign)) {
					return type;
				}
			}
			if (chuck.startsWith(MARK_DOWN.startSign)) {
				if (origin == TEXT) {
					return TextType.MARK_DOWN;
				}
				else {
					return TextType.TEXT;
				}
			}
			return origin;
		}

		public static String trimSign(TextType textType, String text) {
			if (textType == TextType.TEXT || !text.startsWith(textType.startSign)) {
				return text;
			}
			return text.substring(textType.startSign.length());
		}

	}

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
