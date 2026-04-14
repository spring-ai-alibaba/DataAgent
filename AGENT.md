# DataAgent 仓库协作规范

本文件定义仓库内 AI agent / 开发者的默认工作约束。目标是让改动稳定、可回归、符合当前项目已经收敛出的实现习惯。

## 1. 仓库结构

- 后端主工程：`data-agent-management`
- 前端主工程：`data-agent-frontend`
- 文档与重构状态：`docs/todolist.md`
- SQL 基线：`data-agent-management/src/main/resources/sql/`

默认先看后端，再看前端，最后同步文档。

## 2. 当前产品约束

### 2.1 Agent / Prompt 约束

- 业务侧只保留一个默认 `agentType=commonagent`
- 系统提示词只保留一个默认 `promptType=system`
- 不要再把 `scene` 当作 Prompt 配置维度扩展
- 兼容接口可以保留，但落库和运行时语义必须统一收敛到 `commonagent/system`

### 2.2 会话 / Memory 约束

- 前端流式请求的 `threadId` 默认直接使用当前 `sessionId`
- `memory-text` 只进入 memory，不直接返回给会话消息列表
- 新增消息类型时，必须同时审查：
  - 是否应在 UI 可见
  - 是否应进入 memory
  - `ChatMessageMapper` / `ChatMessageService` / 前端渲染是否需要同步
- 任何 stop / cancel 改动，必须同时检查：
  - SSE 停止
  - 后端执行停止或最少做到副作用抑制
  - `memory-text` 不被脏回写

### 2.3 数据库约束

- 默认验证环境是真实 MySQL，不以 H2 作为本轮主判据
- 不保留启动期 migration runner
- 结构变更直接落到 SQL 基线文件
- 改 MySQL 基线时，至少同步：
  - `src/main/resources/sql/schema.sql`
  - `src/test/resources/sql/schema.sql`
- 如果是旧库兼容问题，要在文档里明确“需要手工对齐”，不要偷偷加启动时自动修库

## 3. 修改原则

### 3.1 先查现状再动手

- 先定位完整调用链，再修改
- 优先查：
  - Controller
  - Service
  - Mapper / SQL
  - 前端调用点
  - `docs/todolist.md`
- 不要只改表层接口而不看运行时真实落点

### 3.2 改动要成链闭环

一个需求如果跨越多层，必须一次改完整。典型例子：

- Prompt 配置改动：
  - DTO
  - Service
  - Controller
  - 前端组件
  - SQL 默认值
  - 文档
- 会话 / memory 改动：
  - Graph 流式控制
  - Session registry
  - Hook
  - ChatMessage 过滤
  - 前端展示

### 3.3 避免引入与当前设计相反的能力

以下方向默认不要新增，除非明确提出需求：

- 多 agentType 模板体系
- 重新把 `scene` 作为 Prompt 业务维度
- 启动期自动 migration
- 只修真实库、不修 SQL 基线

## 4. 代码风格

### 4.1 后端

- 沿用当前 Java / Spring Boot / MyBatis 风格
- 以最小必要改动为原则，不做无关重构
- 注释可以写中文，但保持短而直接
- 公共默认值优先集中定义，避免散落魔法字符串
- 兼容逻辑必须“对外兼容、对内收敛”，不要把旧值继续传进核心路径

### 4.2 前端

- 沿用现有 Vue 组件写法，不强推额外抽象
- 改接口时同步检查：
  - 保存参数
  - 编辑回填
  - 状态按钮
  - 批量操作
  - 完成后的 reload 行为
- 不要出现“UI 看起来能配，实际没落库”的假功能

## 5. 文档与待办

- 每次完成实质性改动后，更新 `docs/todolist.md`
- 已完成事项归档，不删除
- 当前待办只保留未完成项
- 如果实现决策发生变化，必须把旧口径一起改掉
- `todolist` 是当前重构状态的单一事实源

## 6. 验证要求

后端 Java 改动后，默认执行：

```powershell
mvn -pl data-agent-management -am -DskipTests compile
```

满足以下任一情况时，额外补前端验证：

- 改了前端请求参数
- 改了前端展示逻辑
- 改了组件交互状态

如果没有跑某项验证，要在结果里明确说明。

## 7. 提交前检查清单

- 是否仍然符合 `commonagent/system`
- 是否把 SQL 基线和测试基线同步
- 是否把 stop / cancel 的副作用处理完整
- 是否把 message visibility / memory eligibility 同步
- 是否更新 `docs/todolist.md`
- 是否留下无用文件、临时脚本、调试产物

## 8. 禁止事项

- 不要恢复已废弃的 `scene -> prompt` 设计
- 不要重新引入自动 migration runner
- 不要只改真实数据库而不改仓库 SQL
- 不要把完成项从 `todolist` 里直接删除
- 不要留下根目录临时文件
