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

import com.alibaba.cloud.ai.config.DataAgentProperties;
import com.alibaba.cloud.ai.connector.accessor.Accessor;
import com.alibaba.cloud.ai.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.connector.bo.DbQueryParameter;
import com.alibaba.cloud.ai.connector.bo.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.connector.bo.TableInfoBO;
import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.constant.Constant;
import com.alibaba.cloud.ai.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.request.AgentSearchRequest;
import com.alibaba.cloud.ai.request.SchemaInitRequest;
import com.alibaba.cloud.ai.service.TableMetadataService;
import com.alibaba.cloud.ai.service.hybrid.retrieval.HybridRetrievalStrategy;
import com.alibaba.cloud.ai.util.SearchUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

import static com.alibaba.cloud.ai.util.DocumentConverterUtil.convertColumnsToDocuments;
import static com.alibaba.cloud.ai.util.DocumentConverterUtil.convertTablesToDocuments;
import static com.alibaba.cloud.ai.util.SearchUtil.buildFilterExpressionString;

@Slf4j
@Service
public class AgentVectorStoreServiceImpl implements AgentVectorStoreService {

	private static final String DEFAULT = "default";

	private final VectorStore vectorStore;

	private final ExecutorService dbOperationExecutor;

	private final Optional<HybridRetrievalStrategy> hybridRetrievalStrategy;

	private final DataAgentProperties dataAgentProperties;

	protected final AccessorFactory accessorFactory;

	protected final BatchingStrategy batchingStrategy;

	protected final TableMetadataService tableMetadataService;

	public AgentVectorStoreServiceImpl(VectorStore vectorStore, ExecutorService dbOperationExecutor,
			Optional<HybridRetrievalStrategy> hybridRetrievalStrategy, DataAgentProperties dataAgentProperties,
			BatchingStrategy batchingStrategy, AccessorFactory accessorFactory,
			TableMetadataService tableMetadataService) {
		this.vectorStore = vectorStore;
		this.dbOperationExecutor = dbOperationExecutor;
		this.hybridRetrievalStrategy = hybridRetrievalStrategy;
		this.dataAgentProperties = dataAgentProperties;
		this.batchingStrategy = batchingStrategy;
		this.accessorFactory = accessorFactory;
		this.tableMetadataService = tableMetadataService;
		log.info("VectorStore type: {}", vectorStore.getClass().getSimpleName());
	}

	@Override
	public List<Document> search(AgentSearchRequest searchRequest) {
		Assert.hasText(searchRequest.getAgentId(), "AgentId cannot be empty");
		Assert.hasText(searchRequest.getDocVectorType(), "DocVectorType cannot be empty");
		if (dataAgentProperties.getVectorStore().isEnableHybridSearch() && hybridRetrievalStrategy.isPresent()) {
			return hybridRetrievalStrategy.get().retrieve(searchRequest);
		}
		log.debug("Hybrid search is not enabled. use vector-search only");
		SearchRequest vectorSearchRequest = SearchUtil.buildVectorSearchRequest(searchRequest);
		List<Document> results = vectorStore.similaritySearch(vectorSearchRequest);
		log.debug("Search completed with vectorType: {}, found {} documents for SearchRequest: {}",
				searchRequest.getDocVectorType(), results.size(), searchRequest);
		return results;

	}

	@Override
	public Boolean schema(String agentId, SchemaInitRequest schemaInitRequest) throws Exception {
		log.info("Starting schema initialization for agent: {}", agentId);
		DbConfig config = schemaInitRequest.getDbConfig();
		DbQueryParameter dqp = DbQueryParameter.from(config)
			.setSchema(config.getSchema())
			.setTables(schemaInitRequest.getTables());

		try {
			// 根据当前DbConfig获取Accessor
			Accessor dbAccessor = accessorFactory.getAccessorByDbConfig(config);

			// 清理旧数据
			log.info("Clearing existing schema data for agent: {}", agentId);
			clearSchemaDataForAgent(agentId);
			log.debug("Successfully cleared existing schema data for agent: {}", agentId);

			// 处理外键
			log.debug("Fetching foreign keys for agent: {}", agentId);
			List<ForeignKeyInfoBO> foreignKeys = dbAccessor.showForeignKeys(config, dqp);
			log.info("Found {} foreign keys for agent: {}", foreignKeys.size(), agentId);

			Map<String, List<String>> foreignKeyMap = buildForeignKeyMap(foreignKeys);
			log.debug("Built foreign key map with {} entries for agent: {}", foreignKeyMap.size(), agentId);

			// 处理表和列
			log.debug("Fetching tables for agent: {}", agentId);
			List<TableInfoBO> tables = dbAccessor.fetchTables(config, dqp);
			log.info("Found {} tables for agent: {}", tables.size(), agentId);

			if (tables.size() > 5) {
				// 对于大量表，使用并行处理
				log.info("Processing {} tables in parallel mode for agent: {}", tables.size(), agentId);
				processTablesInParallel(tables, config, foreignKeyMap);
			}
			else {
				// 对于少量表，使用批量处理
				log.info("Processing {} tables in batch mode for agent: {}", tables.size(), agentId);
				tableMetadataService.batchEnrichTableMetadata(tables, config, foreignKeyMap);
			}

			log.info("Successfully processed all tables for agent: {}", agentId);

			// 转换为文档
			List<Document> columnDocs = convertColumnsToDocuments(agentId, tables);
			List<Document> tableDocs = convertTablesToDocuments(agentId, tables);

			// 存储文档
			log.info("Storing {} columns and {} tables for agent: {}", columnDocs.size(), tableDocs.size(), agentId);
			storeSchemaDocuments(columnDocs, tableDocs);
			log.info("Successfully stored all documents for agent: {}", agentId);
			return true;
		}
		catch (Exception e) {
			log.error("Failed to process schema for agent: {}", agentId, e);
			return false;
		}
	}

