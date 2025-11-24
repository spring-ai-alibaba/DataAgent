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

package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.config.file.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.dispatcher.FeasibilityAssessmentDispatcher;
import com.alibaba.cloud.ai.dataagent.dispatcher.HumanFeedbackDispatcher;
import com.alibaba.cloud.ai.dataagent.dispatcher.IntentRecognitionDispatcher;
import com.alibaba.cloud.ai.dataagent.dispatcher.PlanExecutorDispatcher;
import com.alibaba.cloud.ai.dataagent.dispatcher.PythonExecutorDispatcher;
import com.alibaba.cloud.ai.dataagent.dispatcher.QueryEnhanceDispatcher;
import com.alibaba.cloud.ai.dataagent.dispatcher.SQLExecutorDispatcher;
import com.alibaba.cloud.ai.dataagent.dispatcher.SemanticConsistenceDispatcher;
import com.alibaba.cloud.ai.dataagent.dispatcher.SqlGenerateDispatcher;
import com.alibaba.cloud.ai.dataagent.dispatcher.SqlOptimizeDispatcher;
import com.alibaba.cloud.ai.dataagent.dispatcher.TableRelationDispatcher;
import com.alibaba.cloud.ai.dataagent.node.EvidenceRecallNode;
import com.alibaba.cloud.ai.dataagent.node.FeasibilityAssessmentNode;
import com.alibaba.cloud.ai.dataagent.node.HumanFeedbackNode;
import com.alibaba.cloud.ai.dataagent.node.IntentRecognitionNode;
import com.alibaba.cloud.ai.dataagent.node.PlanExecutorNode;
import com.alibaba.cloud.ai.dataagent.node.PlannerNode;
import com.alibaba.cloud.ai.dataagent.node.PythonAnalyzeNode;
import com.alibaba.cloud.ai.dataagent.node.PythonExecuteNode;
import com.alibaba.cloud.ai.dataagent.node.PythonGenerateNode;
import com.alibaba.cloud.ai.dataagent.node.QueryEnhanceNode;
import com.alibaba.cloud.ai.dataagent.node.ReportGeneratorNode;
import com.alibaba.cloud.ai.dataagent.node.SchemaRecallNode;
import com.alibaba.cloud.ai.dataagent.node.SemanticConsistencyNode;
import com.alibaba.cloud.ai.dataagent.node.SqlExecuteNode;
import com.alibaba.cloud.ai.dataagent.node.SqlGenerateNode;
import com.alibaba.cloud.ai.dataagent.node.SqlOptimizeNode;
import com.alibaba.cloud.ai.dataagent.node.TableRelationNode;
import com.alibaba.cloud.ai.dataagent.strategy.EnhancedTokenCountBatchingStrategy;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.dataagent.util.NodeBeanUtil;
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

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
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
			keyStrategyHashMap.put(INPUT_KEY, KeyStrategy.REPLACE);
			// Agent ID
			keyStrategyHashMap.put(AGENT_ID, KeyStrategy.REPLACE);
			// Intent recognition
			keyStrategyHashMap.put(INTENT_RECOGNITION_NODE_OUTPUT, KeyStrategy.REPLACE);
			// QUERY_ENHANCE_NODE节点输出
			keyStrategyHashMap.put(QUERY_ENHANCE_NODE_OUTPUT, KeyStrategy.REPLACE);
			// Semantic model
			keyStrategyHashMap.put(GENEGRATED_SEMANTIC_MODEL_PROMPT, KeyStrategy.REPLACE);
			// EVIDENCE节点输出
			keyStrategyHashMap.put(EVIDENCE, KeyStrategy.REPLACE);
			// schema recall节点输出
			keyStrategyHashMap.put(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, KeyStrategy.REPLACE);
			// table relation节点输出
			keyStrategyHashMap.put(TABLE_RELATION_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(TABLE_RELATION_EXCEPTION_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(TABLE_RELATION_RETRY_COUNT, KeyStrategy.REPLACE);
			// Feasibility Assessment 节点输出
			keyStrategyHashMap.put(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, KeyStrategy.REPLACE);
			// sql generate节点输出
			keyStrategyHashMap.put(SQL_GENERATE_SCHEMA_MISSING_ADVICE, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_GENERATE_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_GENERATE_COUNT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_REGENERATE_REASON, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_OPTIMIZE_COUNT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_OPTIMIZE_BEST_SQL, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_OPTIMIZE_BEST_SCORE, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_OPTIMIZE_FINISHED, KeyStrategy.REPLACE);
			// Semantic consistence节点输出
			keyStrategyHashMap.put(SEMANTIC_CONSISTENCY_NODE_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SEMANTIC_CONSISTENCY_NODE_RECOMMEND_OUTPUT, KeyStrategy.REPLACE);
			// Planner 节点输出
			keyStrategyHashMap.put(PLANNER_NODE_OUTPUT, KeyStrategy.REPLACE);
			// PlanExecutorNode
			keyStrategyHashMap.put(PLAN_CURRENT_STEP, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PLAN_NEXT_NODE, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PLAN_VALIDATION_STATUS, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PLAN_VALIDATION_ERROR, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PLAN_REPAIR_COUNT, KeyStrategy.REPLACE);
			// SQL Execute 节点输出
			keyStrategyHashMap.put(SQL_EXECUTE_NODE_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(SQL_EXECUTE_NODE_EXCEPTION_OUTPUT, KeyStrategy.REPLACE);
			// Python代码运行相关
			keyStrategyHashMap.put(SQL_RESULT_LIST_MEMORY, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PYTHON_IS_SUCCESS, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PYTHON_TRIES_COUNT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PYTHON_EXECUTE_NODE_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PYTHON_GENERATE_NODE_OUTPUT, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(PYTHON_ANALYSIS_NODE_OUTPUT, KeyStrategy.REPLACE);
			// NL2SQL相关
			keyStrategyHashMap.put(IS_ONLY_NL2SQL, KeyStrategy.REPLACE);
			keyStrategyHashMap.put(ONLY_NL2SQL_OUTPUT, KeyStrategy.REPLACE);
			// Human Review keys
			keyStrategyHashMap.put(HUMAN_REVIEW_ENABLED, KeyStrategy.REPLACE);
			// Final result
			keyStrategyHashMap.put(RESULT, KeyStrategy.REPLACE);
			return keyStrategyHashMap;
		};

		StateGraph stateGraph = new StateGraph(NL2SQL_GRAPH_NAME, keyStrategyFactory)
			.addNode(INTENT_RECOGNITION_NODE, nodeBeanUtil.getNodeBeanAsync(IntentRecognitionNode.class))
			.addNode(EVIDENCE_RECALL_NODE, nodeBeanUtil.getNodeBeanAsync(EvidenceRecallNode.class))
			.addNode(QUERY_ENHANCE_NODE, nodeBeanUtil.getNodeBeanAsync(QueryEnhanceNode.class))
			.addNode(SCHEMA_RECALL_NODE, nodeBeanUtil.getNodeBeanAsync(SchemaRecallNode.class))
			.addNode(TABLE_RELATION_NODE, nodeBeanUtil.getNodeBeanAsync(TableRelationNode.class))
			.addNode(FEASIBILITY_ASSESSMENT_NODE, nodeBeanUtil.getNodeBeanAsync(FeasibilityAssessmentNode.class))
			.addNode(SQL_GENERATE_NODE, nodeBeanUtil.getNodeBeanAsync(SqlGenerateNode.class))
			.addNode(PLANNER_NODE, nodeBeanUtil.getNodeBeanAsync(PlannerNode.class))
			.addNode(PLAN_EXECUTOR_NODE, nodeBeanUtil.getNodeBeanAsync(PlanExecutorNode.class))
			.addNode(SQL_EXECUTE_NODE, nodeBeanUtil.getNodeBeanAsync(SqlExecuteNode.class))
			.addNode(SQL_OPTIMIZE_NODE, nodeBeanUtil.getNodeBeanAsync(SqlOptimizeNode.class))
			.addNode(PYTHON_GENERATE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonGenerateNode.class))
			.addNode(PYTHON_EXECUTE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonExecuteNode.class))
			.addNode(PYTHON_ANALYZE_NODE, nodeBeanUtil.getNodeBeanAsync(PythonAnalyzeNode.class))
			.addNode(REPORT_GENERATOR_NODE, nodeBeanUtil.getNodeBeanAsync(ReportGeneratorNode.class))
			.addNode(SEMANTIC_CONSISTENCY_NODE, nodeBeanUtil.getNodeBeanAsync(SemanticConsistencyNode.class))
			.addNode(HUMAN_FEEDBACK_NODE, nodeBeanUtil.getNodeBeanAsync(HumanFeedbackNode.class));

		stateGraph.addEdge(START, INTENT_RECOGNITION_NODE)
			.addConditionalEdges(INTENT_RECOGNITION_NODE, edge_async(new IntentRecognitionDispatcher()),
					Map.of(EVIDENCE_RECALL_NODE, EVIDENCE_RECALL_NODE, END, END))
			.addEdge(EVIDENCE_RECALL_NODE, QUERY_ENHANCE_NODE)
			.addConditionalEdges(QUERY_ENHANCE_NODE, edge_async(new QueryEnhanceDispatcher()),
					Map.of(SCHEMA_RECALL_NODE, SCHEMA_RECALL_NODE, END, END))
			.addEdge(SCHEMA_RECALL_NODE, TABLE_RELATION_NODE)
			.addConditionalEdges(TABLE_RELATION_NODE, edge_async(new TableRelationDispatcher()),
					Map.of(FEASIBILITY_ASSESSMENT_NODE, FEASIBILITY_ASSESSMENT_NODE, END, END, TABLE_RELATION_NODE,
							TABLE_RELATION_NODE)) // retry
			.addConditionalEdges(FEASIBILITY_ASSESSMENT_NODE, edge_async(new FeasibilityAssessmentDispatcher()),
					Map.of(PLANNER_NODE, PLANNER_NODE, END, END))

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
					SQL_GENERATE_NODE, SQL_GENERATE_NODE, PYTHON_GENERATE_NODE, PYTHON_GENERATE_NODE,
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
			// sql generate and sql execute node
			.addConditionalEdges(SQL_GENERATE_NODE, nodeBeanUtil.getEdgeBeanAsync(SqlGenerateDispatcher.class),
					Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE, FEASIBILITY_ASSESSMENT_NODE,
							FEASIBILITY_ASSESSMENT_NODE, END, END, SQL_OPTIMIZE_NODE, SQL_OPTIMIZE_NODE))
			.addConditionalEdges(SQL_OPTIMIZE_NODE, edge_async(new SqlOptimizeDispatcher()),
					Map.of(SEMANTIC_CONSISTENCY_NODE, SEMANTIC_CONSISTENCY_NODE, SQL_OPTIMIZE_NODE, SQL_OPTIMIZE_NODE))
			.addConditionalEdges(SEMANTIC_CONSISTENCY_NODE, edge_async(new SemanticConsistenceDispatcher()),
					Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE, SQL_EXECUTE_NODE, SQL_EXECUTE_NODE))
			.addConditionalEdges(SQL_EXECUTE_NODE, edge_async(new SQLExecutorDispatcher()),
					Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE, PLAN_EXECUTOR_NODE, PLAN_EXECUTOR_NODE));

		GraphRepresentation graphRepresentation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
				"workflow graph");

		log.info("workflow in PlantUML format as follows \n\n" + graphRepresentation.content() + "\n\n");

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
