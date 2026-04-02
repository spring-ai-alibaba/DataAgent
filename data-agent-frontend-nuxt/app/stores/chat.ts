import { defineStore } from 'pinia';
import chatService, { type ChatSession, type ChatMessage } from '~/services/chat/index';
import graphService, { type GraphRequest, type GraphNodeResponse, TextType } from '~/services/graph/index';
import agentDatasourceService from '~/services/agentDatasource/index';
import { useSessionStateManager } from '~/services/sessionStateManager/index';
import modelConfigService, { type ModelConfig } from '~/services/modelConfig/index';
import axios from 'axios';

export interface Datasource {
	id?: number;
	name?: string;
	type?: string;
	databaseName?: string;
	status?: string;
	testStatus?: string;
	description?: string;
	isActive?: boolean;
}

export interface ExtendedChatSession extends ChatSession {
	editing?: boolean;
	editingTitle?: string;
}

export interface ChatRequestOptions {
	humanFeedback: boolean;
	nl2sqlOnly: boolean;
	showSqlResults: boolean;
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

	// ── Request options ─────────────────────────────────────────────────────────
	const requestOptions = ref<ChatRequestOptions>({
		humanFeedback: false,
		nl2sqlOnly: false,
		showSqlResults: false,
		pageSize: 20,
	});

	// ── Report state ────────────────────────────────────────────────────────────
	const reportFormat = ref<'markdown' | 'html'>('markdown');
	const showReportFullscreen = ref(false);
	const fullscreenReportContent = ref('');
	const streamingReportContent = ref('');
	const isReportStreaming = ref(false);

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

	const { getSessionState, syncStateToView, saveViewToState, deleteSessionState } =
		useSessionStateManager();

	// ── Session stream ──────────────────────────────────────────────────────────
	function connectSessionStream(agentId: number) {
		if (sessionReconnectTimer) { clearTimeout(sessionReconnectTimer); sessionReconnectTimer = null; }
		if (sessionEventSource) sessionEventSource.close();

		const source = new EventSource(`/api/agent/${agentId}/sessions/stream`);
		source.addEventListener('title-updated', (event) => {
			try {
				const data = JSON.parse((event as MessageEvent<string>).data) as { sessionId: string; title: string };
				const target = sessions.value.find(s => s.id === data.sessionId);
				if (target) { target.title = data.title; target.editingTitle = data.title; }
				if (currentSession.value?.id === data.sessionId) currentSession.value.title = data.title;
			} catch { /* ignore */ }
		});
		source.onerror = () => {
			source.close();
			sessionEventSource = null;
			if (isStoreActive) sessionReconnectTimer = setTimeout(() => connectSessionStream(agentId), 3000);
		};
		sessionEventSource = source;
	}

