<template>
	<div class="chat-sidebar">
		<!-- New Session Button -->
		<div class="sidebar-top">
			<button class="new-session-btn" @click="handleCreateNewSession">
				<span class="new-session-icon">
					<svg width="16" height="16" viewBox="0 0 16 16" fill="none">
						<circle cx="8" cy="8" r="7.5" stroke="currentColor" />
						<path d="M8 5v6M5 8h6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
					</svg>
				</span>
				新建分析会话
			</button>
		</div>

		<!-- Session List -->
		<div class="session-list custom-scrollbar">
			<div class="session-group-label">最近任务</div>

			<div
				v-for="session in store.sessions"
				:key="session.id"
				class="session-item"
				:class="{ active: store.currentSession?.id === session.id }"
				@click="handleSelectSession(session)"
			>
				<template v-if="session.editing">
					<input
						v-model="session.editingTitle"
						class="session-rename-input"
						@click.stop
						@blur="handleSaveTitle(session)"
						@keyup.enter="handleSaveTitle(session)"
						@keyup.esc="cancelEdit(session)"
					/>
				</template>
				<template v-else>
					<span class="session-item-title" @dblclick.stop="startEdit(session)">
						{{ session.title || '新会话' }}
					</span>
					<div class="session-item-actions">
						<button class="action-btn" title="重命名" @click.stop="startEdit(session)">
							<v-icon size="12">mdi-pencil-outline</v-icon>
						</button>
						<button class="action-btn" title="置顶" @click.stop="handlePin(session)">
							<v-icon size="12" :color="session.isPinned ? '#f59e0b' : ''">
								{{ session.isPinned ? 'mdi-star' : 'mdi-star-outline' }}
							</v-icon>
						</button>
						<button class="action-btn action-btn--danger" title="删除" @click.stop="handleDelete(session)">
							<v-icon size="12">mdi-close</v-icon>
						</button>
					</div>
				</template>
			</div>

			<div v-if="store.sessions.length === 0" class="empty-sessions">
				暂无历史会话
			</div>
		</div>

		<!-- Collapse button at bottom-left (UI ref has it) -->
		<div class="sidebar-footer">
			<button class="collapse-btn" title="收起">
				<v-icon size="16">mdi-chevron-left</v-icon>
			</button>
		</div>

		<!-- Confirm Dialog -->
		<v-dialog v-model="showDeleteConfirm" max-width="360">
			<v-card rounded="xl">
				<v-card-title class="text-subtitle-1 font-weight-bold pa-5 pb-2">删除会话</v-card-title>
				<v-card-text class="px-5 text-body-2 text-medium-emphasis">确定要删除这个会话吗？</v-card-text>
				<v-card-actions class="px-5 pb-4 gap-2">
					<v-spacer />
					<v-btn variant="text" size="small" class="text-none" @click="showDeleteConfirm = false">取消</v-btn>
					<v-btn color="error" variant="flat" size="small" class="text-none" @click="confirmDelete">确定</v-btn>
				</v-card-actions>
			</v-card>
		</v-dialog>
	</div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useChatStore, type ExtendedChatSession } from '~/stores/chat';
import type { ChatSession } from '~/services/chat/index';

const store = useChatStore();
const showDeleteConfirm = ref(false);
let sessionToDelete: ChatSession | null = null;

async function handleCreateNewSession() {
	if (!store.currentAgentId) return;
	try {
		await store.createNewSession(store.currentAgentId);
	} catch (e) {
		console.error('创建会话失败', e);
	}
}

async function handleSelectSession(session: ChatSession) {
	if (store.currentSession?.id === session.id) return;
	try {
		await store.selectSession(session);
	} catch (e) {
		console.error('切换会话失败', e);
	}
}

function startEdit(session: ExtendedChatSession) {
	session.editing = true;
	session.editingTitle = session.title || '新会话';
}

function cancelEdit(session: ExtendedChatSession) {
	session.editing = false;
}

async function handleSaveTitle(session: ExtendedChatSession) {
	const newTitle = (session.editingTitle || '').trim();
	if (!newTitle) { session.editing = false; return; }
	if (newTitle === session.title) { session.editing = false; return; }
	try {
		await store.renameSession(session, newTitle);
	} catch (e) {
		console.error('重命名失败', e);
		session.editing = false;
	}
}

