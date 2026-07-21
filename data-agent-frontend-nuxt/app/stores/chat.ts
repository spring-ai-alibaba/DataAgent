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

import { defineStore } from 'pinia';
import chatService, {
	type ChatSession,
	type ChatMessage,
} from '~/services/chat/index';
import graphService, {
	type GraphRequest,
	type GraphNodeResponse,
	TextType,
} from '~/services/graph/index';
import agentDatasourceService from '~/services/agentDatasource/index';
import { useSessionStateManager } from '~/services/sessionStateManager/index';
import modelConfigService, {
	type ModelConfig,
} from '~/services/modelConfig/index';
import datasourceService, {
	type Datasource as BaseDatasource,
} from '~/services/datasource/index';

export type Datasource = BaseDatasource & { isActive?: boolean };

export interface ExtendedChatSession extends ChatSession {
	editing?: boolean;
	editingTitle?: string;
}

export interface ChatRequestOptions {
	humanFeedback: boolean;
	nl2sqlOnly: boolean;
	showSqlResults: boolean;
	thinkingEnabled: boolean;
	reasoningEffort: 'high' | 'max';
	pageSize: number;
}

export const useChatStore = defineStore('chat', () => {
	// ── Session list state ──────────────────────────────────────────────────────
	const sessions = ref<ExtendedChatSession[]>([]);
	const currentSession = ref<ChatSession | null>(null);
	const currentMessages = ref<ChatMessage[]>([]);

	// ── Streaming state ─────────────────────────────────────────────────────────
	const isStreaming = ref(false);
	const nodeBlocks = ref<GraphNodeResponse[][]>([]);

	// ── Human feedback state ────────────────────────────────────────────────────
	const showHumanFeedback = ref(false);
	const lastRequest = ref<GraphRequest | null>(null);
	const feedbackContent = ref('');
	const awaitingClarification = ref(false);
	const clarificationQuestion = ref('');
	const clarificationCount = ref(0);

	// ── Request options ─────────────────────────────────────────────────────────
	const requestOptions = ref<ChatRequestOptions>({
		humanFeedback: false,
		nl2sqlOnly: false,
		showSqlResults: false,
		thinkingEnabled: false,
		reasoningEffort: 'high',
		pageSize: 20,
	});

	// ── Report state ────────────────────────────────────────────────────────────
	const reportFormat = ref<'markdown' | 'html'>('markdown');
	const showReportFullscreen = ref(false);
	const fullscreenReportContent = ref('');
	const streamingReportContent = ref('');
	const isReportStreaming = ref(false);

	// ── Chat sidebar collapse state ─────────────────────────────────────────────
	const chatSidebarCollapsed = ref(false);

	// ── Agent info (set by layout) ──────────────────────────────────────────────
	const currentAgentId = ref<number | undefined>(undefined);
	const activeChatModel = ref('');
	const currentAgentName = ref('');
	const currentAgentAvatar = ref('');
	const currentAgentDescription = ref('');

	// ── Datasource state ──────────────────────────────────────────────────────────
	const allDatasources = ref<Datasource[]>([]);
	const activeDatasource = ref<Datasource | null>(null);

	// ── Model state ──────────────────────────────────────────────────────────────
	const chatModels = ref<ModelConfig[]>([]);
	const activeModelConfig = ref<ModelConfig | null>(null);

	// ── SSE session stream refs (not reactive) ──────────────────────────────────
	let sessionEventSource: EventSource | null = null;
	let sessionReconnectTimer: ReturnType<typeof setTimeout> | null = null;
	let isStoreActive = true;

	const {
		getSessionState,
		syncStateToView,
		saveViewToState,
		deleteSessionState,
		persistSessionState,
		clearPersistedSessionState,
	} = useSessionStateManager();

	// ── Session stream ──────────────────────────────────────────────────────────
	function connectSessionStream(agentId: number) {
		if (sessionReconnectTimer) {
			clearTimeout(sessionReconnectTimer);
			sessionReconnectTimer = null;
		}
		if (sessionEventSource) sessionEventSource.close();

		const source = new EventSource(`/api/agent/${agentId}/sessions/stream`);
		source.addEventListener('title-updated', (event) => {
			try {
				const data = JSON.parse((event as MessageEvent<string>).data) as {
					sessionId: string;
					title: string;
				};
				const target = sessions.value.find((s) => s.id === data.sessionId);
				if (target) {
					target.title = data.title;
					target.editingTitle = data.title;
				}
				if (currentSession.value?.id === data.sessionId)
					currentSession.value.title = data.title;
			} catch {
				/* ignore */
			}
		});
		source.onerror = () => {
			source.close();
			sessionEventSource = null;
			if (isStoreActive)
				sessionReconnectTimer = setTimeout(
					() => connectSessionStream(agentId),
					3000,
				);
		};
		sessionEventSource = source;
	}

	function disconnectSessionStream() {
		isStoreActive = false;
		if (sessionReconnectTimer) clearTimeout(sessionReconnectTimer);
		if (sessionEventSource) {
			sessionEventSource.close();
			sessionEventSource = null;
		}
	}

	// ── Session operations ──────────────────────────────────────────────────────
	async function loadSessions(agentId: number) {
		sessions.value = await chatService.getAgentSessions(agentId);
		const firstSession = sessions.value[0];
		if (firstSession) {
			await selectSession(firstSession);
		} else {
			await createNewSession(agentId);
		}
		// Load global datasources (active)
		try {
			const list = await datasourceService.getAllDatasource('active');
			allDatasources.value = list;
			activeDatasource.value = list[0] || null;
		} catch {
			/* ignore */
		}
		// Load chat models
		try {
			const models = await modelConfigService.list();
			chatModels.value = models.filter((m) => m.modelType === 'CHAT');
			const active = chatModels.value.find((m) => m.isActive);
			if (active) {
				activeModelConfig.value = active;
				activeChatModel.value = active.modelName;
				applyModelThinkingDefaults(active);
			}
		} catch {
			/* ignore */
		}
	}

	async function switchDatasource(ds: Datasource) {
		const agentId = currentAgentId.value;
		const nextDatasourceId = ds?.id;
		if (!agentId || !nextDatasourceId) {
			activeDatasource.value = ds;
			return;
		}
		if (activeDatasource.value?.id === nextDatasourceId) {
			activeDatasource.value = { ...ds, isActive: true };
			return;
		}
		try {
			// 全局数据源列表切换：确保先建立/启用 agent 关联
			// 后端 add 接口会自动禁用该 agent 其他数据源并启用当前数据源
			await agentDatasourceService.addDatasourceToAgent(
				String(agentId),
				nextDatasourceId,
			);
			allDatasources.value = allDatasources.value.map((item) => ({
				...item,
				isActive: item.id === nextDatasourceId,
			}));
			activeDatasource.value = { ...ds, isActive: true };
		} catch (e) {
			console.error('切换数据源失败', e);
		}
	}

	async function switchModel(modelId: number) {
		try {
			await modelConfigService.activate(modelId);
			const models = await modelConfigService.list();
			chatModels.value = models.filter((m) => m.modelType === 'CHAT');
			const active = chatModels.value.find((m) => m.isActive);
			if (active) {
				activeModelConfig.value = active;
				activeChatModel.value = active.modelName;
				applyModelThinkingDefaults(active);
			}
		} catch (e) {
			console.error('切换模型失败', e);
		}
	}

	function applyModelThinkingDefaults(model: ModelConfig) {
		requestOptions.value.thinkingEnabled = Boolean(model.thinkingEnabled);
		requestOptions.value.reasoningEffort = model.reasoningEffort || 'high';
	}

	async function createNewSession(agentId: number) {
		const newSession = await chatService.createSession(agentId, '新会话');
		sessions.value.unshift(newSession);
		await selectSession(newSession);
		return newSession;
	}

	function resetClarificationState(
		sessionState?: ReturnType<typeof getSessionState>,
	) {
		awaitingClarification.value = false;
		clarificationQuestion.value = '';
		clarificationCount.value = 0;
		if (sessionState) {
			sessionState.awaitingClarification = false;
			sessionState.clarificationQuestion = '';
			sessionState.clarificationCount = 0;
		}
	}

	function parseClarificationMetadata(metadata?: string) {
		if (!metadata) return null;
		try {
			return JSON.parse(metadata) as {
				awaitingClarification?: boolean;
				clarificationQuestion?: string;
				clarificationCount?: number;
				threadId?: string;
			};
		} catch {
			return null;
		}
	}

	function restoreClarificationStateFromMessages(
		sessionState: ReturnType<typeof getSessionState>,
	) {
		resetClarificationState(sessionState);
		const lastMessage = currentMessages.value[currentMessages.value.length - 1];
		if (
			!lastMessage ||
			lastMessage.role !== 'assistant' ||
			lastMessage.messageType !== 'clarification'
		) {
			return;
		}

		const metadata = parseClarificationMetadata(lastMessage.metadata);
		if (!metadata?.awaitingClarification) {
			return;
		}

		awaitingClarification.value = true;
		clarificationQuestion.value =
			metadata.clarificationQuestion || lastMessage.content || '';
		clarificationCount.value = metadata.clarificationCount || 0;
		sessionState.awaitingClarification = true;
		sessionState.clarificationQuestion = clarificationQuestion.value;
		sessionState.clarificationCount = clarificationCount.value;
		sessionState.lastRequest = {
			agentId: String(currentAgentId.value || ''),
			threadId: metadata.threadId,
			query: metadata.clarificationQuestion || '',
			humanFeedback: requestOptions.value.humanFeedback,
			humanFeedbackContent: undefined,
			clarificationAnswer: undefined,
			resumeMode: null,
			rejectedPlan: false,
			nl2sqlOnly: requestOptions.value.nl2sqlOnly,
			thinkingEnabled: requestOptions.value.thinkingEnabled,
			reasoningEffort: requestOptions.value.reasoningEffort,
		};
		lastRequest.value = sessionState.lastRequest;
	}

	async function selectSession(session: ChatSession) {
		// Save current session state
		if (currentSession.value) {
			saveViewToState(currentSession.value.id, {
				isStreaming,
				isReportStreaming,
				nodeBlocks,
				streamingReportContent,
				awaitingClarification,
				clarificationQuestion,
				clarificationCount,
				showHumanFeedback,
				feedbackContent,
			});
		}
		currentSession.value = session;
		syncStateToView(session.id, {
			isStreaming,
			isReportStreaming,
			nodeBlocks,
			streamingReportContent,
			awaitingClarification,
			clarificationQuestion,
			clarificationCount,
			showHumanFeedback,
			feedbackContent,
		});
		currentMessages.value = await chatService.getSessionMessages(session.id);
		const sessionState = getSessionState(session.id);
		lastRequest.value = sessionState.lastRequest;
		if (sessionState.isStreaming && sessionState.lastRequest?.threadId) {
			await reconnectStreamingSession(session.id);
		}
		restoreClarificationStateFromMessages(sessionState);
	}

	async function renameSession(session: ExtendedChatSession, newTitle: string) {
		await chatService.renameSession(session.id, newTitle);
		session.title = newTitle;
		session.editing = false;
		if (currentSession.value?.id === session.id)
			currentSession.value.title = newTitle;
	}

	async function pinSession(session: ChatSession) {
		await chatService.pinSession(session.id, !session.isPinned);
		session.isPinned = !session.isPinned;
	}

	async function removeSession(session: ChatSession) {
		await chatService.deleteSession(session.id);
		deleteSessionState(session.id);
		sessions.value = sessions.value.filter((s) => s.id !== session.id);
		if (currentSession.value?.id === session.id) {
			currentSession.value = null;
			currentMessages.value = [];
			isStreaming.value = false;
			nodeBlocks.value = [];
			resetClarificationState();
		}
	}

	async function clearSessions(agentId: number) {
		await chatService.clearAgentSessions(agentId);
		sessions.value.forEach((s) => deleteSessionState(s.id));
		sessions.value = [];
		currentSession.value = null;
		currentMessages.value = [];
		isStreaming.value = false;
		nodeBlocks.value = [];
		resetClarificationState();
	}

	function appendTransientAssistantMessage(
		sessionId: string,
		messageType: string,
		content: string,
	) {
		if (currentSession.value?.id !== sessionId) return;
		currentMessages.value.push({
			id: -Date.now(),
			sessionId,
			role: 'assistant',
			content,
			messageType,
		});
	}

	async function reconnectStreamingSession(sessionId: string) {
		const sessionState = getSessionState(sessionId);
		const request = sessionState.lastRequest;
		if (
			!request ||
			!sessionState.threadId ||
			!sessionState.isStreaming ||
			!currentSession.value ||
			currentSession.value.id !== sessionId
		) {
			return;
		}

		await _sendGraphRequest(
			{
				...request,
				threadId: sessionState.threadId,
				reconnect: true,
				lastSequence: sessionState.lastSequence,
			},
			false,
			{ reconnect: true },
		);
	}

	// ── Message send & stream ───────────────────────────────────────────────────
	async function sendMessage(query: string) {
		if (!currentSession.value) return;

		const needsTitle =
			!currentSession.value.title || currentSession.value.title === '新会话';
		const userMessage: ChatMessage = {
			sessionId: currentSession.value.id,
			role: 'user',
			content: query,
			messageType: 'text',
			titleNeeded: needsTitle,
		};

		const saved = await chatService.saveMessage(
			currentSession.value.id,
			userMessage,
		);
		currentMessages.value.push(saved);

		const sessionState = getSessionState(currentSession.value.id);
		const isClarificationReply = sessionState.awaitingClarification;
		const previousClarificationCount = sessionState.clarificationCount;
		const request: GraphRequest = {
			agentId: String(currentAgentId.value || ''),
			query,
			humanFeedback: requestOptions.value.humanFeedback,
			nl2sqlOnly: requestOptions.value.nl2sqlOnly,
			thinkingEnabled: requestOptions.value.thinkingEnabled,
			reasoningEffort: requestOptions.value.reasoningEffort,
			rejectedPlan: false,
			humanFeedbackContent: undefined,
			threadId: sessionState.lastRequest?.threadId,
			clarificationAnswer: isClarificationReply ? query : undefined,
			resumeMode: isClarificationReply ? 'clarification' : null,
		};
		if (isClarificationReply) {
			resetClarificationState(sessionState);
		}

		await _sendGraphRequest(request, true, {
			isClarificationReply,
			previousClarificationCount,
		});
	}

	async function _sendGraphRequest(
		request: GraphRequest,
		_rejectedPlan: boolean,
		options: {
			isClarificationReply?: boolean;
			previousClarificationCount?: number;
			reconnect?: boolean;
		} = {},
	) {
		const session = currentSession.value;
		if (!session) return;

		const sessionId = session.id;
		const sessionTitle = session.title;
		const sessionState = getSessionState(sessionId);
		const isReconnect = options.reconnect === true;

		lastRequest.value = request;
		sessionState.lastRequest = request;
		sessionState.agentId = request.agentId;
		sessionState.threadId = request.threadId || sessionState.threadId;
		sessionState.showHumanFeedback = false;
		sessionState.feedbackContent = '';
		sessionState.isStreaming = true;
		isStreaming.value = true;
		showHumanFeedback.value = false;
		feedbackContent.value = '';

		if (!isReconnect) {
			nodeBlocks.value = [];
			sessionState.nodeBlocks = [];
			sessionState.htmlReportContent = '';
			sessionState.htmlReportSize = 0;
			sessionState.markdownReportContent = '';
			sessionState.awaitingClarification = false;
			sessionState.clarificationQuestion = '';
			sessionState.isReportStreaming = false;
			sessionState.lastSequence = 0;
			if (!options.isClarificationReply) {
				sessionState.clarificationCount = 0;
			}
			streamingReportContent.value = '';
			isReportStreaming.value = false;
		} else {
			nodeBlocks.value = [...sessionState.nodeBlocks];
			streamingReportContent.value = sessionState.markdownReportContent;
			isReportStreaming.value = !!sessionState.markdownReportContent;
			sessionState.isReportStreaming = isReportStreaming.value;
		}
		persistSessionState(sessionId);

		let currentNodeName: string | null = null;
		let currentBlockIndex = -1;
		let isClarificationStream = false;
		let streamedClarificationText = '';
		let streamedClarificationCount = 0;
		let streamedFinalAnswerText = '';
		const transientFinalAnswerId = -Date.now();

		function appendToCurrentBlock(response: GraphNodeResponse) {
			const currentBlock =
				currentBlockIndex >= 0
					? sessionState.nodeBlocks[currentBlockIndex]
					: undefined;
			if (!currentBlock) {
				sessionState.nodeBlocks.push([{ ...response }]);
				currentBlockIndex = sessionState.nodeBlocks.length - 1;
				currentNodeName = response.nodeName;
				return;
			}

			const lastItem = currentBlock[currentBlock.length - 1];
			if (
				lastItem &&
				lastItem.nodeName === response.nodeName &&
				lastItem.textType === response.textType
			) {
				lastItem.text += response.text;
				lastItem.complete = response.complete;
				lastItem.error = response.error;
				lastItem.threadId = response.threadId;
				lastItem.interactionType = response.interactionType;
				lastItem.awaitingInput = response.awaitingInput;
			} else {
				currentBlock.push({ ...response });
			}
		}

		function applyTimingUpdate(response: GraphNodeResponse) {
			if (!response.nodeName) return;
			const block = [...sessionState.nodeBlocks]
				.reverse()
				.find((item) => item[0]?.nodeName === response.nodeName);
			const target = block?.[0];
			if (!target) return;
			if (typeof response.workflowStartedAt === 'number')
				target.workflowStartedAt = response.workflowStartedAt;
			if (typeof response.nodeStartedAt === 'number')
				target.nodeStartedAt = response.nodeStartedAt;
			if (typeof response.nodeElapsedMs === 'number')
				target.nodeElapsedMs = response.nodeElapsedMs;
			if (typeof response.totalElapsedMs === 'number')
				target.totalElapsedMs = response.totalElapsedMs;
		}

		let viewSyncTimer: ReturnType<typeof setTimeout> | null = null;
		const VIEW_SYNC_INTERVAL = 120;
		function scheduleViewSync() {
			if (viewSyncTimer) return;
			viewSyncTimer = setTimeout(() => {
				viewSyncTimer = null;
				persistSessionState(sessionId);
				if (currentSession.value?.id === sessionId) {
					nodeBlocks.value = [...sessionState.nodeBlocks];
				}
			}, VIEW_SYNC_INTERVAL);
		}

		// Throttle report content pushes: batch SSE chunks and push at most
		// once every ~80ms. This prevents excessive re-renders while keeping
		// the typewriter animation looking smooth on the frontend.
		let reportSyncTimer: ReturnType<typeof setTimeout> | null = null;
		const REPORT_SYNC_INTERVAL = 80; // ms
		function scheduleReportSync() {
			if (reportSyncTimer) return;
			reportSyncTimer = setTimeout(() => {
				reportSyncTimer = null;
				sessionState.isReportStreaming = true;
				persistSessionState(sessionId);
				if (currentSession.value?.id === sessionId) {
					isReportStreaming.value = true;
					streamingReportContent.value = sessionState.markdownReportContent;
				}
			}, REPORT_SYNC_INTERVAL);
		}

		function flushPendingSync() {
			if (viewSyncTimer) {
				clearTimeout(viewSyncTimer);
				viewSyncTimer = null;
			}
			if (reportSyncTimer) {
				clearTimeout(reportSyncTimer);
				reportSyncTimer = null;
			}
			if (currentSession.value?.id === sessionId) {
				nodeBlocks.value = [...sessionState.nodeBlocks];
				if (sessionState.markdownReportContent) {
					isReportStreaming.value = true;
					streamingReportContent.value = sessionState.markdownReportContent;
				}
			}
			sessionState.isReportStreaming = !!sessionState.markdownReportContent;
			persistSessionState(sessionId);
		}

		const closeStream = await graphService.streamSearch(
			request,
			async (response: GraphNodeResponse) => {
				if (response.error) return;
				sessionState.threadId = response.threadId || sessionState.threadId;
				if (typeof response.sequence === 'number') {
					sessionState.lastSequence = response.sequence;
				}
				if (sessionState.lastRequest) {
					sessionState.lastRequest.threadId = response.threadId;
					lastRequest.value = sessionState.lastRequest;
				}
				if (response.timingOnly || response.complete) {
					applyTimingUpdate(response);
					scheduleViewSync();
					return;
				}
				if (response.interactionType === 'clarification') {
					isClarificationStream = true;
					streamedClarificationText += response.text;
					if (response.awaitingInput) {
						streamedClarificationCount = options.isClarificationReply
							? (options.previousClarificationCount || 0) + 1
							: 1;
					}
				}
				if (response.textType === TextType.FINAL_ANSWER) {
					streamedFinalAnswerText += response.text;
					if (currentSession.value?.id === sessionId) {
						const existing = currentMessages.value.find(
							(message) => message.id === transientFinalAnswerId,
						);
						if (existing) {
							existing.content = streamedFinalAnswerText;
						} else {
							currentMessages.value.push({
								id: transientFinalAnswerId,
								sessionId,
								role: 'assistant',
								content: streamedFinalAnswerText,
								messageType: 'text',
							});
						}
					}
					return;
				}

				if (response.nodeName === 'ReportGeneratorNode') {
					const isNewNode =
						currentNodeName === null || response.nodeName !== currentNodeName;
					if (isNewNode) {
						sessionState.nodeBlocks.push([{ ...response }]);
						currentBlockIndex = sessionState.nodeBlocks.length - 1;
						currentNodeName = response.nodeName;
					}
					if (response.textType === 'HTML') {
						sessionState.htmlReportContent += response.text;
						sessionState.htmlReportSize = sessionState.htmlReportContent.length;
						const rn = sessionState.nodeBlocks.find(
							(b) =>
								b.length > 0 &&
								b[0].nodeName === 'ReportGeneratorNode' &&
								b[0].textType === 'HTML',
						);
						if (rn)
							rn[0].text = `正在收集HTML报告... 已收集 ${sessionState.htmlReportSize} 字节`;
						else
							sessionState.nodeBlocks.push([
								{ ...response, text: `正在收集HTML报告...` },
							]);
					} else if (response.textType === 'MARK_DOWN') {
						sessionState.markdownReportContent += response.text;
						scheduleReportSync();
						const rn = sessionState.nodeBlocks.find(
							(b) =>
								b.length > 0 &&
								b[0].nodeName === 'ReportGeneratorNode' &&
								b[0].textType === 'MARK_DOWN',
						);
						if (rn) rn[0].text = sessionState.markdownReportContent;
						else
							sessionState.nodeBlocks.push([
								{ ...response, text: response.text },
							]);
					}
				} else if (response.textType === TextType.RESULT_SET) {
					currentNodeName = 'result_set';
					sessionState.nodeBlocks.push([{ ...response }]);
					currentBlockIndex = sessionState.nodeBlocks.length - 1;
				} else {
					const isNewNode =
						currentNodeName === null || response.nodeName !== currentNodeName;
					if (isNewNode) {
						sessionState.nodeBlocks.push([{ ...response }]);
						currentBlockIndex = sessionState.nodeBlocks.length - 1;
						currentNodeName = response.nodeName;
					} else {
						appendToCurrentBlock(response);
					}
				}

				applyTimingUpdate(response);
				scheduleViewSync();
			},
			async (error: Error) => {
				console.error('Stream error:', error);
				flushPendingSync();

				if (options.reconnect) {
					sessionState.isStreaming = false;
					sessionState.closeStream = null;
					sessionState.isReportStreaming = false;
					currentNodeName = null;
					clearPersistedSessionState(sessionId);
					if (currentSession.value?.id === sessionId) {
						isStreaming.value = false;
						isReportStreaming.value = false;
						if (sessionState.nodeBlocks.length > 0) {
							appendTransientAssistantMessage(
								sessionId,
								'timeline',
								JSON.stringify(sessionState.nodeBlocks),
							);
						}
						appendTransientAssistantMessage(
							sessionId,
							'warning',
							error.message || '流式任务连接已中断，请重新发起任务。',
						);
						nodeBlocks.value = [];
					}
					return;
				}

				if (!isClarificationStream && sessionState.nodeBlocks.length > 0) {
					const msg: ChatMessage = {
						sessionId,
						role: 'assistant',
						content: JSON.stringify(sessionState.nodeBlocks),
						messageType: 'timeline',
					};
					await chatService
						.saveMessage(sessionId, msg)
						.catch((e) => console.error(e));
				}

				// Save error message
				const errorMsg: ChatMessage = {
					sessionId,
					role: 'assistant',
					content: error.message || '请求失败，请检查网络连接并重试。',
					messageType: 'error',
				};
				await chatService
					.saveMessage(sessionId, errorMsg)
					.catch((e) => console.error(e));

				sessionState.isStreaming = false;
				sessionState.closeStream = null;
				sessionState.isReportStreaming = false;
				currentNodeName = null;
				clearPersistedSessionState(sessionId);
				if (currentSession.value?.id === sessionId) {
					isStreaming.value = false;
					isReportStreaming.value = false;
					streamingReportContent.value = '';
					currentMessages.value =
						await chatService.getSessionMessages(sessionId);
				}
			},
			async () => {
				flushPendingSync();

				if (isClarificationStream) {
					const question = streamedClarificationText.trim();
					const count =
						streamedClarificationCount ||
						(options.isClarificationReply
							? (options.previousClarificationCount || 0) + 1
							: 1);
					const clarificationMsg: ChatMessage = {
						sessionId,
						role: 'assistant',
						content: question,
						messageType: 'clarification',
						metadata: JSON.stringify({
							awaitingClarification: true,
							clarificationQuestion: question,
							clarificationCount: count,
							threadId: sessionState.lastRequest?.threadId || request.threadId,
						}),
					};
					await chatService
						.saveMessage(sessionId, clarificationMsg)
						.catch((e) => console.error(e));
					sessionState.awaitingClarification = true;
					sessionState.clarificationQuestion = question;
					sessionState.clarificationCount = count;
					sessionState.isReportStreaming = false;
					awaitingClarification.value = true;
					clarificationQuestion.value = question;
					clarificationCount.value = count;
					sessionState.isStreaming = false;
					persistSessionState(sessionId);
					if (currentSession.value?.id === sessionId) isStreaming.value = false;
				} else {
					if (sessionState.nodeBlocks.length > 0) {
						const timelineMsg: ChatMessage = {
							sessionId,
							role: 'assistant',
							content: JSON.stringify(sessionState.nodeBlocks),
							messageType: 'timeline',
						};
						const savedTimeline = await chatService
							.saveMessage(sessionId, timelineMsg)
							.catch((e) => {
								console.error(e);
								return null;
							});
						if (savedTimeline && currentSession.value?.id === sessionId)
							currentMessages.value.push(savedTimeline);
					}
					const finalAnswer = streamedFinalAnswerText.trim();
					if (finalAnswer) {
						const finalAnswerMsg: ChatMessage = {
							sessionId,
							role: 'assistant',
							content: finalAnswer,
							messageType: 'text',
						};
						await chatService
							.saveMessage(sessionId, finalAnswerMsg)
							.catch((e) => console.error(e));
					}

					resetClarificationState(sessionState);
					if (requestOptions.value.humanFeedback && _rejectedPlan) {
						sessionState.showHumanFeedback = true;
						sessionState.feedbackContent = feedbackContent.value;
						sessionState.isStreaming = false;
						persistSessionState(sessionId);
						showHumanFeedback.value = true;
					} else {
						sessionState.isStreaming = false;
						sessionState.isReportStreaming = false;
						clearPersistedSessionState(sessionId);
						if (currentSession.value?.id === sessionId)
							isStreaming.value = false;
					}
				}

				if (currentSession.value?.id === sessionId) {
					isReportStreaming.value = false;
					streamingReportContent.value = '';
				}

				currentNodeName = null;
				closeStream();
				if (currentSession.value?.id === sessionId) {
					currentMessages.value =
						await chatService.getSessionMessages(sessionId);
					nodeBlocks.value = [];
				}
				if (!showHumanFeedback.value) {
					clearPersistedSessionState(sessionId);
				}
				console.log(`会话[${sessionTitle}]处理完成`);
			},
		);
		sessionState.closeStream = closeStream;
		persistSessionState(sessionId);
	}

	async function stopStreaming() {
		if (!currentSession.value) return;
		const sessionId = currentSession.value.id;
		const sessionState = getSessionState(sessionId);
		if (!sessionState.closeStream) return;

		const threadId =
			sessionState.threadId || sessionState.lastRequest?.threadId;
		if (threadId) {
			await graphService.stopStream(threadId).catch((e) => console.error(e));
		}
		sessionState.closeStream();
		sessionState.closeStream = null;
		sessionState.isStreaming = false;
		sessionState.isReportStreaming = false;
		sessionState.nodeBlocks = [];
		sessionState.markdownReportContent = '';
		sessionState.lastSequence = 0;
		sessionState.showHumanFeedback = false;
		sessionState.feedbackContent = '';
		resetClarificationState(sessionState);
		clearPersistedSessionState(sessionId);

		// Save user-terminated warning message
		const warningMsg: ChatMessage = {
			sessionId,
			role: 'assistant',
			content: '用户已终止本次对话。',
			messageType: 'warning',
		};
		await chatService
			.saveMessage(sessionId, warningMsg)
			.catch((e) => console.error(e));

		if (currentSession.value?.id === sessionId) {
			isStreaming.value = false;
			nodeBlocks.value = [];
			isReportStreaming.value = false;
			streamingReportContent.value = '';
			currentMessages.value = await chatService.getSessionMessages(sessionId);
		}
	}

	async function submitFeedback(rejected: boolean, content: string) {
		if (!lastRequest.value) return;
		showHumanFeedback.value = false;
		feedbackContent.value = '';
		const newRequest: GraphRequest = {
			...lastRequest.value,
			rejectedPlan: rejected,
			humanFeedbackContent: content || 'Accept',
		};
		await _sendGraphRequest(newRequest, rejected);
	}

	// ── Report utils ────────────────────────────────────────────────────────────
	function openReportFullscreen(content: string) {
		fullscreenReportContent.value = content;
		showReportFullscreen.value = true;
	}

	async function downloadHtmlReport(content: string) {
		if (!currentSession.value) return;
		await chatService.downloadHtmlReport(currentSession.value.id, content);
	}

	return {
		// state
		sessions,
		currentSession,
		currentMessages,
		isStreaming,
		nodeBlocks,
		showHumanFeedback,
		lastRequest,
		feedbackContent,
		awaitingClarification,
		clarificationQuestion,
		clarificationCount,
		requestOptions,
		reportFormat,
		showReportFullscreen,
		fullscreenReportContent,
		streamingReportContent,
		isReportStreaming,
		currentAgentId,
		chatSidebarCollapsed,
		activeChatModel,
		currentAgentName,
		currentAgentAvatar,
		currentAgentDescription,
		allDatasources,
		activeDatasource,
		chatModels,
		activeModelConfig,
		// actions
		connectSessionStream,
		disconnectSessionStream,
		loadSessions,
		createNewSession,
		selectSession,
		renameSession,
		pinSession,
		removeSession,
		clearSessions,
		sendMessage,
		stopStreaming,
		submitFeedback,
		openReportFullscreen,
		downloadHtmlReport,
		switchDatasource,
		switchModel,
	};
});
