<template>
	<div class="chat-sidebar">
		<!-- New Session Button -->
		<div class="sidebar-top">
			<v-btn
				block
				variant="outlined"
				color="primary"
				prepend-icon="mdi-plus-circle-outline"
				class="new-session-btn"
				@click="handleCreateNewSession"
			>
				新建分析会话
			</v-btn>
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
					<div class="session-item-info" @dblclick.stop="startEdit(session)">
						<span class="session-item-title">{{ session.title || '新会话' }}</span>
						<span class="session-item-time">{{ formatTime(session.createTime || session.updateTime) || '—' }}</span>
					</div>
					<div class="session-item-actions">
						<v-btn icon variant="text" density="compact" size="x-small" class="action-btn--edit" title="重命名" @click.stop="startEdit(session)">
							<v-icon size="14">mdi-pencil-outline</v-icon>
						</v-btn>
						<v-btn icon variant="text" density="compact" size="x-small" class="action-btn--star" title="收藏" @click.stop="handlePin(session)">
							<v-icon size="14" :color="session.isPinned ? '#f59e0b' : ''">
								{{ session.isPinned ? 'mdi-star' : 'mdi-star-outline' }}
							</v-icon>
						</v-btn>
						<v-btn icon variant="text" density="compact" size="x-small" class="action-btn--danger" title="删除" @click.stop="handleDelete(session)">
							<v-icon size="14">mdi-delete-outline</v-icon>
						</v-btn>
					</div>
				</template>
			</div>

			<div v-if="store.sessions.length === 0" class="empty-sessions">
				暂无历史会话
			</div>
		</div>

		<!-- Collapse button at bottom-left (UI ref has it) -->
		<div class="sidebar-footer">
			<v-btn icon variant="outlined" density="compact" size="small" title="收起" color="grey">
				<v-icon size="16">mdi-chevron-left</v-icon>
			</v-btn>
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

function formatTime(time: Date | string | undefined): string {
	if (!time) return '';
	const d = typeof time === 'string' ? new Date(time) : time;
	if (isNaN(d.getTime())) return '';
	const now = new Date();
	const isToday = d.toDateString() === now.toDateString();
	const pad = (n: number) => String(n).padStart(2, '0');
	if (isToday) return `今天 ${pad(d.getHours())}:${pad(d.getMinutes())}`;
	const isThisYear = d.getFullYear() === now.getFullYear();
	if (isThisYear) return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
	return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

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
	text-transform: none !important;
	letter-spacing: 0 !important;
	font-size: 14px !important;
	border-style: dashed !important;
	border-radius: 10px !important;
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
	padding: 8px 10px;
	border-radius: 8px;
	cursor: pointer;
	transition: background 0.12s;
	margin-bottom: 2px;
	min-height: 44px;
}
.session-item:hover {
	background: #e8f0fe;
}
.session-item.active {
	background: #e8f0fe;
}

.session-item-info {
	display: flex;
	flex-direction: column;
	flex: 1;
	min-width: 0;
	gap: 1px;
}

.session-item-title {
	font-size: 13px;
	color: #1e293b;
	line-height: 1.35;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.session-item-time {
	font-size: 11px;
	color: #94a3b8;
	font-style: italic;
	line-height: 1.2;
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
	gap: 12px;
	margin-left: 6px;
}
.session-item:hover .session-item-actions,
.session-item.active .session-item-actions {
	display: flex;
}

.action-btn--edit:hover {
	color: #3b82f6 !important;
}

.action-btn--star:hover {
	color: #f59e0b !important;
}

.action-btn--danger:hover {
	color: #ef4444 !important;
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

/* ── Scrollbar ───────────────────────────────────────────────────────────────── */
.custom-scrollbar::-webkit-scrollbar { width: 4px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #94a3b8; }
</style>
