# 逻辑模块: datasource

## 模块描述
数据源管理服务，处理基础数据源的增删改查、连接测试及逻辑外键管理

## 类 (Classes)
### Class: `DatasourceService`
数据源业务逻辑处理类
#### 公开方法:
- `getAllDatasource`: 获取所有数据源列表
- `getDatasourceById`: 根据 ID 获取数据源详情
- `getDatasourceTables`: 获取数据源下的所有表名
- `getTableColumns`: 获取指定表的列名列表
- `createDatasource`: 创建新数据源
- `updateDatasource`: 更新数据源信息
- `deleteDatasource`: 删除指定数据源
- `testConnection`: 测试数据源连接是否正常
- `getLogicalRelations`: 获取数据源的逻辑外键列表
- `addLogicalRelation`: 为数据源添加逻辑外键关系
- `deleteLogicalRelation`: 删除逻辑外键关系

## 类型定义 (Interfaces)
### `ApiResponse`
**描述**: 通用 API 响应结构
```typescript
export interface ApiResponse<T> {
  /** 是否成功 */
  success: boolean;
  /** 提示消息 */
  message?: string;
  /** 返回数据 */
  data?: T;
}
```

### `Datasource`
**描述**: 数据源实体接口
```typescript
export interface Datasource {
  /** 数据源 ID */
  id?: number;
  /** 数据源名称 */
  name?: string;
  /** 数据源类型 (如 MySQL, PostgreSQL) */
  type?: string;
  /** 主机地址 */
  host?: string;
  /** 端口号 */
  port?: number;
  /** 数据库名称 */
  databaseName?: string;
  /** Schema 名称 */
  schemaName?: string;
  /** 用户名 */
  username?: string;
  /** 密码 */
  password?: string;
  /** 连接 URL */
  connectionUrl?: string;
  /** 状态 */
  status?: string;
  /** 测试连接状态 */
  testStatus?: string;
  /** 描述 */
  description?: string;
  /** 创建者 ID */
  creatorId?: number;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}
```

### `LogicalRelation`
**描述**: 逻辑外键关系实体接口
```typescript
export interface LogicalRelation {
  /** 关系 ID */
  id?: number;
  /** 数据源 ID */
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
  relationType: string;
  /** 描述 */
  description?: string;
}
```

### `CreateLogicalRelationDTO`
**描述**: 创建逻辑外键关系的 DTO
```typescript
export interface CreateLogicalRelationDTO {
  /** 源表名 */
  sourceTableName: string;
  /** 源列名 */
  sourceColumnName: string;
  /** 目标表名 */
  targetTableName: string;
  /** 目标列名 */
  targetColumnName: string;
  /** 关系类型 */
  relationType: string;
  /** 描述 */
  description?: string;
}
```


---
> 🤖 AI 提示: 逻辑实现请参考 `datasource/index.ts`。
