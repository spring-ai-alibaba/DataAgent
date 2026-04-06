# API 能力补全实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 DataAgent 补全对外 API 能力：API Key 鉴权、消息 SSE 流式推理、统一错误响应。

**Architecture:** WebFilter 拦截 `/api/**` 做 API Key 校验；`POST /api/sessions/{sessionId}/messages` 改为返回 `Flux<ServerSentEvent<GraphNodeResponse>>`，复用 GraphController 的 Sinks 模式调用 GraphService；`@RestControllerAdvice` 统一异常响应格式。

**Tech Stack:** Spring Boot 3.4.8 + WebFlux, MyBatis, Lombok, JUnit 5 + Mockito

---

## 文件结构

### 新建
- `src/main/java/com/alibaba/cloud/ai/dataagent/filter/ApiKeyFilter.java` — WebFilter，校验 X-API-Key
- `src/main/java/com/alibaba/cloud/ai/dataagent/exception/SessionNotFoundException.java` — 自定义异常
- `src/main/java/com/alibaba/cloud/ai/dataagent/exception/GlobalExceptionHandler.java` — @RestControllerAdvice
- `src/test/java/com/alibaba/cloud/ai/dataagent/filter/ApiKeyFilterTest.java`
- `src/test/java/com/alibaba/cloud/ai/dataagent/exception/GlobalExceptionHandlerTest.java`
- `src/test/java/com/alibaba/cloud/ai/dataagent/controller/ChatControllerSseTest.java`

### 修改
- `src/main/resources/application.yml` — 新增 `app.api-key`
- `src/main/java/com/alibaba/cloud/ai/dataagent/service/chat/ChatMessageService.java` — 新增 `saveAssistantMessage`
- `src/main/java/com/alibaba/cloud/ai/dataagent/service/chat/ChatMessageServiceImpl.java` — 实现
- `src/main/java/com/alibaba/cloud/ai/dataagent/controller/ChatController.java` — saveMessage 改为 SSE 流

---

## Task 1: application.yml 新增 API Key 配置

**Files:**
- Modify: `data-agent-management/src/main/resources/application.yml`

- [ ] **Step 1: 在 application.yml 末尾追加配置**

在文件末尾添加：
```yaml
app:
  api-key: "sk-dataagent-default-key"
```

- [ ] **Step 2: Commit**

```bash
git add data-agent-management/src/main/resources/application.yml
git commit -m "config: add app.api-key property"
```

---

## Task 2: 实现 ApiKeyFilter

**Files:**
- Create: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/filter/ApiKeyFilter.java`
- Test: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/filter/ApiKeyFilterTest.java`

- [ ] **Step 1: 写失败测试**

新建 `ApiKeyFilterTest.java`：

```java
package com.alibaba.cloud.ai.dataagent.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

class ApiKeyFilterTest {

    private ApiKeyFilter filter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyFilter("sk-test-key");
        chain = exchange -> Mono.empty();
    }

    @Test
    void missingApiKey_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest
                .get("/api/sessions/123/messages").build());

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
    }

    @Test
    void wrongApiKey_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest
                .get("/api/sessions/123/messages")
                .header("X-API-Key", "wrong-key").build());

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
    }

    @Test
    void correctApiKey_passesThrough() {
        var chainCalled = new boolean[]{false};
        WebFilterChain passingChain = exchange -> {
            chainCalled[0] = true;
            return Mono.empty();
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest
                .get("/api/sessions/123/messages")
                .header("X-API-Key", "sk-test-key").build());

        StepVerifier.create(filter.filter(exchange, passingChain))
            .verifyComplete();

        assert chainCalled[0];
    }

    @Test
    void swaggerPath_skipsAuth() {
        var chainCalled = new boolean[]{false};
        WebFilterChain passingChain = exchange -> {
            chainCalled[0] = true;
            return Mono.empty();
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest
                .get("/swagger-ui/index.html").build());

        StepVerifier.create(filter.filter(exchange, passingChain))
            .verifyComplete();

        assert chainCalled[0];
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd data-agent-management && ./mvnw test -pl . -Dtest=ApiKeyFilterTest -q 2>&1 | tail -5
```
预期：编译失败，`ApiKeyFilter` 类不存在。

