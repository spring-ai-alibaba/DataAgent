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

const STORAGE_KEY_PREFIX = 'data-agent:chat-runtime:';
const SNAPSHOT_VERSION = 1;

export interface SessionRuntimeState {
	sessionId: string;
	agentId?: string;
	threadId?: string;
	isStreaming: boolean;
	isReportStreaming: boolean;
	nodeBlocks: GraphNodeResponse[][];
	closeStream: (() => void) | null;
	lastRequest: GraphRequest | null;
	htmlReportContent: string;
	htmlReportSize: number;
	markdownReportContent: string;
	awaitingClarification: boolean;
	clarificationQuestion: string;
	clarificationCount: number;
	showHumanFeedback: boolean;
	feedbackContent: string;
	lastSequence: number;
	updatedAt: number;
}

export interface PersistedSessionRuntimeState {
	version: number;
	sessionId: string;
	agentId?: string;
	threadId?: string;
	isStreaming: boolean;
	isReportStreaming: boolean;
	nodeBlocks: GraphNodeResponse[][];
	lastRequest: GraphRequest | null;
	htmlReportContent: string;
	htmlReportSize: number;
	markdownReportContent: string;
	awaitingClarification: boolean;
	clarificationQuestion: string;
	clarificationCount: number;
	showHumanFeedback: boolean;
	feedbackContent: string;
	lastSequence: number;
	updatedAt: number;
}

interface SessionViewStateRefs {
	isStreaming: Ref<boolean>;
	isReportStreaming: Ref<boolean>;
	nodeBlocks: Ref<GraphNodeResponse[][]>;
	streamingReportContent: Ref<string>;
	awaitingClarification: Ref<boolean>;
	clarificationQuestion: Ref<string>;
	clarificationCount: Ref<number>;
	showHumanFeedback: Ref<boolean>;
	feedbackContent: Ref<string>;
}

function createDefaultState(sessionId: string): SessionRuntimeState {
	return {
		sessionId,
		agentId: undefined,
		threadId: undefined,
		isStreaming: false,
		isReportStreaming: false,
		nodeBlocks: [],
		closeStream: null,
		lastRequest: null,
		htmlReportContent: '',
		htmlReportSize: 0,
		markdownReportContent: '',
		awaitingClarification: false,
		clarificationQuestion: '',
		clarificationCount: 0,
		showHumanFeedback: false,
		feedbackContent: '',
		lastSequence: 0,
		updatedAt: Date.now(),
	};
}

function storageKey(sessionId: string) {
	return `${STORAGE_KEY_PREFIX}${sessionId}`;
}

function canUseStorage() {
	return typeof window !== 'undefined' && !!window.localStorage;
}

function toPersistedState(state: SessionRuntimeState): PersistedSessionRuntimeState {
	return {
		version: SNAPSHOT_VERSION,
		sessionId: state.sessionId,
		agentId: state.agentId,
		threadId: state.threadId,
		isStreaming: state.isStreaming,
		isReportStreaming: state.isReportStreaming,
		nodeBlocks: state.nodeBlocks,
		lastRequest: state.lastRequest,
		htmlReportContent: state.htmlReportContent,
		htmlReportSize: state.htmlReportSize,
		markdownReportContent: state.markdownReportContent,
		awaitingClarification: state.awaitingClarification,
		clarificationQuestion: state.clarificationQuestion,
		clarificationCount: state.clarificationCount,
		showHumanFeedback: state.showHumanFeedback,
		feedbackContent: state.feedbackContent,
		lastSequence: state.lastSequence,
		updatedAt: Date.now(),
	};
}

function fromPersistedState(
	sessionId: string,
	payload: Partial<PersistedSessionRuntimeState> | null,
): SessionRuntimeState {
	const base = createDefaultState(sessionId);
	if (!payload) return base;
	return {
		...base,
		sessionId,
		agentId: payload.agentId,
		threadId: payload.threadId,
		isStreaming: payload.isStreaming ?? base.isStreaming,
		isReportStreaming: payload.isReportStreaming ?? base.isReportStreaming,
		nodeBlocks: payload.nodeBlocks ?? base.nodeBlocks,
		lastRequest: payload.lastRequest ?? base.lastRequest,
		htmlReportContent: payload.htmlReportContent ?? base.htmlReportContent,
		htmlReportSize: payload.htmlReportSize ?? base.htmlReportSize,
		markdownReportContent:
			payload.markdownReportContent ?? base.markdownReportContent,
		awaitingClarification:
			payload.awaitingClarification ?? base.awaitingClarification,
		clarificationQuestion:
			payload.clarificationQuestion ?? base.clarificationQuestion,
		clarificationCount:
			payload.clarificationCount ?? base.clarificationCount,
		showHumanFeedback: payload.showHumanFeedback ?? base.showHumanFeedback,
		feedbackContent: payload.feedbackContent ?? base.feedbackContent,
		lastSequence: payload.lastSequence ?? base.lastSequence,
		updatedAt: payload.updatedAt ?? base.updatedAt,
	};
}

