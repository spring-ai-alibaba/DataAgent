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

package com.alibaba.cloud.ai.dataagent.service.context;

import com.alibaba.cloud.ai.dataagent.entity.ChatMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 管理多轮对话上下文的缓存与构建逻辑。
 * @author Makoto
 */
@Component
public class MultiTurnContextManager {

    private final ConcurrentHashMap<String, MultiTurnContextSnapshot> contextCache = new ConcurrentHashMap<>();

    public String buildContext(String sessionId, List<ChatMessage> messages, String latestQuery) {
        if (CollectionUtils.isEmpty(messages)) {
            return null;
        }
        MultiTurnContextSnapshot snapshot = MultiTurnContextProcessor.process(messages, latestQuery,
                contextCache.get(sessionId));
        if (snapshot == null) {
            contextCache.remove(sessionId);
            return null;
        }
        contextCache.put(sessionId, snapshot);
        return snapshot.context();
    }

    private static final class MultiTurnContextProcessor {

        private static final int MAX_ROUNDS = 5;

        private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

        private MultiTurnContextProcessor() {
        }

        static MultiTurnContextSnapshot process(List<ChatMessage> messages, String latestQuery,
                                                MultiTurnContextSnapshot existing) {
            RoundHistory history = RoundHistory.restore(existing);
            long lastId = existing != null && existing.lastMessageId() != null ? existing.lastMessageId()
                    : Long.MIN_VALUE;
            long newestId = lastId;
            for (ChatMessage message : messages) {
                if (message.getId() == null || message.getId() <= lastId) {
                    continue;
                }
                newestId = Math.max(newestId, message.getId());
                history.append(message);
            }
            history.trim(MAX_ROUNDS);
            history.removeIfMatchesLatestQuestion(normalize(latestQuery));
            List<MultiTurnContextSnapshot.ContextRound> rounds = history.toSnapshot();
            if (CollectionUtils.isEmpty(rounds)) {
                return null;
            }
            String context = ContextRenderer.render(rounds);
            if (!StringUtils.hasText(context)) {
                return null;
            }
            long resolvedId = newestId == Long.MIN_VALUE ? lastId : newestId;
            return new MultiTurnContextSnapshot(rounds, resolvedId, context);
        }

        private static String normalize(String text) {
            if (!StringUtils.hasText(text)) {
                return null;
            }
            String normalized = HtmlUtils.htmlUnescape(text);
            normalized = normalized.replace('\u00A0', ' ');
            normalized = WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
            return normalized.isEmpty() ? null : normalized;
        }

    }

    private enum StructuredType {

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

    private static final class RoundHistory {

        private final List<RoundEntry> entries;

        private RoundEntry currentEntry;

        private RoundHistory(List<RoundEntry> entries) {
            this.entries = entries;
            this.currentEntry = entries.isEmpty() ? null : entries.get(entries.size() - 1);
        }

        static RoundHistory restore(MultiTurnContextSnapshot snapshot) {
            if (snapshot == null || CollectionUtils.isEmpty(snapshot.rounds())) {
                return new RoundHistory(new ArrayList<>());
            }
            List<RoundEntry> result = new ArrayList<>();
            for (MultiTurnContextSnapshot.ContextRound round : snapshot.rounds()) {
                result.add(RoundEntry.restore(round));
            }
            return new RoundHistory(result);
        }

        void append(ChatMessage message) {
            if ("user".equalsIgnoreCase(message.getRole())) {
                startNewRound(message);
                return;
            }
            StructuredType type = StructuredType.from(message.getMessageType());
            if (type == null) {
                return;
            }
            ensureCurrentEntry();
            currentEntry.accept(type, MultiTurnContextProcessor.normalize(message.getContent()));
        }

        void trim(int maxRounds) {
            while (entries.size() > maxRounds) {
                entries.remove(0);
            }
            currentEntry = entries.isEmpty() ? null : entries.get(entries.size() - 1);
        }

