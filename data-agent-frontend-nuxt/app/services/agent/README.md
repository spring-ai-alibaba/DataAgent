# 逻辑模块: agent

## 模块描述
智能体管理服务，处理智能体的增删改查、发布、下线及 API Key 管理

## 类 (Classes)
### Class: `AgentService`
智能体业务逻辑处理类
#### 公开方法:
- `list`: 获取智能体列表
- `get`: 根据 ID 获取智能体详情
- `create`: 创建新智能体
- `update`: 更新智能体信息
- `delete`: 删除指定智能体
- `publish`: 发布智能体
- `offline`: 下线智能体
- `getApiKey`: 获取智能体的 API Key (遮罩态)
- `generateApiKey`: 为智能体生成 API Key
- `resetApiKey`: 重置智能体的 API Key
- `deleteApiKey`: 删除智能体的 API Key
- `toggleApiKey`: 启用或禁用智能体的 API Key

## 类型定义 (Interfaces)
### `Agent`
**描述**: 智能体实体定义
```typescript
export interface Agent {
	/** 智能体 ID */
	id?: number;
	/** 智能体名称 */
	name?: string;
	/** 智能体描述 */
	description?: string;
	/** 智能体头像 URL */
	avatar?: string;
	/** 状态 (draft, published, offline) */
	status?: string;
	/** API Key */
	apiKey?: string | null;
	/** 是否启用 API Key */
	apiKeyEnabled?: number | boolean;
	/** 提示词 (Prompt) */
	prompt?: string;
	/** 分类 */
	category?: string;
	/** 管理员 ID */
	adminId?: number;
	/** 标签 */
	tags?: string;
	/** 创建时间 */
	createTime?: Date;
	/** 更新时间 */
	updateTime?: Date;
	/** 是否启用人工审核 (0 或 1) */
	humanReviewEnabled?: number | boolean;
}
```

### `AgentApiKeyResponse`
**描述**: 智能体 API Key 响应结构
```typescript
export interface AgentApiKeyResponse {
	/** API Key 内容 */
	apiKey: string | null;
	/** 是否启用 */
	apiKeyEnabled: number | boolean;
}
```


---
> 🤖 AI 提示: 逻辑实现请参考 `agent/index.ts`。