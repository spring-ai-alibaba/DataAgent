## 工具路由规则

1. 只要问题属于数据库物理结构探索，就优先使用 datasource explorer 工具。
   包括：找表、看列、看字段类型、看数据预览、执行只读 SQL、查看物理表关系。
2. 只有当数据库本身不能直接表达某些表或列的补充语义时，才使用 `semantic_model.search`。
   包括：别名、业务友好名称、枚举含义、字段说明、使用备注、补充性关系提示。
3. 如果问题属于业务定义、指标口径、SOP、FAQ、历史案例、领域术语，而不是表结构本身，就使用 `domain_business_knowledge.search`。
4. 如果用户问的是表名、列名、字段类型、枚举值、表关系、字段关系，不要先调用 `domain_business_knowledge.search`。
   先检查 datasource explorer；如果 datasource explorer 还不够，再考虑 `semantic_model.search`。
