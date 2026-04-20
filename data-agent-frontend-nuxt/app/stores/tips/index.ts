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

/**
 * @description 全局提示消息 (Toast) 状态管理 Store
 */

import type { Anchor } from 'vuetify';

/**
 * @description 提示消息配置选项接口
 */
interface TipOptions {
  /** 背景颜色 (Vuetify color name) */
  color?: string;
  /** 显示持续时间 (毫秒) */
  timeout?: number;
  /** 显示位置 */
  location?: Anchor;
  /** 图标名称 (MDI) */
  icon?: string;
}

/**
 * @description 提示消息 Store 定义
 */
export const useTipStore = defineStore('toast', () => {
  /** 是否显示提示框 */
  const isVisible = ref(false);
  /** 消息内容 */
  const message = ref('');
  /** 提示框颜色 */
  const color = ref('success');
  /** 自动关闭延迟 */
  const timeout = ref(3000);
  /** 弹出位置 */
  const location = ref<Anchor>('top');
  /** 图标 */
  const icon = ref('');

  /**
   * @description 显示提示消息
   * @param {string} msg - 消息内容
   * @param {TipOptions} [options] - 配置选项
   */
  function show(
    msg: string,
    options: TipOptions = {},
  ) {
    // 确保只在客户端运行
    if (import.meta.client) {
      message.value = msg;
      color.value = options.color || 'success';
      timeout.value = options.timeout || 3000;
      location.value = options.location || 'top';
      icon.value = options.icon || 'mdi-check';
      isVisible.value = true;
    }
    // 如果在服务器端调用，则不执行任何操作
  }

  /**
   * @description 隐藏提示消息
   */
  function hide() {
    isVisible.value = false;
  }

  return { isVisible, message, color, timeout, location, icon, show, hide };
});
