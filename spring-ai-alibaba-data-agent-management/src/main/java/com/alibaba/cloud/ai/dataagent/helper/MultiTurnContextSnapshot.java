/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.dataagent.helper;

import java.util.Collections;
import java.util.List;

/**
 * 多轮上下文的快照，包含最近的对话轮次和序列化后的上下文。
 */
public class MultiTurnContextSnapshot {

	private final List<ContextRound> rounds;

	private final Long lastMessageId;

	private final String context;

	public MultiTurnContextSnapshot(List<ContextRound> rounds, Long lastMessageId, String context) {
		this.rounds = rounds == null ? Collections.emptyList() : Collections.unmodifiableList(rounds);
		this.lastMessageId = lastMessageId;
		this.context = context;
	}

	public List<ContextRound> getRounds() {
		return rounds;
	}

	public Long getLastMessageId() {
		return lastMessageId;
	}

	public String getContext() {
		return context;
	}

	/**
	 * 代表一轮对话的结构化信息。
	 */
	public static class ContextRound {

		private final Long questionMessageId;

		private final String question;

		private final List<String> sqlStatements;

		private final List<String> pythonCodes;

		private final List<String> reportSummaries;

		public ContextRound(Long questionMessageId, String question, List<String> sqlStatements,
				List<String> pythonCodes, List<String> reportSummaries) {
			this.questionMessageId = questionMessageId;
			this.question = question;
			this.sqlStatements = sqlStatements == null ? Collections.emptyList()
					: Collections.unmodifiableList(sqlStatements);
			this.pythonCodes = pythonCodes == null ? Collections.emptyList()
					: Collections.unmodifiableList(pythonCodes);
			this.reportSummaries = reportSummaries == null ? Collections.emptyList()
					: Collections.unmodifiableList(reportSummaries);
		}

		public Long getQuestionMessageId() {
			return questionMessageId;
		}

		public String getQuestion() {
			return question;
		}

		public List<String> getSqlStatements() {
			return sqlStatements;
		}

		public List<String> getPythonCodes() {
			return pythonCodes;
		}

		public List<String> getReportSummaries() {
			return reportSummaries;
		}

	}

}
