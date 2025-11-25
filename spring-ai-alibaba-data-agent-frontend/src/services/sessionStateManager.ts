/*
 * Copyright 2024-2025 the original author or authors.
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

import { ref, Ref } from 'vue';
import { GraphNodeResponse, GraphRequest } from '@/services/graph.ts';

export interface SessionRuntimeState {
  isStreaming: boolean;
  nodeBlocks: GraphNodeResponse[][];
  closeStream: (() => void) | null;
  lastRequest: GraphRequest | null;
  htmlReportContent: string;
  htmlReportSize: number;
  markdownReportContent: string;
}

/**
 * 会话状态管理器
 * 用于管理运行中的会话，实现会话隔离
 */
export function useSessionStateManager() {
  const sessionStates = ref<Map<string, SessionRuntimeState>>(new Map());

  /**
   * 获取会话运行状态
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
   * 将会话状态同步到页面状态
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
   * 保存页面状态到会话
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
   * 删除会话状态
   */
  const deleteSessionState = (sessionId: string) => {
    const state = sessionStates.value.get(sessionId);
    if (state?.closeStream) {
      state.closeStream();
    }
    sessionStates.value.delete(sessionId);
  };

  /**
   * 获取所有正在运行的会话ID
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
