package com.alibaba.cloud.ai.dataagent.service.context;

import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.Locale;

/**
 * Enum for Structured Types
 *
 * @author Makoto
 */
public enum StructuredType {

	SQL("sql"), PYTHON("python"), SUMMARY("summary");

	private final String typeName;

	StructuredType(String typeName) {
		this.typeName = typeName;
	}

	static StructuredType from(String messageType) {
		if (!StringUtils.hasText(messageType)) {
			return null;
		}
		String normalized = messageType.toLowerCase(Locale.ROOT);
		for (StructuredType value : EnumSet.allOf(StructuredType.class)) {
			if (value.typeName.equals(normalized)) {
				return value;
			}
		}
		return null;
	}

}