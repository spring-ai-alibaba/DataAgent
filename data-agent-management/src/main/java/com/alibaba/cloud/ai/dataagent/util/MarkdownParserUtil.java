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
package com.alibaba.cloud.ai.dataagent.util;

public class MarkdownParserUtil {

	public static String extractText(String markdownCode) {
		String code = extractRawText(markdownCode);
		// Correctly handle various newline character types: \r\n, \n, \r, but maintain
		// compatibility with NewLineParser.format()
		return code.replaceAll("\r\n", " ").replaceAll("\n", " ").replaceAll("\r", " ");
	}

	public static String extractRawText(String markdownCode) {
		CodeBlock codeBlock = findNextCodeBlock(markdownCode, 0);
		return codeBlock == null ? markdownCode : codeBlock.rawText;
	}

	public static String extractLastRawText(String markdownCode) {
		CodeBlock lastCodeBlock = null;
		int searchIndex = 0;

		while (searchIndex <= markdownCode.length() - 3) {
			CodeBlock codeBlock = findNextCodeBlock(markdownCode, searchIndex);
			if (codeBlock == null) {
				break;
			}
			lastCodeBlock = codeBlock;
			searchIndex = codeBlock.nextSearchIndex;
		}

		return lastCodeBlock == null ? markdownCode : lastCodeBlock.rawText;
	}

	private static CodeBlock findNextCodeBlock(String markdownCode, int searchIndex) {
		int startIndex = -1;
		int delimiterLength = 0;

		for (int i = searchIndex; i <= markdownCode.length() - 3; i++) {
			if (markdownCode.substring(i, i + 3).equals("```")) {
				startIndex = i;
				delimiterLength = 3;
				while (i + delimiterLength < markdownCode.length() && markdownCode.charAt(i + delimiterLength) == '`') {
					delimiterLength++;
				}
				break;
			}
		}

		if (startIndex == -1) {
			return null;
		}

		int contentStart = startIndex + delimiterLength;
		while (contentStart < markdownCode.length() && markdownCode.charAt(contentStart) != '\n') {
			contentStart++;
		}
		if (contentStart < markdownCode.length() && markdownCode.charAt(contentStart) == '\n') {
			contentStart++;
		}

		String closingDelimiter = "`".repeat(delimiterLength);
		int endIndex = markdownCode.indexOf(closingDelimiter, contentStart);

		if (endIndex == -1) {
			return new CodeBlock(markdownCode.substring(contentStart), markdownCode.length());
		}

		return new CodeBlock(markdownCode.substring(contentStart, endIndex), endIndex + delimiterLength);
	}

	private static final class CodeBlock {

		private final String rawText;

		private final int nextSearchIndex;

		private CodeBlock(String rawText, int nextSearchIndex) {
			this.rawText = rawText;
			this.nextSearchIndex = nextSearchIndex;
		}

	}

}
