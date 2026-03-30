---
name: migrate-old-frontend
description: |
  前端迁移指南：将老前端 (data-agent-frontend, Vue 3 + Element Plus) 的页面逻辑迁移到
  新前端 (data-agent-frontend-nuxt, Nuxt 4 + Vuetify 3)。
  当你需要在新前端中实现老前端已有的某个页面或功能时，使用此 Skill。
  它提供完整的技术栈对比、UI 组件映射、全局工具用法、以及逐页迁移参考。
---

# 前端迁移指南：data-agent-frontend → data-agent-frontend-nuxt

## 1. 项目背景

本项目有两个前端，后端 API 完全相同。目标是将老前端的所有页面重写到新前端，
**保持全部业务逻辑不变**，同时升级技术栈、重构 UI。

| 维度 | 老前端 (`data-agent-frontend`) | 新前端 (`data-agent-frontend-nuxt`) |
| :--- | :--- | :--- |
| 框架 | Vue 3 + Vite (SPA) | Nuxt 4 (SSR 已关闭，SPA 模式) |
| UI 库 | Element Plus | Vuetify 3 (MDI 图标) |
| 状态管理 | `ref` / `reactive` | Pinia stores + `ref` / `reactive` |
| HTTP 客户端 | axios | `$fetch` (ofetch, Nuxt 内置) |
| 路由 | vue-router 手动配置 | Nuxt 文件路由 (`app/pages/`) |
| 布局 | `BaseLayout.vue` (顶部 Header 导航) | `default.vue` layout (深色侧边栏 + `BaseDrawer`) |
| 通知 | `ElMessage` | `$tip(msg, options)` 全局插件 |
| 确认弹窗 | `ElMessageBox.confirm` | `useConfirm().showConfirm(options)` |
| 图表 | ECharts 原生 | 待实现（复用老前端逻辑，包装进 Vuetify card） |
| Markdown | markdown-it + 自定义插件 | 待实现（复用老前端 composable） |

---

## 2. 布局 & 导航架构

### 老前端
顶部导航栏 (`BaseLayout.vue`)，仅有两个链接：智能体列表 / 模型配置。

### 新前端
深色左侧边栏 (`app/layouts/default.vue` + `BaseDrawer` 组件)，完整导航树：
- 数据问答 → `/chat`
- 数据看板 → `/dashboard`
- 提示词配置 → `/prompt-config`
- 知识库管理（折叠组） → `/knowledge/business`, `/knowledge/agents`, `/knowledge/semantic-models`
- 系统管理（折叠组） → `/system/data-sources`, `/system/model-config`, `/system/settings`
- 新建智能体 → `/agent/new`

**迁移规则：** 永远不要在页面里添加 `<BaseLayout>` 包裹层。
`app/pages/` 下的页面会被 Nuxt 自动套上 `default.vue` layout。
页面内容直接写即可。

---

## 3. 路由：文件路由 vs 手动路由

### 老前端路由
```javascript
{ path: '/agents',        component: () => import('@/views/AgentList.vue') }
{ path: '/agent/:id',     component: () => import('@/views/AgentDetail.vue') }
{ path: '/agent/create',  component: () => import('@/views/AgentCreate.vue') }
{ path: '/model-config',  component: () => import('@/views/ModelConfig.vue') }
{ path: '/agent/:id/run', component: () => import('@/views/AgentRun.vue') }
```

### 新前端文件映射
```
app/pages/agent/index.vue              ← AgentList (智能体列表)
app/pages/agent/new.vue                ← AgentCreate (创建智能体)
app/pages/agent/[id].vue               ← AgentDetail (智能体详情配置)
app/pages/chat.vue                     ← AgentRun (数据问答对话)
app/pages/system/model-config.vue      ← ModelConfig ✅ 已实现
app/pages/system/data-sources.vue      ← 数据源管理 ✅ 已实现
app/pages/knowledge/business.vue       ← 业务知识配置 (stub)
app/pages/knowledge/agents.vue         ← 智能体知识库 (stub)
app/pages/knowledge/semantic-models.vue← 语义模型配置 (stub)
app/pages/prompt-config/index.vue      ← 提示词配置 (stub)
app/pages/dashboard.vue                ← 数据看板 (stub)
```

**获取路由参数（两端 API 相同）：**
```typescript
const route = useRoute();   // Nuxt 自动导入，无需 import
const agentId = Number(route.params.id);
```

---

## 4. UI 组件对照表（Element Plus → Vuetify 3）

