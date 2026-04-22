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
package com.alibaba.cloud.ai.dataagent.agentscope.tool.semantic;

import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticModelService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SemanticModelSearchService {

	private static final int DEFAULT_MAX_HITS = 8;

	private final AgentDatasourceService agentDatasourceService;

	private final SemanticModelService semanticModelService;

	public SemanticModelSearchResult search(String agentId, SemanticModelSearchRequest request) {
		if (!StringUtils.hasText(agentId)) {
			return emptyResult(request == null ? null : request.getQuery(),
					"semantic_model.search requires a numeric agent id.");
		}
		Long parsedAgentId;
		try {
			parsedAgentId = Long.valueOf(agentId);
		}
		catch (NumberFormatException ex) {
			return emptyResult(request == null ? null : request.getQuery(),
					"semantic_model.search requires a numeric agent id.");
		}
		return search(parsedAgentId, request);
	}

	public SemanticModelSearchResult search(Long agentId, SemanticModelSearchRequest request) {
		String query = request == null ? null : request.getQuery();
		if (!StringUtils.hasText(query)) {
			throw new IllegalArgumentException("query is required for semantic_model.search");
		}
		AgentDatasource activeDatasource = resolveActiveDatasource(agentId);
		if (activeDatasource == null || activeDatasource.getDatasourceId() == null) {
			return emptyResult(query, "No active datasource is available for semantic_model.search.");
		}
		TableSearchScope scope = resolveTableSearchScope(activeDatasource,
				request == null ? null : request.getTableNames());
		if (scope.isScoped() && CollectionUtils.isEmpty(scope.getTableNames())) {
			return emptyResult(query,
					"Requested tables are outside the active datasource visibility scope for semantic_model.search.");
		}
		List<SemanticModel> candidates = scope.isUnbounded()
				? semanticModelService.getEnabledByAgentIdAndDatasourceId(agentId, activeDatasource.getDatasourceId())
				: semanticModelService.getEnabledByAgentIdAndDatasourceIdAndTableNames(agentId,
						activeDatasource.getDatasourceId(), scope.getTableNames());
		if (CollectionUtils.isEmpty(candidates)) {
			return emptyResult(query,
					"No enabled semantic model entries matched this agent/table scope. Use datasource explorer for physical schema details.");
		}

		List<ScoredHit> scoredHits = candidates.stream()
			.map(candidate -> score(query, candidate))
			.filter(Objects::nonNull)
			.sorted(Comparator.comparingInt(ScoredHit::getScore)
				.reversed()
				.thenComparing(scoredHit -> scoredHit.getModel().getCreatedTime(),
						Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(scoredHit -> scoredHit.getModel().getTableName(),
						Comparator.nullsLast(String::compareToIgnoreCase))
				.thenComparing(scoredHit -> scoredHit.getModel().getColumnName(),
						Comparator.nullsLast(String::compareToIgnoreCase)))
			.limit(DEFAULT_MAX_HITS)
			.toList();

		if (scoredHits.isEmpty()) {
			return emptyResult(query,
					"No supplemental semantic hints matched the query. If datasource explorer already answers the schema question, do not call semantic_model.search.");
		}

		List<SemanticModelSearchHit> hits = scoredHits.stream().map(this::toHit).toList();
		return SemanticModelSearchResult.builder()
			.query(query)
			.summary(
					"Found %d supplemental semantic hints. These are auxiliary explanations for table/column understanding, not a replacement for datasource exploration."
						.formatted(hits.size()))
			.hits(hits)
			.build();
	}

	private SemanticModelSearchResult emptyResult(String query, String summary) {
		return SemanticModelSearchResult.builder().query(query).summary(summary).build();
	}

	private AgentDatasource resolveActiveDatasource(Long agentId) {
		try {
			return agentDatasourceService.getCurrentAgentDatasource(agentId);
		}
		catch (Exception ex) {
			return null;
		}
	}

	private SemanticModelSearchHit toHit(ScoredHit scoredHit) {
		SemanticModel model = scoredHit.getModel();
		return SemanticModelSearchHit.builder()
			.tableName(model.getTableName())
			.columnName(model.getColumnName())
			.businessName(model.getBusinessName())
			.businessDescription(model.getBusinessDescription())
			.synonyms(model.getSynonyms())
			.columnComment(model.getColumnComment())
			.dataType(model.getDataType())
			.relationHint(extractRelationHint(model))
			.matchedBy(String.join(", ", scoredHit.getMatchedBy()))
			.build();
	}

	private String extractRelationHint(SemanticModel model) {
		String[] candidates = { model.getBusinessDescription(), model.getColumnComment() };
		for (String candidate : candidates) {
			if (!StringUtils.hasText(candidate)) {
				continue;
			}
			String normalized = candidate.trim();
			if (normalized.contains("关联") || normalized.toLowerCase(Locale.ROOT).contains("join")
					|| normalized.contains("映射") || normalized.contains("外键")) {
				return normalized;
			}
		}
		return null;
	}

	private ScoredHit score(String query, SemanticModel model) {
		String normalizedQuery = normalize(query);
		List<String> tokens = tokenize(query);
		Set<String> matchedBy = new LinkedHashSet<>();
		int score = 0;
		score += scoreField(model.getBusinessName(), normalizedQuery, tokens, "businessName", 120, 80, 36, 12,
				matchedBy);
		score += scoreSynonyms(model.getSynonyms(), normalizedQuery, tokens, matchedBy);
		score += scoreField(model.getColumnName(), normalizedQuery, tokens, "columnName", 110, 74, 30, 10, matchedBy);
		score += scoreField(model.getTableName(), normalizedQuery, tokens, "tableName", 64, 42, 18, 6, matchedBy);
		score += scoreField(model.getBusinessDescription(), normalizedQuery, tokens, "businessDescription", 48, 30, 12,
				4, matchedBy);
		score += scoreField(model.getColumnComment(), normalizedQuery, tokens, "columnComment", 40, 24, 10, 3,
				matchedBy);
		score += scoreField(model.getDataType(), normalizedQuery, tokens, "dataType", 20, 14, 6, 2, matchedBy);
		if (score <= 0) {
			return null;
		}
		return new ScoredHit(model, score, List.copyOf(matchedBy));
	}

	private int scoreSynonyms(String synonyms, String normalizedQuery, List<String> tokens, Set<String> matchedBy) {
		if (!StringUtils.hasText(synonyms)) {
			return 0;
		}
		int score = 0;
		for (String synonym : splitSynonyms(synonyms)) {
			score += scoreField(synonym, normalizedQuery, tokens, "synonym", 108, 72, 28, 10, matchedBy);
		}
		return score;
	}

	private int scoreField(String fieldValue, String normalizedQuery, List<String> tokens, String matchedLabel,
			int exactScore, int containsScore, int tokenExactScore, int tokenContainsScore, Set<String> matchedBy) {
		if (!StringUtils.hasText(fieldValue) || !StringUtils.hasText(normalizedQuery)) {
			return 0;
		}
		String normalizedField = normalize(fieldValue);
		if (!StringUtils.hasText(normalizedField)) {
			return 0;
		}
		int score = 0;
		if (normalizedField.equals(normalizedQuery)) {
			score += exactScore;
		}
		else if (normalizedField.contains(normalizedQuery) || normalizedQuery.contains(normalizedField)) {
			score += containsScore;
		}
		for (String token : tokens) {
			if (!StringUtils.hasText(token)) {
				continue;
			}
			if (normalizedField.equals(token)) {
				score += tokenExactScore;
			}
			else if (normalizedField.contains(token)) {
				score += tokenContainsScore;
			}
		}
		if (score > 0) {
			matchedBy.add(matchedLabel);
		}
		return score;
	}

	private List<String> normalizeTableNames(List<String> tableNames) {
		if (CollectionUtils.isEmpty(tableNames)) {
			return List.of();
		}
		return tableNames.stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.map(tableName -> tableName.toLowerCase(Locale.ROOT))
			.distinct()
			.toList();
	}

	private TableSearchScope resolveTableSearchScope(AgentDatasource activeDatasource, List<String> requestTableNames) {
		List<String> selectedTables = normalizeTableNames(activeDatasource.getSelectTables());
		List<String> requestedTables = normalizeTableNames(requestTableNames);
		if (CollectionUtils.isEmpty(selectedTables)) {
			return CollectionUtils.isEmpty(requestedTables) ? TableSearchScope.unbounded()
					: TableSearchScope.scoped(requestedTables);
		}
		if (CollectionUtils.isEmpty(requestedTables)) {
			return TableSearchScope.scoped(selectedTables);
		}
		return TableSearchScope.scoped(requestedTables.stream().filter(selectedTables::contains).distinct().toList());
	}

	private List<String> splitSynonyms(String synonyms) {
		String[] items = synonyms.split("[,，;；/|、\\s]+");
		List<String> result = new ArrayList<>(items.length);
		for (String item : items) {
			if (StringUtils.hasText(item)) {
				result.add(item.trim());
			}
		}
		return result;
	}

	private List<String> tokenize(String query) {
		String normalized = normalize(query);
		if (!StringUtils.hasText(normalized)) {
			return List.of();
		}
		String[] pieces = normalized.split("\\s+");
		List<String> result = new ArrayList<>(pieces.length + 1);
		result.add(normalized);
		for (String piece : pieces) {
			if (StringUtils.hasText(piece)) {
				result.add(piece.trim());
			}
		}
		return result.stream().filter(StringUtils::hasText).distinct().toList();
	}

	private String normalize(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		return value.trim()
			.toLowerCase(Locale.ROOT)
			.replace('_', ' ')
			.replace('-', ' ')
			.replaceAll("[()\\[\\]{}]", " ")
			.replaceAll("[,，;；/|、]+", " ")
			.replaceAll("\\s+", " ");
	}

	private static final class ScoredHit {

		private final SemanticModel model;

		private final int score;

		private final List<String> matchedBy;

		private ScoredHit(SemanticModel model, int score, List<String> matchedBy) {
			this.model = model;
			this.score = score;
			this.matchedBy = matchedBy;
		}

		private SemanticModel getModel() {
			return model;
		}

		private int getScore() {
			return score;
		}

		private List<String> getMatchedBy() {
			return matchedBy;
		}

	}

	private static final class TableSearchScope {

		private final boolean unbounded;

		private final List<String> tableNames;

		private TableSearchScope(boolean unbounded, List<String> tableNames) {
			this.unbounded = unbounded;
			this.tableNames = tableNames;
		}

		private static TableSearchScope unbounded() {
			return new TableSearchScope(true, List.of());
		}

		private static TableSearchScope scoped(List<String> tableNames) {
			return new TableSearchScope(false, List.copyOf(tableNames));
		}

		private boolean isUnbounded() {
			return unbounded;
		}

		private boolean isScoped() {
			return !unbounded;
		}

		private List<String> getTableNames() {
			return tableNames;
		}

	}

}
