# Dark Theme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add dark/light theme toggle to the frontend with system-preference detection, manual switch button in the header, and localStorage persistence.

**Architecture:** CSS variables in `global.css` are overridden under `[data-theme="dark"]` on `<html>`. `App.vue` initializes the theme on mount and provides `toggleTheme()` via Vue `provide`. `BaseLayout.vue` injects it and renders a sun/moon button in the header.

**Tech Stack:** Vue 3 (Composition API), CSS custom properties, `localStorage`, `window.matchMedia`

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `data-agent-frontend/src/styles/global.css` | Modify | Add `[data-theme="dark"]` variable block; add dark overrides for hardcoded-color classes |
| `data-agent-frontend/src/App.vue` | Modify | Theme init on mount, `provide('toggleTheme')` and `provide('isDark')` |
| `data-agent-frontend/src/layouts/BaseLayout.vue` | Modify | Inject theme state; add toggle button; replace scoped hardcoded colors with CSS variables |

---

## Task 1: Add Dark CSS Variables to `global.css`

**Files:**
- Modify: `data-agent-frontend/src/styles/global.css`

- [ ] **Step 1: Add `[data-theme="dark"]` variable block**

Open `data-agent-frontend/src/styles/global.css`. After the closing `}` of the existing `@media (prefers-color-scheme: dark)` block (around line 1346), insert the following new block:

```css
/* 手动暗色主题 - 由 data-theme="dark" 触发 */
[data-theme="dark"] {
  --text-primary: #e8e8e8;
  --text-secondary: #b3b3b3;
  --text-tertiary: #808080;
  --text-quaternary: #4d4d4d;
  --text-disabled: #383838;

  --bg-primary: #1a1a1a;
  --bg-secondary: #242424;
  --bg-tertiary: #2d2d2d;
  --bg-layout: #141414;

  --border-primary: #383838;
  --border-secondary: #2d2d2d;
  --border-tertiary: #242424;
  --border-color: #383838;

  --shadow-xs: 0 1px 2px rgba(0, 0, 0, 0.2);
  --shadow-sm: 0 2px 4px rgba(0, 0, 0, 0.3);
  --shadow-base: 0 4px 8px rgba(0, 0, 0, 0.4);
  --shadow-md: 0 6px 16px rgba(0, 0, 0, 0.5);
  --shadow-lg: 0 8px 24px rgba(0, 0, 0, 0.6);
  --shadow-xl: 0 12px 32px rgba(0, 0, 0, 0.7);
}
```

Also **replace** the existing `@media (prefers-color-scheme: dark)` block (lines 1332–1346) with one that skips when user has manually set light:

```css
/* 系统暗色偏好 - 未手动设置时生效 */
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --text-primary: #e8e8e8;
    --text-secondary: #b3b3b3;
    --text-tertiary: #808080;
    --text-quaternary: #4d4d4d;
    --text-disabled: #383838;

    --bg-primary: #1a1a1a;
    --bg-secondary: #242424;
    --bg-tertiary: #2d2d2d;
    --bg-layout: #141414;

    --border-primary: #383838;
    --border-secondary: #2d2d2d;
    --border-tertiary: #242424;
    --border-color: #383838;

    --shadow-xs: 0 1px 2px rgba(0, 0, 0, 0.2);
    --shadow-sm: 0 2px 4px rgba(0, 0, 0, 0.3);
    --shadow-base: 0 4px 8px rgba(0, 0, 0, 0.4);
    --shadow-md: 0 6px 16px rgba(0, 0, 0, 0.5);
    --shadow-lg: 0 8px 24px rgba(0, 0, 0, 0.6);
    --shadow-xl: 0 12px 32px rgba(0, 0, 0, 0.7);
  }
}
```

- [ ] **Step 2: Add dark overrides for hardcoded-color classes**

At the end of `global.css`, append:

