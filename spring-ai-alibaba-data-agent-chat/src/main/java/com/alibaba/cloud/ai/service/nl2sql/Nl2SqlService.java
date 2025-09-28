package com.alibaba.cloud.ai.service.nl2sql;

import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.dto.schema.SchemaDTO;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.List;

public interface Nl2SqlService {

	Flux<ChatResponse> semanticConsistencyStream(String sql, String queryPrompt);

	String generateSql(List<String> evidenceList, String query, SchemaDTO schemaDTO, String sql,
			String exceptionMessage);

	SchemaDTO fineSelect(SchemaDTO schemaDTO, String query, List<String> evidenceList,
			String sqlGenerateSchemaMissingAdvice, DbConfig specificDbConfig);

}
