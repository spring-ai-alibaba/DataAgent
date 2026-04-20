/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { reactive } from 'vue';

/**
 * @description 确认对话框的状态接口定义
 */
interface ConfirmState {
	/** 是否显示对话框 */
	isVisible: boolean;
	/** 对话框标题 */
	title: string;
	/** 对话框正文内容 */
	message: string;
	/** 图标名称 (Vuetify mdi icon) */
	icon: string;
	/** 确认按钮文字 */
	confirmText: string;
	/** 点击确认后的回调函数 */
	onConfirm: () => void;
}

/**
 * @description 对话框默认初始状态
 */
const defaultState: ConfirmState = {
	isVisible: false,
	title: '',
	message: '',
	icon: 'mdi-help-circle',
	confirmText: '确认',
	onConfirm: () => {},
};

/**
 * @description 集中式的响应式状态对象，用于管理全局确认对话框的显示与行为
 */
const dialogState = reactive<ConfirmState>({ ...defaultState });

/**
 * @description 显示确认对话框
 * @param {Partial<Omit<ConfirmState, 'isVisible'>>} options - 对话框配置选项
 * @example
 * showConfirm({
 *   title: '提示',
 *   message: '确定要删除吗？',
 *   onConfirm: () => doDelete()
 * });
 */
export function showConfirm(options: Partial<Omit<ConfirmState, 'isVisible'>>) {
	Object.assign(dialogState, defaultState, options, { isVisible: true });
}

/**
 * @description 对话框内部触发确认动作时调用，执行回调并关闭对话框
 */
export function handleGlobalConfirm() {
	dialogState.onConfirm();
	dialogState.isVisible = false;
}

/**
 * @description 隐藏对话框
 */
export function hideConfirm() {
	dialogState.isVisible = false;
}

/**
 * @description 提供给组件使用的 Composition API，用于绑定对话框状态和操作
 * @returns {Object} 包含对话框状态和控制方法的对象
 */
export function useConfirm() {
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
}
