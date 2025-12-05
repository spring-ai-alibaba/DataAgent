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

import com.alibaba.cloud.ai.dataagent.entity.ChatMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 构建多轮对话上下文，提取用户问题、生成的SQL、Python代码和报告摘要等结构化信息。
 */
@Component
public class MultiTurnContextBuilder {

	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

	private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

	private static final int MAX_ROUNDS = 5;

	private static final int REPORT_SUMMARY_LIMIT = 400;

	public MultiTurnContextSnapshot build(List<ChatMessage> messages, String latestQuery,
			MultiTurnContextSnapshot previousSnapshot) {
		if (CollectionUtils.isEmpty(messages)) {
			return previousSnapshot;
		}

		List<MutableRound> mutableRounds = initRounds(previousSnapshot);
		long lastProcessedId = previousSnapshot != null && previousSnapshot.getLastMessageId() != null
				? previousSnapshot.getLastMessageId() : Long.MIN_VALUE;
		long maxProcessedId = lastProcessedId;

		for (ChatMessage message : messages) {
			if (message.getId() == null || message.getId() <= lastProcessedId) {
				continue;
			}
			maxProcessedId = Math.max(maxProcessedId, message.getId());
			processMessage(message, mutableRounds);
			trimRounds(mutableRounds);
		}

		if (mutableRounds.isEmpty()) {
			return null;
		}

		removeLatestQueryRound(mutableRounds, latestQuery);

		if (mutableRounds.isEmpty()) {
			return null;
		}

		List<MultiTurnContextSnapshot.ContextRound> rounds = toImmutableRounds(mutableRounds);
		String context = renderContext(rounds);
		if (!StringUtils.hasText(context)) {
			return null;
		}
		Long snapshotMessageId = maxProcessedId == Long.MIN_VALUE ? lastProcessedId : maxProcessedId;
		return new MultiTurnContextSnapshot(rounds, snapshotMessageId, context);
	}

	private List<MutableRound> initRounds(MultiTurnContextSnapshot snapshot) {
		List<MutableRound> rounds = new ArrayList<>();
		if (snapshot == null || CollectionUtils.isEmpty(snapshot.getRounds())) {
			return rounds;
		}
		for (MultiTurnContextSnapshot.ContextRound round : snapshot.getRounds()) {
			rounds.add(MutableRound.copyOf(round));
		}
		return rounds;
	}

	private void processMessage(ChatMessage message, List<MutableRound> rounds) {
		if (isUserQuestion(message)) {
			rounds.add(new MutableRound(message.getId(), normalizeQuestion(message.getContent())));
			return;
		}

		if (rounds.isEmpty() && shouldStartRoundWithSystemMessage(message)) {
			rounds.add(new MutableRound(null, null));
		}

		if (rounds.isEmpty()) {
			return;
		}

		MutableRound current = rounds.get(rounds.size() - 1);
		String type = normalizeType(message.getMessageType());
		String content = safeContent(message.getContent());

		if ("sql".equals(type)) {
			current.addSql(content);
		}
		else if ("python".equals(type)) {
			current.addPython(content);
		}
		else if ("html-report".equals(type) || "summary".equals(type)) {
			current.addReportSummary(extractSummary(content));
		}
	}

	private boolean shouldStartRoundWithSystemMessage(ChatMessage message) {
		String type = normalizeType(message.getMessageType());
		return "sql".equals(type) || "python".equals(type) || "html-report".equals(type) || "summary".equals(type);
	}

	private void removeLatestQueryRound(List<MutableRound> rounds, String latestQuery) {
		if (StringUtils.isEmpty(latestQuery) || CollectionUtils.isEmpty(rounds)) {
			return;
		}
		String normalizedQuery = normalizeQuestion(latestQuery);
		if (!StringUtils.hasText(normalizedQuery)) {
			return;
		}
		MutableRound lastRound = rounds.get(rounds.size() - 1);
		if (!StringUtils.hasText(lastRound.getQuestion())) {
			return;
		}
		if (Objects.equals(normalizedQuery, lastRound.getQuestion())) {
			rounds.remove(rounds.size() - 1);
		}
	}

	private void trimRounds(List<MutableRound> rounds) {
		while (rounds.size() > MAX_ROUNDS) {
			rounds.remove(0);
		}
	}

