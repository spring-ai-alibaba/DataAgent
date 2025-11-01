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

package com.alibaba.cloud.ai.service.hybrid.retrieval;

import com.alibaba.cloud.ai.request.AgentSearchRequest;
import org.springframework.ai.document.Document;

import java.util.List;

public interface HybridRetrievalStrategy {

	/**
	 * 查询某个agent下文档类型为vectorType的文档，通过query、关键词进行混合检索
	 * @return 混合检索后的文档
	 */
	List<Document> retrieve(AgentSearchRequest request);

}
