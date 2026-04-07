# 逻辑模块: useConfirm

## 模块描述
确认对话框的状态接口定义

## 函数 (Functions)
### `showConfirm`
- **描述**: 显示确认对话框
- **签名**: `export function showConfirm(options: Partial<Omit<ConfirmState, 'isVisible'>>) {
	Object.assign(dialogState, defaultState, options, { isVisible: true });
}`

### `handleGlobalConfirm`
- **描述**: 对话框内部触发确认动作时调用，执行回调并关闭对话框
- **签名**: `export function handleGlobalConfirm() {
	dialogState.onConfirm();
	dialogState.isVisible = false;
}`

### `hideConfirm`
- **描述**: 隐藏对话框
- **签名**: `export function hideConfirm() {
	dialogState.isVisible = false;
}`

### `useConfirm`
- **描述**: 提供给组件使用的 Composition API，用于绑定对话框状态和操作
- **签名**: `export function useConfirm() {
	return {
		/** 对话框响应式状态 */
		dialogState,
		/** 处理确认逻辑 */
		handleGlobalConfirm,
		/** 隐藏对话框 */
		hideConfirm,
		/** 显示对话框 */
		showConfirm,
	};
}`


---
> 🤖 AI 提示: 逻辑实现请参考 `useConfirm/index.ts`。
