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

package com.alibaba.cloud.ai.dataagent.service.schema;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SchemaInitRequest;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import org.springframework.ai.document.Document;

import java.util.List;

public interface SchemaService {

	Boolean schema(String agentId, SchemaInitRequest schemaInitRequest) throws Exception;

	List<Document> getTableDocumentsForAgent(String agentId, String query);

	void extractDatabaseName(SchemaDTO schemaDTO, DbConfigBO dbConfig);

	void buildSchemaFromDocuments(String agentId, List<Document> columnDocumentList, List<Document> tableDocuments,
			SchemaDTO schemaDTO);

	List<Document> getTableDocuments(String agentId, List<String> tableNames);

	List<Document> getColumnDocumentsByTableName(String agentId, List<String> tableNames);

}
