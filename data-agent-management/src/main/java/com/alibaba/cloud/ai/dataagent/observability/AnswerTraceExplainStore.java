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

import com.alibaba.cloud.ai.dataagent.agentscope.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.agentscope.tool.datasource.DatasourceExplorerResult;
import com.alibaba.cloud.ai.dataagent.agentscope.tool.semantic.SemanticModelSearchHit;
import com.alibaba.cloud.ai.dataagent.service.knowledge.DomainKnowledgeSearchService.DomainKnowledgeSearchResult;
import com.alibaba.cloud.ai.dataagent.service.knowledge.DomainKnowledgeSearchService.KnowledgeHit;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AnswerTraceExplainStore {

	private static final int MAX_SESSION_COUNT = 128;

	private static final int MAX_REQUEST_COUNT_PER_SESSION = 24;

	private final Object monitor = new Object();

	private final ThreadLocal<ExplainContext> currentContext = new ThreadLocal<>();

	private final LinkedHashMap<String, LinkedHashMap<String, ExplainAssembly>> explainsBySession = new LinkedHashMap<>(32,
			0.75f, true);

	public void openScope(GraphRequest request) {
		if (request == null || !StringUtils.hasText(request.getThreadId())
				|| !StringUtils.hasText(request.getRuntimeRequestId())) {
			return;
		}
		synchronized (monitor) {
			ExplainAssembly assembly = resolveAssemblyLocked(request.getThreadId(), request.getRuntimeRequestId());
			applyRequestContext(assembly, request);
			assembly.updatedAt = Instant.now().toEpochMilli();
			currentContext.set(new ExplainContext(request.getThreadId(), request.getRuntimeRequestId()));
			evictOverflowLocked();
		}
	}

	public void closeScope() {
		currentContext.remove();
	}

	public void recordFinalAnswer(String answer) {
		withCurrentAssembly(assembly -> {
			assembly.answer = answer;
			assembly.updatedAt = Instant.now().toEpochMilli();
		});
	}

	public void recordWarning(String warning) {
		if (!StringUtils.hasText(warning)) {
			return;
		}
		withCurrentAssembly(assembly -> {
			assembly.warnings.add(warning.trim());
			assembly.updatedAt = Instant.now().toEpochMilli();
		});
	}

	public void recordSemanticSearch(String query, String summary, List<SemanticModelSearchHit> hits) {
		withCurrentAssembly(assembly -> applySemanticSearch(assembly, query, summary, hits));
	}

	public void recordSemanticSearch(GraphRequest request, String query, String summary, List<SemanticModelSearchHit> hits) {
		withAssembly(request, assembly -> applySemanticSearch(assembly, query, summary, hits));
	}

	public void recordKnowledgeSearch(DomainKnowledgeSearchResult result) {
		if (result == null) {
			return;
		}
		withCurrentAssembly(assembly -> applyKnowledgeSearch(assembly, result));
	}

	public void recordKnowledgeSearch(GraphRequest request, DomainKnowledgeSearchResult result) {
		if (result == null) {
			return;
		}
		withAssembly(request, assembly -> applyKnowledgeSearch(assembly, result));
	}

	public void recordDatasourceResult(DatasourceExplorerResult result) {
		if (result == null) {
			return;
		}
		withCurrentAssembly(assembly -> applyDatasourceResult(assembly, result));
	}

	public void recordDatasourceResult(GraphRequest request, DatasourceExplorerResult result) {
		if (result == null) {
			return;
		}
		withAssembly(request, assembly -> applyDatasourceResult(assembly, result));
	}

	public Optional<AnswerTraceExplainView> getExplain(String sessionId, String runtimeRequestId) {
		if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(runtimeRequestId)) {
			return Optional.empty();
		}
		synchronized (monitor) {
			LinkedHashMap<String, ExplainAssembly> explainsByRequest = explainsBySession.get(sessionId);
			if (explainsByRequest == null) {
				return Optional.empty();
			}
			ExplainAssembly assembly = explainsByRequest.get(runtimeRequestId);
			if (assembly == null) {
				return Optional.empty();
			}
			return Optional.of(assembly.toView());
		}
	}

	public Optional<ExplainMirrorSummary> getMirrorSummary(String sessionId, String runtimeRequestId) {
		return getExplain(sessionId, runtimeRequestId).map(explain -> ExplainMirrorSummary.builder()
			.datasource(explain.getDatasource())
			.usedTables(explain.getUsedTables())
			.semanticHitCount(explain.getSemanticHits() == null ? 0 : explain.getSemanticHits().size())
			.knowledgeHitCount(explain.getKnowledgeHits() == null ? 0 : explain.getKnowledgeHits().size())
			.toolStepCount(explain.getToolSteps() == null ? 0 : explain.getToolSteps().size())
			.build());
	}

	private void withCurrentAssembly(java.util.function.Consumer<ExplainAssembly> consumer) {
		ExplainContext context = currentContext.get();
		if (context == null) {
			return;
		}
		synchronized (monitor) {
			ExplainAssembly assembly = resolveAssemblyLocked(context.sessionId, context.runtimeRequestId);
			consumer.accept(assembly);
		}
	}

	private void withAssembly(GraphRequest request, java.util.function.Consumer<ExplainAssembly> consumer) {
		if (request == null || !StringUtils.hasText(request.getThreadId())
				|| !StringUtils.hasText(request.getRuntimeRequestId())) {
			return;
		}
		synchronized (monitor) {
			ExplainAssembly assembly = resolveAssemblyLocked(request.getThreadId(), request.getRuntimeRequestId());
			applyRequestContext(assembly, request);
			consumer.accept(assembly);
			evictOverflowLocked();
		}
	}

	private ExplainAssembly resolveAssemblyLocked(String sessionId, String runtimeRequestId) {
		LinkedHashMap<String, ExplainAssembly> explainsByRequest = explainsBySession.computeIfAbsent(sessionId,
				ignored -> new LinkedHashMap<>(8, 0.75f, true));
		return explainsByRequest.computeIfAbsent(runtimeRequestId, ignored -> new ExplainAssembly());
	}

	private void applyRequestContext(ExplainAssembly assembly, GraphRequest request) {
		if (assembly == null || request == null) {
			return;
		}
		if (StringUtils.hasText(request.getThreadId())) {
			assembly.sessionId = request.getThreadId();
		}
		if (StringUtils.hasText(request.getRuntimeRequestId())) {
			assembly.runtimeRequestId = request.getRuntimeRequestId();
		}
		if (StringUtils.hasText(request.getAgentId())) {
			assembly.agentId = request.getAgentId();
		}
		if (StringUtils.hasText(request.getQuery())) {
			assembly.question = request.getQuery();
		}
	}

	private void applySemanticSearch(ExplainAssembly assembly, String query, String summary, List<SemanticModelSearchHit> hits) {
		assembly.toolSteps.add(ToolStepView.builder()
			.toolName("semantic_model.search")
			.title("语义匹配")
			.summary(summary)
			.detail(query)
			.timestampEpochMs(Instant.now().toEpochMilli())
			.build());
		if (hits != null) {
			for (SemanticModelSearchHit hit : hits) {
				if (hit == null) {
					continue;
				}
				assembly.semanticHits.add(SemanticHitView.builder()
					.tableName(hit.getTableName())
					.columnName(hit.getColumnName())
					.businessName(hit.getBusinessName())
					.businessDescription(hit.getBusinessDescription())
					.matchedBy(hit.getMatchedBy())
					.score(hit.getScore())
					.relationHint(hit.getRelationHint())
					.build());
			}
		}
		assembly.updatedAt = Instant.now().toEpochMilli();
	}

	private void applyKnowledgeSearch(ExplainAssembly assembly, DomainKnowledgeSearchResult result) {
		assembly.toolSteps.add(ToolStepView.builder()
			.toolName("domain_business_knowledge.search")
			.title("业务知识检索")
			.summary("检索到 %d 条知识命中".formatted(result.hits() == null ? 0 : result.hits().size()))
			.detail(result.query())
			.timestampEpochMs(Instant.now().toEpochMilli())
			.build());
		if (result.hits() != null) {
			for (KnowledgeHit hit : result.hits()) {
				if (hit == null) {
					continue;
				}
				assembly.knowledgeHits.add(KnowledgeHitView.builder()
					.vectorType(hit.vectorType())
					.knowledgeId(hit.knowledgeId())
					.title(hit.title())
					.summary(hit.summary())
					.snippet(hit.snippet())
					.source(hit.source())
					.concreteType(hit.concreteType())
					.build());
			}
		}
		if (result.warnings() != null) {
			assembly.warnings.addAll(result.warnings().stream().filter(StringUtils::hasText).map(String::trim).toList());
		}
		assembly.updatedAt = Instant.now().toEpochMilli();
	}

	private void applyDatasourceResult(ExplainAssembly assembly, DatasourceExplorerResult result) {
		if (StringUtils.hasText(result.getDatasource())) {
			assembly.datasource = result.getDatasource();
		}
		if (StringUtils.hasText(result.getSql())) {
			assembly.sql = result.getSql();
		}
		if (StringUtils.hasText(result.getSqlExplanation())) {
			assembly.sqlExplanation = result.getSqlExplanation();
		}
		mergeOrdered(assembly.usedTables, result.getUsedTables());
		mergeOrdered(assembly.usedColumns, result.getUsedColumns());
		mergeMap(assembly.permissions, result.getPermissions());
		mergeMap(assembly.stats, result.getStats());
		assembly.toolSteps.add(ToolStepView.builder()
			.toolName("datasource.explorer")
			.title(result.getAction())
			.summary(result.getSummary())
			.detail(result.getSql())
			.datasource(result.getDatasource())
			.timestampEpochMs(Instant.now().toEpochMilli())
			.build());
		assembly.updatedAt = Instant.now().toEpochMilli();
	}

	private void mergeOrdered(Set<String> target, Collection<String> source) {
		if (source == null) {
			return;
		}
		for (String item : source) {
			if (StringUtils.hasText(item)) {
				target.add(item.trim());
			}
		}
	}

	private void mergeMap(Map<String, Object> target, Map<String, Object> source) {
		if (source == null) {
			return;
		}
		source.forEach((key, value) -> {
			if (!StringUtils.hasText(key) || value == null) {
				return;
			}
			target.put(key, value);
		});
	}

	private void evictOverflowLocked() {
		while (explainsBySession.size() > MAX_SESSION_COUNT) {
			String eldestSessionId = explainsBySession.keySet().iterator().next();
			explainsBySession.remove(eldestSessionId);
		}
		explainsBySession.values().forEach(this::evictRequestOverflowLocked);
	}

	private void evictRequestOverflowLocked(LinkedHashMap<String, ExplainAssembly> explainsByRequest) {
		while (explainsByRequest.size() > MAX_REQUEST_COUNT_PER_SESSION) {
			String eldestRequestId = explainsByRequest.keySet().iterator().next();
			explainsByRequest.remove(eldestRequestId);
		}
	}

	private record ExplainContext(String sessionId, String runtimeRequestId) {
	}

	private static final class ExplainAssembly {

		private String sessionId;

		private String runtimeRequestId;

		private String agentId;

		private String question;

		private String answer;

		private String datasource;

		private String sql;

		private String sqlExplanation;

		private final List<SemanticHitView> semanticHits = new ArrayList<>();

		private final List<KnowledgeHitView> knowledgeHits = new ArrayList<>();

		private final List<ToolStepView> toolSteps = new ArrayList<>();

		private final Set<String> usedTables = new LinkedHashSet<>();

		private final Set<String> usedColumns = new LinkedHashSet<>();

		private final Map<String, Object> permissions = new LinkedHashMap<>();

		private final Map<String, Object> stats = new LinkedHashMap<>();

		private final Set<String> warnings = new LinkedHashSet<>();

		private long updatedAt;

		private AnswerTraceExplainView toView() {
			return AnswerTraceExplainView.builder()
				.sessionId(sessionId)
				.runtimeRequestId(runtimeRequestId)
				.agentId(agentId)
				.question(question)
				.answer(answer)
				.datasource(datasource)
				.sql(sql)
				.sqlExplanation(sqlExplanation)
				.semanticHits(List.copyOf(semanticHits))
				.knowledgeHits(List.copyOf(knowledgeHits))
				.toolSteps(List.copyOf(toolSteps))
				.usedTables(List.copyOf(usedTables))
				.usedColumns(List.copyOf(usedColumns))
				.permissions(new LinkedHashMap<>(permissions))
				.stats(new LinkedHashMap<>(stats))
				.warnings(List.copyOf(warnings))
				.updatedAt(updatedAt)
				.build();
		}
	}

	@Data
	@Builder
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class AnswerTraceExplainView {

		private String sessionId;

		private String runtimeRequestId;

		private String agentId;

		private String question;

		private String answer;

		private String datasource;

		private String sql;

		private String sqlExplanation;

		@Builder.Default
		private List<SemanticHitView> semanticHits = List.of();

		@Builder.Default
		private List<KnowledgeHitView> knowledgeHits = List.of();

		@Builder.Default
		private List<ToolStepView> toolSteps = List.of();

		@Builder.Default
		private List<String> usedTables = List.of();

		@Builder.Default
		private List<String> usedColumns = List.of();

		@Builder.Default
		private Map<String, Object> permissions = Map.of();

		@Builder.Default
		private Map<String, Object> stats = Map.of();

		@Builder.Default
		private List<String> warnings = List.of();

		private long updatedAt;
	}

	@Data
	@Builder
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class SemanticHitView {

		private String tableName;

		private String columnName;

		private String businessName;

		private String businessDescription;

		private String matchedBy;

		private Integer score;

		private String relationHint;
	}

	@Data
	@Builder
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class KnowledgeHitView {

		private String vectorType;

		private String knowledgeId;

		private String title;

		private String summary;

		private String snippet;

		private String source;

		private String concreteType;
	}

	@Data
	@Builder
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class ToolStepView {

		private String toolName;

		private String title;

		private String summary;

		private String detail;

		private String datasource;

		private long timestampEpochMs;
	}

	@Data
	@Builder
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class ExplainMirrorSummary {

		private String datasource;

		@Builder.Default
		private List<String> usedTables = List.of();

		private int semanticHitCount;

		private int knowledgeHitCount;

		private int toolStepCount;
	}

}
