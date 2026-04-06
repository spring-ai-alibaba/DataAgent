# API 能力补全设计文档

**日期**: 2026-04-06  
**状态**: 已审批  
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

Spring `HandlerInterceptor` 拦截所有 `/api/**` 请求，从 `X-API-Key` Header 提取 key，与配置文件中预置值比对。

### 配置

```yaml
# application.yml
app:
  api-key: "your-secret-key-here"
```

### 拦截器逻辑

```
请求进入
  → 读取 Header: X-API-Key
  → 为空或不匹配 → 返回 401 { code: 401, message: "Invalid API Key" }
  → 匹配 → 放行
```

### 注册范围

- 拦截路径：`/api/**`
- 排除路径：`/actuator/**`、`/swagger-ui/**`、`/v3/api-docs/**`

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
1. 校验 sessionId 存在 → 不存在立即发送 SSE error 事件后关闭流
2. 保存用户消息到 chat_message
3. 从 chat_session 读取 agentId
4. 设置响应头 Content-Type: text/event-stream
5. 调用 GraphService.graphStreamProcess(agentId, sessionId, content)
   （sessionId 直接作为 threadId 传入，统一命名）
6. 将 Flux<ServerSentEvent> 直接透传给客户端：
     - 每个 token → data: {"type":"token","content":"..."}
     - 完成      → data: {"type":"done"}
     - 异常      → data: {"type":"error","message":"..."}
7. 流结束后，将完整 AI 回复拼接保存到 chat_message 表
```

### SSE 事件格式

```
data: {"type":"token","content":"AI"}
data: {"type":"token","content":" 生成"}
data: {"type":"token","content":"内容..."}
data: {"type":"done"}
```

异常时：
```
data: {"type":"error","message":"AI 服务调用超时"}
```

### 数据库变更

无需新增状态字段。流结束后将完整内容存入现有 `content` 字段，新增一个 `role = assistant` 的消息记录。

---

## 第三节：统一错误响应

### 实现方式

`@RestControllerAdvice` 全局异常处理器 `GlobalExceptionHandler`。

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
| API Key 无效（拦截器直接写响应） | 401 | 401 |
| 未捕获异常 | 500 | 500 |

> AI 推理失败通过 SSE `{"type":"error","message":"..."}` 事件通知客户端，不走 HTTP 异常体系。

---

## 涉及文件清单

| 文件 | 变更类型 |
|------|----------|
| `application.yml` | 新增 `app.api-key` 配置 |
| `ApiKeyInterceptor.java` | 新建：鉴权拦截器 |
| `WebMvcConfig.java` | 新建/修改：注册拦截器 |
| `GlobalExceptionHandler.java` | 新建：全局异常处理 |
| `SessionNotFoundException.java` | 新建：自定义异常 |
| `ChatController.java` | 修改：POST 消息返回 SSE 流，透传 GraphService Flux |
| `ChatMessageService.java` | 修改：新增流结束后保存 AI 回复方法 |
