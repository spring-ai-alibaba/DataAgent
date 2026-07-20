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

/**
 * @description 图搜索服务，处理与后端的流式 (SSE) 交互，实现搜索过程的实时反馈
 */

import axios from 'axios';
import { buildApiUrl } from '~/utils/api';

export interface GraphRequest {
	agentId: string;
	threadId?: string;
	query: string;
	humanFeedback: boolean;
	humanFeedbackContent?: string;
	clarificationAnswer?: string;
	resumeMode?: 'clarification' | 'human_feedback' | null;
	reconnect?: boolean;
	lastSequence?: number;
	rejectedPlan: boolean;
	nl2sqlOnly: boolean;
	thinkingEnabled?: boolean;
	reasoningEffort?: 'high' | 'max';
}

export interface GraphNodeResponse {
	agentId: string;
	threadId: string;
	nodeName: string;
	textType: TextType;
	text: string;
	sequence?: number;
	workflowStartedAt?: number;
	nodeStartedAt?: number;
	nodeElapsedMs?: number;
	totalElapsedMs?: number;
	timingOnly?: boolean;
	interactionType?: 'normal' | 'clarification';
	awaitingInput?: boolean;
	error: boolean;
	complete: boolean;
}

export enum TextType {
	JSON = 'JSON',
	PYTHON = 'PYTHON',
	SQL = 'SQL',
	HTML = 'HTML',
	MARK_DOWN = 'MARK_DOWN',
	RESULT_SET = 'RESULT_SET',
	TEXT = 'TEXT',
}

const API_BASE_URL = '/api';

class GraphService {
	async streamSearch(
		request: GraphRequest,
		onMessage: (response: GraphNodeResponse) => Promise<void>,
		onError?: (error: Error) => Promise<void>,
		onComplete?: () => Promise<void>,
	): Promise<() => void> {
		const params = new URLSearchParams();
		params.append('agentId', request.agentId);
		if (request.threadId) params.append('threadId', request.threadId);
		params.append('query', request.query);
		params.append('humanFeedback', request.humanFeedback.toString());
		params.append('rejectedPlan', request.rejectedPlan.toString());
		params.append('nl2sqlOnly', request.nl2sqlOnly.toString());
		if (request.thinkingEnabled !== undefined) {
			params.append('thinkingEnabled', request.thinkingEnabled.toString());
		}
		if (request.reasoningEffort) {
			params.append('reasoningEffort', request.reasoningEffort);
		}
		if (request.humanFeedbackContent) {
			params.append('humanFeedbackContent', request.humanFeedbackContent);
		}
		if (request.clarificationAnswer) {
			params.append('clarificationAnswer', request.clarificationAnswer);
		}
		if (request.resumeMode) {
			params.append('resumeMode', request.resumeMode);
		}
		if (request.reconnect) {
			params.append('reconnect', 'true');
		}
		if (request.lastSequence !== undefined) {
			params.append('lastSequence', String(request.lastSequence));
		}

		const url = buildApiUrl(
			`${API_BASE_URL}/stream/search?${params.toString()}`,
		);
		const eventSource = new EventSource(url);

		let isCompleted = false;
		let isFailed = false;
		let isClosedIntentionally = false;

		eventSource.onmessage = async (event) => {
			try {
				const nodeResponse: GraphNodeResponse = JSON.parse(event.data);
				await onMessage(nodeResponse);
			} catch (parseError) {
				console.error('Failed to parse SSE data:', parseError);
				if (onError) {
					await onError(new Error('Failed to parse server response'));
				}
			}
		};

		eventSource.onerror = async (errorEvent) => {
			if (isCompleted || isFailed || isClosedIntentionally) {
				return;
			}
			if (eventSource.readyState === EventSource.CLOSED) {
				return;
			}

			isFailed = true;
			eventSource.close();

			let streamError = new Error('Stream connection failed');
			if (errorEvent instanceof MessageEvent && errorEvent.data) {
				try {
					const response = JSON.parse(errorEvent.data) as GraphNodeResponse;
					streamError = new Error(response.text || 'Stream processing failed');
				} catch (parseError) {
					console.error('Failed to parse SSE error data:', parseError);
				}
			}

			console.error('EventSource error:', errorEvent);
			if (onError) {
				await onError(streamError);
			}
		};

		eventSource.addEventListener('complete', async (event) => {
			isCompleted = true;
			eventSource.close();
			if (event instanceof MessageEvent && event.data) {
				try {
					await onMessage(JSON.parse(event.data) as GraphNodeResponse);
				} catch (parseError) {
					console.error('Failed to parse SSE completion data:', parseError);
				}
			}
			if (onComplete) {
				await onComplete();
			}
		});

		return () => {
			isClosedIntentionally = true;
			eventSource.close();
		};
	}

	async stopStream(threadId: string): Promise<void> {
		await axios.post(buildApiUrl(`${API_BASE_URL}/stream/search/stop`), null, {
			params: { threadId },
		});
	}
}

export default new GraphService();
