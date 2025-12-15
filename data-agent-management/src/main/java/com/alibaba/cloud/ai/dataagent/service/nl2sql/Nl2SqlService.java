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

package com.alibaba.cloud.ai.dataagent.service.nl2sql;

import com.alibaba.cloud.ai.dataagent.connector.config.DbConfig;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.common.util.MarkdownParserUtil;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

public interface Nl2SqlService {

	Flux<ChatResponse> semanticConsistencyStream(String sql, String queryPrompt);

	Flux<String> generateSql(String evidence, String query, SchemaDTO schemaDTO, String sql, String exceptionMessage,
			DbConfig dbConfig, String executionDescription, String dialect);

	Flux<String> generateOptimizedSql(String previousSql, String exceptionMessage, int round, String dialect);

	Flux<ChatResponse> fineSelect(SchemaDTO schemaDTO, String query, String evidence,
			String sqlGenerateSchemaMissingAdvice, DbConfig specificDbConfig, Consumer<SchemaDTO> dtoConsumer);

	default String sqlTrim(String sql) {
		return MarkdownParserUtil.extractRawText(sql).trim();
	}

}