| Element Plus | Vuetify 3 | 备注 |
| :--- | :--- | :--- |
| `<el-button>` | `<v-btn>` | 全局默认 `variant="outlined"` |
| `<el-button type="primary">` | `<v-btn color="primary">` | |
| `<el-input>` | `<v-text-field>` | |
| `<el-textarea>` | `<v-textarea>` | |
| `<el-select>` + `<el-option>` | `<v-select :items="...">` | |
| `<el-card>` | `<v-card>` | 使用 `variant="outlined"` 或 `variant="flat"` |
| `<el-table>` + `<el-table-column>` | `<v-data-table :headers="..." :items="...">` | headers 为对象数组 |
| `<el-tag>` | `<v-chip size="small" :color="...">` | |
| `<el-dialog>` | `<v-dialog>` | |
| `<el-avatar>` | `<v-avatar>` | |
| `<el-icon>` + 图标组件 | `<v-icon icon="mdi-xxx">` | MDI 图标名称字符串 |
| `<el-skeleton>` | `<v-skeleton-loader>` | |
| `<el-radio-group>` + `<el-radio-button>` | `<v-btn-toggle mandatory>` | |
| `<el-form>` | `<v-form>` | |
| `<el-switch>` | `<v-switch>` | |
| `<el-progress>` | `<v-progress-linear>` 或 `<v-progress-circular>` | |
| `<el-tabs>` + `<el-tab-pane>` | `<v-tabs>` + `<v-tab>` + `<v-window>` | |
| `<el-menu>` + `<el-menu-item>` | `<v-list>` + `<v-list-item>` | |
| `<el-empty>` | `<v-icon>` + 文字自定义 | |
| `<el-row :gutter>` + `<el-col :span>` | `<v-row>` + `<v-col cols="...">` | |

### 图标迁移（Element Plus 图标 → MDI）
老前端从 `@element-plus/icons-vue` 导入图标组件；新前端直接用 MDI 字符串：

```html
<!-- 老前端 -->
<el-icon><Delete /></el-icon>

<!-- 新前端 -->
<v-icon icon="mdi-delete" />
```

常用图标对照：
- `Delete` → `mdi-delete`
- `Plus` → `mdi-plus`
- `Edit` / `Edit` → `mdi-pencil`
- `Search` → `mdi-magnify`
- `Refresh` → `mdi-refresh`
- `ArrowLeft` → `mdi-arrow-left`
- `Document` → `mdi-file-document`
- `Upload` → `mdi-upload`
- `InfoFilled` → `mdi-information`
- `Coin` → `mdi-database`
- `ChatLineSquare` → `mdi-chat-processing-outline`
- `Grid` → `mdi-view-grid`
- `Check` → `mdi-check`
- `VideoPause` → `mdi-pause-circle`
- `Download` → `mdi-download`

---

## 5. Service 层对照

两端 service API 形态完全相同，只是 HTTP 客户端从 `axios` 改成了 `$fetch`。
新 service 位于 `app/services/<domain>/index.ts`，被 Nuxt 自动导入。

| 业务域 | 老前端 | 新前端 |
| :--- | :--- | :--- |
| 智能体 CRUD | `@/services/agent.ts` → `agentService` | `~/services/agent/index.ts` → `agentService` |
| 数据源 | `@/services/datasource.ts` | `~/services/datasource/index.ts` |
| 模型配置 | `@/services/modelConfig.ts` | `~/services/modelConfig/index.ts` |
| 对话会话 | `@/services/chat.ts` | `~/services/chat/index.ts` |
| 智能体数据源 | `@/services/agentDatasource.ts` | `~/services/agentDatasource/index.ts` |
| 业务知识 | `@/services/businessKnowledge.ts` | `~/services/businessKnowledge/index.ts` |
| 智能体知识 | `@/services/agentKnowledge.ts` | `~/services/agentKnowledge/index.ts` |
| 语义模型 | `@/services/semanticModel.ts` | `~/services/semanticModel/index.ts` |
| 预设问题 | `@/services/presetQuestion.ts` | `~/services/presetQuestion/index.ts` |
| 文件上传 | `@/services/fileUpload.ts` | `~/services/fileUpload/index.ts` |
| Graph | `@/services/graph.ts` | `~/services/graph/index.ts` |
| 逻辑关系 | `@/services/logicalRelation.ts` | `~/services/logicalRelation/index.ts` |
| 提示词 | 不存在 | `~/services/prompt/index.ts` |

---

## 6. 全局反馈工具（最高频使用）

### 6.1 Toast 通知：$tip

`$tip` 是通过 `app/plugins/tipPlugin.ts` 注入的全局函数，
内部调用 `useTipStore().show()`，由 `app/layouts/default.vue` 中的 `<Tip>` 组件渲染。

```typescript
// 老前端
import { ElMessage } from 'element-plus';
ElMessage.success('操作成功');
ElMessage.error('请检查输入！');
ElMessage.warning('警告');

// 新前端
const { $tip } = useNuxtApp();
$tip('操作成功');                              // 默认绿色 success
$tip('请检查输入！', { color: 'error' });      // 红色 error
$tip('警告信息', { color: 'warning' });
$tip('提示', { color: 'info', timeout: 5000 });
```

