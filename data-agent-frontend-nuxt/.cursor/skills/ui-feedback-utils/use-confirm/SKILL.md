---
name: ui-useconfirm-usage
description: 规范全局确认弹窗 useConfirm 的调用方式。
---

# useConfirm 确认弹窗 Skill

本 Skill 用于当用户提出“删除确认/是否继续/确认弹窗怎么写”等需求时。

## 使用方式（必做）
1. 使用前必须写：
   - `const { showConfirm } = useConfirm();`
2. 参数使用 `showConfirm(options)`，并通过 `onConfirm` 提供确认后回调。
3. 取消/关闭弹窗时：不会触发 `onConfirm`。

## showConfirm(options) 参数规范
`showConfirm({
  title: string,
  message: string,
  icon?: string,         // 默认 'mdi-help-circle'
  confirmText?: string,  // 默认 '确认'
  onConfirm: () => void | Promise<void>
})`

## 示例：删除会话
```typescript
const { showConfirm } = useConfirm();
const { $tip } = useNuxtApp();

showConfirm({
  title: '删除会话',
  message: '确定要删除这个会话吗？此操作不可恢复。',
  icon: 'mdi-delete',
  confirmText: '确定删除',
  onConfirm: async () => {
    await store.removeSession(sessionToDelete);
    $tip('删除成功');
  }
});
```