async function handlePin(session: ChatSession) {
	try {
		await store.pinSession(session);
	} catch (e) {
		console.error('置顶操作失败', e);
	}
}

function handleDelete(session: ChatSession) {
	sessionToDelete = session;
	showDeleteConfirm.value = true;
}

async function confirmDelete() {
	if (!sessionToDelete) return;
	showDeleteConfirm.value = false;
	try {
		await store.removeSession(sessionToDelete);
	} catch (e) {
		console.error('删除会话失败', e);
	}
	sessionToDelete = null;
}
</script>

<style scoped>
.chat-sidebar {
	width: 260px;
	min-width: 260px;
	background: #f8fafc;
	border-right: 1px solid #e8edf2;
	display: flex;
	flex-direction: column;
	height: 100%;
}

/* ── Top section ─────────────────────────────────────────────────────────────── */
.sidebar-top {
	padding: 16px 16px 12px;
}

.new-session-btn {
	display: flex;
	align-items: center;
	gap: 8px;
	width: 100%;
	padding: 10px 16px;
	background: white;
	border: 1.5px dashed #b0bec5;
	border-radius: 10px;
	font-size: 14px;
	color: #475569;
	cursor: pointer;
	transition: border-color 0.15s, color 0.15s;
}
.new-session-btn:hover {
	border-color: #3b82f6;
	color: #3b82f6;
}
.new-session-icon {
	display: flex;
	align-items: center;
	color: inherit;
}

/* ── Session list ────────────────────────────────────────────────────────────── */
.session-list {
	flex: 1;
	overflow-y: auto;
	padding: 0 8px;
}

.session-group-label {
	font-size: 11px;
	font-weight: 600;
	color: #94a3b8;
	letter-spacing: 0.5px;
	text-transform: uppercase;
	padding: 8px 8px 6px;
}

.session-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 9px 10px;
	border-radius: 8px;
	cursor: pointer;
	transition: background 0.12s;
	margin-bottom: 2px;
	min-height: 36px;
}
.session-item:hover {
	background: #e8f0fe;
}
.session-item.active {
	background: #e8f0fe;
}
.session-item-title {
	font-size: 13.5px;
	color: #1e293b;
	line-height: 1.4;
	flex: 1;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	min-width: 0;
}
.session-item.active .session-item-title {
	color: #1d4ed8;
	font-weight: 500;
}

/* ── Actions (show on hover) ─────────────────────────────────────────────────── */
.session-item-actions {
	display: none;
	flex-shrink: 0;
	align-items: center;
	gap: 2px;
	margin-left: 4px;
}
.session-item:hover .session-item-actions,
.session-item.active .session-item-actions {
	display: flex;
}
.action-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 22px;
	height: 22px;
	background: none;
	border: none;
	cursor: pointer;
	border-radius: 4px;
	color: #64748b;
	transition: background 0.1s, color 0.1s;
}
.action-btn:hover {
	background: rgba(59, 130, 246, 0.1);
	color: #3b82f6;
}
.action-btn--danger:hover {
	background: rgba(239, 68, 68, 0.1);
	color: #ef4444;
}

/* ── Rename input ────────────────────────────────────────────────────────────── */
.session-rename-input {
	flex: 1;
	font-size: 13px;
	border: 1px solid #3b82f6;
	border-radius: 4px;
	padding: 2px 6px;
	outline: none;
	min-width: 0;
	background: white;
}

/* ── Empty state ─────────────────────────────────────────────────────────────── */
.empty-sessions {
	text-align: center;
	font-size: 12px;
	color: #94a3b8;
	padding: 20px 0;
}

/* ── Footer ──────────────────────────────────────────────────────────────────── */
.sidebar-footer {
	padding: 8px 12px 12px;
	display: flex;
	align-items: center;
}
.collapse-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 28px;
	height: 28px;
	background: none;
	border: 1px solid #e2e8f0;
	border-radius: 6px;
	cursor: pointer;
	color: #64748b;
	transition: background 0.1s;
}
.collapse-btn:hover {
	background: #e2e8f0;
}

/* ── Scrollbar ───────────────────────────────────────────────────────────────── */
.custom-scrollbar::-webkit-scrollbar { width: 4px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #94a3b8; }
</style>
