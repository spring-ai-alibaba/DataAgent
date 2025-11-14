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
package com.alibaba.cloud.ai.constant;

// Document中metadata中存储的key
public final class DocumentMetadataConstant {

	private DocumentMetadataConstant() {

	}

	// column
	public static final String COLUMN = "column";

	// table
	public static final String TABLE = "table";

	// vectorType
	public static final String VECTOR_TYPE = "vectorType";

	// knowledgeId
	public static final String KNOWLEDGE_ID = "knowledgeId";

	// businessTerm
	public static final String BUSINESS_TERM = "businessTerm";

	// 智能体的知识
	public static final String AGENT_KNOWLEDGE = "agentKnowledge";

	// 关联该文档在数据库中的记录id
	public static final String DB_RECORD_ID = "dbRecordId";

	// isRecall
	public static final String IS_RECALL = "isRecall";

}
