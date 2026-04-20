# 逻辑模块: logicalRelation

## 模块描述
逻辑外键管理服务，处理数据源表之间的关联关系定义及字段同步

## 类 (Classes)
### Class: `LogicalRelationService`
逻辑外键业务逻辑处理类
#### 公开方法:
- `getLogicalRelations`: 获取指定数据源的所有逻辑外键列表
- `addLogicalRelation`: 为数据源添加新的逻辑外键
- `updateLogicalRelation`: 更新现有的逻辑外键信息
- `deleteLogicalRelation`: 删除指定的逻辑外键
- `saveLogicalRelations`: 批量保存逻辑外键 (全量替换)
- `getTableColumns`: 获取指定数据源表的列名列表

## 类型定义 (Interfaces)
### `LogicalRelation`
**描述**: 逻辑外键关系实体接口
```typescript
export interface LogicalRelation {
  /** 关系 ID */
  id?: number;
  /** 关联的数据源 ID */
  datasourceId?: number;
  /** 源表名 */
  sourceTableName: string;
  /** 源列名 */
  sourceColumnName: string;
  /** 目标表名 */
  targetTableName: string;
  /** 目标列名 */
  targetColumnName: string;
  /** 关系类型 (1:1, 1:N, N:1) */
  relationType?: string;
  /** 描述 */
  description?: string;
  /** 是否已删除 (0: 否, 1: 是) */
  isDeleted?: number;
  /** 创建时间 */
  createdTime?: string;
  /** 更新时间 */
  updatedTime?: string;
}
```


---
> 🤖 AI 提示: 逻辑实现请参考 `logicalRelation/index.ts`。