- [ ] **Step 3: 实现 ApiKeyFilter**

新建 `ApiKeyFilter.java`：

```java
package com.alibaba.cloud.ai.dataagent.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@Order(-100)
public class ApiKeyFilter implements WebFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final List<String> EXCLUDED_PREFIXES = List.of(
        "/swagger-ui", "/v3/api-docs", "/actuator"
    );

    private final String configuredApiKey;

    public ApiKeyFilter(@Value("${app.api-key}") String configuredApiKey) {
        this.configuredApiKey = configuredApiKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (apiKey == null || !apiKey.equals(configuredApiKey)) {
            log.warn("Unauthorized request to {}: invalid API key", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            var body = "{\"code\":401,\"message\":\"Invalid API Key\",\"data\":null}";
            var buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes());
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }

        return chain.filter(exchange);
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd data-agent-management && ./mvnw test -pl . -Dtest=ApiKeyFilterTest -q 2>&1 | tail -5
```
预期：`BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/filter/ApiKeyFilter.java \
        data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/filter/ApiKeyFilterTest.java
git commit -m "feat: add ApiKeyFilter for X-API-Key authentication"
```

---

## Task 3: 自定义异常 + 全局异常处理器

**Files:**
- Create: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/exception/SessionNotFoundException.java`
- Create: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/exception/GlobalExceptionHandler.java`
- Test: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/exception/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: 写失败测试**

新建 `GlobalExceptionHandlerTest.java`：

```java
package com.alibaba.cloud.ai.dataagent.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void sessionNotFound_returns404() {
        ResponseEntity<Map<String, Object>> response =
            handler.handleSessionNotFound(new SessionNotFoundException("abc-123"));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().get("code"));
        assertTrue(response.getBody().get("message").toString().contains("abc-123"));
    }

    @Test
    void illegalArgument_returns400() {
        ResponseEntity<Map<String, Object>> response =
            handler.handleBadRequest(new IllegalArgumentException("bad input"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().get("code"));
    }

    @Test
    void genericException_returns500() {
        ResponseEntity<Map<String, Object>> response =
            handler.handleGeneric(new RuntimeException("boom"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().get("code"));
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd data-agent-management && ./mvnw test -pl . -Dtest=GlobalExceptionHandlerTest -q 2>&1 | tail -5
```
预期：编译失败，相关类不存在。

- [ ] **Step 3: 实现 SessionNotFoundException**

新建 `SessionNotFoundException.java`：

```java
package com.alibaba.cloud.ai.dataagent.exception;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String sessionId) {
        super("Session not found: " + sessionId);
    }
}
```

- [ ] **Step 4: 实现 GlobalExceptionHandler**

新建 `GlobalExceptionHandler.java`：

```java
package com.alibaba.cloud.ai.dataagent.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSessionNotFound(SessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("code", 404, "message", ex.getMessage(), "data", ""));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("code", 400, "message", ex.getMessage(), "data", ""));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("code", 500, "message", "Internal server error", "data", ""));
    }
}
```

- [ ] **Step 5: 运行测试，确认通过**

```bash
cd data-agent-management && ./mvnw test -pl . -Dtest=GlobalExceptionHandlerTest -q 2>&1 | tail -5
```
预期：`BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/exception/ \
        data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/exception/
git commit -m "feat: add SessionNotFoundException and GlobalExceptionHandler"
```

---

## Task 4: ChatMessageService 新增 saveAssistantMessage

**Files:**
- Modify: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/chat/ChatMessageService.java`
- Modify: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/chat/ChatMessageServiceImpl.java`

- [ ] **Step 1: 在 ChatMessageService 接口添加方法**

在 `ChatMessageService.java` 的现有方法后追加：

```java
ChatMessage saveAssistantMessage(String sessionId, String content);
```

- [ ] **Step 2: 在 ChatMessageServiceImpl 实现该方法**

在 `ChatMessageServiceImpl.java` 的 `saveMessage` 方法后追加：

```java
@Override
public ChatMessage saveAssistantMessage(String sessionId, String content) {
    ChatMessage message = ChatMessage.builder()
        .sessionId(sessionId)
        .role("assistant")
        .content(content)
        .messageType("text")
        .build();
    log.info("Saving assistant message for session: {}", sessionId);
    return saveMessage(message);
}
```