	private List<MultiTurnContextSnapshot.ContextRound> toImmutableRounds(List<MutableRound> rounds) {
		List<MultiTurnContextSnapshot.ContextRound> result = new ArrayList<>();
		for (MutableRound round : rounds) {
			if (!round.hasContent()) {
				continue;
			}
			result.add(round.toImmutable());
		}
		return result;
	}

	private String renderContext(List<MultiTurnContextSnapshot.ContextRound> rounds) {
		if (CollectionUtils.isEmpty(rounds)) {
			return null;
		}
		StringBuilder builder = new StringBuilder();
		builder.append("【多轮上下文】\n");
		for (int i = 0; i < rounds.size(); i++) {
			MultiTurnContextSnapshot.ContextRound round = rounds.get(i);
			builder.append("回合").append(i + 1).append(":\n");
			if (StringUtils.hasText(round.getQuestion())) {
				builder.append("- 用户问题: ").append(round.getQuestion()).append("\n");
			}
			if (!CollectionUtils.isEmpty(round.getSqlStatements())) {
				for (String sql : round.getSqlStatements()) {
					builder.append("- 生成SQL: ").append(sql).append("\n");
				}
			}
			if (!CollectionUtils.isEmpty(round.getPythonCodes())) {
				for (String code : round.getPythonCodes()) {
					builder.append("- Python代码: ").append(code).append("\n");
				}
			}
			if (!CollectionUtils.isEmpty(round.getReportSummaries())) {
				for (String summary : round.getReportSummaries()) {
					builder.append("- 报告摘要: ").append(summary).append("\n");
				}
			}
		}
		return builder.toString().trim();
	}

	private boolean isUserQuestion(ChatMessage message) {
		return "user".equalsIgnoreCase(message.getRole());
	}

	private String normalizeType(String type) {
		return type == null ? null : type.toLowerCase(Locale.ROOT);
	}

	private String normalizeQuestion(String question) {
		String normalized = safeContent(question);
		return StringUtils.hasText(normalized) ? normalized : null;
	}

	private String safeContent(String content) {
		if (!StringUtils.hasText(content)) {
			return "";
		}
		String normalized = HtmlUtils.htmlUnescape(content);
		normalized = normalized.replace("\u00A0", " ");
		return WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
	}

	private String extractSummary(String htmlContent) {
		String text = HTML_TAG_PATTERN.matcher(htmlContent).replaceAll(" ");
		text = WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim();
		if (text.length() <= REPORT_SUMMARY_LIMIT) {
			return text;
		}
		return text.substring(0, REPORT_SUMMARY_LIMIT) + "...";
	}

	private static class MutableRound {

		private Long questionMessageId;

		private String question;

		private final List<String> sqlStatements = new ArrayList<>();

		private final List<String> pythonCodes = new ArrayList<>();

		private final List<String> reportSummaries = new ArrayList<>();

		private MutableRound(Long questionMessageId, String question) {
			this.questionMessageId = questionMessageId;
			this.question = question;
		}

		public static MutableRound copyOf(MultiTurnContextSnapshot.ContextRound source) {
			MutableRound round = new MutableRound(source.getQuestionMessageId(), source.getQuestion());
			round.sqlStatements.addAll(source.getSqlStatements());
			round.pythonCodes.addAll(source.getPythonCodes());
			round.reportSummaries.addAll(source.getReportSummaries());
			return round;
		}

		public boolean hasContent() {
			return StringUtils.hasText(question) || !sqlStatements.isEmpty() || !pythonCodes.isEmpty()
					|| !reportSummaries.isEmpty();
		}

		public void addSql(String sql) {
			if (StringUtils.hasText(sql)) {
				this.sqlStatements.add(sql);
			}
		}

		public void addPython(String python) {
			if (StringUtils.hasText(python)) {
				this.pythonCodes.add(python);
			}
		}

		public void addReportSummary(String summary) {
			if (StringUtils.hasText(summary)) {
				this.reportSummaries.add(summary);
			}
		}

		public Long getQuestionMessageId() {
			return questionMessageId;
		}

		public String getQuestion() {
			return question;
		}

		public MultiTurnContextSnapshot.ContextRound toImmutable() {
			return new MultiTurnContextSnapshot.ContextRound(this.questionMessageId, this.question,
					new ArrayList<>(this.sqlStatements), new ArrayList<>(this.pythonCodes),
					new ArrayList<>(this.reportSummaries));
		}
	}

}
