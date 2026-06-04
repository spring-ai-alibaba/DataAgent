# Code Review 重构计划

> 基于 2026-04-02 对 `data-agent-frontend-nuxt` 项目的全面 Code Review，本文档将发现的问题整理为可执行的阶段化重构计划。
>
> **原则**：行为优先、小步可验证、先提取再替换。每个阶段完成后应能独立通过验证，不影响已有功能。

---

## 问题清单总览

| # | 严重度 | 类别 | 问题摘要 | 影响范围 |
|---|--------|------|----------|----------|
| P1 | 🔴 高 | 安全 | `v-html` 输出未经 DOMPurify 过滤 | ChatWorkflowTimeline, ChatMarkdownReport, ChatStreamingReport |
| P2 | 🔴 高 | 性能 | SSE 流式更新缺少节流，每条消息触发响应式重渲染 | stores/chat.ts → 聊天页面整体 |
| P3 | 🟠 中 | 架构 | Service 层 HTTP 客户端不统一 (axios vs ofetch)，Store/Page 绕过 Service 层直接调用 | stores/chat.ts, knowledge/agents.vue, services/datasource |
| P4 | 🟠 中 | 类型安全 | `Datasource` 类型在 store 和 service 层重复定义 | stores/chat.ts, services/datasource |
| P5 | 🟠 中 | 类型安全 | service 层使用 `error: any` 违反 TS 规范 | services/datasource/index.ts |
| P6 | 🟡 中 | 可维护性 | `data-sources.vue` 1274 行，混杂多职责 | pages/system/data-sources.vue |
| P7 | 🟡 中 | DRY | 6 个管理页面重复 CRUD 模式 (状态 + 弹窗 + 表单) | knowledge/\*, prompt-config, system/\* |
| P8 | 🟡 低 | CSS | 跨组件样式大量重复 (.page-shell, .custom-label, .breathing-dot 等) | 多个页面组件 |
| P9 | 🟡 低 | CSS | layouts/default.vue 中 CSS 重复定义 (agent-option 样式出现两次) | layouts/default.vue |
| P10 | ⚪ 低 | 规范 | 自动导入 API 手动 import、v-slot 写法不统一 | 多个文件 |

---

## 阶段规划

### 阶段一：安全与性能护栏（高优先级）

> **目标**：修复安全漏洞、消除性能隐患。改动小、影响面可控。

#### 任务 1.1 — 为所有 `v-html` 输出添加 DOMPurify

- **关联问题**：P1
- **涉及文件**：
  - `app/components/chat/ChatWorkflowTimeline.vue`
  - `app/components/chat/ChatMarkdownReport.vue`
  - `app/components/chat/ChatStreamingReport.vue`
- **改动内容**：
  1. 安装 `dompurify` 依赖：`pnpm add dompurify && pnpm add -D @types/dompurify`
  2. 在 `renderCode()`、`renderTextWithJsonDetection()` 等返回 HTML 字符串的函数中，返回前统一调用 `DOMPurify.sanitize(html)`
  3. 对 `ChatMarkdownReport.vue` 和 `ChatStreamingReport.vue` 中 markdown 渲染后的 HTML 输出同样添加 sanitize
- **验证**：
  - [ ] 聊天页面 Timeline 各节点（SQL、JSON、纯文本、报告）渲染正常
  - [ ] 代码高亮与 ECharts 图表不受影响
  - [ ] 手动注入 `<script>alert(1)</script>` 类内容确认被过滤

---

#### 任务 1.2 — SSE 流式更新添加 RAF 节流

- **关联问题**：P2
- **涉及文件**：
  - `app/stores/chat.ts` — `_sendGraphRequest` 方法中的 SSE 回调
- **改动内容**：
  1. 在 SSE 回调中，将直接赋值 `nodeBlocks.value = [...]` 改为通过 `requestAnimationFrame` 合并更新
  2. 同样对 `streamingReportContent.value` 的赋值添加节流
  3. 流结束 (complete/error) 时强制 flush 一次确保最终状态同步
- **参考实现**：
  ```typescript
  let rafPending = false;
  function scheduleViewSync() {
    if (rafPending) return;
    rafPending = true;
    requestAnimationFrame(() => {
      if (currentSession.value?.id === sessionId) {
        nodeBlocks.value = [...sessionState.nodeBlocks];
      }
      rafPending = false;
    });
  }
  ```
- **验证**：
  - [ ] 聊天发送 → SSE 流式接收 → Timeline 实时渲染，内容无丢失
  - [ ] 报告流式生成过程中实时展示，结束后内容完整
  - [ ] 浏览器 Performance 面板确认渲染帧率改善

---

### 阶段二：架构统一与类型修复

> **目标**：统一服务层调用规范，消除类型重复与 `any`，确保模块边界清晰。

#### 任务 2.1 — 统一 HTTP 客户端，消除 Service 旁路调用

