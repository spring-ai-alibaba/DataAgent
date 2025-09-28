package com.alibaba.cloud.ai.service.processing.impls;

import com.alibaba.cloud.ai.service.LlmService;
import com.alibaba.cloud.ai.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.service.schema.SchemaService;
import com.alibaba.cloud.ai.service.processing.AbstractQueryProcessingService;
import com.alibaba.cloud.ai.service.vectorstore.VectorStoreService;
import org.springframework.stereotype.Service;

@Service
public class QueryProcessingServiceImpl extends AbstractQueryProcessingService {

	private final VectorStoreService vectorStoreService;

	private final SchemaService schemaService;

	private final Nl2SqlService nl2SqlService;

	public QueryProcessingServiceImpl(LlmService aiService, VectorStoreService vectorStoreService,
			SchemaService schemaService, Nl2SqlService nl2SqlService) {
		super(aiService);
		this.vectorStoreService = vectorStoreService;
		this.schemaService = schemaService;
		this.nl2SqlService = nl2SqlService;
	}

	@Override
	protected VectorStoreService getVectorStoreService() {
		return this.vectorStoreService;
	}

	@Override
	protected SchemaService getSchemaService() {
		return this.schemaService;
	}

	@Override
	protected Nl2SqlService getNl2SqlService() {
		return this.nl2SqlService;
	}

}
