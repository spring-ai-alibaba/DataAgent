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
package com.alibaba.cloud.ai.dataagent.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.document.Document;

/**
 * Small deterministic embedding model for vector-store integration tests. It performs
 * real vector calculations without a network dependency or mocking framework.
 */
public final class KeywordEmbeddingModel implements EmbeddingModel {

	private static final int DIMENSIONS = 3;

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		List<Embedding> embeddings = new ArrayList<>();
		for (int i = 0; i < request.getInstructions().size(); i++) {
			embeddings.add(new Embedding(embed(request.getInstructions().get(i)), i));
		}
		return new EmbeddingResponse(embeddings);
	}

	@Override
	public float[] embed(String text) {
		String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
		if (normalized.contains("订单") || normalized.contains("order")) {
			return new float[] { 1.0f, 0.0f, 0.0f };
		}
		if (normalized.contains("用户") || normalized.contains("user")) {
			return new float[] { 0.0f, 1.0f, 0.0f };
		}
		return new float[] { 0.0f, 0.0f, 1.0f };
	}

	@Override
	public float[] embed(Document document) {
		return embed(document.getText());
	}

	@Override
	public int dimensions() {
		return DIMENSIONS;
	}

}
