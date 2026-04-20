# 逻辑模块: chat

## 模块描述
聊天会话服务，处理会话的创建、查询、删除、置顶以及消息的保存与报告下载

## 类 (Classes)
### Class: `ChatService`
聊天业务逻辑处理类
#### 公开方法:
- `getAgentSessions`: 获取指定智能体的会话列表
- `createSession`: 创建新会话
- `clearAgentSessions`: 清空指定智能体的所有会话
- `getSessionMessages`: 获取指定会话的所有消息
- `saveMessage`: 保存消息到指定会话
- `pinSession`: 置顶或取消置顶会话
- `renameSession`: 重命名会话标题
- `deleteSession`: 删除指定会话
- `downloadHtmlReport`: 下载会话的 HTML 报告

## 类型定义 (Interfaces)
### `ChatSession`
**描述**: 聊天会话实体接口
```typescript
export interface ChatSession {
  /** 会话 ID (UUID) */
  id: string;
  /** 关联的智能体 ID */
  agentId: number;
  /** 会话标题 */
  title: string;
  /** 状态 (active, archived, deleted) */
  status: string;
  /** 是否置顶 */
  isPinned: boolean;
  /** 用户 ID */
  userId?: number;
  /** 创建时间 */
  createTime?: Date;
  /** 更新时间 */
  updateTime?: Date;
}
```

### `ChatMessage`
**描述**: 聊天消息实体接口
```typescript
export interface ChatMessage {
  /** 消息 ID */
  id?: number;
  /** 所属会话 ID */
  sessionId: string;
  /** 角色 (user, assistant, system) */
  role: string;
  /** 消息内容 */
  content: string;
  /** 消息类型 (text, sql, result, error) */
  messageType: string;
  /** 元数据 (JSON 字符串) */
  metadata?: string;
  /** 创建时间 */
  createTime?: Date;
  /** 是否需要生成标题 */
  titleNeeded?: boolean;
}
```


---
> 🤖 AI 提示: 逻辑实现请参考 `chat/index.ts`。
