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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.entity.BusinessKnowledge;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for converting business objects to Document objects. Provides common
 * document conversion functionality for vector store operations.
 */
@Slf4j
public class DocumentConverterUtil {

	public static List<Document> convertColumnsToDocuments(Integer datasourceId, List<TableInfoBO> tables) {
		List<Document> documents = new ArrayList<>();
		for (TableInfoBO table : tables) {
			// 使用已经处理过的列数据，避免重复查询
			List<ColumnInfoBO> columns = table.getColumns();
			if (columns != null) {
				for (ColumnInfoBO column : columns) {
					documents.add(DocumentConverterUtil.convertColumnToDocument(datasourceId, table, column));
				}
			}
		}
		return documents;
	}

	/**
	 * Converts a column info object to a Document for vector storage.
	 * @param datasourceId the datasource ID
	 * @param tableInfoBO the table information containing schema details
	 * @param columnInfoBO the column information to convert
	 * @return Document object with column metadata
	 */
	public static Document convertColumnToDocument(Integer datasourceId, TableInfoBO tableInfoBO,
			ColumnInfoBO columnInfoBO) {
		String text = StringUtils.isBlank(columnInfoBO.getDescription()) ? columnInfoBO.getName()
				: columnInfoBO.getDescription();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("name", columnInfoBO.getName());
		metadata.put("tableName", tableInfoBO.getName());
		metadata.put("description", Optional.ofNullable(columnInfoBO.getDescription()).orElse(""));
		metadata.put("type", columnInfoBO.getType());
		metadata.put("primary", columnInfoBO.isPrimary());
		metadata.put("notnull", columnInfoBO.isNotnull());
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.COLUMN);
		metadata.put(Constant.DATASOURCE_ID, datasourceId.toString());

		if (columnInfoBO.getSamples() != null) {
			metadata.put("samples", columnInfoBO.getSamples());
		}

		return new Document(text, metadata);
	}

	/**
	 * Converts a table info object to a Document for vector storage.
	 * @param datasourceId the datasource ID
	 * @param tableInfoBO the table information to convert
	 * @return Document object with table metadata
	 */
	public static Document convertTableToDocument(Integer datasourceId, TableInfoBO tableInfoBO) {
		String text = StringUtils.isBlank(tableInfoBO.getDescription()) ? tableInfoBO.getName()
				: tableInfoBO.getDescription();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("schema", Optional.ofNullable(tableInfoBO.getSchema()).orElse(""));
		metadata.put("name", tableInfoBO.getName());
		metadata.put("description", Optional.ofNullable(tableInfoBO.getDescription()).orElse(""));
		metadata.put("foreignKey", Optional.ofNullable(tableInfoBO.getForeignKey()).orElse(""));
		metadata.put("primaryKey", Optional.ofNullable(tableInfoBO.getPrimaryKeys()).orElse(new ArrayList<>()));
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.TABLE);
		metadata.put(Constant.DATASOURCE_ID, datasourceId.toString());
		return new Document(text, metadata);
	}

	public static List<Document> convertTablesToDocuments(Integer datasourceId, List<TableInfoBO> tables) {
		return tables.stream()
			.map(table -> DocumentConverterUtil.convertTableToDocument(datasourceId, table))
			.collect(Collectors.toList());
	}

	public static Document convertBusinessKnowledgeToDocument(BusinessKnowledge businessKnowledge) {

		// 构建文档内容，包含业务名词、说明和同义词
		String businessTerm = businessKnowledge.getBusinessTerm();
		String description = Optional.ofNullable(businessKnowledge.getDescription()).orElse("无");
		String synonyms = Optional.ofNullable(businessKnowledge.getSynonyms()).orElse("无");

		String content = String.format("业务名词: %s, 说明: %s, 同义词: %s", businessTerm, description, synonyms);

		// 构建元数据
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.BUSINESS_TERM);
		metadata.put(Constant.AGENT_ID, businessKnowledge.getAgentId().toString());
		metadata.put(DocumentMetadataConstant.DB_BUSINESS_TERM_ID, businessKnowledge.getId());

		return new Document(content, metadata);
	}

	public static Document convertQaFaqKnowledgeToDocument(AgentKnowledge knowledge) {
		// 使用question作为Document的content字段
		String content = knowledge.getQuestion();
		Map<String, Object> metadata = new HashMap<>();
		// answer和isRecall经常变更的放到关系数据库
		metadata.put(Constant.AGENT_ID, knowledge.getAgentId().toString());
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.AGENT_KNOWLEDGE);
		metadata.put(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, knowledge.getId());
		metadata.put(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE, knowledge.getType().getCode());

		return new Document(content, metadata);
	}

	/**
	 * 为文档列表添加元数据，用于DOCUMENT类型知识处理
	 * @param documents 原始文档列表
	 * @param knowledge 知识对象
	 * @return 添加了元数据的文档列表
	 */
	public static List<Document> convertAgentKnowledgeDocumentsWithMetadata(List<Document> documents,
			AgentKnowledge knowledge) {
		List<Document> documentsWithMetadata = new ArrayList<>();

		for (Document doc : documents) {
			// isRecall经常变更的放到关系数据库不放metadata中
			// 创建元数据
			Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
			metadata.put(Constant.AGENT_ID, knowledge.getAgentId().toString());
			metadata.put(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, knowledge.getId());
			metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.AGENT_KNOWLEDGE);
			metadata.put(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE, knowledge.getType().getCode());

			// 创建带有元数据的新文档
			Document docWithMetadata = new Document(doc.getId(), doc.getText(), metadata);
			documentsWithMetadata.add(docWithMetadata);
		}
		return documentsWithMetadata;
	}

	/**
	 * Private constructor to prevent instantiation.
	 */
	private DocumentConverterUtil() {
		throw new AssertionError("Cannot instantiate utility class");
	}

}
