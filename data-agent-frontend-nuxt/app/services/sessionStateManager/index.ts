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

import { ref, type Ref } from 'vue';
import type { GraphNodeResponse, GraphRequest } from '~/services/graph/index';

export interface SessionRuntimeState {
  isStreaming: boolean;
  nodeBlocks: GraphNodeResponse[][];
  /** 关闭流的回调函数 */
  closeStream: ((cancelRun?: boolean) => Promise<void>) | null;
  /** 最后一次请求参数 */
  lastRequest: GraphRequest | null;
  htmlReportContent: string;
  htmlReportSize: number;
  markdownReportContent: string;
  awaitingClarification: boolean;
  clarificationQuestion: string;
  clarificationCount: number;
}

interface SessionViewStateRefs {
  isStreaming: Ref<boolean>;
  nodeBlocks: Ref<GraphNodeResponse[][]>;
  awaitingClarification: Ref<boolean>;
  clarificationQuestion: Ref<string>;
  clarificationCount: Ref<number>;
}

export function useSessionStateManager() {
  const sessionStates = ref<Map<string, SessionRuntimeState>>(new Map());

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
        awaitingClarification: false,
        clarificationQuestion: '',
        clarificationCount: 0,
      });
    }
    return sessionStates.value.get(sessionId)!;
  };

  const syncStateToView = (sessionId: string, viewState: SessionViewStateRefs) => {
    const state = getSessionState(sessionId);
    viewState.isStreaming.value = state.isStreaming;
    viewState.nodeBlocks.value = state.nodeBlocks;
    viewState.awaitingClarification.value = state.awaitingClarification;
    viewState.clarificationQuestion.value = state.clarificationQuestion;
    viewState.clarificationCount.value = state.clarificationCount;
  };

  const saveViewToState = (sessionId: string, viewState: SessionViewStateRefs) => {
    const state = getSessionState(sessionId);
    state.isStreaming = viewState.isStreaming.value;
    state.nodeBlocks = viewState.nodeBlocks.value;
    state.awaitingClarification = viewState.awaitingClarification.value;
    state.clarificationQuestion = viewState.clarificationQuestion.value;
    state.clarificationCount = viewState.clarificationCount.value;
  };

  const deleteSessionState = (sessionId: string) => {
    const state = sessionStates.value.get(sessionId);
    if (state?.closeStream) {
      void state.closeStream(true);
    }
    sessionStates.value.delete(sessionId);
  };

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
