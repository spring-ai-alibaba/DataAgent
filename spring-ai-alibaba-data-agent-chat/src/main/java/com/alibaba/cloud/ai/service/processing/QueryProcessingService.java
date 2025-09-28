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
