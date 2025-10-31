package com.alibaba.cloud.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum TextType {

	JSON("```json", "```"),

	PYTHON("```python", "```"),

	SQL("```sql", "```"),

	HTML("```html", "```"),

	MARK_DOWN("```", "```"),

	TEXT(null, null);

	private final String startSign;

	private final String endSign;

	public static TextType getType(TextType origin, String chuck) {
		if (origin == TEXT) {
			for (TextType type : TextType.values()) {
				if (chuck.equals(type.startSign)) {
					return type;
				}
			}
		}
		else {
			if (chuck.equals(origin.endSign)) {
				return TextType.TEXT;
			}
		}
		return origin;
	}

}
