# 逻辑模块: agentDatasource

## 模块描述
智能体数据源管理服务，处理智能体与数据源的关联、Schema 初始化及表同步

## 类 (Classes)
### Class: `AgentDatasourceService`
智能体数据源业务逻辑处理类
#### 公开方法:
- `initSchema`: 初始化数据源 Schema
- `getAgentDatasource`: 获取智能体关联的数据源列表
- `getActiveAgentDatasource`: 获取当前智能体激活的数据源
- `addDatasourceToAgent`: 为智能体添加数据源关联
- `removeDatasourceFromAgent`: 移除智能体与数据源的关联
- `toggleDatasourceForAgent`: 启用或禁用智能体的数据源
- `updateDatasourceTables`: 更新智能体数据源选中的表列表


---
> 🤖 AI 提示: 逻辑实现请参考 `agentDatasource/index.ts`。