- **关联问题**：P3
- **涉及文件**：
  - `app/services/datasource/index.ts` — 从 `ofetch` 切换到 `axios`（与其他 service 保持一致）
  - `app/stores/chat.ts` — L124 处 `axios.get('/api/datasource')` → 改为调用 `datasourceService.getAllDatasource('active')`
  - `app/pages/knowledge/agents.vue` — L748 处 `axios.post('/api/agent-knowledge/create')` → 迁入 `agentKnowledgeService`，新增 `createWithFile(formData)` 方法
- **改动内容**：
  1. `services/datasource/index.ts`：将所有 `$fetch` 替换为 `axios`，保持方法签名不变
  2. `stores/chat.ts`：导入 `datasourceService`，替换直接 axios 调用
  3. `services/agentKnowledge/index.ts`：新增 `createWithFile(formData: FormData)` 方法
  4. `pages/knowledge/agents.vue`：替换直接 axios 调用为 service 方法
- **验证**：
  - [ ] 数据源页面 CRUD 功能正常
  - [ ] 聊天页面数据源切换正常
  - [ ] 智能体知识库文件上传创建正常

---

#### 任务 2.2 — 统一 `Datasource` 类型，消除 `any`

- **关联问题**：P4, P5
- **涉及文件**：
  - `app/stores/chat.ts` — 删除本地 `Datasource` 接口，改为 `import type { Datasource } from '~/services/datasource'`
  - `app/services/datasource/index.ts` — 将 `catch (error: any)` 替换为 `catch (error: unknown)` + 类型守卫
- **改动内容**：
  1. `stores/chat.ts`：删除 L9-L18 的 `Datasource` 接口定义，从 service 导入
  2. `services/datasource/index.ts`：两处 `error: any` 改为：
     ```typescript
     catch (error: unknown) {
       if (error instanceof Error && 'statusCode' in error) {
         const statusCode = (error as { statusCode: number }).statusCode;
         if (statusCode === 404) return null;
       }
       throw error;
     }
     ```
- **验证**：
  - [ ] `npx nuxi typecheck` 通过
  - [ ] 聊天页面数据源下拉功能正常

---

### 阶段三：CSS 去重与规范统一

> **目标**：提取跨组件的重复样式到公共 CSS，修复 layout 中的重复定义，统一编码规范。

#### 任务 3.1 — 提取公共样式到 `main.css`

- **关联问题**：P8
- **涉及文件**：
  - `app/assets/css/main.css` — 追加公共样式
  - 以下页面删除各自的重复样式定义：
    - `pages/system/data-sources.vue`
    - `pages/system/model-config.vue`
    - `pages/system/agents.vue`
    - `pages/knowledge/agents.vue`
    - `pages/knowledge/business.vue`
    - `pages/knowledge/semantic-models.vue`
    - `pages/prompt-config/index.vue`
- **需提取的公共样式**：

  | 样式 | 出现次数 | 说明 |
  |------|---------|------|
  | `.page-shell { padding: 32px; }` | 5 处 | 页面外壳间距 |
  | `.text-slate-900 { color: #0f172a; }` | 3 处 | Slate 文字色 |
  | `.custom-label` | 2 处 | 表单标签样式 |
  | `.breathing-dot` + `@keyframes breathe` | 2 处（实现不同） | 呼吸灯效果，合并为一份 |
  | `.search-field :deep(.v-field__outline)` | 4 处 | 搜索框边框色 |

- **验证**：
  - [ ] 所有页面视觉渲染与重构前完全一致
  - [ ] 样式不会因移除 `scoped` 导致其他页面污染（提取的都是功能性 class）

---

#### 任务 3.2 — 修复 `layouts/default.vue` 重复 CSS

- **关联问题**：P9
- **涉及文件**：`app/layouts/default.vue`
- **改动内容**：
  1. 删除 L429-L474 重复的 `.agent-option__text`、`.agent-option__title`、`.agent-option__subtitle`、`.agent-option--selection` 定义
  2. 保留 L392-L426 的定义（或合并为最终需要的值）
- **验证**：
  - [ ] 侧边栏智能体切换下拉菜单样式正常

---

#### 任务 3.3 — 编码规范对齐

- **关联问题**：P10
- **涉及文件**：
  - `pages/system/data-sources.vue` — 删除 `import { ref, reactive, ... } from 'vue'`（Nuxt 自动导入）
  - `components/chat/ChatWorkflowTimeline.vue` — 同上
  - `components/chat/ChatInputArea.vue` — 同上
  - `layouts/default.vue` — 同上
  - `pages/system/data-sources.vue` — `v-slot:item.xxx` 统一改为 `#item.xxx`
- **改动内容**：
  1. 删除所有不必要的 Vue API 手动 import
  2. 统一 slot 写法为 `#slotName` 简写
- **验证**：
  - [ ] ESLint 无新增错误
  - [ ] 所有页面功能正常

---

### 阶段四：大文件拆分

> **目标**：将超过 500 行的大组件拆分为职责单一的子组件。

#### 任务 4.1 — 拆分 `data-sources.vue`

- **关联问题**：P6
- **涉及文件**：`app/pages/system/data-sources.vue`（1274 行）
- **拆分方案**：

  ```
  app/pages/system/data-sources/
  ├── index.vue                  ← 页面骨架，组合子组件 (~150 行)
  ├── DatasourceFormDialog.vue   ← 创建/编辑数据源弹窗 (~200 行)
  ├── ForeignKeyDialog.vue       ← 逻辑外键配置弹窗 (~250 行)
  └── ExpandedTableManager.vue   ← 展开行数据表管理 (~180 行)
  ```

