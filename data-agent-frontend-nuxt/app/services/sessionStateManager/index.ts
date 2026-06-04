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
 * @description 会话运行时状态管理器，处理多会话间的状态隔离、流式输出控制及报告内容缓存
 */

import { ref, type Ref } from 'vue';
import type { GraphNodeResponse, GraphRequest } from './graph';

/**
 * @description 会话运行时状态接口
 */
export interface SessionRuntimeState {
  /** 是否正在流式输出 */
  isStreaming: boolean;
  /** 图节点响应块列表 */
  nodeBlocks: GraphNodeResponse[][];
  /** 关闭流的回调函数 */
  closeStream: (() => void) | null;
  /** 最后一次请求参数 */
  lastRequest: GraphRequest | null;
  /** HTML 报告内容 */
  htmlReportContent: string;
  /** HTML 报告大小 */
  htmlReportSize: number;
  /** Markdown 报告内容 */
  markdownReportContent: string;
}

/**
 * @description 会话状态管理 Hook
 * @returns {object} 状态管理方法
 */
export function useSessionStateManager() {
  /** 所有会话的状态映射表 */
  const sessionStates = ref<Map<string, SessionRuntimeState>>(new Map());

  /**
   * @description 获取或初始化指定会话的运行状态
   * @param {string} sessionId - 会话 ID
   * @returns {SessionRuntimeState} 会话状态
   */
  const getSessionState = (sessionId: string): SessionRuntimeState => {
    if (!sessionStates.value.has(sessionId)) {
      sessionStates.value.set(sessionId, {
        isStreaming: false,
        nodeBlocks: [],
        closeStream: null,
        lastRequest: null,
        htmlReportContent: '',
        htmlReportSize: 0,
        markdownReportContent: '',
      });
    }
    return sessionStates.value.get(sessionId)!;
  };

  /**
   * @description 将会话状态同步到视图响应式变量
   * @param {string} sessionId - 会话 ID
   * @param {object} viewState - 视图状态引用
   */
  const syncStateToView = (
    sessionId: string,
    viewState: {
      isStreaming: Ref<boolean>;
      nodeBlocks: Ref<GraphNodeResponse[][]>;
    },
  ) => {
    const state = getSessionState(sessionId);
    viewState.isStreaming.value = state.isStreaming;
    viewState.nodeBlocks.value = state.nodeBlocks;
  };

  /**
   * @description 保存视图状态到会话管理器
   * @param {string} sessionId - 会话 ID
   * @param {object} viewState - 视图状态引用
   */
  const saveViewToState = (
    sessionId: string,
    viewState: {
      isStreaming: Ref<boolean>;
      nodeBlocks: Ref<GraphNodeResponse[][]>;
    },
  ) => {
    const state = getSessionState(sessionId);
    state.isStreaming = viewState.isStreaming.value;
    state.nodeBlocks = viewState.nodeBlocks.value;
  };

  /**
   * @description 删除并清理指定会话的状态
   * @param {string} sessionId - 会话 ID
   */
  const deleteSessionState = (sessionId: string) => {
    const state = sessionStates.value.get(sessionId);
    if (state?.closeStream) {
      state.closeStream();
    }
    sessionStates.value.delete(sessionId);
  };

  /**
   * @description 获取所有正在流式输出的会话 ID 列表
   * @returns {string[]} 会话 ID 列表
   */
  const getRunningSessionIds = (): string[] => {
    const runningIds: string[] = [];
    sessionStates.value.forEach((state, sessionId) => {
      if (state.isStreaming) {
        runningIds.push(sessionId);
      }
    });
    return runningIds;
  };

  return {
    sessionStates,
    getSessionState,
    syncStateToView,
    saveViewToState,
    deleteSessionState,
    getRunningSessionIds,
  };
}
