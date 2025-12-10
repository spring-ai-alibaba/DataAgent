
/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.util.Assert;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 支持运行时热替换的 EmbeddingModel 代理类。
 */
@Slf4j
public class SwitchableEmbeddingModel implements EmbeddingModel {

	private final AtomicReference<EmbeddingModel> delegate = new AtomicReference<>();

	public SwitchableEmbeddingModel(EmbeddingModel initialModel) {
		Assert.notNull(initialModel, "Initial EmbeddingModel must not be null");
		this.delegate.set(initialModel);
	}

	public void updateDelegate(EmbeddingModel newModel) {
		Assert.notNull(newModel, "New EmbeddingModel must not be null");
		this.delegate.set(newModel);
		log.info("EmbeddingModel implementation has been switched.");
	}

	// ================== 核心接口实现 (必须覆盖) ==================
	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		// 这是最核心的方法，所有的 embed 操作最终都会调到这里
		return delegate.get().call(request);
	}

	@Override
	public float[] embed(Document document) {
		return delegate.get().embed(document);
	}

	// ================== 默认方法的覆盖 (覆盖以确保委托正确) ==================

	@Override
	public float[] embed(String text) {
		return delegate.get().embed(text);
	}

	@Override
	public List<float[]> embed(List<String> texts) {
		return delegate.get().embed(texts);
	}

	@Override
	public EmbeddingResponse embedForResponse(List<String> texts) {
		return delegate.get().embedForResponse(texts);
	}

	@Override
	public int dimensions() {
		return delegate.get().dimensions();
	}

	@Override
	public List<float[]> embed(List<Document> documents, EmbeddingOptions options, BatchingStrategy batchingStrategy) {
		return delegate.get().embed(documents, options, batchingStrategy);
	}

}
