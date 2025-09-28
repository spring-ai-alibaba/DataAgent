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

package com.alibaba.cloud.ai.service.processing;

import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 问题加工服务
 */
public interface QueryProcessingService {

	List<String> extractEvidences(String query, String agentId);

	List<String> extractKeywords(String query, List<String> evidenceList);

	List<String> expandQuestion(String query);

	Flux<ChatResponse> rewriteStream(String query, String agentId) throws Exception;

	default List<String> extractEvidences(String query) {
		return extractEvidences(query, null);
	}

	default Flux<ChatResponse> rewriteStream(String query) throws Exception {
		return rewriteStream(query, null);
	}

}
