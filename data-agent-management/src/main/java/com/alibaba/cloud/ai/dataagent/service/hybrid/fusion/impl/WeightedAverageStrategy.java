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
package com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.impl;

import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.FusionStrategy;
import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class WeightedAverageStrategy implements FusionStrategy {

	@SuppressWarnings("unchecked")
	@Override
	public List<Document> fuseResults(int topK, List<Document>... resultLists) {
		return fuseResultsWithWeights(topK, List.of(), resultLists);
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<Document> fuseResultsWithWeights(int topK, List<Double> weights, List<Document>... resultLists) {
		if (topK <= 0 || resultLists == null || resultLists.length == 0) {
			return List.of();
		}

		Map<String, ScoredDocument> fusedDocuments = new LinkedHashMap<>();
		int sequence = 0;

		for (int listIndex = 0; listIndex < resultLists.length; listIndex++) {
			List<Document> resultList = resultLists[listIndex];
			if (resultList == null || resultList.isEmpty()) {
				continue;
			}

			double listWeight = resolveWeight(weights, listIndex);
			if (listWeight == 0.0) {
				continue;
			}

			List<Double> normalizedScores = normalizeScores(resultList);
			for (int documentIndex = 0; documentIndex < resultList.size(); documentIndex++) {
				Document document = resultList.get(documentIndex);
				if (document == null) {
					continue;
				}

				String documentId = getDocumentId(document);
				ScoredDocument scoredDocument = fusedDocuments.get(documentId);
				if (scoredDocument == null) {
					scoredDocument = new ScoredDocument(document, sequence);
					fusedDocuments.put(documentId, scoredDocument);
				}
				scoredDocument.addScore(normalizedScores.get(documentIndex) * listWeight);
				sequence++;
			}
		}

		return fusedDocuments.values()
			.stream()
			.sorted(Comparator.comparingDouble(ScoredDocument::getScore)
				.reversed()
				.thenComparingInt(ScoredDocument::getFirstSeen))
			.limit(topK)
			.map(ScoredDocument::getDocument)
			.collect(Collectors.toList());
	}

	private double resolveWeight(List<Double> weights, int index) {
		if (weights == null || index >= weights.size()) {
			return 1.0;
		}
		Double weight = weights.get(index);
		if (weight == null || !Double.isFinite(weight)) {
			return 1.0;
		}
		return Math.max(0.0, weight);
	}

	private List<Double> normalizeScores(List<Document> resultList) {
		List<Double> rawScores = IntStream.range(0, resultList.size())
			.mapToObj(index -> resolveScore(resultList.get(index), index, resultList.size()))
			.collect(Collectors.toList());

		double minScore = rawScores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
		double maxScore = rawScores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
		if (Double.compare(maxScore, minScore) == 0) {
			return rawScores.stream().map(this::clampToUnitInterval).collect(Collectors.toList());
		}

		return rawScores.stream().map(score -> (score - minScore) / (maxScore - minScore)).collect(Collectors.toList());
	}

	private double resolveScore(Document document, int index, int resultListSize) {
		if (document != null && document.getScore() != null && Double.isFinite(document.getScore())) {
			return document.getScore();
		}
		return (resultListSize - index) / (double) resultListSize;
	}

	private double clampToUnitInterval(double score) {
		return Math.max(0.0, Math.min(1.0, score));
	}

	private String getDocumentId(Document document) {
		if (StringUtils.hasText(document.getId())) {
			return document.getId();
		}
		if (StringUtils.hasText(document.getText())) {
			return String.valueOf(document.getText().hashCode());
		}
		return String.valueOf(Objects.hashCode(document.getMetadata()));
	}

	private static final class ScoredDocument {

		private final Document document;

		private final int firstSeen;

		private double score;

		private ScoredDocument(Document document, int firstSeen) {
			this.document = document;
			this.firstSeen = firstSeen;
		}

		private void addScore(double score) {
			this.score += score;
		}

		private Document getDocument() {
			return this.document;
		}

		private int getFirstSeen() {
			return this.firstSeen;
		}

		private double getScore() {
			return this.score;
		}

	}

}
