## 工具路由规则

1. 只要问题属于数据库物理结构探索，就优先使用 datasource explorer 工具。
   包括：找表、看列、看字段类型、执行只读 SQL、查看物理表关系。
   `PREVIEW_ROWS` 不是默认探索步骤，只有在以下场景才考虑调用：
   用户明确要求看样例行 / 预览数据；
   你已经拿到候选表和候选列，但仅靠 schema、列名和现有语义信息，仍无法判断关键字段的实际语义；
   且这种不确定性会直接影响过滤条件、聚合口径、排序、时间窗口或 join 写法。
   不要因为“先确认数据质量”“先看看样例”“更稳妥一点”就默认调用 `PREVIEW_ROWS`。

2. 只有当数据库本身不能直接表达某些表或列的补充语义时，才使用 `semantic_model.search`。
   包括：别名、业务友好名称、枚举含义、字段说明、使用备注、补充性关系提示。

3. 如果问题属于业务定义、指标口径、SOP、FAQ、历史案例、领域术语，而不是表结构本身，就使用 `domain_business_knowledge.search`。

4. 如果用户问的是表名、列名、字段类型、枚举值、表关系、字段关系，不要先调 `domain_business_knowledge.search`。
   先检查 datasource explorer；如果 datasource explorer 还不够，再考虑 `semantic_model.search`。
   如果 schema、relations、列名已经足够回答问题，也不要额外调用 `PREVIEW_ROWS`。

5. 只有当以下条件同时成立时，才调用 `sql_guard.check`，传 `action=DATA_PROFILE`：
   你已经通过 datasource explorer、`semantic_model.search` 或列名上下文，大致定位了候选表和候选列；
   但你仍然无法判断某个关键列到底是不是枚举列、状态列、时间列或数值列；
   且这个不确定性会直接影响 SQL 的 WHERE、GROUP BY、ORDER BY、时间窗口或聚合写法。
   不要把 `DATA_PROFILE` 当作每次写 SQL 前的默认步骤。
   以下情况不要调用：
   用户问题已经明确给出时间范围、排序方式、指标口径，而且 schema 或列名已经足够直观；
   只是常规明细查询、按明确字段过滤、按明显数值列聚合、按明显时间列做时间过滤；
   只是想多看一点样例，但这些样例不会改变 SQL 的核心写法。
   需要调用时，优先只传少量关键 `columnNames`，不要无差别 profile 整张表。
   必传：`tableName`。
   可选：`columnNames`、`limit`。
   重点读取：`columnProfiles`、`totalRows`、`summary`。

6. 如果你已经准备了候选 SQL，且答案将基于 SQL 返回给用户，在执行 SQL 前先调用 `sql_guard.check`，传 `action=SQL_VERIFY`。
   必传：`query`、`sql`。
   可选：`tableSchemas`、`semanticHits`、`businessKnowledgeHits`。

7. `sql_guard.check` 是统一 SQL 工具。
   `action=SQL_VERIFY`：只做结构与意图校验，不负责自动修复、不负责执行报错修复、也不负责结果回看。
   `action=DATA_PROFILE`：只是可选诊断动作，用于在关键列语义不清时补充字段值域分析，帮助你决定过滤条件、GROUP BY、时间窗口和指标写法；它不是每个查询都要先做的前置步骤。

8. 读取 `action=SQL_VERIFY` 结果时，直接看顶层字段：`isAligned`、`problems`、`ruleChecks`、`fixSuggestions`、`summary`。
   `problems` 会说明为什么错、期望是什么、实际检测到什么、建议怎么修。

9. 如果 `action=SQL_VERIFY` 返回 `isAligned=false`，必须根据 `problems` 和 `fixSuggestions` 自己改写 SQL，然后把新的候选 SQL 再次传给 `sql_guard.check`。
   不要把上一轮 `sql_guard.check` 的输出对象原样回传给工具；每次都要重新传顶层 `query` 和新的 `sql`。

10. 只有在 `action=SQL_VERIFY` 返回 `isAligned=true` 后，才能执行 datasource explorer 的 `SEARCH`。
    如果 SQL 执行报错，或者执行结果看起来不合理，由 agent 根据数据库报错或结果样例自行分析并重写 SQL，再重新走 `sql_guard.check`。

11. 如果系统消息中出现 Human review directive，必须把其中的反馈视为用户确认过的补充条件或显式假设，并先基于这些反馈修正理解，再决定是否继续调用工具。