- [ ] **Step 3: 运行现有测试确保无回归**

```bash
cd data-agent-management && ./mvnw test -pl . -q 2>&1 | tail -10
```
预期：`BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/chat/ChatMessageService.java \
        data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/chat/ChatMessageServiceImpl.java
git commit -m "feat: add saveAssistantMessage to ChatMessageService"
```

---

## Task 5: ChatController.saveMessage 改为 SSE 流式

**Files:**
- Modify: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/controller/ChatController.java`
- Test: `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/controller/ChatControllerSseTest.java`

- [ ] **Step 1: 写失败测试**

新建 `ChatControllerSseTest.java`：

```java
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.dto.ChatMessageDTO;
import com.alibaba.cloud.ai.dataagent.entity.ChatMessage;
import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.exception.SessionNotFoundException;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatMessageService;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatSessionService;
import com.alibaba.cloud.ai.dataagent.service.chat.SessionTitleService;
import com.alibaba.cloud.ai.dataagent.service.graph.GraphService;
import com.alibaba.cloud.ai.dataagent.util.ReportTemplateUtil;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatControllerSseTest {

    private ChatController controller;
    private ChatSessionService chatSessionService;
    private ChatMessageService chatMessageService;
    private GraphService graphService;

    @BeforeEach
    void setUp() {
        chatSessionService = mock(ChatSessionService.class);
        chatMessageService = mock(ChatMessageService.class);
        graphService = mock(GraphService.class);
        SessionTitleService sessionTitleService = mock(SessionTitleService.class);
        ReportTemplateUtil reportTemplateUtil = mock(ReportTemplateUtil.class);
        controller = new ChatController(chatSessionService, chatMessageService,
            sessionTitleService, reportTemplateUtil, graphService);
    }

    @Test
    void sessionNotFound_throwsSessionNotFoundException() {
        when(chatSessionService.findBySessionId("no-such")).thenReturn(null);

        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setRole("user");
        dto.setContent("hello");
        dto.setMessageType("text");

        assertThrows(SessionNotFoundException.class,
            () -> controller.sendMessage("no-such", dto, null));
    }

    @Test
    void validSession_callsGraphService() {
        ChatSession session = new ChatSession();
        session.setId("sess-1");
        session.setAgentId(1);

        when(chatSessionService.findBySessionId("sess-1")).thenReturn(session);
        when(chatMessageService.saveMessage(any())).thenReturn(new ChatMessage());
        doAnswer(inv -> {
            Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = inv.getArgument(0);
            sink.tryEmitNext(ServerSentEvent.<GraphNodeResponse>builder()
                .event("complete")
                .data(GraphNodeResponse.complete("1", "sess-1"))
                .build());
            sink.tryEmitComplete();
            return null;
        }).when(graphService).graphStreamProcess(any(), any());

        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setRole("user");
        dto.setContent("hello");
        dto.setMessageType("text");

        var flux = controller.sendMessage("sess-1", dto, null);
        assertNotNull(flux);
        verify(graphService).graphStreamProcess(any(), any());
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd data-agent-management && ./mvnw test -pl . -Dtest=ChatControllerSseTest -q 2>&1 | tail -5
```
预期：编译失败，`sendMessage` 方法不存在且 `ChatController` 构造函数不匹配。

- [ ] **Step 3: 修改 ChatController**

在 `ChatController.java` 中：

**3a. 新增导入（在现有 import 块后追加）：**

```java
import com.alibaba.cloud.ai.dataagent.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.exception.SessionNotFoundException;
import com.alibaba.cloud.ai.dataagent.service.graph.GraphService;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import java.util.concurrent.atomic.AtomicReference;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_COMPLETE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_ERROR;
```

**3b. 在字段区新增 graphService 字段：**

```java
private final GraphService graphService;
```

**3c. 将现有 `saveMessage` 方法替换为 `sendMessage`：**

将整个 `@PostMapping("/sessions/{sessionId}/messages")` 方法块替换为：

```java
@PostMapping(value = "/sessions/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<GraphNodeResponse>> sendMessage(
        @PathVariable("sessionId") String sessionId,
        @RequestBody ChatMessageDTO request,
        ServerHttpResponse response) {

    ChatSession session = chatSessionService.findBySessionId(sessionId);
    if (session == null) {
        throw new SessionNotFoundException(sessionId);
    }

    ChatMessage userMessage = ChatMessage.builder()
        .sessionId(sessionId)
        .role(request.getRole())
        .content(request.getContent())
        .messageType(request.getMessageType())
        .metadata(request.getMetadata())
        .build();
    chatMessageService.saveMessage(userMessage);
    chatSessionService.updateSessionTime(sessionId);

    if (request.isTitleNeeded()) {
        sessionTitleService.scheduleTitleGeneration(sessionId, request.getContent());
    }

    if (response != null) {
        response.getHeaders().add("Cache-Control", "no-cache");
        response.getHeaders().add("Connection", "keep-alive");
        response.getHeaders().add("Access-Control-Allow-Origin", "*");
    }

    Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink =
        Sinks.many().unicast().onBackpressureBuffer();

    GraphRequest graphRequest = GraphRequest.builder()
        .agentId(String.valueOf(session.getAgentId()))
        .threadId(sessionId)
        .query(request.getContent())
        .build();

    graphService.graphStreamProcess(sink, graphRequest);

    AtomicReference<StringBuilder> accumulator = new AtomicReference<>(new StringBuilder());

    return sink.asFlux()
        .filter(sse -> {
            if (STREAM_EVENT_COMPLETE.equals(sse.event()) || STREAM_EVENT_ERROR.equals(sse.event())) {
                return true;
            }
            return sse.data() != null && sse.data().getText() != null && !sse.data().getText().isEmpty();
        })
        .doOnNext(sse -> {
            if (sse.data() != null && sse.data().getText() != null
                    && !sse.data().isComplete() && !sse.data().isError()) {
                accumulator.get().append(sse.data().getText());
            }
        })
        .doOnComplete(() -> {
            String fullContent = accumulator.get().toString();
            if (!fullContent.isBlank()) {
                chatMessageService.saveAssistantMessage(sessionId, fullContent);
            }
        });
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd data-agent-management && ./mvnw test -pl . -Dtest=ChatControllerSseTest -q 2>&1 | tail -5
```
预期：`BUILD SUCCESS`

- [ ] **Step 5: 运行全量测试确保无回归**

```bash
cd data-agent-management && ./mvnw test -pl . -q 2>&1 | tail -10
```
预期：`BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/controller/ChatController.java \
        data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/controller/ChatControllerSseTest.java
git commit -m "feat: replace saveMessage with SSE streaming sendMessage endpoint"
```

---

## Task 6: 端到端冒烟测试

- [ ] **Step 1: 启动服务**

```bash
cd data-agent-management && ./mvnw spring-boot:run -q
```

- [ ] **Step 2: 创建会话**

```bash
curl -s -X POST "http://localhost:8065/api/agent/1/sessions" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: sk-dataagent-default-key" \
  -d '{"title":"smoke-test"}' | jq .
```
预期：返回包含 `id` 字段的 session JSON。

- [ ] **Step 3: 无 API Key 应返回 401**

```bash
curl -s -o /dev/null -w "%{http_code}" \
  -X POST "http://localhost:8065/api/agent/1/sessions" \
  -H "Content-Type: application/json" \
  -d '{"title":"test"}'
```
预期：`401`

- [ ] **Step 4: 发送消息并接收 SSE 流**

将上一步的 `id` 替换 `<SESSION_ID>`：

```bash
curl -s -N -X POST "http://localhost:8065/api/sessions/<SESSION_ID>/messages" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: sk-dataagent-default-key" \
  -d '{"role":"user","content":"你好","messageType":"text"}'
```
预期：终端逐行输出 `data: {...}` SSE 事件，最后一个 event 为 `complete`。

- [ ] **Step 5: 无效 sessionId 应返回 404**

```bash
curl -s -X POST "http://localhost:8065/api/sessions/not-exist/messages" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: sk-dataagent-default-key" \
  -d '{"role":"user","content":"test","messageType":"text"}' | jq .
```
预期：`{"code":404,"message":"Session not found: not-exist","data":""}`

- [ ] **Step 6: Commit**

```bash
git commit --allow-empty -m "test: smoke test passed for API capability"
```
