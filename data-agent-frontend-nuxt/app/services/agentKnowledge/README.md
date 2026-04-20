# 逻辑模块: agentKnowledge

## 模块描述
智能体知识库管理服务，处理知识的分页查询、增删改查及向量化重试

## 类 (Classes)
### Class: `AgentKnowledgeService`
智能体知识库业务逻辑处理类
#### 公开方法:
- `queryByPage`: 分页查询知识列表
- `listByAgentId`: 根据智能体 ID 获取所有知识列表
- `getById`: 根据 ID 获取知识详情
- `create`: 创建新知识
- `createWithFile`: 通过 FormData 创建知识（支持文件上传）
- `update`: 更新知识信息
- `updateRecallStatus`: 更新知识的召回状态
- `delete`: 删除指定知识
- `retryEmbedding`: 重新触发知识的向量化处理
- `getStatistics`: 获取智能体知识库的统计信息

## 类型定义 (Interfaces)
### `AgentKnowledge`
**描述**: 知识库实体接口
```typescript
export interface AgentKnowledge {
  /** 知识 ID */
  id?: number;
  /** 关联的智能体 ID */
  agentId?: number;
  /** 标题 */
  title?: string;
  /** 内容 */
  content?: string;
  /** 类型 (如 text, file) */
  type?: string;
  /** 问题 (针对 QA 类型) */
  question?: string;
  /** 是否召回 (true=召回, false=非召回) */
  isRecall?: boolean;
  /** 向量化状态 */
  embeddingStatus?: string;
  /** 错误信息 */
  errorMsg?: string;
  /** 创建时间 */
  createdTime?: string;
  /** 更新时间 */
  updatedTime?: string;
}
```

### `AgentKnowledgeQueryDTO`
**描述**: 知识库分页查询 DTO
```typescript
export interface AgentKnowledgeQueryDTO {
  /** 智能体 ID */
  agentId: number;
  /** 标题关键词 */
  title?: string;
  /** 知识类型 */
  type?: string;
  /** 向量化状态 */
  embeddingStatus?: string;
  /** 当前页码 */
  pageNum?: number;
  /** 每页条数 */
  pageSize?: number;
}
```

### `PageResult`
**描述**: 通用分页响应结构
```typescript
export interface PageResult<T> {
  /** 是否成功 */
  success: boolean;
  /** 数据列表 */
  data: T[];
  /** 总条数 */
  total: number;
  /** 当前页码 */
  pageNum: number;
  /** 每页条数 */
  pageSize: number;
  /** 总页数 */
  totalPages: number;
  /** 提示消息 */
  message?: string;
}
```


---
> 🤖 AI 提示: 逻辑实现请参考 `agentKnowledge/index.ts`。