- **拆分原则**：
  1. 每个子组件通过 Props 接收必要数据、通过 Emits 向父组件传递事件
  2. 父组件 (`index.vue`) 持有列表状态与公共方法（如 `fetchDatasources`）
  3. 弹窗组件通过 `v-model` 控制显隐
- **验证**：
  - [ ] 数据源列表加载、展开/收起、数据表选择
  - [ ] 创建/编辑弹窗、表单验证
  - [ ] 逻辑外键弹窗：关系列表、添加/删除关系
  - [ ] 连接测试、启用/禁用、删除
  - [ ] 初始化数据源功能

---

### 阶段五：CRUD 逻辑复用

> **目标**：提取通用 CRUD 页面 composable，减少管理页面的重复代码。

#### 任务 5.1 — 创建 `useCrudPage` composable

- **关联问题**：P7
- **新建文件**：`app/composables/useCrudPage/index.ts`
- **接口设计**：

  ```typescript
  interface UseCrudPageOptions<T, TCreate = T, TUpdate = T> {
    /** 列表加载函数 */
    loadFn: () => Promise<T[]>;
    /** 创建函数（可选） */
    createFn?: (data: TCreate) => Promise<boolean | { success: boolean; message?: string }>;
    /** 更新函数（可选） */
    updateFn?: (id: number, data: TUpdate) => Promise<boolean | { success: boolean; message?: string }>;
    /** 删除函数（可选） */
    deleteFn?: (id: number) => Promise<boolean | { success: boolean; message?: string }>;
    /** 表单默认值工厂 */
    defaultFormFactory: () => T;
  }

  function useCrudPage<T extends { id?: number }>(options: UseCrudPageOptions<T>) {
    return {
      // 状态
      loading, saveLoading, items, dialogVisible, isEdit, formRef, formData,
      // 方法
      loadItems, openCreateDialog, openEditDialog, closeDialog, resetForm,
      saveItem, deleteItem,
    };
  }
  ```

- **验证**：
  - [ ] 新 composable 的类型推导在各页面中正确工作

---

#### 任务 5.2 — 逐步迁移管理页面使用 `useCrudPage`

- **改造顺序**（按页面复杂度从低到高）：
  1. `knowledge/business.vue` — 最简单，仅 CRUD + 搜索
  2. `knowledge/semantic-models.vue` — CRUD + 批量操作 + 导入
  3. `knowledge/agents.vue` — CRUD + 筛选 + 分页 + 文件上传
  4. `prompt-config/index.vue` — CRUD + 批量启用/禁用 + 优先级
  5. `system/agents.vue` — CRUD + 状态筛选 + 搜索
- **每个页面的改造步骤**：
  1. 引入 `useCrudPage`，替换重复的状态声明
  2. 保留页面特有逻辑（如筛选、批量操作、文件上传）
  3. 删除已被 composable 覆盖的代码
- **验证**：
  - [ ] 每改造完一个页面，立即验证该页面所有功能
  - [ ] 确认页面行为与改造前完全一致

---

## 执行时间线

```
阶段一 (安全+性能)        ████░░░░░░░░░░░░░░░░░░  任务 1.1 + 1.2
阶段二 (架构+类型)        ░░░░████░░░░░░░░░░░░░░  任务 2.1 + 2.2
阶段三 (CSS+规范)         ░░░░░░░░████░░░░░░░░░░  任务 3.1 + 3.2 + 3.3
阶段四 (大文件拆分)       ░░░░░░░░░░░░████░░░░░░  任务 4.1
阶段五 (CRUD 复用)        ░░░░░░░░░░░░░░░░██████  任务 5.1 + 5.2
```

**依赖关系**：
- 阶段一、二、三 之间无强依赖，可并行推进
- 阶段四 (拆分 data-sources) 建议在阶段二 (HTTP 统一) 和阶段三 (CSS 去重) 完成后执行，避免冲突
- 阶段五 (CRUD composable) 应最后执行，因为它依赖阶段三的规范对齐和阶段四拆分后的稳定结构

---

## 风险与注意事项

1. **DOMPurify 可能过滤 ECharts 容器属性**：sanitize 配置可能需要允许特定的 `data-*` 属性和 `style`，需要测试确认
2. **RAF 节流可能导致短暂延迟感**：如果用户反馈 Timeline 更新不够"实时"，可将节流策略改为 16ms debounce（约等于一帧）
3. **ofetch → axios 迁移**：`$fetch` 与 `axios` 的错误处理模型不同（`$fetch` 非 2xx 直接 throw），迁移时需注意 catch 逻辑
4. **Nuxt 自动导入删除手动 import**：需确认 `nuxt.config.ts` 的 auto-import 配置已覆盖所有使用到的 API
5. **CRUD composable 的灵活性**：部分页面有特殊逻辑（如文件上传、批量操作），composable 设计需保留足够扩展点，不可过度抽象
