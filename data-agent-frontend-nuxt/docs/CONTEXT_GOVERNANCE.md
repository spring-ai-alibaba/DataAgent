# AI 上下文治理体系 (Context Governance)

本项目引入了一套自动化的 AI 上下文治理体系，旨在通过“代码即文档”的原则，为 AI 辅助开发提供精确、实时的模块索引和技术说明。

## 1. 核心理念

*   **Single Source of Truth (单一事实来源)**：代码中的 TypeScript 类型和 JSDoc/TSDoc 注释是唯一的真理。文档是通过这些信息自动生成的，而非手动维护。
*   **Deterministic (确定性)**：使用 AST（抽象语法树）静态分析技术生成文档，避免 AI 幻觉，确保信息的准确性。
*   **Folder-as-Context (目录即上下文)**：通过物理目录隔离，将每个组件或逻辑模块封装在独立文件夹中，通过 `README.md` 为 AI 提供清晰的边界。

## 2. 自动化架构

我们采用双引擎驱动的自动化脚本 `scripts/gen-ai-context.mjs`：

### UI 引擎 (`ui-engine.mjs`)
*   **技术栈**：基于 `vue-docgen-api`。
*   **功能**：解析 Vue 组件的 `Props`、`Slots`、`Events` 以及脚本顶部的 `@description`。
*   **输出**：在组件目录下生成 `README.md`。

### 逻辑引擎 (`logic-engine.mjs`)
*   **技术栈**：基于 `ts-morph`。
*   **功能**：解析 TypeScript 文件的 `Class`、`Function`、`Interface` 以及导出的变量（如 Pinia Store）。
*   **输出**：在逻辑模块目录下生成 `README.md`。

### 索引引擎 (Recursive Indexer)
*   **功能**：递归扫描 `app/` 目录，为包含多个子模块或文件的文件夹生成导航索引。
*   **Pages 特殊处理**：`app/pages` 仅生成目录索引，不生成详细的 API 文档，以保持简洁。

## 3. 开发者规范

为了确保文档生成的有效性，开发者需遵循以下规范：

### 组件开发 (Vue)
*   **位置**：公共组件必须放在 `app/components/common/`。
*   **注释**：`<script setup>` 顶部必须包含 `/** @description ... */`。
*   **Props**：使用 `defineProps` 并在上方添加注释说明业务含义。

### 逻辑开发 (TypeScript)
*   **结构**：复杂的逻辑应采用文件夹模式（如 `services/agent/index.ts`）。
*   **注释**：所有导出的函数、类和接口必须包含标准的 TSDoc 注释。

## 4. 常用命令

*   **生成文档**：`pnpm gen:ctx`
    *   该命令会执行增量更新，仅处理内容发生变化的文件。
    *   生成的缓存文件位于 `.scripts/ai-gen-cache.json`。

## 5. 收益

1.  **AI 准确率提升**：AI 在修改代码前会先读取 `README.md`，了解组件的 Props 和逻辑接口，减少生成错误代码的概率。
2.  **无感维护**：开发者只需写好代码和注释，文档自动同步，无需额外精力维护 Markdown。
3.  **快速上手**：新成员通过阅读各目录下的自动生成文档，可以快速理解项目架构和模块职责。