**TipOptions 参数：**
| 参数 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `color` | `string` | `'success'` | Vuetify 颜色名 |
| `timeout` | `number` | `3000` | 显示毫秒数 |
| `location` | `Anchor` | `'top'` | 弹出位置 |
| `icon` | `string` | `'mdi-check'` | MDI 图标名 |

### 6.2 确认弹窗：useConfirm

`useConfirm` composable 位于 `app/composables/useConfirm/index.ts`，
对话框状态是模块级 reactive，全局 `<ConfirmDialog>` 组件挂载在 `default.vue` 中。

```typescript
// 老前端
import { ElMessageBox, ElMessage } from 'element-plus';
try {
  await ElMessageBox.confirm(
    `确定要删除智能体 "${agent.name}" 吗？此操作不可恢复。`,
    '删除确认',
    { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
  );
  await agentService.delete(agent.id!);
  ElMessage.success('智能体删除成功');
} catch {
  // 用户取消
}

// 新前端
const { showConfirm } = useConfirm();  // Nuxt 自动导入，无需 import
const { $tip } = useNuxtApp();

showConfirm({
  title: '删除确认',
  message: `确定要删除智能体 "${agent.name}" 吗？此操作不可恢复。`,
  confirmText: '确定删除',
  icon: 'mdi-delete',
  onConfirm: async () => {
    await agentService.delete(agent.id!);
    $tip('智能体删除成功');
    agents.value = agents.value.filter(a => a.id !== agent.id);
  }
});
```

**showConfirm 参数：**
| 参数 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `title` | `string` | `''` | 弹窗标题 |
| `message` | `string` | `''` | 正文内容 |
| `icon` | `string` | `'mdi-help-circle'` | MDI 图标 |
| `confirmText` | `string` | `'确认'` | 确认按钮文字 |
| `onConfirm` | `() => void` | `() => {}` | 确认后回调 |

---

## 7. 逐页迁移参考

### 7.1 智能体列表 (`/agent/index.vue` ← `AgentList.vue`)

**核心逻辑（必须保留）：**
- `onMounted` → 调用 `agentService.list()` 填充 `agents` ref
- `filteredAgents` computed：按 `activeFilter`（all/published/draft/offline）和 `searchKeyword` 过滤
- `handleDeleteAgent(agent)`：弹确认框 → `agentService.delete()` → 从列表移除
- `enterAgent(id)`：`navigateTo('/agent/${id}')`

**UI 变化（新前端）：**
- 用 `v-card` 网格替代 `el-card` + `el-row/col`
- 用 `v-chip` 替代 `el-tag` 表示状态
- 用 `v-btn-toggle mandatory` 替代 `el-radio-group` 做筛选 Tab
- 用 `v-text-field prepend-inner-icon="mdi-magnify"` 替代 `el-input` 搜索框
- 状态颜色：`published` → `color="success"`，`draft` → `color="warning"`，`offline` → `color="default"`

---

### 7.2 创建智能体 (`/agent/new.vue` ← `AgentCreate.vue`)

**核心逻辑（必须保留）：**
```typescript
const agentForm = reactive({
  name: '',
  description: '',
  avatar: '',
  category: '',
  tags: '',
  prompt: '',
  status: 'draft',
  humanReviewEnabled: false,
});

onMounted(() => { agentForm.avatar = generateFallbackAvatar(); });
```
- `generateFallbackAvatar()`：生成随机颜色 SVG base64（完整代码可直接从老前端复制）
- `handleFileUpload(event)`：验证类型 + 大小 → base64 预览 → `fileUploadApi.uploadAvatar(file)` → 更新 avatar
- `createAgent()`：验证必填字段（name、category、tags）→ `agentService.create(data)` → `navigateTo('/agent/${result.id}')`

**UI 变化（新前端）：**
- 用 `v-text-field` / `v-textarea` 替代 `el-input`
- 用 `v-select` 替代 `el-select`
- 用 `v-avatar` + `v-btn` 替代头像上传区域
- 不用 `el-row/el-col`，改用 `v-row/v-col`

---

### 7.3 智能体详情 (`/agent/[id].vue` ← `AgentDetail.vue`)

**核心逻辑（必须保留）：**
- `onMounted`：通过 `route.params.id` 调用 `agentService.get(id)` 加载智能体
- 左侧菜单分区（8 个面板）：
  - `basic` — 基本信息
  - `datasource` — 数据源配置
  - `prompt` — 自定义 Prompt
  - `agent-knowledge` — 智能体知识配置
  - `business-knowledge` — 业务知识配置
  - `semantics` — 语义模型配置
  - `presets` — 预设问题
  - `api-access` — API 访问
