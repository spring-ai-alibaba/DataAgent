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
package com.alibaba.cloud.ai.dataagent.service.aimodelconfig;

import com.alibaba.cloud.ai.dataagent.enums.ModelTier;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelRegistry {

	private final DynamicModelFactory modelFactory;

	private final ModelConfigDataService modelConfigDataService;

	// 缓存对象 (volatile 保证可见性)
	private volatile EmbeddingModel currentEmbeddingModel;

	private final Map<ModelTier, ChatClient> chatClientCache = new ConcurrentHashMap<>();

	// =========================================================
	// 1. 获取 ChatClient (懒加载 + 缓存)
	// =========================================================
	public ChatClient getChatClient(ModelTier modelTier) {
		return chatClientCache.computeIfAbsent(modelTier, tier -> {
			log.info("Initializing ChatClient for tier: {}", tier);

			try {
				ModelConfigDTO config = modelConfigDataService.getActiveConfigByTypeAndTier(ModelType.CHAT, tier);

				if (config != null) {
					ChatModel chatModel = modelFactory.createChatModel(config);
					return ChatClient.builder(chatModel).build();
				}
			}
			catch (Exception e) {
				log.error("Failed to initialize ChatClient for tier {}: {}", tier, e.getMessage(), e);
				throw new RuntimeException("Failed to initialize ChatClient", e);
			}

			throw new RuntimeException(String
				.format("No active CHAT model configured for tier %s. Please configure it in the dashboard.", tier));
		});
	}

	@Deprecated
	public ChatClient getChatClient() {
		// 默认使用 STANDARD 层的对话模型
		return getChatClient(ModelTier.STANDARD);
	}

	// =========================================================
	// 2. 获取 EmbeddingModel (懒加载 + Dummy 兜底)
	// =========================================================
	public EmbeddingModel getEmbeddingModel() {
		if (currentEmbeddingModel == null) {
			synchronized (this) {
				if (currentEmbeddingModel == null) {
					log.info("Initializing global EmbeddingModel...");
					try {
						ModelConfigDTO config = modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING);
						if (config != null) {
							currentEmbeddingModel = modelFactory.createEmbeddingModel(config);
						}
					}
					catch (Exception e) {
						log.error("Failed to initialize EmbeddingModel: {}", e.getMessage());
					}

					// 兜底：为了防止 VectorStore Starter 启动时调用 dimensions() 报错
					// 我们必须返回一个"哑巴"模型，而不是 null 或 抛异常
					if (currentEmbeddingModel == null) {
						log.warn("Using DummyEmbeddingModel for fallback.");
						currentEmbeddingModel = new DummyEmbeddingModel();
					}
				}
			}
		}
		return currentEmbeddingModel;
	}

	// =========================================================
	// 3. 刷新/重置缓存 (用于热切换)
	// =========================================================

	public void refreshChat(@Nullable ModelTier modelTier) {
		if (modelTier == null) {
			// 如果没有指定层级，默认清除所有层级的缓存
			chatClientCache.clear();
			log.info("Chat cache CLEARED for ALL tiers.");
			return;
		}

		ChatClient removedClient = this.chatClientCache.remove(modelTier);
		if (removedClient != null) {
			log.info("Chat cache CLEARED for tier: {}. Old client hash: {}", modelTier, removedClient.hashCode());
		}
		else {
			log.warn("Attempted to clear cache for tier: {}, but it was already empty.", modelTier);
		}
	}

	public void refreshChat() {
		this.chatClientCache.clear();
		log.info("Chat cache cleared.");
	}

	public void refreshEmbedding() {
		this.currentEmbeddingModel = null;
		log.info("Embedding cache cleared.");
	}

	// =========================================================
	// 4. 内部类：哑巴嵌入模型 (仅用于启动时防崩)
	// =========================================================
	private static class DummyEmbeddingModel implements EmbeddingModel {

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			throw new RuntimeException("No active EMBEDDING model. Please configure it first!");
		}

		@Override
		public float[] embed(Document document) {
			return new float[0];
		}

		@Override
		public float[] embed(String text) {
			return new float[0];
		}

		@Override
		public List<float[]> embed(List<String> texts) {
			return List.of();
		}

		@Override
		public EmbeddingResponse embedForResponse(List<String> texts) {
			return null;
		}

		// 关键：返回一个常用维度 (1536是OpenAI的维度)，骗过向量库的初始化检查
		@Override
		public int dimensions() {
			return 1536;
		}

	}

}
