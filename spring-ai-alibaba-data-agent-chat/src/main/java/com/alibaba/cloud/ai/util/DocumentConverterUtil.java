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
package com.alibaba.cloud.ai.util;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;

import com.alibaba.cloud.ai.connector.bo.ColumnInfoBO;
import com.alibaba.cloud.ai.connector.bo.TableInfoBO;
import com.alibaba.cloud.ai.request.EvidenceRequest;

/**
 * Utility class for converting business objects to Document objects. Provides common
 * document conversion functionality for vector store operations.
 */
public class DocumentConverterUtil {

	public static List<Document> convertColumnsToDocuments(String agentId, List<TableInfoBO> tables) {
		List<Document> documents = new ArrayList<>();
		for (TableInfoBO table : tables) {
			// 使用已经处理过的列数据，避免重复查询
			List<ColumnInfoBO> columns = table.getColumns();
			if (columns != null) {
				for (ColumnInfoBO column : columns) {
					documents.add(DocumentConverterUtil.convertColumnToDocumentForAgent(agentId, table, column));
				}
			}
		}
		return documents;
	}

	/**
	 * Converts a column info object to a Document for vector storage.
	 * @param tableInfoBO the table information containing schema details
	 * @param columnInfoBO the column information to convert
	 * @return Document object with column metadata
	 */
	public static Document convertColumnToDocumentForAgent(String agentId, TableInfoBO tableInfoBO,
			ColumnInfoBO columnInfoBO) {
		String text = Optional.ofNullable(columnInfoBO.getDescription()).orElse(columnInfoBO.getName());
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("name", columnInfoBO.getName());
		metadata.put("tableName", tableInfoBO.getName());
		metadata.put("description", Optional.ofNullable(columnInfoBO.getDescription()).orElse(""));
		metadata.put("type", columnInfoBO.getType());
		metadata.put("primary", columnInfoBO.isPrimary());
		metadata.put("notnull", columnInfoBO.isNotnull());
		metadata.put("vectorType", "column");
		metadata.put("agentId", agentId);

		if (columnInfoBO.getSamples() != null) {
			metadata.put("samples", columnInfoBO.getSamples());
		}

		return new Document(columnInfoBO.getName(), text, metadata);
	}

	/**
	 * Converts a table info object to a Document for vector storage.
	 * @param tableInfoBO the table information to convert
	 * @return Document object with table metadata
	 */
	public static Document convertTableToDocumentForAgent(String agentId, TableInfoBO tableInfoBO) {
		String text = Optional.ofNullable(tableInfoBO.getDescription()).orElse(tableInfoBO.getName());
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("schema", Optional.ofNullable(tableInfoBO.getSchema()).orElse(""));
		metadata.put("name", tableInfoBO.getName());
		metadata.put("description", Optional.ofNullable(tableInfoBO.getDescription()).orElse(""));
		metadata.put("foreignKey", Optional.ofNullable(tableInfoBO.getForeignKey()).orElse(""));
		metadata.put("primaryKey", Optional.ofNullable(tableInfoBO.getPrimaryKeys()).orElse(new ArrayList<>()));
		metadata.put("vectorType", "table");
		metadata.put("agentId", agentId);
		return new Document(tableInfoBO.getName(), text, metadata);
	}

	public static List<Document> convertTablesToDocuments(String agentId, List<TableInfoBO> tables) {
		return tables.stream()
			.map(table -> DocumentConverterUtil.convertTableToDocumentForAgent(agentId, table))
			.collect(Collectors.toList());
	}

	/**
	 * Converts evidence requests to Documents for vector storage.
	 * @param evidenceRequests list of evidence requests
	 * @return list of Document objects
	 */
	public static List<Document> convertEvidenceToDocumentsForAgent(String agentId,
			List<EvidenceRequest> evidenceRequests) {
		return evidenceRequests.stream().map(evidenceRequest -> {
			Map<String, Object> metadata = new HashMap<>();
			metadata.put("evidenceType", evidenceRequest.getType());
			metadata.put("vectorType", "evidence");

			metadata.put("agentId", agentId);
			return new Document(UUID.randomUUID().toString(), evidenceRequest.getContent(), metadata);
		}).toList();
	}

	/**
	 * Private constructor to prevent instantiation.
	 */
	private DocumentConverterUtil() {
		throw new AssertionError("Cannot instantiate utility class");
	}

}