```css
/* ========================================
   暗色主题 - 硬编码颜色类覆盖
   ======================================== */
[data-theme="dark"] .agent-response-block,
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .agent-response-block {
    border-color: #383838;
    background: #1e1e1e;
  }
}

[data-theme="dark"] .agent-response-block {
  border-color: #383838;
  background: #1e1e1e;
}

[data-theme="dark"] .agent-response-title {
  background: #2a2a2a;
  color: #b3b3b3;
  border-bottom-color: #383838;
}

[data-theme="dark"] .agent-response-title i {
  color: #808080;
}

[data-theme="dark"] .agent-response-content {
  background: #1a1a1a;
  color: #e8e8e8;
}

[data-theme="dark"] .agent-response-content pre {
  background: #242424;
  border-color: #383838;
  color: #e8e8e8;
}

[data-theme="dark"] .agent-response-content code {
  background: #242424;
  color: #e8e8e8;
}

[data-theme="dark"] .agent-response-content .language-sql,
[data-theme="dark"] .agent-response-content .language-json {
  color: #79b8ff;
}

@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .agent-response-block {
    border-color: #383838;
    background: #1e1e1e;
  }

  :root:not([data-theme="light"]) .agent-response-title {
    background: #2a2a2a;
    color: #b3b3b3;
    border-bottom-color: #383838;
  }

  :root:not([data-theme="light"]) .agent-response-title i {
    color: #808080;
  }

  :root:not([data-theme="light"]) .agent-response-content {
    background: #1a1a1a;
    color: #e8e8e8;
  }

  :root:not([data-theme="light"]) .agent-response-content pre {
    background: #242424;
    border-color: #383838;
    color: #e8e8e8;
  }

  :root:not([data-theme="light"]) .agent-response-content code {
    background: #242424;
    color: #e8e8e8;
  }
}

/* html-rendered-content 报告区块始终保持白底，不参与暗色模式反色 */
[data-theme="dark"] .html-rendered-content {
  background: white !important;
  color: #333 !important;
}
```

- [ ] **Step 3: Commit**

```bash
cd data-agent-frontend
git add src/styles/global.css
git commit -m "feat: add dark theme CSS variables and class overrides"
```

---

## Task 2: Theme State Management in `App.vue`

**Files:**
- Modify: `data-agent-frontend/src/App.vue`

- [ ] **Step 1: Replace `App.vue` script block**

Replace the existing `<script>` block in `data-agent-frontend/src/App.vue` with:

```vue
<script setup>
import { ref, provide, onMounted, onUnmounted } from 'vue';

const isDark = ref(false);

let mediaQuery = null;

function applyTheme(dark) {
  isDark.value = dark;
  document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
}

function handleSystemChange(e) {
  // 只有用户未手动设置时才跟随系统
  if (!localStorage.getItem('theme')) {
    applyTheme(e.matches);
  }
}

function toggleTheme() {
  const next = !isDark.value;
  localStorage.setItem('theme', next ? 'dark' : 'light');
  applyTheme(next);
}

onMounted(() => {
  const saved = localStorage.getItem('theme');
  if (saved) {
    applyTheme(saved === 'dark');
  } else {
    mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    applyTheme(mediaQuery.matches);
    mediaQuery.addEventListener('change', handleSystemChange);
  }
});

onUnmounted(() => {
  mediaQuery?.removeEventListener('change', handleSystemChange);
});

provide('toggleTheme', toggleTheme);
provide('isDark', isDark);
</script>
```

Also update the `<template>` — it stays the same (`<div id="app"><router-view /></div>`), only the script changes. Remove the old `export default { name: 'App' }` options-API block entirely.

- [ ] **Step 2: Verify dev server starts without errors**

```bash
cd data-agent-frontend
npm run dev
```

Expected: server starts, no console errors, `<html>` element has `data-theme="light"` or `data-theme="dark"` attribute in browser DevTools.

- [ ] **Step 3: Commit**

```bash
git add src/App.vue
git commit -m "feat: add theme state management with localStorage and system preference"
```

---

## Task 3: Header Toggle Button in `BaseLayout.vue`

**Files:**
- Modify: `data-agent-frontend/src/layouts/BaseLayout.vue`

- [ ] **Step 1: Update `<template>` — add toggle button**

In the `header-content` div, after the closing `</div>` of `.brand-section`, add:

