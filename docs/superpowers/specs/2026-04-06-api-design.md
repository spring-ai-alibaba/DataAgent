# API 能力补全设计文档

**日期**: 2026-04-06  
**状态**: 已实施  
**范围**: 补全 DataAgent 项目的对外 API 能力，包含鉴权、消息 SSE 流式推理、统一错误响应三个模块

---

## 背景

当前项目已有两个接口路径：

- `POST /api/agent/{agentId}/sessions` — 创建会话（已实现）
- `POST /api/sessions/{sessionId}/messages` — 发送消息（仅持久化，未触发 AI 推理）

缺口：
1. 无 API Key 鉴权机制
2. 发送消息接口不触发 AI 推理
3. `sessionId` 与 `GraphService` 使用的 `threadId` 命名不统一
4. 各接口错误响应格式不一致

---

## 总体方案

**最小侵入方案**：在现有 `ChatController` + `GraphController` 基础上补齐缺口，不引入新依赖。消息接口直接返回 `text/event-stream`，复用 `GraphService` 已有的 `Flux<ServerSentEvent>` 流式能力。

---

## 第一节：API Key 鉴权

### 机制

Spring `WebFilter`（非 `HandlerInterceptor`，项目使用 WebFlux）拦截所有 `/api/**` 请求，从 `X-API-Key` Header 提取 key，与配置文件中预置值比对。实现类：`ApiKeyFilter`（`@Component @Order(-100)`）。

### 配置

```yaml
# application.yml / application-h2.yml / application-test.yml（三个 profile 均配置）
app:
  api-key: ${APP_API_KEY:sk-dataagent-default-key}
```

支持通过环境变量 `APP_API_KEY` 覆盖默认值。

### 过滤器逻辑

```
请求进入
  → path 不以 /api/ 开头 → 放行（含 /swagger-ui, /v3/api-docs, /actuator）
  → path 以 /api/ 开头
      → Header X-API-Key 为空、空白或不匹配
          → 响应 401 {"code":401,"message":"Invalid API Key","data":null}
      → 匹配 → 放行
```

### 注册范围

- 拦截路径：以 `/api/` 开头的所有请求
- 放行路径：所有不以 `/api/` 开头的请求（含 `/swagger-ui`、`/v3/api-docs`、`/actuator`）
- **注意**：`/api/swagger-ui` 等路径仍会被拦截（正确行为）

### 不引入

- 无数据库表
- 无 API Key 实体
- 无动态管理接口

---

## 第二节：消息 SSE 流式处理

### 接口语义变更

`POST /api/sessions/{sessionId}/messages` 改为返回 `text/event-stream`，客户端通过 EventSource 或 fetch + ReadableStream 接收 AI 逐 token 输出。

### 请求

```json
{
  "role": "user",
  "content": "给我一个示例",
  "messageType": "text"
}
```

### `POST /api/sessions/{sessionId}/messages` 处理流程

```
1. 校验 sessionId 存在 → 不存在抛出 SessionNotFoundException（走 GlobalExceptionHandler 返回 404）
2. 保存用户消息到 chat_message
3. 调用 chatSessionService.updateSessionTime
4. 可选：scheduleTitleGeneration（若 isTitleNeeded）
5. 从 chat_session 读取 agentId，构建 GraphRequest（threadId = sessionId）
6. 创建 Sinks.Many unicast sink，调用 GraphService.graphStreamProcess(sink, graphRequest)
7. 返回 sink.asFlux()，由 Spring WebFlux 设置 Content-Type: text/event-stream
8. 流中：过滤空 text 事件，用 AtomicReference<StringBuilder> 累积 token 文本
9. doOnComplete / doOnCancel（AtomicBoolean 防重复）：将累积内容存为 role=assistant 的消息
10. doOnError：记录日志
```

> 注意：`sessionId` 直接作为 `threadId` 传入 `GraphRequest`，实现命名统一。

### SSE 事件格式

实际事件由 `GraphNodeResponse` 对象序列化，通过 Spring WebFlux 的 `ServerSentEvent<GraphNodeResponse>` 传递，格式由框架控制。事件类型（event field）：
- 正常 token 事件：无 event 字段（data 中 `text` 含内容）
- 完成事件：`event: complete`，data 中 `complete=true`
- 错误事件：`event: error`，data 中 `error=true`

> SSE 事件格式由 GraphService 内部定义，ChatController 仅过滤并透传。

### 数据库变更

无需新增状态字段。流结束后将完整内容存入现有 `content` 字段，新增一个 `role = assistant` 的消息记录。

---

## 第三节：统一错误响应

### 实现方式

`@RestControllerAdvice` 全局异常处理器 `GlobalExceptionHandler`（`exception` 包）。

**已合并旧有的两个冲突 advisor**（`aop/ExceptionAdvice`、`controller/GlobalExceptionHandler`）均已删除，统一由新 handler 接管。

### 统一响应结构

```json
{
  "code": 404,
  "message": "Session not found",
  "data": null
}
```

### 覆盖场景

| 异常类型 | HTTP Status | code |
|----------|-------------|------|
| `SessionNotFoundException` | 404 | 404 |
| `IllegalArgumentException` | 400 | 400 |
| `InvalidInputException` | 400 | 400 |
| `InternalServerException` | 500 | 500 |
| API Key 无效（WebFilter 直接写响应） | 401 | 401 |
| 未捕获异常 | 500 | 500 |

> AI 推理失败通过 SSE `event: error` 事件通知客户端，不走 HTTP 异常体系（mid-stream 时 HTTP 响应头已发出，无法修改状态码）。

---

## 涉及文件清单（实际实施）

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `application.yml` / `application-h2.yml` / `application-test.yml` | 新增 | `app.api-key: ${APP_API_KEY:sk-dataagent-default-key}` |
| `filter/ApiKeyFilter.java` | 新建 | `@Component @Order(-100) WebFilter`，非 Interceptor |
| `exception/SessionNotFoundException.java` | 新建 | 自定义 404 异常 |
| `exception/GlobalExceptionHandler.java` | 新建 | `@RestControllerAdvice`，统一错误格式 |
| `aop/ExceptionAdvice.java` | **删除** | 与新 handler 冲突，已合并 |
| `controller/GlobalExceptionHandler.java` | **删除** | 与新 handler 冲突，已合并 |
| `service/chat/ChatMessageService.java` | 修改 | 新增 `ChatMessage saveAssistantMessage(String, String)` |
| `service/chat/ChatMessageServiceImpl.java` | 修改 | 实现 `saveAssistantMessage`，复用 `saveMessage` |
| `controller/ChatController.java` | 修改 | `saveMessage` 替换为 SSE 流式 `sendMessage`，新增 `GraphService` 依赖 |
