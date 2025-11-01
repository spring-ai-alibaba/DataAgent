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

package com.alibaba.cloud.ai.config;

import com.alibaba.cloud.ai.config.file.FileStorageProperties;
import com.alibaba.cloud.ai.dispatcher.*;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.node.*;
import com.alibaba.cloud.ai.util.NodeBeanUtil;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.cloud.ai.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

/**
 * DataAgent的自动配置类
 *
 * @author vlsmb
 * @since 2025/9/28
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({ CodeExecutorProperties.class, DataAgentProperties.class, FileStorageProperties.class })
public class DataAgentConfiguration implements DisposableBean {

	/**
	 * 专用线程池，用于数据库操作的并行处理
	 */
	private ExecutorService dbOperationExecutor;

	@Bean
	@ConditionalOnMissingBean(RestClientCustomizer.class)
	public RestClientCustomizer restClientCustomizer(@Value("${rest.connect.timeout:600}") long connectTimeout,
			@Value("${rest.read.timeout:600}") long readTimeout) {
		return restClientBuilder -> restClientBuilder
			.requestFactory(ClientHttpRequestFactoryBuilder.reactor().withCustomizer(factory -> {
				factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
				factory.setReadTimeout(Duration.ofSeconds(readTimeout));
			}).build());
	}

	@Bean
	@ConditionalOnMissingBean(WebClient.Builder.class)
	public WebClient.Builder webClientBuilder(@Value("${webclient.response.timeout:600}") long responseTimeout) {

		return WebClient.builder()
			.clientConnector(new ReactorClientHttpConnector(
					HttpClient.create().responseTimeout(Duration.ofSeconds(responseTimeout))));
	}

	@Bean
	public StateGraph nl2sqlGraph(NodeBeanUtil nodeBeanUtil) throws GraphStateException {

		KeyStrategyFactory keyStrategyFactory = () -> {
			HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
			// User input
			keyStrategyHashMap.put(INPUT_KEY, new ReplaceStrategy());
			// Agent ID
			keyStrategyHashMap.put(AGENT_ID, new ReplaceStrategy());
			// Business knowledge
			keyStrategyHashMap.put(BUSINESS_KNOWLEDGE, new ReplaceStrategy());
			// Semantic model
			keyStrategyHashMap.put(SEMANTIC_MODEL, new ReplaceStrategy());
			// queryWrite节点输出
			keyStrategyHashMap.put(QUERY_REWRITE_NODE_OUTPUT, new ReplaceStrategy());
			// keyword extract节点输出
			keyStrategyHashMap.put(KEYWORD_EXTRACT_NODE_OUTPUT, new ReplaceStrategy());
			keyStrategyHashMap.put(EVIDENCES, new ReplaceStrategy());
			// schema recall节点输出
			keyStrategyHashMap.put(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, new ReplaceStrategy());
			keyStrategyHashMap.put(COLUMN_DOCUMENTS_BY_KEYWORDS_OUTPUT, new ReplaceStrategy());
			// table relation节点输出
			keyStrategyHashMap.put(TABLE_RELATION_OUTPUT, new ReplaceStrategy());
			keyStrategyHashMap.put(TABLE_RELATION_EXCEPTION_OUTPUT, new ReplaceStrategy());
			keyStrategyHashMap.put(TABLE_RELATION_RETRY_COUNT, new ReplaceStrategy());
			// sql generate节点输出
			keyStrategyHashMap.put(SQL_GENERATE_SCHEMA_MISSING_ADVICE, new ReplaceStrategy());
			keyStrategyHashMap.put(SQL_GENERATE_OUTPUT, new ReplaceStrategy());
			keyStrategyHashMap.put(SQL_GENERATE_COUNT, new ReplaceStrategy());
			// Semantic consistence节点输出
			keyStrategyHashMap.put(SEMANTIC_CONSISTENCY_NODE_OUTPUT, new ReplaceStrategy());
			keyStrategyHashMap.put(SEMANTIC_CONSISTENCY_NODE_RECOMMEND_OUTPUT, new ReplaceStrategy());
			// Planner 节点输出
			keyStrategyHashMap.put(PLANNER_NODE_OUTPUT, new ReplaceStrategy());
			// PlanExecutorNode
			keyStrategyHashMap.put(PLAN_CURRENT_STEP, new ReplaceStrategy());
			keyStrategyHashMap.put(PLAN_NEXT_NODE, new ReplaceStrategy());
			keyStrategyHashMap.put(PLAN_VALIDATION_STATUS, new ReplaceStrategy());
			keyStrategyHashMap.put(PLAN_VALIDATION_ERROR, new ReplaceStrategy());
			keyStrategyHashMap.put(PLAN_REPAIR_COUNT, new ReplaceStrategy());
			// SQL Execute 节点输出
			keyStrategyHashMap.put(SQL_EXECUTE_NODE_OUTPUT, new ReplaceStrategy());
			keyStrategyHashMap.put(SQL_EXECUTE_NODE_EXCEPTION_OUTPUT, new ReplaceStrategy());
			// Python代码运行相关
			keyStrategyHashMap.put(SQL_RESULT_LIST_MEMORY, new ReplaceStrategy());
			keyStrategyHashMap.put(PYTHON_IS_SUCCESS, new ReplaceStrategy());
			keyStrategyHashMap.put(PYTHON_TRIES_COUNT, new ReplaceStrategy());
			keyStrategyHashMap.put(PYTHON_EXECUTE_NODE_OUTPUT, new ReplaceStrategy());
			keyStrategyHashMap.put(PYTHON_GENERATE_NODE_OUTPUT, new ReplaceStrategy());
			keyStrategyHashMap.put(PYTHON_ANALYSIS_NODE_OUTPUT, new ReplaceStrategy());
			// NL2SQL相关
			keyStrategyHashMap.put(IS_ONLY_NL2SQL, new ReplaceStrategy());
			keyStrategyHashMap.put(ONLY_NL2SQL_OUTPUT, new ReplaceStrategy());
			// Human Review keys
			keyStrategyHashMap.put(HUMAN_REVIEW_ENABLED, new ReplaceStrategy());
			// Final result
			keyStrategyHashMap.put(RESULT, new ReplaceStrategy());
			return keyStrategyHashMap;
		};

		StateGraph stateGraph = new StateGraph(NL2SQL_GRAPH_NAME, keyStrategyFactory)
			.addNode(QUERY_REWRITE_NODE, nodeBeanUtil.getNodeBeanAsync(QueryRewriteNode.class))
			.addNode(KEYWORD_EXTRACT_NODE, nodeBeanUtil.getNodeBeanAsync(KeywordExtractNode.class))
			.addNode(SCHEMA_RECALL_NODE, nodeBeanUtil.getNodeBeanAsync(SchemaRecallNode.class))
			.addNode(TABLE_RELATION_NODE, nodeBeanUtil.getNodeBeanAsync(TableRelationNode.class))
			.addNode(SQL_GENERATE_NODE, nodeBeanUtil.getNodeBeanAsync(SqlGenerateNode.class))
			.addNode(PLANNER_NODE, nodeBeanUtil.getNodeBeanAsync(PlannerNode.class))
			.addNode(PLAN_EXECUTOR_NODE, nodeBeanUtil.getNodeBeanAsync(PlanExecutorNode.class))
			.addNode(SQL_EXECUTE_NODE, nodeBeanUtil.getNodeBeanAsync(SqlExecuteNode.class))
			.addNode(PYTHON_GENERATE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonGenerateNode.class))
			.addNode(PYTHON_EXECUTE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonExecuteNode.class))
			.addNode(PYTHON_ANALYZE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonAnalyzeNode.class))
			.addNode(REPORT_GENERATOR_NODE, nodeBeanUtil.getNodeBeanAsync(ReportGeneratorNode.class))
			.addNode(SEMANTIC_CONSISTENCY_NODE, nodeBeanUtil.getNodeBeanAsync(SemanticConsistencyNode.class))
			.addNode(HUMAN_FEEDBACK_NODE, nodeBeanUtil.getNodeBeanAsync(HumanFeedbackNode.class));

		stateGraph.addEdge(START, QUERY_REWRITE_NODE)
			.addConditionalEdges(QUERY_REWRITE_NODE, edge_async(new QueryRewriteDispatcher()),
					Map.of(KEYWORD_EXTRACT_NODE, KEYWORD_EXTRACT_NODE, END, END))
			.addEdge(KEYWORD_EXTRACT_NODE, SCHEMA_RECALL_NODE)
			.addEdge(SCHEMA_RECALL_NODE, TABLE_RELATION_NODE)
			.addConditionalEdges(TABLE_RELATION_NODE, edge_async(new TableRelationDispatcher()),
					Map.of(PLANNER_NODE, PLANNER_NODE, END, END, TABLE_RELATION_NODE, TABLE_RELATION_NODE)) // retry
			// The edge from PlannerNode now goes to PlanExecutorNode for validation and
			// execution
			.addEdge(PLANNER_NODE, PLAN_EXECUTOR_NODE)
			// python nodes
			.addEdge(PYTHON_GENERATE_NODE, PYTHON_EXECUTE_NODE)
			.addConditionalEdges(PYTHON_EXECUTE_NODE, edge_async(new PythonExecutorDispatcher()),
					Map.of(PYTHON_ANALYZE_NODE, PYTHON_ANALYZE_NODE, END, END, PYTHON_GENERATE_NODE,
							PYTHON_GENERATE_NODE))
			.addEdge(PYTHON_ANALYZE_NODE, PLAN_EXECUTOR_NODE)
			// The dispatcher at PlanExecutorNode will decide the next step
			.addConditionalEdges(PLAN_EXECUTOR_NODE, edge_async(new PlanExecutorDispatcher()), Map.of(
					// If validation fails, go back to PlannerNode to repair
					PLANNER_NODE, PLANNER_NODE,
					// If validation passes, proceed to the correct execution node
					SQL_EXECUTE_NODE, SQL_EXECUTE_NODE, PYTHON_GENERATE_NODE, PYTHON_GENERATE_NODE,
					REPORT_GENERATOR_NODE, REPORT_GENERATOR_NODE,
					// If human review is enabled, go to human_feedback node
					HUMAN_FEEDBACK_NODE, HUMAN_FEEDBACK_NODE,
					// If max repair attempts are reached, end the process
					END, END))
			// Human feedback node routing
			.addConditionalEdges(HUMAN_FEEDBACK_NODE, edge_async(new HumanFeedbackDispatcher()), Map.of(
					// If plan is rejected, go back to PlannerNode
					PLANNER_NODE, PLANNER_NODE,
					// If plan is approved, continue with execution
					PLAN_EXECUTOR_NODE, PLAN_EXECUTOR_NODE,
					// If max repair attempts are reached, end the process
					END, END))
			.addEdge(REPORT_GENERATOR_NODE, END)
			.addConditionalEdges(SQL_EXECUTE_NODE, edge_async(new SQLExecutorDispatcher()),
					Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE, SEMANTIC_CONSISTENCY_NODE, SEMANTIC_CONSISTENCY_NODE))
			.addConditionalEdges(SQL_GENERATE_NODE, edge_async(new SqlGenerateDispatcher()),
					Map.of(KEYWORD_EXTRACT_NODE, KEYWORD_EXTRACT_NODE, END, END, SQL_EXECUTE_NODE, SQL_EXECUTE_NODE))
			.addConditionalEdges(SEMANTIC_CONSISTENCY_NODE, edge_async(new SemanticConsistenceDispatcher()),
					Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE, PLAN_EXECUTOR_NODE, PLAN_EXECUTOR_NODE));

		GraphRepresentation graphRepresentation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
				"workflow graph");

		log.info("\n\n");
		log.info(graphRepresentation.content());
		log.info("\n\n");

		return stateGraph;
	}

	/**
	 * 为了不必要的重复手动配置，不要在此添加其他向量的手动配置，如果扩展其他向量，请阅读spring ai文档
	 * <a href="https://springdoc.cn/spring-ai/api/vectordbs.html">...</a>
	 * 根据自己想要的向量，在pom文件引入 Boot Starter 依赖即可。此处配置使用内存向量作为兜底配置
	 */
	@Primary
	@Bean
	@ConditionalOnMissingBean(VectorStore.class)
	@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "simple", matchIfMissing = true)
	public VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
		return SimpleVectorStore.builder(embeddingModel).build();
	}

	@Bean
	@ConditionalOnMissingBean(BatchingStrategy.class)
	public BatchingStrategy customBatchingStrategy(DataAgentProperties properties) {
		// 使用增强的批处理策略，同时考虑token数量和文本数量限制
		EncodingType encodingType;
		try {
			Optional<EncodingType> encodingTypeOptional = EncodingType
				.fromName(properties.getEmbeddingBatch().getEncodingType());
			encodingType = encodingTypeOptional.orElse(EncodingType.CL100K_BASE);
		}
		catch (Exception e) {
			log.warn("Unknown encodingType '{}', falling back to CL100K_BASE",
					properties.getEmbeddingBatch().getEncodingType());
			encodingType = EncodingType.CL100K_BASE;
		}

		return new EnhancedTokenCountBatchingStrategy(encodingType, properties.getEmbeddingBatch().getMaxTokenCount(),
				properties.getEmbeddingBatch().getReservePercentage(),
				properties.getEmbeddingBatch().getMaxTextCount());
	}

	@Bean
	@ConditionalOnMissingBean(ChatClient.class)
	public ChatClient chatClient(ChatModel chatModel) {
		return ChatClient.builder(chatModel).build();
	}

	@Bean(name = "dbOperationExecutor")
	public ExecutorService dbOperationExecutor() {
		// 初始化专用线程池，用于数据库操作
		// 线程数量设置为CPU核心数的2倍，但不少于4个，不超过16个
		int threadCount = Math.max(4, Math.min(Runtime.getRuntime().availableProcessors() * 2, 16));
		log.info("Database operation executor initialized with {} threads", threadCount);
		dbOperationExecutor = Executors.newFixedThreadPool(threadCount, new ThreadFactory() {
			private final AtomicInteger threadNumber = new AtomicInteger(1);

			@Override
			public Thread newThread(@NotNull Runnable r) {
				Thread t = new Thread(r, "db-operation-" + threadNumber.getAndIncrement());
				t.setDaemon(true);
				return t;
			}
		});
		return dbOperationExecutor;
	}

	@Override
	public void destroy() {
		if (dbOperationExecutor != null && !dbOperationExecutor.isShutdown()) {
			log.info("Shutting down database operation executor");
			dbOperationExecutor.shutdown();
			try {
				if (!dbOperationExecutor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
					log.warn("Database operation executor did not terminate gracefully, forcing shutdown");
					dbOperationExecutor.shutdownNow();
				}
			}
			catch (InterruptedException e) {
				log.warn("Interrupted while waiting for database operation executor to terminate");
				dbOperationExecutor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}

}
