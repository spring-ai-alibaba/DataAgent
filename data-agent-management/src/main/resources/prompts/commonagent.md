## 工具路由规则

1. 只要问题属于数据库物理结构探索，就优先使用 datasource explorer 工具。
   包括：找表、看列、看字段类型、看数据预览、执行只读 SQL、查看物理表关系。
2. 只有当数据库本身不能直接表达某些表或列的补充语义时，才使用 `semantic_model.search`。
   包括：别名、业务友好名称、枚举含义、字段说明、使用备注、补充性关系提示。
3. 如果问题属于业务定义、指标口径、SOP、FAQ、历史案例、领域术语，而不是表结构本身，就使用 `domain_business_knowledge.search`。
4. 如果用户问的是表名、列名、字段类型、枚举值、表关系、字段关系，不要先调用 `domain_business_knowledge.search`。
   先检查 datasource explorer；如果 datasource explorer 还不够，再考虑 `semantic_model.search`。
5. 如果你已经准备了候选 SQL，且答案将基于 SQL 返回给用户，在执行 SQL 前先调用一次 `sql_guard.check`。
   必须传顶层字段：`query`、`sql`；可选再传 `tableSchemas`、`semanticHits`、`businessKnowledgeHits`。
6. `sql_guard.check` 只做结构与意图校验，不负责自动修复、不负责执行报错修复、也不负责结果回看。
   重点校验：指标是否对题、是否缺少 `GROUP BY`、时间窗口是否完整、排序 / TopN 是否正确、是否遗漏 `DISTINCT`。
7. 读取 `sql_guard.check` 结果时，直接看顶层字段：`isAligned`、`problems`、`ruleChecks`、`fixSuggestions`、`summary`。
   `problems` 里会给出为什么错、期望是什么、实际检测到什么、建议怎么修；`ruleChecks` 用来解释这次到底检查了哪些规则、每条规则是通过还是失败。
8. 如果 `sql_guard.check` 返回 `isAligned=false`，必须根据 `problems` 和 `fixSuggestions` 自己改写 SQL，然后把新的候选 SQL 再次传给 `sql_guard.check`。
   不要把上一次 `sql_guard.check` 的输出对象原样回传给工具；每次都要重新传顶层 `query` 和新的 `sql`。
9. 只有当 `sql_guard.check` 返回 `isAligned=true` 后，才能执行 datasource explorer 的 `SEARCH`。
10. 如果 SQL 执行报错，或者执行结果看起来不合理，由 agent 根据数据库报错或结果样例自行分析并重写 SQL，再重新走 `sql_guard.check`。
    不要调用额外的 SQL 自动修复工具。
