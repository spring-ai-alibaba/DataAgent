package com.alibaba.cloud.ai.service.vectorstore;

import com.alibaba.cloud.ai.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.service.vectorstore.impls.AnalyticVectorStoreService;
import com.alibaba.cloud.ai.service.vectorstore.impls.SimpleVectorStoreService;
import com.alibaba.cloud.ai.vectorstore.analyticdb.AnalyticDbVectorStoreProperties;
import com.aliyun.gpdb20160503.Client;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(VectorStoreService.class)
public class VectorStoreServiceFactory implements FactoryBean<VectorStoreService> {

	// todo: 改为枚举，由用户配置决定实现类
	@Value("${spring.ai.vectorstore.analytic.enabled:false}")
	private Boolean analyticEnabled;

	@Autowired
	private EmbeddingModel embeddingModel;

	@Autowired
	private AnalyticDbVectorStoreProperties analyticDbVectorStoreProperties;

	@Autowired
	private Client client;

	@Autowired
	private AccessorFactory accessorFactory;

	@Autowired
	private AgentVectorStoreManager agentVectorStoreManager;

	@Override
	public VectorStoreService getObject() {
		if (Boolean.TRUE.equals(analyticEnabled)) {
			return new AnalyticVectorStoreService(analyticDbVectorStoreProperties, embeddingModel, client);
		}
		else {
			return new SimpleVectorStoreService(embeddingModel, accessorFactory, agentVectorStoreManager);
		}
	}

	@Override
	public Class<?> getObjectType() {
		return VectorStoreService.class;
	}

}