```html
<button class="theme-toggle" @click="toggleTheme" :title="isDark ? '切换到亮色模式' : '切换到暗色模式'">
  <i :class="isDark ? 'bi bi-sun' : 'bi bi-moon'"></i>
</button>
```

The full `header-content` div should look like:

```html
<div class="header-content">
  <div class="brand-section">
    <div class="brand-logo">
      <i class="bi bi-robot"></i>
      <span class="brand-text">Spring AI Alibaba Data Agent</span>
    </div>
    <nav class="header-nav">
      <div class="nav-item" :class="{ active: isAgentPage() }" @click="goToAgentList">
        <i class="bi bi-grid-3x3-gap"></i>
        <span>智能体列表</span>
      </div>
      <div class="nav-item" :class="{ active: isModelConfigPage() }" @click="goToModelConfig">
        <i class="bi bi-gear"></i>
        <span>模型配置</span>
      </div>
    </nav>
  </div>
  <button class="theme-toggle" @click="toggleTheme" :title="isDark ? '切换到亮色模式' : '切换到暗色模式'">
    <i :class="isDark ? 'bi bi-sun' : 'bi bi-moon'"></i>
  </button>
</div>
```

- [ ] **Step 2: Update `<script>` — inject theme**

Replace the existing `<script>` block with:

```vue
<script>
import { inject } from 'vue';
import { useRouter } from 'vue-router';

export default {
  name: 'BaseLayout',
  setup() {
    const router = useRouter();
    const toggleTheme = inject('toggleTheme');
    const isDark = inject('isDark');

    const goToAgentList = () => {
      router.push('/agents');
    };

    const goToModelConfig = () => {
      router.push('/model-config');
    };

    const isAgentPage = () => {
      return (
        router.currentRoute.value.name === 'AgentList' ||
        router.currentRoute.value.name === 'AgentDetail' ||
        router.currentRoute.value.name === 'AgentCreate' ||
        router.currentRoute.value.name === 'AgentRun'
      );
    };

    const isModelConfigPage = () => {
      return router.currentRoute.value.name === 'ModelConfig';
    };

    return {
      goToAgentList,
      goToModelConfig,
      isAgentPage,
      isModelConfigPage,
      toggleTheme,
      isDark,
    };
  },
};
</script>
```

- [ ] **Step 3: Replace hardcoded colors in `<style scoped>` with CSS variables**

Replace the entire `<style scoped>` block with:

```css
<style scoped>
.base-layout {
  min-height: 100vh;
  background: var(--bg-layout);
}

.page-header {
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-primary);
  box-shadow: var(--shadow-sm);
  position: sticky;
  top: 0;
  z-index: 100;
  transition: background var(--transition-base), border-color var(--transition-base);
}

.header-content {
  width: 100%;
  padding: 0 1.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 4rem;
}

.brand-section {
  display: flex;
  align-items: center;
  gap: 2rem;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
}

.brand-logo i {
  font-size: 1.5rem;
  color: var(--primary-color);
}

.header-nav {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  color: var(--text-secondary);
  font-weight: 500;
}

.nav-item:hover {
  background: var(--bg-secondary);
  color: var(--text-primary);
}

.nav-item.active {
  background: var(--primary-light);
  color: var(--primary-color);
}

.nav-item i {
  font-size: 1rem;
}

.page-content {
  flex: 1;
  padding: 0;
}

.theme-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-base);
  flex-shrink: 0;
}

.theme-toggle:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border-color: var(--border-secondary);
}

.theme-toggle i {
  font-size: 1rem;
}
</style>
```

- [ ] **Step 4: Verify in browser**

```bash
npm run dev
```

Open http://127.0.0.1:5173. Verify:
1. Moon icon appears in top-right of header
2. Clicking it switches to dark mode (background turns dark, text turns light)
3. Clicking again switches back to light mode
4. Refreshing the page preserves the last chosen theme
5. `<html data-theme="dark">` attribute visible in DevTools when dark is active

- [ ] **Step 5: Commit**

```bash
git add src/layouts/BaseLayout.vue
git commit -m "feat: add dark theme toggle button to header"
```
