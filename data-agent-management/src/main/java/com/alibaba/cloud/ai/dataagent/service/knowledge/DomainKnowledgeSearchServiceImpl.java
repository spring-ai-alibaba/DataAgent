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
package com.alibaba.cloud.ai.dataagent.service.knowledge;

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.entity.BusinessKnowledge;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.mapper.BusinessKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.DynamicFilterService;
import com.alibaba.cloud.ai.dataagent.service.knowledge.DomainKnowledgeSearchService.DomainKnowledgeSearchRequest;
import com.alibaba.cloud.ai.dataagent.service.knowledge.DomainKnowledgeSearchService.SearchDiagnostics;
import com.alibaba.cloud.ai.dataagent.service.knowledge.DomainKnowledgeSearchService.DomainKnowledgeSearchResult;
import com.alibaba.cloud.ai.dataagent.service.knowledge.DomainKnowledgeSearchService.KnowledgeHit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class DomainKnowledgeSearchServiceImpl implements DomainKnowledgeSearchService {

	private static final int DEFAULT_TOP_K = 5;

	private static final int MAX_TOP_K = 8;

	private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.2D;

	private static final int VECTOR_READY_PROBE_TOP_K = 5;

	private static final int MAX_SUMMARY_LENGTH = 180;

	private static final int MAX_SNIPPET_LENGTH = 320;

	private final AgentVectorStoreService agentVectorStoreService;

	private final BusinessKnowledgeMapper businessKnowledgeMapper;

	private final AgentKnowledgeMapper agentKnowledgeMapper;

	private final DynamicFilterService dynamicFilterService;

	@Override
	public DomainKnowledgeSearchResult search(String agentId, DomainKnowledgeSearchRequest request) {
		Assert.hasText(agentId, "AgentId cannot be empty");
		Assert.notNull(request, "Search request cannot be null");
		String query = requireText(request.query(), "Query cannot be blank");
		NormalizedSearchOptions options = normalizeOptions(request);
		String vectorQuery = buildVectorQuery(query);
		double effectiveThreshold = resolveEffectiveThreshold(query, options.similarityThreshold());
		CategoryDiagnostics businessTermDiagnostics = inspectCategory(agentId, SearchCategory.BUSINESS_TERM);
		CategoryDiagnostics agentKnowledgeDiagnostics = inspectCategory(agentId, SearchCategory.AGENT_KNOWLEDGE);

		List<KnowledgeHit> hits = new ArrayList<>();
		List<String> warnings = new ArrayList<>(options.warnings());

		for (SearchCategory category : options.categories()) {
			SearchReadiness readiness = diagnoseCategoryForSearch(
					category == SearchCategory.BUSINESS_TERM ? businessTermDiagnostics : agentKnowledgeDiagnostics);
			warnings.addAll(readiness.warnings());
			if (!readiness.searchable()) {
				continue;
			}
			int beforeSize = hits.size();
			CategoryDiagnostics categoryDiagnostics = category == SearchCategory.BUSINESS_TERM ? businessTermDiagnostics
					: agentKnowledgeDiagnostics;
			switch (category) {
				case BUSINESS_TERM ->
					hits.addAll(searchBusinessKnowledge(agentId, vectorQuery, options.topK(), effectiveThreshold));
				case AGENT_KNOWLEDGE -> hits.addAll(searchAgentKnowledge(agentId, vectorQuery, options.topK(),
						effectiveThreshold, options.agentKnowledgeTypes(), options.filterAgentKnowledgeByType()));
			}
			if (hits.size() == beforeSize && categoryDiagnostics != null && categoryDiagnostics.recallEnabled()
					&& !categoryDiagnostics.vectorReady()) {
				appendWarning(warnings, "当前 Agent 已启用 " + categoryDiagnostics.category().knowledgeLabel()
						+ " 召回，但诊断探测未确认向量命中。若结果持续为空，请检查 embeddingStatus，必要时执行 retry embedding。");
			}
			if (category == SearchCategory.AGENT_KNOWLEDGE && options.filterAgentKnowledgeByType()
					&& hits.size() == beforeSize) {
				appendWarning(warnings,
						"已启用 agentKnowledge 检索，但在指定 knowledgeTypes 下未命中。请确认目标条目的 type、isRecall 和 embeddingStatus。");
			}
		}

		if (hits.size() > options.topK()) {
			hits = new ArrayList<>(hits.subList(0, options.topK()));
		}

		if (hits.isEmpty() && warnings.isEmpty()) {
			warnings.add("未检索到匹配的业务知识，请缩短问题或换一种业务说法重试。");
		}

		SearchDiagnostics diagnostics = new SearchDiagnostics(agentId, businessTermDiagnostics.recalledCount(),
				agentKnowledgeDiagnostics.recalledCount(), businessTermDiagnostics.vectorReady(),
				agentKnowledgeDiagnostics.vectorReady());
		return new DomainKnowledgeSearchResult(query, List.copyOf(options.appliedKnowledgeTypes()), List.copyOf(hits),
				List.copyOf(warnings), diagnostics);
	}

	private String buildVectorQuery(String rawQuery) {
		String query = rawQuery.trim();
		if (!isShortCodeQuery(query)) {
			return query;
		}
		return "业务术语 " + query + " 的定义、说明、同义词、FAQ、SOP、历史案例";
	}

	private double resolveEffectiveThreshold(String rawQuery, double similarityThreshold) {
		String query = rawQuery.trim();
		if (!isShortCodeQuery(query)) {
			return similarityThreshold;
		}
		if (query.length() <= 4) {
			return 0.0D;
		}
		return Math.min(similarityThreshold, 0.05D);
	}

	private boolean isShortCodeQuery(String query) {
		if (!StringUtils.hasText(query)) {
			return false;
		}
		String normalized = query.trim();
		if (normalized.length() > 8 || normalized.contains(" ")) {
			return false;
		}
		if (normalized.matches(".*[\\u4e00-\\u9fa5].*")) {
			return false;
		}
		return normalized.matches("[A-Za-z0-9_-]+");
	}

	private CategoryDiagnostics inspectCategory(String agentId, SearchCategory category) {
		String vectorType = category.vectorType();
		Integer recalledCount = category == SearchCategory.BUSINESS_TERM
				? businessKnowledgeMapper.selectRecalledKnowledgeIds(Long.valueOf(agentId)).size()
				: agentKnowledgeMapper.selectRecalledKnowledgeIds(Integer.valueOf(agentId)).size();
		Filter.Expression filter = dynamicFilterService.buildDynamicFilter(agentId, vectorType);
		if (filter == null) {
			return new CategoryDiagnostics(category, recalledCount, false, false);
		}
		List<Document> documents = agentVectorStoreService.getDocumentsOnlyByFilter(filter, VECTOR_READY_PROBE_TOP_K);
		return new CategoryDiagnostics(category, recalledCount, true, !documents.isEmpty());
	}

	private SearchReadiness diagnoseCategory(CategoryDiagnostics diagnostics) {
		if (diagnostics == null) {
			return SearchReadiness.READY;
		}
		String knowledgeLabel = diagnostics.category().knowledgeLabel();
		if (!diagnostics.recallEnabled()) {
			return new SearchReadiness(false, List.of("当前 Agent 没有启用可召回的 " + knowledgeLabel + " 条目。"));
		}
		if (!diagnostics.vectorReady()) {
			return new SearchReadiness(false, List
				.of("当前 Agent 已启用 " + knowledgeLabel + " 召回，但向量库中没有可检索文档。请检查 embeddingStatus，必要时执行 retry embedding。"));
		}
		return SearchReadiness.READY;
	}

	private SearchReadiness diagnoseCategoryForSearch(CategoryDiagnostics diagnostics) {
		if (diagnostics == null) {
			return SearchReadiness.READY;
		}
		if (!diagnostics.recallEnabled()) {
			return diagnoseCategory(diagnostics);
		}
		return SearchReadiness.READY;
	}

	private List<KnowledgeHit> searchBusinessKnowledge(String agentId, String query, int topK,
			double similarityThreshold) {
		List<Document> documents = agentVectorStoreService.getDocumentsForAgent(agentId, query,
				DocumentMetadataConstant.BUSINESS_TERM, topK, similarityThreshold);
		List<KnowledgeHit> hits = new ArrayList<>();
		for (Document document : documents) {
			Long knowledgeId = asLong(document.getMetadata().get(DocumentMetadataConstant.DB_BUSINESS_TERM_ID));
			if (knowledgeId == null) {
				log.debug("Skip business knowledge document without id metadata. documentId={}", document.getId());
				continue;
			}
			BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(knowledgeId);
			if (knowledge == null || knowledge.getIsDeleted() != null && knowledge.getIsDeleted() == 1) {
				continue;
			}
			if (knowledge.getIsRecall() == null || knowledge.getIsRecall() != 1) {
				continue;
			}
			hits.add(new KnowledgeHit(DocumentMetadataConstant.BUSINESS_TERM, String.valueOf(knowledgeId),
					knowledge.getBusinessTerm(), abbreviate(knowledge.getDescription(), MAX_SUMMARY_LENGTH),
					abbreviate(document.getText(), MAX_SNIPPET_LENGTH), "businessKnowledge#" + knowledgeId, null));
		}
		return hits;
	}

	private List<KnowledgeHit> searchAgentKnowledge(String agentId, String query, int topK, double similarityThreshold,
			Set<KnowledgeType> allowedTypes, boolean filterByConcreteType) {
		List<Document> documents = agentVectorStoreService.getDocumentsForAgent(agentId, query,
				DocumentMetadataConstant.AGENT_KNOWLEDGE, topK, similarityThreshold);
		List<KnowledgeHit> hits = new ArrayList<>();
		for (Document document : documents) {
			Integer knowledgeId = asInteger(document.getMetadata().get(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID));
			if (knowledgeId == null) {
				log.debug("Skip agent knowledge document without id metadata. documentId={}", document.getId());
				continue;
			}
			AgentKnowledge knowledge = agentKnowledgeMapper.selectById(knowledgeId);
			if (knowledge == null || knowledge.getIsDeleted() != null && knowledge.getIsDeleted() == 1) {
				continue;
			}
			if (filterByConcreteType && !allowedTypes.contains(knowledge.getType())) {
				continue;
			}
			hits.add(new KnowledgeHit(DocumentMetadataConstant.AGENT_KNOWLEDGE, String.valueOf(knowledgeId),
					resolveAgentKnowledgeTitle(knowledge), resolveAgentKnowledgeSummary(knowledge),
					resolveAgentKnowledgeSnippet(document, knowledge), "agentKnowledge#" + knowledgeId,
					knowledge.getType() == null ? null : knowledge.getType().getCode()));
		}
		return hits;
	}

	private String resolveAgentKnowledgeTitle(AgentKnowledge knowledge) {
		if (StringUtils.hasText(knowledge.getTitle())) {
			return knowledge.getTitle().trim();
		}
		if (StringUtils.hasText(knowledge.getQuestion())) {
			return abbreviate(knowledge.getQuestion(), 60);
		}
		return "未命名知识";
	}

	private String resolveAgentKnowledgeSummary(AgentKnowledge knowledge) {
		if (knowledge.getType() == KnowledgeType.QA || knowledge.getType() == KnowledgeType.FAQ) {
			return abbreviate(firstNonBlank(knowledge.getContent(), knowledge.getQuestion()), MAX_SUMMARY_LENGTH);
		}
		return abbreviate(firstNonBlank(knowledge.getTitle(), knowledge.getSourceFilename(), knowledge.getQuestion()),
				MAX_SUMMARY_LENGTH);
	}

	private String resolveAgentKnowledgeSnippet(Document document, AgentKnowledge knowledge) {
		if (knowledge.getType() == KnowledgeType.QA || knowledge.getType() == KnowledgeType.FAQ) {
			return abbreviate(
					"Q: " + defaultText(knowledge.getQuestion()) + "\nA: " + defaultText(knowledge.getContent()),
					MAX_SNIPPET_LENGTH);
		}
		return abbreviate(firstNonBlank(document.getText(), knowledge.getContent(), knowledge.getQuestion()),
				MAX_SNIPPET_LENGTH);
	}

	private NormalizedSearchOptions normalizeOptions(DomainKnowledgeSearchRequest request) {
		int normalizedTopK = normalizeTopK(request.topK());
		double normalizedThreshold = normalizeThreshold(request.similarityThreshold());
		List<String> warnings = new ArrayList<>();
		List<String> appliedTypes = new ArrayList<>();
		LinkedHashSet<SearchCategory> categories = new LinkedHashSet<>();
		LinkedHashSet<KnowledgeType> agentKnowledgeTypes = new LinkedHashSet<>();
		boolean filterAgentKnowledgeByType = false;

		if (CollectionUtils.isEmpty(request.knowledgeTypes())) {
			categories.add(SearchCategory.BUSINESS_TERM);
			categories.add(SearchCategory.AGENT_KNOWLEDGE);
			appliedTypes.add("businessTerm");
			appliedTypes.add("agentKnowledge");
			return new NormalizedSearchOptions(List.copyOf(appliedTypes), List.copyOf(categories),
					Set.copyOf(agentKnowledgeTypes), false, normalizedTopK, normalizedThreshold, List.copyOf(warnings));
		}

		for (String rawType : request.knowledgeTypes()) {
			if (!StringUtils.hasText(rawType)) {
				continue;
			}
			String normalizedType = rawType.trim().toLowerCase(Locale.ROOT);
			switch (normalizedType) {
				case "all" -> {
					categories.add(SearchCategory.BUSINESS_TERM);
					categories.add(SearchCategory.AGENT_KNOWLEDGE);
					addAppliedType(appliedTypes, "businessTerm");
					addAppliedType(appliedTypes, "agentKnowledge");
					filterAgentKnowledgeByType = false;
				}
				case "businessterm", "business_term", "business", "businessknowledge" -> {
					categories.add(SearchCategory.BUSINESS_TERM);
					addAppliedType(appliedTypes, "businessTerm");
				}
				case "agentknowledge", "agent_knowledge", "agent", "knowledge" -> {
					categories.add(SearchCategory.AGENT_KNOWLEDGE);
					addAppliedType(appliedTypes, "agentKnowledge");
					filterAgentKnowledgeByType = false;
				}
				case "document", "doc" -> {
					categories.add(SearchCategory.AGENT_KNOWLEDGE);
					agentKnowledgeTypes.add(KnowledgeType.DOCUMENT);
					addAppliedType(appliedTypes, "document");
					filterAgentKnowledgeByType = true;
				}
				case "qa" -> {
					categories.add(SearchCategory.AGENT_KNOWLEDGE);
					agentKnowledgeTypes.add(KnowledgeType.QA);
					addAppliedType(appliedTypes, "qa");
					filterAgentKnowledgeByType = true;
				}
				case "faq" -> {
					categories.add(SearchCategory.AGENT_KNOWLEDGE);
					agentKnowledgeTypes.add(KnowledgeType.FAQ);
					addAppliedType(appliedTypes, "faq");
					filterAgentKnowledgeByType = true;
				}
				default -> warnings.add("忽略未识别的 knowledgeType: " + rawType);
			}
		}

		if (categories.isEmpty()) {
			categories.add(SearchCategory.BUSINESS_TERM);
			categories.add(SearchCategory.AGENT_KNOWLEDGE);
			addAppliedType(appliedTypes, "businessTerm");
			addAppliedType(appliedTypes, "agentKnowledge");
			warnings.add("knowledgeTypes 全部无法识别，已回退为 businessTerm + agentKnowledge。");
			filterAgentKnowledgeByType = false;
		}

		return new NormalizedSearchOptions(List.copyOf(appliedTypes), List.copyOf(categories),
				Set.copyOf(agentKnowledgeTypes), filterAgentKnowledgeByType, normalizedTopK, normalizedThreshold,
				List.copyOf(warnings));
	}

	private void addAppliedType(List<String> appliedTypes, String value) {
		if (!appliedTypes.contains(value)) {
			appliedTypes.add(value);
		}
	}

	private void appendWarning(List<String> warnings, String warning) {
		if (!warnings.contains(warning)) {
			warnings.add(warning);
		}
	}

	private int normalizeTopK(Integer topK) {
		if (topK == null || topK <= 0) {
			return DEFAULT_TOP_K;
		}
		return Math.min(topK, MAX_TOP_K);
	}

	private double normalizeThreshold(Double similarityThreshold) {
		if (similarityThreshold == null) {
			return DEFAULT_SIMILARITY_THRESHOLD;
		}
		if (similarityThreshold < 0) {
			return 0.0D;
		}
		if (similarityThreshold > 1) {
			return 1.0D;
		}
		return similarityThreshold;
	}

	private String requireText(String value, String message) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}

	private Long asLong(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
			try {
				return Long.parseLong(stringValue.trim());
			}
			catch (NumberFormatException ex) {
				return null;
			}
		}
		return null;
	}

	private Integer asInteger(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
			try {
				return Integer.parseInt(stringValue.trim());
			}
			catch (NumberFormatException ex) {
				return null;
			}
		}
		return null;
	}

	private String abbreviate(String value, int maxLength) {
		String normalized = defaultText(value).replaceAll("\\s+", " ").trim();
		if (!StringUtils.hasText(normalized)) {
			return "";
		}
		if (normalized.length() <= maxLength) {
			return normalized;
		}
		return normalized.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (StringUtils.hasText(value)) {
				return value.trim();
			}
		}
		return "";
	}

	private String defaultText(String value) {
		return value == null ? "" : value;
	}

	private enum SearchCategory {

		BUSINESS_TERM(DocumentMetadataConstant.BUSINESS_TERM, "businessTerm"),

		AGENT_KNOWLEDGE(DocumentMetadataConstant.AGENT_KNOWLEDGE, "agentKnowledge");

		private final String vectorType;

		private final String knowledgeLabel;

		SearchCategory(String vectorType, String knowledgeLabel) {
			this.vectorType = vectorType;
			this.knowledgeLabel = knowledgeLabel;
		}

		private String vectorType() {
			return vectorType;
		}

		private String knowledgeLabel() {
			return knowledgeLabel;
		}

	}

	private record NormalizedSearchOptions(List<String> appliedKnowledgeTypes, List<SearchCategory> categories,
			Set<KnowledgeType> agentKnowledgeTypes, boolean filterAgentKnowledgeByType, int topK,
			double similarityThreshold, List<String> warnings) {
	}

	private record SearchReadiness(boolean searchable, List<String> warnings) {

		private static final SearchReadiness READY = new SearchReadiness(true, List.of());

	}

	private record CategoryDiagnostics(SearchCategory category, int recalledCount, boolean recallEnabled,
			boolean vectorReady) {
	}

}
