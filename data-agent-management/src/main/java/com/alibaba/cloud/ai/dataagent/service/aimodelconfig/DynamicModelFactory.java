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

import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.enums.ChatApiProtocol;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApi;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.responses.ResponsesApiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicModelFactory {

	/**
	 * 根据配置的接口协议创建 ChatModel。 RESPONSES 协议走自研 ResponsesApiChatModel； CHAT_COMPLETIONS（默认）走
	 * OpenAiChatModel。 两者均实现 ChatModel 接口，对上层完全透明。
	 */
	public ChatModel createChatModel(ModelConfigDTO config) {

		log.info("Creating NEW ChatModel instance. Provider: {}, Model: {}, BaseUrl: {}, Protocol: {}",
				config.getProvider(), config.getModelName(), config.getBaseUrl(), config.getChatApiProtocol());
		// 1. 验证参数
		checkBasic(config);

		// 按接口协议分支：RESPONSES 走自研适配层，其余保持现状走 OpenAiChatModel
		if (ChatApiProtocol.RESPONSES.name().equalsIgnoreCase(config.getChatApiProtocol())) {
			return createResponsesApiChatModel(config);
		}

		// 默认：Chat Completions 协议（现有逻辑，零变更）
		return createCompletionsChatModel(config);
	}

	/**
	 * 创建基于 Responses API 的 ChatModel。 复用现有的 proxy RestClient/WebClient 构建体系，代理能力天然继承。
	 * completionsPath 在 RESPONSES 协议下复用为自定义 responses 路径（默认 /v1/responses）。
	 */
	private ChatModel createResponsesApiChatModel(ModelConfigDTO config) {
		String apiKey = StringUtils.hasText(config.getApiKey()) ? config.getApiKey() : "";
		// completionsPath 复用为 Responses API 路径，默认 /v1/responses
		String responsesPath = StringUtils.hasText(config.getCompletionsPath()) ? config.getCompletionsPath()
				: "/v1/responses";

		ResponsesApi responsesApi = new ResponsesApi(config.getBaseUrl(), apiKey, responsesPath,
				getProxiedRestClientBuilder(config), getProxiedWebClientBuilder(config));

		OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
			.model(config.getModelName())
			.temperature(config.getTemperature())
			.maxTokens(config.getMaxTokens())
			.build();

		return new ResponsesApiChatModel(responsesApi, chatOptions);
	}

	/**
	 * 创建基于 Chat Completions 协议的 ChatModel（原有逻辑）
	 */
	private ChatModel createCompletionsChatModel(ModelConfigDTO config) {
		// 2. 构建 OpenAiApi (核心通讯对象)
		String apiKey = StringUtils.hasText(config.getApiKey()) ? config.getApiKey() : "";
		OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
			.apiKey(apiKey)
			.baseUrl(config.getBaseUrl())
			.restClientBuilder(getProxiedRestClientBuilder(config))
			.webClientBuilder(getProxiedWebClientBuilder(config));

		if (StringUtils.hasText(config.getCompletionsPath())) {
			apiBuilder.completionsPath(config.getCompletionsPath());
		}
		OpenAiApi openAiApi = apiBuilder.build();

		// 3. 构建运行时选项 (设置默认的模型名称，如 "deepseek-chat" 或 "gpt-4")
		OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
			.model(config.getModelName())
			.temperature(config.getTemperature())
			.maxTokens(config.getMaxTokens())
			.streamUsage(true)
			.build();
		// 4. 返回统一的 OpenAiChatModel
		return OpenAiChatModel.builder().openAiApi(openAiApi).defaultOptions(openAiChatOptions).build();
	}

	/**
	 * Embedding 同理
	 */
	public EmbeddingModel createEmbeddingModel(ModelConfigDTO config) {
		log.info("Creating NEW EmbeddingModel instance. Provider: {}, Model: {}, BaseUrl: {}", config.getProvider(),
				config.getModelName(), config.getBaseUrl());
		checkBasic(config);

		String apiKey = StringUtils.hasText(config.getApiKey()) ? config.getApiKey() : "";
		OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
			.apiKey(apiKey)
			.baseUrl(config.getBaseUrl())
			.restClientBuilder(getProxiedRestClientBuilder(config))
			.webClientBuilder(getProxiedWebClientBuilder(config));

		if (StringUtils.hasText(config.getEmbeddingsPath())) {
			apiBuilder.embeddingsPath(config.getEmbeddingsPath());
		}

		OpenAiApi openAiApi = apiBuilder.build();
		return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
				OpenAiEmbeddingOptions.builder().model(config.getModelName()).build(),
				RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	private static void checkBasic(ModelConfigDTO config) {
		Assert.hasText(config.getBaseUrl(), "baseUrl must not be empty");
		if (!"custom".equalsIgnoreCase(config.getProvider())) {
			Assert.hasText(config.getApiKey(), "apiKey must not be empty");
		}
		Assert.hasText(config.getModelName(), "modelName must not be empty");
	}

	private RestClient.Builder getProxiedRestClientBuilder(ModelConfigDTO config) {
		if (config.getProxyEnabled() == null || !config.getProxyEnabled()) {
			return RestClient.builder();
		}

		// 打印同步代理日志
		log.info("【Proxy-Init】Model [{}] is using SYNC proxy -> {}:{}", config.getModelName(), config.getProxyHost(),
				config.getProxyPort());

		BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
		if (StringUtils.hasText(config.getProxyUsername())) {
			log.info("【Proxy-Auth】Enabling Basic Auth for SYNC proxy, user: {}", config.getProxyUsername());
			credsProvider.setCredentials(new AuthScope(config.getProxyHost(), config.getProxyPort()),
					new UsernamePasswordCredentials(config.getProxyUsername(),
							config.getProxyPassword().toCharArray()));
		}

		CloseableHttpClient httpClient = HttpClients.custom()
			.setProxy(new HttpHost(config.getProxyHost(), config.getProxyPort()))
			.setDefaultCredentialsProvider(credsProvider)
			.build();

		return RestClient.builder().requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));
	}

	private WebClient.Builder getProxiedWebClientBuilder(ModelConfigDTO config) {
		if (config.getProxyEnabled() == null || !config.getProxyEnabled()) {
			return WebClient.builder();
		}

		log.info("【Proxy-Init】Model [{}] is using ASYNC (Netty) proxy -> {}:{}", config.getModelName(),
				config.getProxyHost(), config.getProxyPort());

		HttpClient nettyClient = HttpClient.create().responseTimeout(java.time.Duration.ofMinutes(3)).proxy(p -> {
			ProxyProvider.Builder proxyBuilder = p.type(ProxyProvider.Proxy.HTTP)
				.host(config.getProxyHost())
				.port(config.getProxyPort());

			if (StringUtils.hasText(config.getProxyUsername())) {
				log.info("【Proxy-Auth】Enabling Basic Auth for ASYNC proxy, user: {}", config.getProxyUsername());
				proxyBuilder.username(config.getProxyUsername()).password(s -> config.getProxyPassword());
			}
		});

		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(nettyClient));
	}

}
