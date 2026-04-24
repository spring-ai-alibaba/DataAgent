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
package com.alibaba.cloud.ai.dataagent.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Cache the latest completed trace for each chat session so the frontend can inspect
 * the most recent AgentScope span chain without querying an external tracing backend.
 */
@Component
public class SessionTraceStore implements SpanExporter {

	public static final String ATTR_THREAD_ID = "dataagent.thread.id";

	public static final String ATTR_RUNTIME_REQUEST_ID = "dataagent.runtime.request.id";

	public static final String ATTR_AGENT_ID = "dataagent.agent.id";

	private static final String ROOT_PARENT_SPAN_ID = "0000000000000000";

	private static final int MAX_TRACE_ASSEMBLIES = 256;

	private static final int MAX_SESSION_TRACES = 128;

	private static final int MAX_ATTRIBUTE_VALUE_LENGTH = 2048;

	private static final String META_OMITTED_ATTRIBUTE_COUNT = "_meta.omitted_attribute_count";

	private static final List<String> SENSITIVE_ATTRIBUTE_KEY_TOKENS = List.of("password", "passwd", "pwd", "secret",
			"api_key", "apikey", "api_token", "apitoken", "access_key", "access_token", "accesstoken",
			"refresh_token", "refreshtoken", "id_token", "idtoken", "auth_token", "authtoken", "private_key",
			"privatekey", "authorization", "cookie", "credential", "signature");

	private final Object monitor = new Object();

	private final LinkedHashMap<String, TraceAssembly> traceAssemblies = new LinkedHashMap<>(32, 0.75f, true);

	private final LinkedHashMap<String, TraceView> latestTraceBySessionId = new LinkedHashMap<>(32, 0.75f, true);

	@Override
	public CompletableResultCode export(Collection<SpanData> spans) {
		synchronized (monitor) {
			for (SpanData span : spans) {
				captureSpan(span);
			}
		}
		return CompletableResultCode.ofSuccess();
	}

	@Override
	public CompletableResultCode flush() {
		return CompletableResultCode.ofSuccess();
	}

	@Override
	public CompletableResultCode shutdown() {
		synchronized (monitor) {
			traceAssemblies.clear();
			latestTraceBySessionId.clear();
		}
		return CompletableResultCode.ofSuccess();
	}

	public Optional<TraceView> getLatestTrace(String sessionId) {
		synchronized (monitor) {
			return Optional.ofNullable(latestTraceBySessionId.get(sessionId));
		}
	}

	private void captureSpan(SpanData span) {
		String traceId = span.getSpanContext().getTraceId();
		TraceAssembly assembly = traceAssemblies.computeIfAbsent(traceId, ignored -> new TraceAssembly());
		assembly.traceId = traceId;
		assembly.spansBySpanId.put(span.getSpanContext().getSpanId(), toSpanView(span));

		String sessionId = readAttribute(span, ATTR_THREAD_ID);
		if (StringUtils.hasText(sessionId)) {
			assembly.sessionId = sessionId;
			assembly.runtimeRequestId = readAttribute(span, ATTR_RUNTIME_REQUEST_ID);
			assembly.agentId = readAttribute(span, ATTR_AGENT_ID);
		}

		if (StringUtils.hasText(assembly.sessionId)) {
			latestTraceBySessionId.put(assembly.sessionId, assembly.toTraceView());
			evictOldSessionTraces();
		}

		evictOldAssemblies();
	}

	private String readAttribute(SpanData span, String key) {
		String value = span.getAttributes().get(AttributeKey.stringKey(key));
		return StringUtils.hasText(value) ? value : null;
	}

	private void evictOldAssemblies() {
		while (traceAssemblies.size() > MAX_TRACE_ASSEMBLIES) {
			String eldestTraceId = traceAssemblies.keySet().iterator().next();
			traceAssemblies.remove(eldestTraceId);
			removeSessionTraceByTraceId(eldestTraceId);
		}
	}

	private void evictOldSessionTraces() {
		while (latestTraceBySessionId.size() > MAX_SESSION_TRACES) {
			String eldestSessionId = latestTraceBySessionId.keySet().iterator().next();
			TraceView removed = latestTraceBySessionId.remove(eldestSessionId);
			if (removed != null) {
				traceAssemblies.remove(removed.traceId());
			}
		}
	}

	private void removeSessionTraceByTraceId(String traceId) {
		String matchedSessionId = null;
		for (Map.Entry<String, TraceView> entry : latestTraceBySessionId.entrySet()) {
			if (Objects.equals(entry.getValue().traceId(), traceId)) {
				matchedSessionId = entry.getKey();
				break;
			}
		}
		if (matchedSessionId != null) {
			latestTraceBySessionId.remove(matchedSessionId);
		}
	}

	private SpanView toSpanView(SpanData span) {
		Map<String, String> attributes = sanitizeAttributes(span);
		long startEpochNanos = span.getStartEpochNanos();
		long endEpochNanos = span.getEndEpochNanos();
		long durationMs = Math.max(0L, (endEpochNanos - startEpochNanos) / 1_000_000L);
		long startEpochMs = startEpochNanos / 1_000_000L;
		long endEpochMs = endEpochNanos / 1_000_000L;
		return new SpanView(span.getName(), span.getSpanContext().getSpanId(), span.getParentSpanContext().getSpanId(),
				span.getKind().name(), span.getStatus().getStatusCode().name(), startEpochMs, endEpochMs, durationMs,
				attributes, List.of());
	}