	/**
	 * 并行处理表元数据，提高大量表时的处理性能
	 * @param tables 表列表
	 * @param config 数据库配置
	 * @param foreignKeyMap 外键映射
	 * @throws Exception 处理失败时抛出异常
	 */
	private void processTablesInParallel(List<TableInfoBO> tables, DbConfig config,
			Map<String, List<String>> foreignKeyMap) throws Exception {

		// 根据CPU核心数确定并行度，但不超过表的数量
		int parallelism = Math.min(Runtime.getRuntime().availableProcessors() * 2, tables.size());
		int batchSize = (int) Math.ceil((double) tables.size() / parallelism);

		log.info("Processing {} tables in parallel with parallelism: {}, batch size: {}", tables.size(), parallelism,
				batchSize);
		// 将表分成多个批次
		List<List<TableInfoBO>> tableBatches = partitionList(tables, batchSize);

		// 使用CompletableFuture进行更精细的并行控制，使用专用线程池
		List<CompletableFuture<Void>> futures = tableBatches.stream().map(batch -> CompletableFuture.runAsync(() -> {
			try {
				log.debug("Processing batch of {} tables", batch.size());

				// 批量处理当前批次的表
				tableMetadataService.batchEnrichTableMetadata(batch, config, foreignKeyMap);
				log.debug("Successfully processed batch of {} tables", batch.size());
			}
			catch (Exception e) {
				log.error("Failed to process batch of tables", e);
				throw new CompletionException(e);
			}
		}, dbOperationExecutor)).toList();

		// 等待所有任务完成，并处理异常
		try {
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
			log.info("All parallel batches completed successfully");
		}
		catch (CompletionException e) {
			log.error("Parallel processing failed", e);
			throw new Exception(e.getCause());
		}
	}

