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
package com.alibaba.cloud.ai.service.vectorstore;

import static com.alibaba.cloud.ai.util.DocumentConverterUtil.convertColumnsToDocuments;
import static com.alibaba.cloud.ai.util.DocumentConverterUtil.convertTablesToDocuments;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.alibaba.cloud.ai.connector.accessor.Accessor;
import com.alibaba.cloud.ai.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.connector.bo.DbQueryParameter;
import com.alibaba.cloud.ai.connector.bo.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.connector.bo.TableInfoBO;
import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.constant.Constant;
import com.alibaba.cloud.ai.request.AgentSearchRequest;
import com.alibaba.cloud.ai.request.SchemaInitRequest;
import com.alibaba.cloud.ai.util.JsonUtil;
import com.alibaba.cloud.ai.util.SchemaProcessorUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractVectorStoreService implements AgentVectorStoreService {

	/**
	 * 相似度阈值配置，用于过滤相似度分数大于等于此阈值的文档
	 */
	@Value("${spring.ai.vectorstore.similarityThreshold:0.6}")
	protected double similarityThreshold;

	/**
	 * Get embedding model
	 */
	protected abstract EmbeddingModel getEmbeddingModel();

	protected final AccessorFactory accessorFactory;

	public AbstractVectorStoreService(AccessorFactory accessorFactory) {
		this.accessorFactory = accessorFactory;
	}

	@Override
	public List<Document> search(AgentSearchRequest searchRequest) {
		Assert.notNull(searchRequest, "SearchRequest cannot be null");
		Assert.notNull(searchRequest.getAgentId(), "agentId  cannot be null");
		org.springframework.ai.vectorstore.SearchRequest.Builder builder = org.springframework.ai.vectorstore.SearchRequest
			.builder();

		if (StringUtils.hasText(searchRequest.getQuery()))
			builder.query(searchRequest.getQuery());

		if (Objects.nonNull(searchRequest.getTopK()))
			builder.topK(searchRequest.getTopK());

		String filterFormatted = buildFilterExpressionString(searchRequest.getMetadataFilter());
		if (StringUtils.hasText(filterFormatted))
			builder.filterExpression(filterFormatted);
		builder.similarityThreshold(similarityThreshold);
		List<Document> results = getVectorStore().similaritySearch(builder.build());
		log.info("Search completed. Found {} documents for SearchRequest: {}", results.size(), searchRequest);
		return results;

	}

	// 模板方法 - 通用schema处理流程
	@Override
	public final Boolean schema(String agentId, SchemaInitRequest schemaInitRequest) throws Exception {
		try {

			DbConfig config = schemaInitRequest.getDbConfig();
			DbQueryParameter dqp = DbQueryParameter.from(config)
				.setSchema(config.getSchema())
				.setTables(schemaInitRequest.getTables());

			// 根据当前DbConfig获取Accessor
			Accessor dbAccessor = accessorFactory.getAccessorByDbConfig(config);

			// 清理旧数据
			clearSchemaDataForAgent(agentId);

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
			List<Document> columnDocs = convertColumnsToDocuments(agentId, tables);
			List<Document> tableDocs = convertTablesToDocuments(agentId, tables);

			// 存储文档
			return storeSchemaDocuments(columnDocs, tableDocs);
		}
		catch (Exception e) {
			log.error("Failed to process schema ", e);
			return false;
		}
	}

	protected Boolean storeSchemaDocuments(List<Document> columns, List<Document> tables) {
		try {
			getVectorStore().add(columns);
			getVectorStore().add(tables);
			return true;
		}
		catch (Exception e) {
			log.error("add document to vectorstore error", e);
			return false;
		}

	}

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

	protected abstract VectorStore getVectorStore();

	protected void clearSchemaDataForAgent(String agentId) throws Exception {
		deleteDocumentsByVectorType(agentId, Constant.COLUMN);
		deleteDocumentsByVectorType(agentId, Constant.TABLE);
	}

	@Override
	public Boolean deleteDocumentsByVectorType(String agentId, String vectorType) throws Exception {
		Assert.notNull(agentId, "AgentId cannot be null.");
		Assert.notNull(vectorType, "VectorType cannot be null.");

		Map<String, Object> metadata = new HashMap<>(
				Map.ofEntries(Map.entry(Constant.AGENT_ID, agentId), Map.entry(Constant.VECTOR_TYPE, vectorType)));

		return this.deleteDocumentsByMetedata(agentId, metadata);
	}

	@Override
	public void addDocuments(String agentId, List<Document> documents) {
		Assert.notNull(agentId, "AgentId cannot be null.");
		Assert.notEmpty(documents, "Documents cannot be empty.");
		getVectorStore().add(documents);
	}

	@Override
	public int estimateDocuments(String agentId) {
		// 初略估算文档数目
		List<Document> docs = getVectorStore()
			.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
				.query("")
				.filterExpression(buildFilterExpressionString(Map.of(Constant.AGENT_ID, agentId)))
				.topK(Integer.MAX_VALUE) // 获取所有匹配的文档
				.build());
		return docs.size();
	}

	@Override
	public Boolean deleteDocumentsByMetedata(String agentId, Map<String, Object> metadata) throws Exception {
		Assert.notNull(agentId, "AgentId cannot be null.");
		Assert.notNull(metadata, "Metadata cannot be null.");
		// 添加agentId元数据过滤条件, 用于删除指定agentId下的所有数据，因为metadata中用户调用可能忘记添加agentId
		metadata.put(Constant.AGENT_ID, agentId);
		String filterExpression = buildFilterExpressionString(metadata);

		// TODO 后续改成getVectorStore().delete(filterExpression);
		// TODO 目前不支持通过元数据删除，使用会抛出UnsupportedOperationException，后续spring
		// TODO ai发布1.1.0正式版本后再修改，现在是通过id删除

		// 先搜索要删除的文档
		List<Document> documentsToDelete = getVectorStore()
			.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
				.query("")
				.filterExpression(filterExpression)
				.topK(Integer.MAX_VALUE)
				.build());

		// 提取文档ID并删除
		if (!documentsToDelete.isEmpty()) {
			List<String> idsToDelete = documentsToDelete.stream().map(Document::getId).collect(Collectors.toList());
			getVectorStore().delete(idsToDelete);
		}

		return true;
	}

	/**
	 * 构建过滤表达式字符串，目前FilterExpressionBuilder 不支持链式拼接元数据过滤，所以只能使用字符串拼接
	 * @param filterMap
	 * @return
	 */
	protected final String buildFilterExpressionString(Map<String, Object> filterMap) {
		if (filterMap == null || filterMap.isEmpty()) {
			return null;
		}

		// 验证键名是否合法（只包含字母、数字和下划线）
		for (String key : filterMap.keySet()) {
			if (!key.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
				throw new IllegalArgumentException("Invalid key name: " + key
						+ ". Keys must start with a letter or underscore and contain only alphanumeric characters and underscores.");
			}
		}

		return filterMap.entrySet().stream().map(entry -> {
			String key = entry.getKey();
			Object value = entry.getValue();

			// 处理空值
			if (value == null) {
				return key + " == null";
			}

			// 根据值的类型决定如何格式化
			if (value instanceof String) {
				// 转义字符串中的特殊字符
				String escapedValue = escapeStringLiteral((String) value);
				return key + " == '" + escapedValue + "'";
			}
			else if (value instanceof Number) {
				// 数字类型直接使用
				return key + " == " + value;
			}
			else if (value instanceof Boolean) {
				// 布尔值使用小写形式
				return key + " == " + ((Boolean) value).toString().toLowerCase();
			}
			else if (value instanceof Enum) {
				// 枚举类型，转换为字符串并转义
				String enumValue = ((Enum<?>) value).name();
				String escapedValue = escapeStringLiteral(enumValue);
				return key + " == '" + escapedValue + "'";
			}
			else {
				// 其他类型尝试转换为字符串并转义
				String stringValue = value.toString();
				String escapedValue = escapeStringLiteral(stringValue);
				return key + " == '" + escapedValue + "'";
			}
		}).collect(Collectors.joining(" && "));
	}

	/**
	 * 转义字符串字面量中的特殊字符
	 */
	private String escapeStringLiteral(String input) {
		if (input == null) {
			return "";
		}

		// 转义反斜杠和单引号
		String escaped = input.replace("\\", "\\\\").replace("'", "\\'");

		// 转义其他特殊字符
		escaped = escaped.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t")
			.replace("\b", "\\b")
			.replace("\f", "\\f");

		return escaped;
	}

	@Override
	public List<Document> getDocumentsForAgent(String agentId, String query, String vectorType) {
		AgentSearchRequest searchRequest = AgentSearchRequest.getInstance(agentId);
		searchRequest.setQuery(query);
		searchRequest.setTopK(20);
		searchRequest.setMetadataFilter(Map.of(Constant.VECTOR_TYPE, vectorType));

		return search(searchRequest);
	}

	@Override
	public boolean hasDocuments(String agentId) {
		// 类似 MySQL 的 LIMIT 1,只检查是否存在文档
		List<Document> docs = getVectorStore()
			.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
				.query("")
				.filterExpression(buildFilterExpressionString(Map.of(Constant.AGENT_ID, agentId)))
				.topK(1) // 只获取1个文档
				.build());
		return !docs.isEmpty();
	}

}