- `saveAgent()`：`agentService.update(id, agentData)`
- `publishAgent()`：`agentService.publish(id)` → 更新本地 status
- `offlineAgent()`：`agentService.offline(id)` → 更新本地 status

**UI 变化（新前端）：**
- 用 `v-navigation-drawer` 或垂直 `v-tabs` 替代 `el-menu`
- 用 Vuetify 布局原语（`v-row/v-col`）替代 `el-container/el-aside/el-main`
- 每个配置面板用 `v-card` 包裹

---

### 7.4 数据问答 Chat (`/chat.vue` ← `AgentRun.vue`)

这是**最复杂的页面**，完整 SSE 流式对话。

**会话管理（API 调用）：**
```typescript
// 加载会话列表
const sessions = await chatService.getAgentSessions(agentId);
// 创建会话
const session = await chatService.createSession(agentId, '新会话');
// 删除会话
await chatService.deleteSession(sessionId);
// 置顶
await chatService.pinSession(sessionId, true);
// 重命名
await chatService.renameSession(sessionId, newTitle);
// 加载消息
const messages = await chatService.getSessionMessages(sessionId);
// 保存消息
await chatService.saveMessage(sessionId, messageObj);
```

**流式对话（SSE）：**
```typescript
// 端点：/nl2sql/chat/stream?agentId=&sessionId=&message=
// 使用 EventSource 或 fetch ReadableStream 消费 SSE
// SSE 消息类型：text | sql | result-set | markdown-report | html | error | human-review
```

**结果渲染：**
- `result-set` → `<ResultSetDisplay>` 组件（表格 + ECharts 图表）
- `markdown-report` → Markdown 渲染 + 代码高亮
- `html` → `v-html` + DOMPurify 消毒
- 图表：ECharts（bar / line / pie），参考老前端 `ChartComponent.vue`

**UI 变化（新前端）：**
- 用 Vuetify 侧边面板替代 `<ChatSessionSidebar>` (el-aside)
- 用可滚动 `v-container` 替代 `el-main` 消息区
- 用 `v-textarea` + 发送按钮替代 `el-input` 聊天框

---

### 7.5 模型配置 (`/system/model-config.vue`)

**状态：已在新前端完整实现。** 参考此页面作为其他页面的样式规范。
- `v-btn-toggle` 实现 CHAT/EMBEDDING Tab 切换
- `v-card` list 展示模型项
- `v-dialog` 实现创建/编辑表单

---

### 7.6 数据源管理 (`/system/data-sources.vue`)

**状态：已在新前端完整实现。** 参考此页面作为表格页面的样式规范。
- `v-data-table` 含展开行
- Drawer 模式编辑表单

---

## 8. 新页面标准结构模板

```vue
<template>
  <section class="page-shell">
    <!-- 顶部标题栏 -->
    <header class="d-flex align-center justify-space-between mb-8">
      <div>
        <h1 class="text-h4 font-weight-bold mb-1">页面标题</h1>
        <p class="text-body-2 text-medium-emphasis">副标题描述</p>
      </div>
      <div class="d-flex ga-3">
        <v-btn
          variant="outlined"
          prepend-icon="mdi-refresh"
          :loading="loading"
          @click="fetchData"
          class="text-none"
        >刷新</v-btn>
        <v-btn
          color="primary"
          prepend-icon="mdi-plus"
          class="text-none px-6"
          elevation="0"
          @click="openDialog"
        >新增</v-btn>
      </div>
    </header>

    <!-- 内容区 -->
    <v-card variant="flat" border class="rounded-lg">
      <!-- ... -->
    </v-card>
  </section>
</template>

<script setup lang="ts">
const loading = ref(false);
const { $tip } = useNuxtApp();
const { showConfirm } = useConfirm();

onMounted(() => fetchData());

async function fetchData() {
  loading.value = true;
  try {
    // 调用 service...
  } catch {
    $tip('加载失败，请重试', { color: 'error' });
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.page-shell {
  padding: 32px;
}
</style>
```

---

## 9. 核心规则（必须遵守）

1. **不使用任何 Element Plus 组件** — 全部使用 Vuetify 3 对应组件
2. **不使用 `ElMessage` / `ElMessageBox`** — 使用 `$tip` 和 `showConfirm`
3. **不给页面加 `<BaseLayout>` 包裹** — Nuxt layout 自动处理
4. **所有依赖自动导入** — 无需 `import ref/computed/onMounted/useRoute/useRouter/useConfirm` 以及任何 service
5. **完整保留业务逻辑** — API 调用、数据结构、校验规则必须与老前端完全一致
6. **UI 升级** — 使用 Vuetify 现代组件、一致间距（`pa-*`/`ga-*`）、深色侧边栏主题
7. **使用 `mdi-*` 图标字符串** — 不导入图标组件
8. **新页面文件放在 `app/pages/`** — 路由由文件路径自动推断
