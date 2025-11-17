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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.common.connector.bo.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.common.connector.bo.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.entity.BusinessKnowledge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for converting business objects to Document objects. Provides common
 * document conversion functionality for vector store operations.
 */
@Slf4j
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

		String docId = DocumentMetadataConstant.COLUMN + ":" + agentId + ":" + tableInfoBO.getName() + ":"
				+ columnInfoBO.getName();
		return new Document(docId, text, metadata);
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
		String docId = DocumentMetadataConstant.TABLE + ":" + agentId + ":" + tableInfoBO.getName();
		return new Document(docId, text, metadata);
	}

	public static List<Document> convertTablesToDocuments(String agentId, List<TableInfoBO> tables) {
		return tables.stream()
			.map(table -> DocumentConverterUtil.convertTableToDocumentForAgent(agentId, table))
			.collect(Collectors.toList());
	}

	public static Document convertBusinessKnowledgeToDocument(BusinessKnowledge businessKnowledge) {

		// 构建文档内容，包含业务名词、说明和同义词
		String businessTerm = Optional.ofNullable(businessKnowledge.getBusinessTerm()).orElse("无");
		String description = Optional.ofNullable(businessKnowledge.getDescription()).orElse("无");
		String synonyms = Optional.ofNullable(businessKnowledge.getSynonyms()).orElse("无");

		String content = String.format("业务名词: %s, 说明: %s, 同义词: %s", businessTerm, description, synonyms);

		// 构建元数据
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.BUSINESS_TERM);
		metadata.put(Constant.AGENT_ID, businessKnowledge.getAgentId().toString());
		metadata.put(DocumentMetadataConstant.DB_RECORD_ID, businessKnowledge.getId());
		metadata.put(DocumentMetadataConstant.IS_RECALL,
				Optional.ofNullable(businessKnowledge.getIsRecall()).orElse(0).toString());
		String docId = generateFixedBusinessKnowledgeDocId(businessKnowledge.getAgentId().toString(),
				businessKnowledge.getId());
		return new Document(docId, content, metadata);
	}

	public static String generateFixedBusinessKnowledgeDocId(String agentId, Long businessKnowledgeId) {
		return DocumentMetadataConstant.BUSINESS_TERM + ":" + agentId + ":" + businessKnowledgeId;
	}

	/**
	 * Create Document from AgentKnowledge
	 */
	public static Document createDocumentFromKnowledge(String agentId, AgentKnowledge knowledge) {
		String content = knowledge.getContent();
		if (content == null || content.trim().isEmpty()) {
			content = knowledge.getTitle(); // If content is empty, use title
		}

		Map<String, Object> metadata = new HashMap<>();
		metadata.put("agentId", agentId);
		metadata.put("knowledgeId", knowledge.getId());
		metadata.put("title", knowledge.getTitle());
		metadata.put("type", knowledge.getType());
		metadata.put("category", knowledge.getCategory());
		metadata.put("tags", knowledge.getTags());
		metadata.put("status", knowledge.getStatus());
		metadata.put("vectorType", "knowledge:" + knowledge.getType());
		metadata.put("sourceUrl", knowledge.getSourceUrl());
		metadata.put("fileType", knowledge.getFileType());
		metadata.put("embeddingStatus", knowledge.getEmbeddingStatus());
		metadata.put("createTime", knowledge.getCreateTime());

		return new Document(content, metadata);
	}

	/**
	 * Private constructor to prevent instantiation.
	 */
	private DocumentConverterUtil() {
		throw new AssertionError("Cannot instantiate utility class");
	}

}