	function disconnectSessionStream() {
		isStoreActive = false;
		if (sessionReconnectTimer) clearTimeout(sessionReconnectTimer);
		if (sessionEventSource) { sessionEventSource.close(); sessionEventSource = null; }
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
			const res = await axios.get<Datasource[]>('/api/datasource', { params: { status: 'active' } });
			const list: Datasource[] = Array.isArray(res.data) ? res.data : [];
			allDatasources.value = list;
			activeDatasource.value = list[0] || null;
		} catch { /* ignore */ }
		// Load chat models
		try {
			const models = await modelConfigService.list();
			chatModels.value = models.filter(m => m.modelType === 'CHAT');
			const active = chatModels.value.find(m => m.isActive);
			if (active) {
				activeModelConfig.value = active;
				activeChatModel.value = active.modelName;
			}
		} catch { /* ignore */ }
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
			await agentDatasourceService.addDatasourceToAgent(String(agentId), nextDatasourceId);
			allDatasources.value = allDatasources.value.map(item => ({
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
			chatModels.value = models.filter(m => m.modelType === 'CHAT');
			const active = chatModels.value.find(m => m.isActive);
			if (active) {
				activeModelConfig.value = active;
				activeChatModel.value = active.modelName;
			}
		} catch (e) {
			console.error('切换模型失败', e);
		}
	}

	async function createNewSession(agentId: number) {
		const newSession = await chatService.createSession(agentId, '新会话');
		sessions.value.unshift(newSession);
		await selectSession(newSession);
		return newSession;
	}

	async function selectSession(session: ChatSession) {
		// Save current session state
		if (currentSession.value) {
			saveViewToState(currentSession.value.id, { isStreaming, nodeBlocks });
		}
		currentSession.value = session;
		syncStateToView(session.id, { isStreaming, nodeBlocks });
		currentMessages.value = await chatService.getSessionMessages(session.id);
	}

	async function renameSession(session: ExtendedChatSession, newTitle: string) {
		await chatService.renameSession(session.id, newTitle);
		session.title = newTitle;
		session.editing = false;
		if (currentSession.value?.id === session.id) currentSession.value.title = newTitle;
	}

	async function pinSession(session: ChatSession) {
		await chatService.pinSession(session.id, !session.isPinned);
		session.isPinned = !session.isPinned;
	}

	async function removeSession(session: ChatSession) {
		await chatService.deleteSession(session.id);
		deleteSessionState(session.id);
		sessions.value = sessions.value.filter(s => s.id !== session.id);
		if (currentSession.value?.id === session.id) {
			currentSession.value = null;
			currentMessages.value = [];
			isStreaming.value = false;
			nodeBlocks.value = [];
		}
	}

	async function clearSessions(agentId: number) {
		await chatService.clearAgentSessions(agentId);
		sessions.value.forEach(s => deleteSessionState(s.id));
		sessions.value = [];
		currentSession.value = null;
		currentMessages.value = [];
		isStreaming.value = false;
		nodeBlocks.value = [];
	}

	// ── Message send & stream ───────────────────────────────────────────────────
	async function sendMessage(query: string) {
		if (!currentSession.value) return;

		const needsTitle = !currentSession.value.title || currentSession.value.title === '新会话';
		const userMessage: ChatMessage = {
			sessionId: currentSession.value.id,
			role: 'user',
			content: query,
			messageType: 'text',
			titleNeeded: needsTitle,
		};

		const saved = await chatService.saveMessage(currentSession.value.id, userMessage);
		currentMessages.value.push(saved);

		const sessionState = getSessionState(currentSession.value.id);
		const request: GraphRequest = {
			agentId: String(currentAgentId.value || ''),
			query,
			humanFeedback: requestOptions.value.humanFeedback,
			nl2sqlOnly: requestOptions.value.nl2sqlOnly,
			rejectedPlan: false,
			humanFeedbackContent: undefined,
			threadId: sessionState.lastRequest?.threadId,
		};

		await _sendGraphRequest(request, true);
	}

	async function _sendGraphRequest(request: GraphRequest, _rejectedPlan: boolean) {
		const session = currentSession.value;
		if (!session) return;

		const sessionId = session.id;
		const sessionTitle = session.title;
		const sessionState = getSessionState(sessionId);

		lastRequest.value = request;
		isStreaming.value = true;
		nodeBlocks.value = [];

		sessionState.isStreaming = true;
		sessionState.nodeBlocks = [];
		sessionState.lastRequest = request;
		sessionState.htmlReportContent = '';
		sessionState.htmlReportSize = 0;
		sessionState.markdownReportContent = '';
		streamingReportContent.value = '';
		isReportStreaming.value = false;

		let currentNodeName: string | null = null;
		let currentBlockIndex = -1;

		const closeStream = await graphService.streamSearch(
			request,
			async (response: GraphNodeResponse) => {
				if (response.error) return;
				if (sessionState.lastRequest) sessionState.lastRequest.threadId = response.threadId;

				if (response.nodeName === 'ReportGeneratorNode') {
					const isNewNode = currentNodeName === null || response.nodeName !== currentNodeName;
					if (isNewNode) {
						sessionState.nodeBlocks.push([{ ...response }]);
						currentBlockIndex = sessionState.nodeBlocks.length - 1;
						currentNodeName = response.nodeName;
					}
					if (response.textType === 'HTML') {
						sessionState.htmlReportContent += response.text;
						sessionState.htmlReportSize = sessionState.htmlReportContent.length;
						const rn = sessionState.nodeBlocks.find(b => b.length > 0 && b[0].nodeName === 'ReportGeneratorNode' && b[0].textType === 'HTML');
						if (rn) rn[0].text = `正在收集HTML报告... 已收集 ${sessionState.htmlReportSize} 字节`;
						else sessionState.nodeBlocks.push([{ ...response, text: `正在收集HTML报告...` }]);
					} else if (response.textType === 'MARK_DOWN') {
						sessionState.markdownReportContent += response.text;
						if (currentSession.value?.id === sessionId) {
							isReportStreaming.value = true;
							streamingReportContent.value = sessionState.markdownReportContent;
						}
						const rn = sessionState.nodeBlocks.find(b => b.length > 0 && b[0].nodeName === 'ReportGeneratorNode' && b[0].textType === 'MARK_DOWN');
						if (rn) rn[0].text = sessionState.markdownReportContent;
						else sessionState.nodeBlocks.push([{ ...response, text: response.text }]);
					}
				} else if (response.textType === TextType.RESULT_SET) {
					currentNodeName = 'result_set';
					sessionState.nodeBlocks.push([{ ...response }]);
					currentBlockIndex = sessionState.nodeBlocks.length - 1;
				} else {
					const isNewNode = currentNodeName === null || response.nodeName !== currentNodeName;
					if (isNewNode) {
						sessionState.nodeBlocks.push([{ ...response }]);
						currentBlockIndex = sessionState.nodeBlocks.length - 1;
						currentNodeName = response.nodeName;
					} else {
						const currentBlock = currentBlockIndex >= 0 ? sessionState.nodeBlocks[currentBlockIndex] : undefined;
						if (currentBlock) {
							currentBlock.push({ ...response });
						} else {
							sessionState.nodeBlocks.push([{ ...response }]);
							currentBlockIndex = sessionState.nodeBlocks.length - 1;
							currentNodeName = response.nodeName;
						}
					}
				}

				if (currentSession.value?.id === sessionId) {
					nodeBlocks.value = [...sessionState.nodeBlocks];
				}
			},
			async (error: Error) => {
				console.error('Stream error:', error);
				
				// Save partial timeline on error
				if (sessionState.nodeBlocks.length > 0) {
					const msg: ChatMessage = { sessionId, role: 'assistant', content: JSON.stringify(sessionState.nodeBlocks), messageType: 'timeline' };
					await chatService.saveMessage(sessionId, msg).catch(e => console.error(e));
				}

				sessionState.isStreaming = false;
				sessionState.closeStream = null;
				currentNodeName = null;
				if (currentSession.value?.id === sessionId) {
					isStreaming.value = false;
					currentMessages.value = await chatService.getSessionMessages(sessionId);
				}
			},
			async () => {
				// Save the entire timeline at the end
				if (sessionState.nodeBlocks.length > 0) {
					const timelineMsg: ChatMessage = { sessionId, role: 'assistant', content: JSON.stringify(sessionState.nodeBlocks), messageType: 'timeline' };
					const savedTimeline = await chatService.saveMessage(sessionId, timelineMsg).catch(e => { console.error(e); return null; });
					if (savedTimeline && currentSession.value?.id === sessionId) currentMessages.value.push(savedTimeline);
				}

				if (requestOptions.value.humanFeedback && _rejectedPlan) {
					showHumanFeedback.value = true;
				} else {
					sessionState.isStreaming = false;
					if (currentSession.value?.id === sessionId) isStreaming.value = false;
				}

				if (currentSession.value?.id === sessionId) {
					isReportStreaming.value = false;
					streamingReportContent.value = '';
				}

				currentNodeName = null;
				closeStream();
				if (currentSession.value?.id === sessionId) {
					currentMessages.value = await chatService.getSessionMessages(sessionId);
					nodeBlocks.value = [];
				}
				console.log(`会话[${sessionTitle}]处理完成`);
			},
		);
		sessionState.closeStream = closeStream;
	}

	async function stopStreaming() {
		if (!currentSession.value) return;
		const sessionId = currentSession.value.id;
		const sessionState = getSessionState(sessionId);
		if (!sessionState.closeStream) return;

		sessionState.closeStream();
		sessionState.closeStream = null;
		sessionState.isStreaming = false;
		sessionState.nodeBlocks = [];

		if (currentSession.value?.id === sessionId) {
			isStreaming.value = false;
			nodeBlocks.value = [];
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
		requestOptions,
		reportFormat,
		showReportFullscreen,
		fullscreenReportContent,
		streamingReportContent,
		isReportStreaming,
		currentAgentId,
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
