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

import java.util.List;

public interface DomainKnowledgeSearchService {

	DomainKnowledgeSearchResult search(String agentId, DomainKnowledgeSearchRequest request);

	record DomainKnowledgeSearchRequest(String query, List<String> knowledgeTypes, Integer topK,
			Double similarityThreshold) {
	}

	record DomainKnowledgeSearchResult(String query, List<String> appliedKnowledgeTypes, List<KnowledgeHit> hits,
			List<String> warnings, SearchDiagnostics diagnostics) {
	}

	record KnowledgeHit(String vectorType, String knowledgeId, String title, String summary, String snippet,
			String source, String concreteType) {
	}

	record SearchDiagnostics(String runtimeAgentId, Integer recalledBusinessTermCount,
			Integer recalledAgentKnowledgeCount, boolean businessTermVectorReady, boolean agentKnowledgeVectorReady) {
	}

}
