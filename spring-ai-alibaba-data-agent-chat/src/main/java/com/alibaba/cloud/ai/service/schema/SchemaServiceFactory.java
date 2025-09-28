package com.alibaba.cloud.ai.service.schema;

import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.service.schema.impls.AnalyticSchemaService;
import com.alibaba.cloud.ai.service.schema.impls.SimpleSchemaService;
import com.alibaba.cloud.ai.service.vectorstore.VectorStoreService;
import com.alibaba.cloud.ai.util.JsonUtil;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SchemaServiceFactory implements FactoryBean<SchemaService> {

	// todo: 改为枚举，由用户配置决定实现类
	@Value("${spring.ai.vectorstore.analytic.enabled:false}")
	private Boolean analyticEnabled;

	@Autowired
	private DbConfig dbConfig;

	@Autowired
	private VectorStoreService vectorStoreService;

	@Override
	public SchemaService getObject() {
		if (Boolean.TRUE.equals(analyticEnabled)) {
			return new AnalyticSchemaService(dbConfig, JsonUtil.getObjectMapper(), vectorStoreService);
		}
		else {
			return new SimpleSchemaService(dbConfig, JsonUtil.getObjectMapper(), vectorStoreService);
		}
	}

	@Override
	public Class<?> getObjectType() {
		return SchemaService.class;
	}

}
