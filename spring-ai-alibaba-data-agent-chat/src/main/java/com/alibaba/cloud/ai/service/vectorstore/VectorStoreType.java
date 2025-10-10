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

/**
 * 向量存储类型枚举 用于配置使用哪种向量存储实现
 */
public enum VectorStoreType {

	/**
	 * 简单向量存储（内存存储）
	 */
	SIMPLE,

	/**
	 * 分析型数据库向量存储
	 */
	ANALYTIC_DB,

	/**
	 * Milvus 向量存储
	 */
	MILVUS,

	/**
	 * PostgreSQL PGVector 向量存储
	 */
	PGVECTOR

}