	/**
	 * 将列表分成指定大小的子列表
	 * @param list 原始列表
	 * @param batchSize 批次大小
	 * @param <T> 列表元素类型
	 * @return 分批后的列表
	 */
	private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
		List<List<T>> partitions = new ArrayList<>();
		for (int i = 0; i < list.size(); i += batchSize) {
			partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
		}
		return partitions;
	}

	protected void storeSchemaDocuments(List<Document> columns, List<Document> tables) {
		// 串行去批写入，并行流的时候有API限速了
		List<List<Document>> columnBatches = batchingStrategy.batch(columns);
		columnBatches.forEach(vectorStore::add);

		List<List<Document>> tableBatches = batchingStrategy.batch(tables);
		tableBatches.forEach(vectorStore::add);

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

	protected void clearSchemaDataForAgent(String agentId) throws Exception {
		deleteDocumentsByVectorType(agentId, DocumentMetadataConstant.COLUMN);
		deleteDocumentsByVectorType(agentId, DocumentMetadataConstant.TABLE);
	}

	@Override
	public Boolean deleteDocumentsByVectorType(String agentId, String vectorType) throws Exception {
		Assert.notNull(agentId, "AgentId cannot be null.");
		Assert.notNull(vectorType, "VectorType cannot be null.");

		Map<String, Object> metadata = new HashMap<>(Map.ofEntries(Map.entry(Constant.AGENT_ID, agentId),
				Map.entry(DocumentMetadataConstant.VECTOR_TYPE, vectorType)));

		return this.deleteDocumentsByMetedata(agentId, metadata);
	}

	@Override
	public void addDocuments(String agentId, List<Document> documents) {
		Assert.notNull(agentId, "AgentId cannot be null.");
		Assert.notEmpty(documents, "Documents cannot be empty.");
		// 校验文档中metadata中包含的agentId
		for (Document document : documents) {
			Assert.notNull(document.getMetadata(), "Document metadata cannot be null.");
			Assert.isTrue(document.getMetadata().containsKey(Constant.AGENT_ID),
					"Document metadata must contain agentId.");
			Assert.isTrue(document.getMetadata().get(Constant.AGENT_ID).equals(agentId),
					"Document metadata agentId does not match.");
		}
		vectorStore.add(documents);
	}

	@Override
	public Boolean deleteDocumentsByMetedata(String agentId, Map<String, Object> metadata) throws Exception {
		Assert.hasText(agentId, "AgentId cannot be empty.");
		Assert.notNull(metadata, "Metadata cannot be null.");
		// 添加agentId元数据过滤条件, 用于删除指定agentId下的所有数据，因为metadata中用户调用可能忘记添加agentId
		metadata.put(Constant.AGENT_ID, agentId);
		String filterExpression = buildFilterExpressionString(metadata);

		// es的可以直接元数据删除
		if (vectorStore instanceof SimpleVectorStore) {
			// 目前SimpleVectorStore不支持通过元数据删除，使用会抛出UnsupportedOperationException,现在是通过id删除
			batchDelDocumentsWithFilter(filterExpression);
		}
		else {
			vectorStore.delete(filterExpression);
		}

		return true;
	}

	private void batchDelDocumentsWithFilter(String filterExpression) {
		Set<String> seenDocumentIds = new HashSet<>();
		// 分批获取，因为Milvus等向量数据库的topK有限制
		List<Document> batch;
		int newDocumentsCount;
		int totalDeleted = 0;

		do {
			batch = vectorStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
				.query(DEFAULT)// 使用默认的查询字符串，因为有的嵌入模型不支持空字符串
				.filterExpression(filterExpression)
				.similarityThreshold(0.0)// 设置最低相似度阈值以获取元数据匹配的所有文档
				.topK(dataAgentProperties.getVectorStore().getBatchDelTopkLimit())
				.build());

			// 过滤掉已经处理过的文档，只删除未处理的文档
			List<String> idsToDelete = new ArrayList<>();
			newDocumentsCount = 0;

			for (Document doc : batch) {
				if (seenDocumentIds.add(doc.getId())) {
					// 如果add返回true，表示这是一个新的文档ID
					idsToDelete.add(doc.getId());
					newDocumentsCount++;
				}
			}

			// 删除这批新文档
			if (!idsToDelete.isEmpty()) {
				vectorStore.delete(idsToDelete);
				totalDeleted += idsToDelete.size();
			}

		}
		while (newDocumentsCount > 0); // 只有当获取到新文档时才继续循环

		log.info("Deleted {} documents with filter expression: {}", totalDeleted, filterExpression);
	}

	@Override
	public List<Document> getDocumentsForAgent(String agentId, String query, String vectorType) {
		AgentSearchRequest searchRequest = AgentSearchRequest.builder()
			.agentId(agentId)
			.docVectorType(vectorType)
			.query(query)
			.topK(dataAgentProperties.getVectorStore().getTopkLimit())
			.similarityThreshold(dataAgentProperties.getVectorStore().getSimilarityThreshold())
			.build();
		return search(searchRequest);
	}

	@Override
	public List<Document> getDocumentsOnlyByFilter(String filterExpression, int topK) {
		Assert.hasText(filterExpression, "filterExpression cannot be empty.");
		SearchRequest searchRequest = SearchRequest.builder()
			.query(DEFAULT)
			.topK(topK)
			.filterExpression(filterExpression)
			.similarityThreshold(0.0)
			.build();
		return vectorStore.similaritySearch(searchRequest);
	}

	@Override
	public List<Document> getTableDocuments(String agentId, List<String> tableNames) {
		Assert.hasText(agentId, "AgentId cannot be empty.");
		if (tableNames.isEmpty())
			return Collections.emptyList();
		// 通过元数据过滤查找目标表
		String filterExpression = SearchUtil.buildFilterExpressionForSearchTables(agentId, tableNames);
		return this.getDocumentsOnlyByFilter(filterExpression, tableNames.size() + 5);
	}

	@Override
	public List<Document> getColumnDocuments(String agentId, String upstreamTableName, List<String> columnNames) {
		Assert.hasText(agentId, "AgentId cannot be empty.");
		Assert.hasText(upstreamTableName, "UpstreamTableName cannot be empty.");
		if (columnNames.isEmpty())
			return Collections.emptyList();
		String filterExpression = SearchUtil.buildFilterExpressionForSearchColumns(agentId, upstreamTableName,
				columnNames);
		return this.getDocumentsOnlyByFilter(filterExpression, columnNames.size() + 5);
	}

	@Override
	public boolean hasDocuments(String agentId) {
		// 类似 MySQL 的 LIMIT 1,只检查是否存在文档
		List<Document> docs = vectorStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
			.query(DEFAULT)// 使用默认的查询字符串，因为有的嵌入模型不支持空字符串
			.filterExpression(buildFilterExpressionString(Map.of(Constant.AGENT_ID, agentId)))
			.topK(1) // 只获取1个文档
			.similarityThreshold(0.0)
			.build());
		return !docs.isEmpty();
	}

}
