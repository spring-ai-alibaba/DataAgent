package com.alibaba.cloud.ai.service.vectorstore;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;

import com.alibaba.cloud.ai.request.AgentSearchRequest;
import com.alibaba.cloud.ai.request.SchemaInitRequest;

public interface AgentVectorStoreService {

	/**
	 * 查询某个Agent的文档 总入口
	 */
	List<Document> search(AgentSearchRequest searchRequest);

	Boolean schema(String agentId, SchemaInitRequest schemaInitRequest) throws Exception;

	Boolean deleteDocumentsByVectorType(String agentId, String vectorType) throws Exception;

	Boolean deleteDocumentsByMetedata(String agentId, Map<String, Object> metadata) throws Exception;

	/**
	 * Get documents for specified agent
	 */
	List<Document> getDocumentsForAgent(String agentId, String query, String vectorType);

	boolean hasDocuments(String agentId);

	void addDocuments(String agentId, List<Document> documents);

	int estimateDocuments(String agentId);

}
