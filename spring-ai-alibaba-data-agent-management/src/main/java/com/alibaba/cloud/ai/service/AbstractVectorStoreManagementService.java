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
package com.alibaba.cloud.ai.service;

import com.alibaba.cloud.ai.connector.accessor.Accessor;
import com.alibaba.cloud.ai.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.connector.bo.ColumnInfoBO;
import com.alibaba.cloud.ai.connector.bo.DbQueryParameter;
import com.alibaba.cloud.ai.connector.bo.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.connector.bo.TableInfoBO;
import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.request.DeleteRequest;
import com.alibaba.cloud.ai.request.EvidenceRequest;
import com.alibaba.cloud.ai.request.SchemaInitRequest;
import com.alibaba.cloud.ai.util.DocumentConverterUtil;
import com.alibaba.cloud.ai.util.JsonUtil;
import com.alibaba.cloud.ai.util.SchemaProcessorUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractVectorStoreManagementService implements VectorStoreManagementService {

	@Autowired
	protected AccessorFactory accessorFactory;

	protected Accessor dbAccessor;

	protected DbConfig dbConfig;

	@PostConstruct
	public void init() {
		this.dbAccessor = accessorFactory.getAccessorByDbConfig(dbConfig);
	}

	@Override
	public Boolean addEvidence(List<EvidenceRequest> evidenceRequests) {
		List<Document> evidences = DocumentConverterUtil.convertEvidenceToDocuments(evidenceRequests);
		getVectorStore().add(evidences);
		return true;
	}

	@Override
	public Boolean deleteDocuments(DeleteRequest deleteRequest) throws Exception {
		try {
			if (deleteRequest.getId() != null && !deleteRequest.getId().isEmpty()) {
				getVectorStore().delete(List.of(deleteRequest.getId()));
			}
			else if (deleteRequest.getVectorType() != null && !deleteRequest.getVectorType().isEmpty()) {
				FilterExpressionBuilder builder = new FilterExpressionBuilder();
				Filter.Expression filterExpression = builder.eq("vectorType", deleteRequest.getVectorType()).build();

				getVectorStore().delete(filterExpression);
			}
			else {
				throw new IllegalArgumentException("Either id or vectorType must be specified.");
			}
			return true;
		}
		catch (Exception e) {
			throw new Exception("Failed to delete collection data by filterExpression: " + e.getMessage(), e);
		}
	}

	protected abstract VectorStore getVectorStore();

	// 模板方法 - 通用schema处理流程
	@Override
	public Boolean schema(SchemaInitRequest schemaInitRequest) throws Exception {
		try {

			DbConfig config = schemaInitRequest.getDbConfig();
			DbQueryParameter dqp = DbQueryParameter.from(config)
				.setSchema(config.getSchema())
				.setTables(schemaInitRequest.getTables());

			// 清理旧数据
			clearSchemaData();

			// 处理外键
			List<ForeignKeyInfoBO> foreignKeys = dbAccessor.showForeignKeys(config, dqp);
			Map<String, List<String>> foreignKeyMap = buildForeignKeyMap(foreignKeys);

			// 处理表和列
			List<TableInfoBO> tables = dbAccessor.fetchTables(config, dqp);
			for (TableInfoBO table : tables) {
				SchemaProcessorUtil.enrichTableMetadata(table, dqp, config, dbAccessor, JsonUtil.getObjectMapper(),
						foreignKeyMap);
			}

			// 转换为文档
			List<Document> columnDocs = convertColumnsToDocuments(tables);
			List<Document> tableDocs = convertTablesToDocuments(tables);

			// 存储文档
			return storeSchemaDocuments(columnDocs, tableDocs);
		}
		catch (Exception e) {
			log.error("Failed to process schema ", e);
			return false;
		}
	}

	// 通用辅助方法
	protected Map<String, List<String>> buildForeignKeyMap(List<ForeignKeyInfoBO> foreignKeys) {
		Map<String, List<String>> map = new HashMap<>();
		for (ForeignKeyInfoBO fk : foreignKeys) {
			String key = fk.getTable() + "." + fk.getColumn() + "=" + fk.getReferencedTable() + "."
					+ fk.getReferencedColumn();

			map.computeIfAbsent(fk.getTable(), k -> new ArrayList<>()).add(key);
			map.computeIfAbsent(fk.getReferencedTable(), k -> new ArrayList<>()).add(key);
		}
		return map;
	}

	private List<Document> convertColumnsToDocuments(List<TableInfoBO> tables) throws Exception {
		List<Document> documents = new ArrayList<>();
		for (TableInfoBO table : tables) {
			// 使用已经处理过的列数据，避免重复查询
			List<ColumnInfoBO> columns = table.getColumns();
			if (columns != null) {
				for (ColumnInfoBO column : columns) {
					documents.add(DocumentConverterUtil.convertColumnToDocument(table, column));
				}
			}
		}
		return documents;
	}

	private List<Document> convertTablesToDocuments(List<TableInfoBO> tables) {
		return tables.stream().map(DocumentConverterUtil::convertTableToDocument).collect(Collectors.toList());
	}

	protected Boolean storeSchemaDocuments(List<Document> columns, List<Document> tables) throws Exception {
		try {
			getVectorStore().add(columns);
			getVectorStore().add(tables);
			return true;
		}
		catch (Exception e) {
			log.error("vectorstore schemaDocuments error", e);
			return false;
		}

	}

	protected void clearSchemaData() throws Exception {
		DeleteRequest deleteRequest = new DeleteRequest();
		deleteRequest.setVectorType("column");
		deleteDocuments(deleteRequest);
		deleteRequest.setVectorType("table");
		deleteDocuments(deleteRequest);
	}

}
