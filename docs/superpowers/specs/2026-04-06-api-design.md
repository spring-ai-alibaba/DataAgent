# API 能力补全设计文档

**日期**: 2026-04-06  
**状态**: 已审批  
**范围**: 补全 DataAgent 项目的对外 API 能力，包含鉴权、消息异步处理、轮询查询、统一错误响应四个模块

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

**最小侵入方案**：在现有 `ChatController` + `GraphController` 基础上补齐缺口，不引入新依赖（无消息队列、无 Redis），使用 Spring `@Async` 处理异步推理。

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

## 第二节：消息异步处理流与状态模型

### 数据库变更

`chat_message` 表新增字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `status` | `VARCHAR(16)` | `PENDING` / `PROCESSING` / `DONE` / `FAILED` |
| `ai_content` | `TEXT` | AI 回复内容（DONE 后写入） |
| `error_message` | `VARCHAR(512)` | 失败原因（FAILED 时写入） |

### `POST /api/sessions/{sessionId}/messages` 新流程

```
1. 校验 sessionId 存在 → 不存在返回 404
2. 保存用户消息到 chat_message，status = PENDING
3. 立即返回 { messageId, status: "PENDING" }
4. @Async 异步触发：
     a. 更新 status = PROCESSING
     b. 从 chat_session 读取 agentId
     c. 调用 GraphService.graphStreamProcess(agentId, sessionId, content)
        （sessionId 直接作为 threadId 传入，统一命名）
     d. 收集完整流式输出
     e. 成功 → 保存 AI 回复消息，更新用户消息 status = DONE，写入 ai_content
     f. 异常 → 更新 status = FAILED，写入 error_message
```

### 立即返回响应

```json
{
  "code": 200,
  "data": {
    "messageId": "123",
    "status": "PENDING"
  }
}
```

---

## 第三节：轮询接口

### 接口

`GET /api/sessions/{sessionId}/messages/{messageId}`

复用现有 `ChatController`，新增查询方法。

### 响应格式

**进行中（PENDING / PROCESSING）**：
```json
{
  "code": 200,
  "data": {
    "messageId": "123",
    "status": "PENDING",
    "content": null,
    "errorMessage": null
  }
}
```

**成功（DONE）**：
```json
{
  "code": 200,
  "data": {
    "messageId": "123",
    "status": "DONE",
    "content": "AI 生成的回复内容...",
    "errorMessage": null
  }
}
```

**失败（FAILED）**：
```json
{
  "code": 200,
  "data": {
    "messageId": "123",
    "status": "FAILED",
    "content": null,
    "errorMessage": "AI 服务调用超时"
  }
}
```

### 错误情况

| 场景 | 状态码 | 说明 |
|------|--------|------|
| sessionId 不存在 | 404 | Session not found |
| messageId 不属于该 sessionId | 404 | Message not found |

### 客户端轮询建议

每 2 秒轮询一次，收到 `DONE` 或 `FAILED` 后停止。

---

## 第四节：统一错误响应

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
| `MessageNotFoundException` | 404 | 404 |
| `IllegalArgumentException` | 400 | 400 |
| API Key 无效（拦截器直接写响应） | 401 | 401 |
| 未捕获异常 | 500 | 500 |

> AI 推理失败不走 HTTP 异常体系，由异步内部捕获后写入 `chat_message.status = FAILED`。

---

## 涉及文件清单

| 文件 | 变更类型 |
|------|----------|
| `application.yml` | 新增 `app.api-key` 配置 |
| `ApiKeyInterceptor.java` | 新建：鉴权拦截器 |
| `WebMvcConfig.java` | 新建/修改：注册拦截器 |
| `GlobalExceptionHandler.java` | 新建：全局异常处理 |
| `SessionNotFoundException.java` | 新建：自定义异常 |
| `MessageNotFoundException.java` | 新建：自定义异常 |
| `ChatMessage.java` | 修改：新增 status / ai_content / error_message 字段 |
| `ChatMessageMapper.xml` | 修改：新增字段映射与更新 SQL |
| `ChatController.java` | 修改：POST 消息触发异步推理，新增 GET 轮询接口 |
| `ChatMessageService.java` | 修改：新增状态更新方法 |
| `AsyncAiService.java` | 新建：封装 @Async 异步调用 GraphService 的逻辑 |
| DB Migration SQL | 新建：chat_message 表 ALTER 语句 |
