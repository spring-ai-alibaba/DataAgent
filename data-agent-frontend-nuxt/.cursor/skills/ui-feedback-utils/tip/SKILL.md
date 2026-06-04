---
name: ui-tip-usage
description: 规范全局 $tip Toast 的调用方式（成功默认、错误需手动指定 icon）。
---

# $tip Toast 统一调用 Skill

本 Skill 用于当用户提出“提示/Toast 怎么写”、“$tip 怎么用”、“复制成功要不要提示”等需求时。  
同时：在你编写代码时，只要你想到需要向用户展示一个提示（成功/失败/警告/信息），也要优先按本 Skill 的规范调用 `$tip`。

## 统一调用规则
1. 使用前必须先写：
   - `const { $tip } = useNuxtApp();`
2. 调用签名：
   - `$tip(message, { color: string, icon: string, timeout: number })`
3. 成功默认规则：
   - 只要写：`$tip(message)`
   - 默认 `color: 'success'`
   - 默认 `icon: 'mdi-check'`（对勾）
   - 默认 `timeout: 3000`
4. `timeout: -1` 代表永久不自动关闭：
   - 需要用户自己手动关闭（Snackbar 右上角关闭按钮）
   - 示例：`$tip('长期提示', { timeout: -1 })`
5. 错误规则（必须手动）：
   - 当你要表达错误时，必须手动传：
   - `$tip(message, { color: 'error', icon: 'mdi-error' })`

## 示例
- 成功提示：
  - `const { $tip } = useNuxtApp();`
  - `$tip('操作成功');`

- 错误提示（必须指定 icon）：
  - `const { $tip } = useNuxtApp();`
  - `$tip('保存失败', { color: 'error', icon: 'mdi-error' });`

- 自定义颜色/超时（非 error 时可不传 icon）：
  - `$tip('正在生成报告...', { color: 'info', timeout: 5000 });`

