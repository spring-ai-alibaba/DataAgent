# AgentDetail.vue Style Unification Design

**Date:** 2026-04-06
**Scope:** `data-agent-frontend/src/views/AgentDetail.vue`

## Goal

Align AgentDetail.vue's visual style with AgentList.vue's design language. Replace all inline `background-color: white` styles with CSS variable-based classes. Redesign the header area to match AgentList's content-header pattern. Unify colors across the page layout.

## Reference

`AgentList.vue` uses:
- `#1f2937` → `var(--text-primary)` for headings
- `#6b7280` → `var(--text-secondary)` for subtitles and secondary labels
- `#3b82f6` → `var(--primary-color)` for accent/interactive elements
- `#f8fafc` → `var(--bg-layout)` for page background
- `background: white` → `var(--bg-primary)` for card/panel backgrounds

## Changes

### 1. Outer Container

**Before:** `el-container` with inline `style="margin-top: 20px; gap: 10px"`

**After:** CSS class `.detail-page` on the outer `el-container`:
```css
.detail-page {
  min-height: 100vh;
  background: var(--bg-layout);
  padding: 1.5rem;
  gap: 0;
  flex-direction: column;
}
```
Remove all inline style attributes from the outer `el-container` and the inner `el-container`.

### 2. Header Redesign

**Before:** `el-header` with `style="background-color: white; margin-bottom: 20px"` containing a flat Row/Col with a circle back button, avatar, and `<h2>` agent name.

**After:** CSS class `.detail-header` replacing the inline style. Header layout redesigned:

```
[← 返回列表]   [avatar]  agent.name
                         agent.description (subtitle)
──────────────────────────────── (el-divider)
```

- Back button: text + icon style (`← 返回列表`), `color: var(--text-secondary)`, hover → `var(--primary-color)`, no background, no border
- Agent name: `font-size: 1.5rem`, `font-weight: 600`, `color: var(--text-primary)`
- Agent description: `font-size: 0.9rem`, `color: var(--text-secondary)` below the name
- Avatar: keeps existing upload interaction (hover overlay, file input trigger)

CSS:
```css
.detail-header {
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-primary);
  padding: 1rem 1.5rem;
  height: auto !important;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--text-secondary);
  font-size: 0.9rem;
  cursor: pointer;
  padding: 0.4rem 0;
  background: none;
  border: none;
  transition: color var(--transition-base);
  margin-bottom: 1rem;
}

.back-btn:hover {
  color: var(--primary-color);
}

.agent-title-section {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.agent-name-text {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 0.25rem 0;
}

.agent-desc-text {
  font-size: 0.9rem;
  color: var(--text-secondary);
  margin: 0;
}
```

### 3. Aside (Left Menu)

**Before:** `el-aside` with inline `style="background-color: white"`

**After:** CSS class `.detail-aside`:
```css
.detail-aside {
  background: var(--bg-primary);
  border-right: 1px solid var(--border-primary);
  padding-top: 0.5rem;
}
```
Remove the inline `style` attribute from `el-aside`.

### 4. Main (Right Content)

**Before:** `el-main` with inline `style="background-color: white"`

**After:** CSS class `.detail-main`:
```css
.detail-main {
  background: var(--bg-secondary);
  padding: 1.5rem;
}
```
Remove the inline `style` attribute from `el-main`.

## Template Changes Summary

| Element | Before | After |
|---------|--------|-------|
| Outer `el-container` | `style="margin-top: 20px; gap: 10px"` | `class="detail-page"` |
| Inner `el-container` | `style="gap: 10px"` | no style |
| `el-header` | `style="background-color: white; margin-bottom: 20px"` | `class="detail-header"` |
| Back button | `el-button type="primary" circle` | plain `<button class="back-btn">` |
| Agent name | `<h2>{{ agent.name }}</h2>` | `<h2 class="agent-name-text">` + `<p class="agent-desc-text">` |
| `el-aside` | `style="background-color: white"` | `class="detail-aside"` |
| `el-main` | `style="background-color: white"` | `class="detail-main"` |

## Out of Scope

- Child components (`AgentBaseSetting`, `AgentDataSourceConfig`, etc.) — not changed
- Script/logic — not changed
- Avatar upload behavior — not changed
- `el-menu` items — not changed