        void removeIfMatchesLatestQuestion(String latestQuestion) {
            if (!StringUtils.hasText(latestQuestion) || entries.isEmpty()) {
                return;
            }
            RoundEntry last = entries.get(entries.size() - 1);
            if (StringUtils.hasText(last.question) && latestQuestion.equals(last.question)) {
                entries.remove(entries.size() - 1);
                currentEntry = entries.isEmpty() ? null : entries.get(entries.size() - 1);
            }
        }

        List<MultiTurnContextSnapshot.ContextRound> toSnapshot() {
            List<MultiTurnContextSnapshot.ContextRound> result = new ArrayList<>();
            for (RoundEntry entry : entries) {
                if (entry.isMeaningful()) {
                    result.add(entry.toSnapshot());
                }
            }
            return result;
        }

        private void startNewRound(ChatMessage message) {
            currentEntry = new RoundEntry(message.getId(), MultiTurnContextProcessor.normalize(message.getContent()));
            entries.add(currentEntry);
        }

        private void ensureCurrentEntry() {
            if (currentEntry == null) {
                currentEntry = new RoundEntry(null, null);
                entries.add(currentEntry);
            }
        }

    }

    private static final class RoundEntry {

        private final Long questionMessageId;

        private final String question;

        private final List<String> sqlList = new ArrayList<>();

        private final List<String> pythonList = new ArrayList<>();

        private final List<String> summaries = new ArrayList<>();

        private RoundEntry(Long questionMessageId, String question) {
            this.questionMessageId = questionMessageId;
            this.question = question;
        }

        static RoundEntry restore(MultiTurnContextSnapshot.ContextRound round) {
            RoundEntry entry = new RoundEntry(round.questionMessageId(), round.question());
            entry.sqlList.addAll(round.sqlStatements());
            entry.pythonList.addAll(round.pythonCodes());
            entry.summaries.addAll(round.reportSummaries());
            return entry;
        }

        void accept(StructuredType type, String content) {
            if (!StringUtils.hasText(content)) {
                return;
            }
            switch (type) {
                case SQL -> sqlList.add(content);
                case PYTHON -> pythonList.add(content);
                case SUMMARY -> summaries.add(content);
            }
        }

        boolean isMeaningful() {
            return StringUtils.hasText(question) || !sqlList.isEmpty() || !pythonList.isEmpty() || !summaries.isEmpty();
        }

        MultiTurnContextSnapshot.ContextRound toSnapshot() {
            return new MultiTurnContextSnapshot.ContextRound(questionMessageId, question, new ArrayList<>(sqlList),
                    new ArrayList<>(pythonList), new ArrayList<>(summaries));
        }
    }

    private static final class ContextRenderer {

        private ContextRenderer() {
        }

        static String render(List<MultiTurnContextSnapshot.ContextRound> rounds) {
            if (CollectionUtils.isEmpty(rounds)) {
                return null;
            }
            StringBuilder builder = new StringBuilder("【多轮上下文】\n");
            for (int i = 0; i < rounds.size(); i++) {
                MultiTurnContextSnapshot.ContextRound round = rounds.get(i);
                builder.append("回合").append(i + 1).append(":\n");
                appendField(builder, "- 用户问题: ", round.question());
                appendList(builder, "- 生成SQL: ", round.sqlStatements());
                appendList(builder, "- Python代码: ", round.pythonCodes());
                appendList(builder, "- 报告摘要: ", round.reportSummaries());
            }
            return builder.toString().trim();
        }

        private static void appendField(StringBuilder builder, String prefix, String value) {
            if (StringUtils.hasText(value)) {
                builder.append(prefix).append(value).append("\n");
            }
        }

        private static void appendList(StringBuilder builder, String prefix, List<String> values) {
            if (CollectionUtils.isEmpty(values)) {
                return;
            }
            for (String value : values) {
                appendField(builder, prefix, value);
            }
        }

    }

    private record MultiTurnContextSnapshot(List<ContextRound> rounds, Long lastMessageId, String context) {

        record ContextRound(Long questionMessageId, String question, List<String> sqlStatements,
                            List<String> pythonCodes, List<String> reportSummaries) {
        }

    }

}