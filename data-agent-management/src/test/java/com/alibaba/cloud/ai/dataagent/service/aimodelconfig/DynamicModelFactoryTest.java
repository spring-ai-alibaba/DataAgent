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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class DynamicModelFactoryTest {

	private DynamicModelFactory dynamicModelFactory;

	private HttpServer providerServer;

	private HttpServer proxyServer;

	private List<CapturedRequest> providerRequests;

	private List<CapturedRequest> proxyRequests;

	private String providerBaseUrl;

	@BeforeEach
	void setUp() throws IOException {
		dynamicModelFactory = new DynamicModelFactory();
		providerRequests = new CopyOnWriteArrayList<>();
		providerServer = startServer(providerRequests, false);
		providerBaseUrl = "http://127.0.0.1:" + providerServer.getAddress().getPort();
	}

	@AfterEach
	void tearDown() {
		providerServer.stop(0);
		if (proxyServer != null) {
			proxyServer.stop(0);
		}
	}

	@Test
	void createChatModel_validConfig_returnsChatModel() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test-key")
			.baseUrl(providerBaseUrl)
			.modelName("gpt-4")
			.temperature(0.7)
			.maxTokens(2000)
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertEquals("provider response", chatModel.call("Hello"));
		CapturedRequest request = singleProviderRequest();
		assertEquals("/v1/chat/completions", request.path());
		assertEquals("Bearer sk-test-key", request.authorization());
		assertTrue(request.body().contains("\"model\":\"gpt-4\""));
		assertTrue(request.body().contains("\"temperature\":0.7"));
		assertTrue(request.body().contains("\"max_tokens\":2000"));
		assertTrue(request.body().contains("Hello"));
	}

	@Test
	void createChatModel_emptyBaseUrl_throwsException() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl("")
			.modelName("gpt-4")
			.build();

		assertThrows(IllegalArgumentException.class, () -> dynamicModelFactory.createChatModel(config));
	}

	@Test
	void createChatModel_nullBaseUrl_throwsException() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl(null)
			.modelName("gpt-4")
			.build();

		assertThrows(IllegalArgumentException.class, () -> dynamicModelFactory.createChatModel(config));
	}

	@Test
	void createChatModel_emptyModelName_throwsException() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl("http://localhost:8080")
			.modelName("")
			.build();

		assertThrows(IllegalArgumentException.class, () -> dynamicModelFactory.createChatModel(config));
	}

	@Test
	void createChatModel_nonCustomProviderWithoutApiKey_throwsException() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("")
			.baseUrl("http://localhost:8080")
			.modelName("gpt-4")
			.build();

		assertThrows(IllegalArgumentException.class, () -> dynamicModelFactory.createChatModel(config));
	}

	@Test
	void createChatModel_customProviderWithoutApiKey_succeeds() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("custom")
			.apiKey("")
			.baseUrl(providerBaseUrl)
			.modelName("local-model")
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertEquals("provider response", chatModel.call("Hello"));
		assertEquals("/v1/chat/completions", singleProviderRequest().path());
	}

	@Test
	void createChatModel_withCompletionsPath_succeeds() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("custom")
			.apiKey("")
			.baseUrl(providerBaseUrl)
			.modelName("local-model")
			.completionsPath("/custom/chat")
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertEquals("provider response", chatModel.call("Hello"));
		assertEquals("/custom/chat", singleProviderRequest().path());
	}

	@Test
	void createEmbeddingModel_validConfig_returnsEmbeddingModel() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test-key")
			.baseUrl(providerBaseUrl)
			.modelName("text-embedding-ada-002")
			.build();

		EmbeddingModel embeddingModel = dynamicModelFactory.createEmbeddingModel(config);
		assertArrayEquals(new float[] { 0.125f, 0.875f }, embeddingModel.embed("Hello"));
		CapturedRequest request = singleProviderRequest();
		assertEquals("/v1/embeddings", request.path());
		assertEquals("Bearer sk-test-key", request.authorization());
		assertTrue(request.body().contains("\"model\":\"text-embedding-ada-002\""));
		assertTrue(request.body().contains("Hello"));
	}

	@Test
	void createEmbeddingModel_emptyBaseUrl_throwsException() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test")
			.baseUrl("")
			.modelName("text-embedding-ada-002")
			.build();

		assertThrows(IllegalArgumentException.class, () -> dynamicModelFactory.createEmbeddingModel(config));
	}

	@Test
	void createEmbeddingModel_customProviderWithoutApiKey_succeeds() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("custom")
			.apiKey("")
			.baseUrl(providerBaseUrl)
			.modelName("local-embed")
			.build();

		EmbeddingModel embeddingModel = dynamicModelFactory.createEmbeddingModel(config);
		assertArrayEquals(new float[] { 0.125f, 0.875f }, embeddingModel.embed("Hello"));
		assertEquals("/v1/embeddings", singleProviderRequest().path());
	}

	@Test
	void createEmbeddingModel_withEmbeddingsPath_succeeds() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("custom")
			.apiKey("")
			.baseUrl(providerBaseUrl)
			.modelName("local-embed")
			.embeddingsPath("/custom/embeddings")
			.build();

		EmbeddingModel embeddingModel = dynamicModelFactory.createEmbeddingModel(config);
		assertArrayEquals(new float[] { 0.125f, 0.875f }, embeddingModel.embed("Hello"));
		assertEquals("/custom/embeddings", singleProviderRequest().path());
	}

	@Test
	void createEmbeddingModel_withBasePathAndEmbeddingsPath_preservesBothPaths() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("custom")
			.apiKey("")
			.baseUrl(providerBaseUrl + "/compatible-mode/v1")
			.modelName("local-embed")
			.embeddingsPath("/embeddings")
			.build();

		EmbeddingModel embeddingModel = dynamicModelFactory.createEmbeddingModel(config);
		assertArrayEquals(new float[] { 0.125f, 0.875f }, embeddingModel.embed("Hello"));
		assertEquals("/compatible-mode/v1/embeddings", singleProviderRequest().path());
	}

	@Test
	void createChatModel_withProxy_routesRequestThroughProxy() throws IOException {
		proxyRequests = new CopyOnWriteArrayList<>();
		proxyServer = startServer(proxyRequests, false);
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test-key")
			.baseUrl("http://provider.invalid")
			.modelName("gpt-4")
			.proxyEnabled(true)
			.proxyHost("127.0.0.1")
			.proxyPort(proxyServer.getAddress().getPort())
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertEquals("provider response", chatModel.call("Hello through proxy"));
		assertTrue(providerRequests.isEmpty());
		assertEquals(1, proxyRequests.size());
		assertTrue(proxyRequests.get(0).path().contains("/v1/chat/completions"));
		assertTrue(proxyRequests.get(0).body().contains("Hello through proxy"));
	}

	@Test
	void createChatModel_withProxyAndAuth_answersProxyChallenge() throws IOException {
		proxyRequests = new CopyOnWriteArrayList<>();
		proxyServer = startServer(proxyRequests, true);
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test-key")
			.baseUrl("http://provider.invalid")
			.modelName("gpt-4")
			.proxyEnabled(true)
			.proxyHost("127.0.0.1")
			.proxyPort(proxyServer.getAddress().getPort())
			.proxyUsername("user")
			.proxyPassword("pass")
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertEquals("provider response", chatModel.call("Hello with proxy auth"));
		assertTrue(proxyRequests.size() >= 2);
		assertTrue(
				proxyRequests.stream().anyMatch(request -> "Basic dXNlcjpwYXNz".equals(request.proxyAuthorization())));
	}

	@Test
	void createChatModel_proxyDisabled_noProxyUsed() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test-key")
			.baseUrl(providerBaseUrl)
			.modelName("gpt-4")
			.proxyEnabled(false)
			.proxyHost("127.0.0.1")
			.proxyPort(1)
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertEquals("provider response", chatModel.call("Direct"));
		assertEquals("/v1/chat/completions", singleProviderRequest().path());
	}

	@Test
	void createChatModel_proxyNull_noProxyUsed() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("openai")
			.apiKey("sk-test-key")
			.baseUrl(providerBaseUrl)
			.modelName("gpt-4")
			.proxyEnabled(null)
			.proxyHost("127.0.0.1")
			.proxyPort(1)
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertEquals("provider response", chatModel.call("Direct"));
		assertEquals("/v1/chat/completions", singleProviderRequest().path());
	}

	@Test
	void createChatModel_nullApiKey_treatedAsEmpty() {
		ModelConfigDTO config = ModelConfigDTO.builder()
			.provider("custom")
			.apiKey(null)
			.baseUrl(providerBaseUrl)
			.modelName("local-model")
			.build();

		ChatModel chatModel = dynamicModelFactory.createChatModel(config);
		assertEquals("provider response", chatModel.call("No key"));
		CapturedRequest request = singleProviderRequest();
		assertTrue(request.authorization() == null || request.authorization().isBlank()
				|| "Bearer".equals(request.authorization()));
	}

	private HttpServer startServer(List<CapturedRequest> requests, boolean requireProxyAuthentication)
			throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", exchange -> handleRequest(exchange, requests, requireProxyAuthentication));
		server.start();
		return server;
	}

	private void handleRequest(HttpExchange exchange, List<CapturedRequest> requests,
			boolean requireProxyAuthentication) throws IOException {
		String requestPath = exchange.getRequestURI().toString();
		CapturedRequest request = new CapturedRequest(requestPath,
				new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
				exchange.getRequestHeaders().getFirst("Authorization"),
				exchange.getRequestHeaders().getFirst("Proxy-Authorization"));
		requests.add(request);

		if (requireProxyAuthentication && request.proxyAuthorization() == null) {
			exchange.getResponseHeaders().add("Proxy-Authenticate", "Basic realm=\"DataAgent test proxy\"");
			exchange.sendResponseHeaders(407, -1);
			exchange.close();
			return;
		}

		String response = requestPath.contains("embeddings") ? embeddingResponse() : chatResponse();
		byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, responseBytes.length);
		exchange.getResponseBody().write(responseBytes);
		exchange.close();
	}

	private CapturedRequest singleProviderRequest() {
		assertEquals(1, providerRequests.size());
		return providerRequests.get(0);
	}

	private String chatResponse() {
		return """
				{
				  "id": "chatcmpl-test",
				  "object": "chat.completion",
				  "created": 1710000000,
				  "model": "test-model",
				  "choices": [{
				    "index": 0,
				    "message": {"role": "assistant", "content": "provider response"},
				    "finish_reason": "stop"
				  }],
				  "usage": {"prompt_tokens": 1, "completion_tokens": 2, "total_tokens": 3}
				}
				""";
	}

	private String embeddingResponse() {
		return """
				{
				  "object": "list",
				  "data": [{"object": "embedding", "index": 0, "embedding": [0.125, 0.875]}],
				  "model": "test-embedding-model",
				  "usage": {"prompt_tokens": 1, "total_tokens": 1}
				}
				""";
	}

	private record CapturedRequest(String path, String body, String authorization, String proxyAuthorization) {
	}

}
