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
package com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.impl;

import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.FusionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class RrfFusionStrategy implements FusionStrategy {

	@SuppressWarnings("unchecked")
	@Override
	public List<Document> fuseResults(int topK, List<Document>... resultLists) {
		if (resultLists == null || resultLists.length == 0) {
			return List.of();
		}

		// RRF参数配置
		int k = 60;

		// 使用Map存储每个文档的RRF分数
		Map<String, Double> rrfScores = new HashMap<>();
		// 使用Map存储文档ID到Document对象的映射
		Map<String, Document> documentMap = new HashMap<>();

		for (List<Document> resultList : resultLists) {
			if (resultList == null) {
				continue;
			}

			for (int i = 0; i < resultList.size(); i++) {
				Document doc = resultList.get(i);
				// 排名从1开始
				int rank = i + 1;
				// 假设 getDocumentId 方法能稳定获取唯一ID
				String docId = getDocumentId(doc);

				// 计算并累加RRF分数: score = 1 / (k + rank)
				rrfScores.merge(docId, 1.0 / (k + rank), Double::sum);
				// 如果是第一次遇到该文档,存入map
				documentMap.putIfAbsent(docId, doc);
			}
		}

		// 如果处理完所有列表后没有任何文档，提前返回
		if (rrfScores.isEmpty()) {
			return List.of();
		}

		// 按RRF分数降序排序，取topK个
		return rrfScores.entrySet()
			.stream()
			.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
			.limit(topK)
			.map(entry -> documentMap.get(entry.getKey()))
			.collect(Collectors.toList());

	}

	private String getDocumentId(Document document) {
		if (StringUtils.hasText(document.getId())) {
			return document.getId();
		}
		log.error("Oops, this should never happen.Document ID is empty, using content hash as ID");
		// 使用内容hash作为ID
		return String.valueOf(document.getText().hashCode());
	}

}
