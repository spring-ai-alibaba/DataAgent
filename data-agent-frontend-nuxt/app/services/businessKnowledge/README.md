# 逻辑模块: businessKnowledge

## 模块描述
业务术语知识管理服务，处理业务词汇的增删改查、召回设置及向量同步

## 类 (Classes)
### Class: `BusinessKnowledgeService`
业务术语知识业务逻辑处理类
#### 公开方法:
- `list`: 获取业务术语列表
- `get`: 根据 ID 获取业务术语详情
- `create`: 创建新业务术语
- `update`: 更新业务术语信息
- `delete`: 删除指定业务术语
- `recallKnowledge`: 设置业务术语的召回状态
- `retryEmbedding`: 重新触发业务术语的向量化处理
- `refreshAllKnowledgeToVectorStore`: 刷新智能体下所有的业务术语到向量存储

## 类型定义 (Interfaces)
### `BusinessKnowledgeVO`
**描述**: 业务术语视图对象
```typescript
export interface BusinessKnowledgeVO {
  /** 术语 ID */
  id?: number;
  /** 业务术语名称 */
  businessTerm: string;
  /** 术语描述 */
  description: string;
  /** 同义词 (逗号分隔) */
  synonyms: string;
  /** 是否召回 */
  isRecall: boolean;
  /** 关联的智能体 ID */
  agentId: number;
  /** 创建时间 */
  createdTime?: string;
  /** 更新时间 */
  updatedTime?: string;
  /** 向量化状态 */
  embeddingStatus?: string;
  /** 错误信息 */
  errorMsg?: string;
}
```

### `CreateBusinessKnowledgeDTO`
**描述**: 创建业务术语的 DTO
```typescript
export interface CreateBusinessKnowledgeDTO {
  /** 业务术语名称 */
  businessTerm: string;
  /** 术语描述 */
  description: string;
  /** 同义词 */
  synonyms: string;
  /** 是否召回 */
  isRecall: boolean;
  /** 智能体 ID */
  agentId: number;
}
```

### `UpdateBusinessKnowledgeDTO`
**描述**: 更新业务术语的 DTO
```typescript
export interface UpdateBusinessKnowledgeDTO {
  /** 业务术语名称 */
  businessTerm: string;
  /** 术语描述 */
  description: string;
  /** 同义词 */
  synonyms: string;
  /** 智能体 ID */
  agentId: number;
}
```


---
> 🤖 AI 提示: 逻辑实现请参考 `businessKnowledge/index.ts`。
