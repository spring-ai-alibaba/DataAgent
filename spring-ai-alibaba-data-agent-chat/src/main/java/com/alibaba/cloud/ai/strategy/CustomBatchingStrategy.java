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

package com.alibaba.cloud.ai.strategy;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;

import java.util.ArrayList;
import java.util.List;

public class CustomBatchingStrategy implements BatchingStrategy {

	@Override
	public List<List<Document>> batch(List<Document> documents) {
		// TODO 后续优化下分批逻辑，目前dashscope的embedding接口一次只能处理25个documents
		int batchSize = 25;
		List<List<Document>> batches = new ArrayList<>();

		for (int i = 0; i < documents.size(); i += batchSize) {
			int end = Math.min(i + batchSize, documents.size());
			batches.add(documents.subList(i, end));
		}

		return batches;
	}

}