	private Map<String, String> sanitizeAttributes(SpanData span) {
		Map<String, String> attributes = new LinkedHashMap<>();
		span.getAttributes().forEach((key, value) -> {
			String attributeKey = key.getKey();
			attributes.put(attributeKey, sanitizeAttributeValue(attributeKey, String.valueOf(value)));
		});
		int omittedAttributeCount = Math.max(0, span.getTotalAttributeCount() - span.getAttributes().size());
		if (omittedAttributeCount > 0) {
			attributes.put(META_OMITTED_ATTRIBUTE_COUNT, String.valueOf(omittedAttributeCount));
		}
		return attributes;
	}

	private String sanitizeAttributeValue(String attributeKey, String value) {
		if (!StringUtils.hasText(value)) {
			return value;
		}
		String normalizedValue = value.replace('\r', ' ').replace('\n', ' ').trim();
		if (isSensitiveAttributeKey(attributeKey)) {
			return maskAttributeValue(normalizedValue);
		}
		if (normalizedValue.length() <= MAX_ATTRIBUTE_VALUE_LENGTH) {
			return normalizedValue;
		}
		return normalizedValue.substring(0, MAX_ATTRIBUTE_VALUE_LENGTH) + "...";
	}

	private boolean isSensitiveAttributeKey(String attributeKey) {
		if (!StringUtils.hasText(attributeKey)) {
			return false;
		}
		String normalizedKey = attributeKey.toLowerCase(Locale.ROOT).replace('-', '_').replace('.', '_');
		return SENSITIVE_ATTRIBUTE_KEY_TOKENS.stream().anyMatch(normalizedKey::contains);
	}

	private String maskAttributeValue(String value) {
		if (value.length() <= 8) {
			return "***";
		}
		return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
	}

	private static final class TraceAssembly {

		private String sessionId;

		private String traceId;

		private String runtimeRequestId;

		private String agentId;

		private final Map<String, SpanView> spansBySpanId = new LinkedHashMap<>();

		private TraceView toTraceView() {
			Map<String, MutableTreeNode> nodesById = new LinkedHashMap<>();
			for (SpanView span : spansBySpanId.values()) {
				nodesById.put(span.getSpanId(), new MutableTreeNode(span));
			}

			List<MutableTreeNode> roots = new ArrayList<>();
			for (MutableTreeNode node : nodesById.values()) {
				String parentSpanId = node.span.getParentSpanId();
				MutableTreeNode parent = nodesById.get(parentSpanId);
				if (!StringUtils.hasText(parentSpanId) || ROOT_PARENT_SPAN_ID.equals(parentSpanId) || parent == null) {
					roots.add(node);
				}
				else {
					parent.children.add(node);
				}
			}

			roots.sort(Comparator.comparingLong(node -> node.span.getStartEpochMs()));
			List<SpanView> rootViews = roots.stream().map(MutableTreeNode::toImmutable).toList();
			SpanView rootSpan = rootViews.isEmpty() ? null : rootViews.get(0);
			long startedAt = spansBySpanId.values()
				.stream()
				.mapToLong(SpanView::getStartEpochMs)
				.min()
				.orElse(0L);
			long endedAt = spansBySpanId.values().stream().mapToLong(SpanView::getEndEpochMs).max().orElse(0L);
			long durationMs = Math.max(0L, endedAt - startedAt);
			return new TraceView(sessionId, traceId, runtimeRequestId, agentId, startedAt, endedAt, durationMs,
					spansBySpanId.size(), rootSpan, rootViews);
		}

	}

	private static final class MutableTreeNode {

		private final SpanView span;

		private final List<MutableTreeNode> children = new ArrayList<>();

		private MutableTreeNode(SpanView span) {
			this.span = span;
		}

		private SpanView toImmutable() {
			children.sort(Comparator.comparingLong(node -> node.span.getStartEpochMs()));
			return new SpanView(span.getName(), span.getSpanId(), span.getParentSpanId(), span.getKind(), span.getStatus(),
					span.getStartEpochMs(), span.getEndEpochMs(), span.getDurationMs(), span.getAttributes(),
					children.stream().map(MutableTreeNode::toImmutable).toList());
		}

	}

	public record TraceView(String sessionId, String traceId, String runtimeRequestId, String agentId,
			long startEpochMs, long endEpochMs, long durationMs, int spanCount, SpanView rootSpan,
			List<SpanView> rootSpans) {
	}

	@Getter
	public static final class SpanView {

		private final String name;

		private final String spanId;

		private final String parentSpanId;

		private final String kind;

		private final String status;

		private final long startEpochMs;

		private final long endEpochMs;

		private final long durationMs;

		private final Map<String, String> attributes;

		private final List<SpanView> children;

		private SpanView(String name, String spanId, String parentSpanId, String kind, String status, long startEpochMs,
				long endEpochMs, long durationMs, Map<String, String> attributes, List<SpanView> children) {
			this.name = name;
			this.spanId = spanId;
			this.parentSpanId = parentSpanId;
			this.kind = kind;
			this.status = status;
			this.startEpochMs = startEpochMs;
			this.endEpochMs = endEpochMs;
			this.durationMs = durationMs;
			this.attributes = attributes;
			this.children = children;
		}

	}

}
