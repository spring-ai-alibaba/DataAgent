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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.properties.AgentSkillProperties;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.SimpleVectorStoreInitialization;
import com.alibaba.cloud.ai.dataagent.splitter.SentenceSplitter;
import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import com.alibaba.cloud.ai.dataagent.splitter.SemanticTextSplitter;
import com.alibaba.cloud.ai.dataagent.splitter.ParagraphTextSplitter;
import com.alibaba.cloud.ai.dataagent.util.McpServerToolUtil;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.alibaba.cloud.ai.dataagent.strategy.EnhancedTokenCountBatchingStrategy;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DataAgent的自动配置类
 *
 * @author vlsmb
 * @since 2025/9/28
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties({ DataAgentProperties.class, FileStorageProperties.class, CodeExecutorProperties.class,
		AgentSkillProperties.class })
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

	/**
	 * 为了不必要的重复手动配置，不要在此添加其他向量的手动配置，如果扩展其他向量，请阅读spring ai文档
	 * <a href="https://springdoc.cn/spring-ai/api/vectordbs.html">...</a>
	 * 根据自己想要的向量，在pom文件引入 Boot Starter 依赖即可。此处配置使用内存向量作为兜底配置
	 */
	@Primary
	@Bean
	@ConditionalOnMissingBean(VectorStore.class)
	@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "simple", matchIfMissing = true)
	public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
		return SimpleVectorStore.builder(embeddingModel).build();
	}

	@Bean
	@ConditionalOnBean(SimpleVectorStore.class)
	public SimpleVectorStoreInitialization simpleVectorStoreInitialization(SimpleVectorStore vectorStore,
			DataAgentProperties properties) {
		return new SimpleVectorStoreInitialization(vectorStore, properties);
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
	public ToolCallbackResolver toolCallbackResolver(GenericApplicationContext context) {
		List<ToolCallback> allFunctionAndToolCallbacks = new ArrayList<>(
				McpServerToolUtil.excludeMcpServerTool(context, ToolCallback.class));
		McpServerToolUtil.excludeMcpServerTool(context, ToolCallbackProvider.class)
			.stream()
			.map(pr -> List.of(pr.getToolCallbacks()))
			.forEach(allFunctionAndToolCallbacks::addAll);

		var staticToolCallbackResolver = new StaticToolCallbackResolver(allFunctionAndToolCallbacks);

		var springBeanToolCallbackResolver = SpringBeanToolCallbackResolver.builder()
			.applicationContext(context)
			.build();

		return new DelegatingToolCallbackResolver(List.of(staticToolCallbackResolver, springBeanToolCallbackResolver));
	}

	/**
	 * 动态生成 EmbeddingModel 的代理 Bean。 原理： 1. 这是一个 Bean，Milvus/PgVector Starter 能看到它，启动不会报错。
	 * 2. 它是动态代理，内部没有写死任何方法。 3. 每次被调用时，它会执行 getTarget() -> registry.getEmbeddingModel()。
	 */
	@Bean
	@Primary
	public EmbeddingModel embeddingModel(AiModelRegistry registry) {

		// 1. 定义目标源 (TargetSource)
		TargetSource targetSource = new TargetSource() {
			@Override
			public Class<?> getTargetClass() {
				return EmbeddingModel.class;
			}

			@Override
			public boolean isStatic() {
				// 关键：声明是动态的，每次都要重新获取目标
				return false;
			}

			@Override
			public Object getTarget() {
				// 每次方法调用，都去注册表拿最新的
				return registry.getEmbeddingModel();
			}

			@Override
			public void releaseTarget(Object target) {
				// 无需释放
			}
		};

		// 2. 创建代理工厂
		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.setTargetSource(targetSource);
		// 代理接口
		proxyFactory.addInterface(EmbeddingModel.class);

		// 3. 返回动态生成的代理对象
		return (EmbeddingModel) proxyFactory.getProxy();
	}

	@Bean(name = "dbOperationExecutor")
	public ExecutorService dbOperationExecutor() {
		// 初始化专用线程池，用于数据库操作
		// 线程数量设置为CPU核心数的2倍，但不少于4个，不超过16个
		int corePoolSize = Math.max(4, Math.min(Runtime.getRuntime().availableProcessors() * 2, 16));
		log.info("Database operation executor initialized with {} threads", corePoolSize);

		// 自定义线程工厂
		ThreadFactory threadFactory = new ThreadFactory() {
			private final AtomicInteger threadNumber = new AtomicInteger(1);

			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "db-operation-" + threadNumber.getAndIncrement());
				t.setDaemon(false);
				if (t.getPriority() != Thread.NORM_PRIORITY) {
					t.setPriority(Thread.NORM_PRIORITY);
				}
				return t;
			}
		};

		// 创建原生线程池
		this.dbOperationExecutor = new ThreadPoolExecutor(corePoolSize, corePoolSize, 60L, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(500), threadFactory, new ThreadPoolExecutor.CallerRunsPolicy());

		return dbOperationExecutor;
	}

	@Override
	public void destroy() {
		if (dbOperationExecutor != null && !dbOperationExecutor.isShutdown()) {
			log.info("Shutting down database operation executor...");

			// 记录关闭前的状态，便于排查问题
			if (dbOperationExecutor instanceof ThreadPoolExecutor tpe) {
				log.info("Executor Status before shutdown: [Queue Size: {}], [Active Count: {}], [Completed Tasks: {}]",
						tpe.getQueue().size(), tpe.getActiveCount(), tpe.getCompletedTaskCount());
			}

			// 1. 停止接收新任务
			dbOperationExecutor.shutdown();

			try {
				// 2. 等待现有任务完成（包括队列中的）
				if (!dbOperationExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
					log.warn("Executor did not terminate in 60s. Forcing shutdown...");

					// 3. 超时强行关闭
					dbOperationExecutor.shutdownNow();

					// 4. 再次确认是否关闭
					if (!dbOperationExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
						log.error("Executor failed to terminate completely.");
					}
				}
				else {
					log.info("Database operation executor terminated gracefully.");
				}
			}
			catch (InterruptedException e) {
				log.warn("Interrupted during executor shutdown. Forcing immediate shutdown.");
				dbOperationExecutor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}

	@Bean(name = "token")
	public TextSplitter textSplitter(DataAgentProperties properties) {
		DataAgentProperties.TextSplitter textSplitterProps = properties.getTextSplitter();
		DataAgentProperties.TextSplitter.TokenTextSplitterConfig config = textSplitterProps.getToken();
		return new TokenTextSplitter(textSplitterProps.getChunkSize(), config.getMinChunkSizeChars(),
				config.getMinChunkLengthToEmbed(), config.getMaxNumChunks(), config.isKeepSeparator());
	}

	/**
	 * 递归字符文本分块器
	 * @param properties 分块配置
	 * @return RecursiveCharacterTextSplitter实例
	 */
	@Bean(name = "recursive")
	public TextSplitter recursiveTextSplitter(DataAgentProperties properties) {
		DataAgentProperties.TextSplitter textSplitterProps = properties.getTextSplitter();
		DataAgentProperties.TextSplitter.RecursiveTextSplitterConfig config = textSplitterProps.getRecursive();
		// RecursiveCharacterTextSplitter
		String[] separators = config.getSeparators();
		if (separators != null && separators.length > 0) {
			return new RecursiveCharacterTextSplitter(textSplitterProps.getChunkSize(), separators);
		}
		else {
			return new RecursiveCharacterTextSplitter(textSplitterProps.getChunkSize());
		}
	}

	/**
	 * 句子分块器
	 * @param properties 分块配置
	 * @return SentenceSplitter实例
	 */
	@Bean(name = "sentence")
	public TextSplitter sentenceSplitter(DataAgentProperties properties) {
		DataAgentProperties.TextSplitter textSplitterConfig = properties.getTextSplitter();
		DataAgentProperties.TextSplitter.SentenceTextSplitterConfig sentenceConfig = textSplitterConfig.getSentence();

		return SentenceSplitter.builder()
			.withChunkSize(textSplitterConfig.getChunkSize())
			.withSentenceOverlap(sentenceConfig.getSentenceOverlap())
			.build();
	}

	/**
	 * 语义分块器
	 * @param properties 分块配置
	 * @param embeddingModel Embedding 模型
	 * @return SemanticTextSplitter实例
	 */
	@Bean(name = "semantic")
	public TextSplitter semanticSplitter(DataAgentProperties properties, EmbeddingModel embeddingModel) {
		DataAgentProperties.TextSplitter textSplitterProps = properties.getTextSplitter();
		DataAgentProperties.TextSplitter.SemanticTextSplitterConfig config = textSplitterProps.getSemantic();
		return SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.minChunkSize(config.getMinChunkSize())
			.maxChunkSize(config.getMaxChunkSize())
			.similarityThreshold(config.getSimilarityThreshold())
			.build();
	}

	/**
	 * 段落分块器
	 * @param properties 分块配置
	 * @return ParagraphTextSplitter实例
	 */
	@Bean(name = "paragraph")
	public TextSplitter paragraphSplitter(DataAgentProperties properties) {
		DataAgentProperties.TextSplitter textSplitterProps = properties.getTextSplitter();
		DataAgentProperties.TextSplitter.ParagraphTextSplitterConfig config = textSplitterProps.getParagraph();
		return ParagraphTextSplitter.builder()
			.chunkSize(textSplitterProps.getChunkSize())
			.paragraphOverlapChars(config.getParagraphOverlapChars())
			.build();
	}

}