export function useSessionStateManager() {
	const sessionStates = ref<Map<string, SessionRuntimeState>>(new Map());

	const hydratePersistedState = (sessionId: string): SessionRuntimeState | null => {
		if (!canUseStorage()) return null;
		try {
			const raw = window.localStorage.getItem(storageKey(sessionId));
			if (!raw) return null;
			const payload = JSON.parse(raw) as PersistedSessionRuntimeState;
			return fromPersistedState(sessionId, payload);
		} catch {
			window.localStorage.removeItem(storageKey(sessionId));
			return null;
		}
	};

	const persistSessionState = (sessionId: string) => {
		if (!canUseStorage()) return;
		const state = sessionStates.value.get(sessionId);
		if (!state) return;
		if (
			!state.isStreaming &&
			!state.awaitingClarification &&
			!state.showHumanFeedback
		) {
			window.localStorage.removeItem(storageKey(sessionId));
			return;
		}
		state.updatedAt = Date.now();
		window.localStorage.setItem(
			storageKey(sessionId),
			JSON.stringify(toPersistedState(state)),
		);
	};

	const clearPersistedSessionState = (sessionId: string) => {
		if (!canUseStorage()) return;
		window.localStorage.removeItem(storageKey(sessionId));
	};

	const getSessionState = (sessionId: string): SessionRuntimeState => {
		if (!sessionStates.value.has(sessionId)) {
			sessionStates.value.set(
				sessionId,
				hydratePersistedState(sessionId) ?? createDefaultState(sessionId),
			);
		}
		return sessionStates.value.get(sessionId)!;
	};

	const syncStateToView = (sessionId: string, viewState: SessionViewStateRefs) => {
		const state = getSessionState(sessionId);
		viewState.isStreaming.value = state.isStreaming;
		viewState.isReportStreaming.value = state.isReportStreaming;
		viewState.nodeBlocks.value = state.nodeBlocks;
		viewState.streamingReportContent.value = state.markdownReportContent;
		viewState.awaitingClarification.value = state.awaitingClarification;
		viewState.clarificationQuestion.value = state.clarificationQuestion;
		viewState.clarificationCount.value = state.clarificationCount;
		viewState.showHumanFeedback.value = state.showHumanFeedback;
		viewState.feedbackContent.value = state.feedbackContent;
	};

	const saveViewToState = (sessionId: string, viewState: SessionViewStateRefs) => {
		const state = getSessionState(sessionId);
		state.isStreaming = viewState.isStreaming.value;
		state.isReportStreaming = viewState.isReportStreaming.value;
		state.nodeBlocks = viewState.nodeBlocks.value;
		state.markdownReportContent = viewState.streamingReportContent.value;
		state.awaitingClarification = viewState.awaitingClarification.value;
		state.clarificationQuestion = viewState.clarificationQuestion.value;
		state.clarificationCount = viewState.clarificationCount.value;
		state.showHumanFeedback = viewState.showHumanFeedback.value;
		state.feedbackContent = viewState.feedbackContent.value;
		persistSessionState(sessionId);
	};

	const deleteSessionState = (sessionId: string) => {
		const state = sessionStates.value.get(sessionId);
		if (state?.closeStream) {
			state.closeStream();
		}
		sessionStates.value.delete(sessionId);
		clearPersistedSessionState(sessionId);
	};

	const getRunningSessionIds = (): string[] => {
		const runningIds: string[] = [];
		sessionStates.value.forEach((state, sessionId) => {
			if (state.isStreaming || state.awaitingClarification || state.showHumanFeedback) {
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
		persistSessionState,
		clearPersistedSessionState,
		hydratePersistedState,
	};
}
