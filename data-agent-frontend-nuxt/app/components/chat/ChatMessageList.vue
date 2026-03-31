<template>
	<div ref="listRef" class="message-list custom-scrollbar">
		<!-- Welcome state when no session -->
		<ChatWelcome v-if="!store.currentSession" />

		<!-- Messages -->
		<template v-else>
			<div class="messages-inner">
				<div v-for="message in store.currentMessages" :key="message.id" class="message-wrapper">

					<!-- ── User message ─────────────────────────────────── -->
					<div v-if="message.role === 'user'" class="row user-row">
						<v-card class="user-card" elevation="1">
							<span v-html="escapeHtml(message.content).replace(/\n/g, '<br>')" />
						</v-card>
						<v-avatar color="grey-darken-2" size="34" rounded="lg" class="avatar">
							<v-icon size="18" color="white">mdi-account</v-icon>
						</v-avatar>
					</div>

					<!-- ── AI messages ──────────────────────────────────── -->
					<div v-else class="row ai-row">
						<v-avatar color="blue-darken-3" size="34" rounded="lg" class="avatar">
							<v-icon size="18" color="white">mdi-robot</v-icon>
						</v-avatar>

						<!-- HTML node message -->
						<v-card v-if="message.messageType === 'html'" class="ai-card" elevation="1">
							<div class="md-body" v-html="message.content" />
						</v-card>

						<!-- Result Set -->
						<v-card v-else-if="message.messageType === 'result-set'" class="ai-card" elevation="1">
							<ChatResultSet :data="safeParseJson(message.content)" :page-size="store.requestOptions.pageSize" />
						</v-card>

						<!-- Markdown Report -->
						<v-card v-else-if="message.messageType === 'markdown-report'" class="ai-card" elevation="1">
							<ChatMarkdownReport :content="message.content" />
						</v-card>

						<!-- Plain AI text (render as markdown) -->
						<v-card v-else class="ai-card" elevation="1">
							<div class="md-body" v-html="renderMarkdown(message.content)" />
						</v-card>
					</div>
				</div>

				<!-- ── Streaming: Workflow Timeline ──────────────────── -->
				<div v-if="store.isStreaming && store.nodeBlocks.length > 0" class="row ai-row">
					<v-avatar color="blue-darken-3" size="34" rounded="lg" class="avatar">
						<v-icon size="18" color="white">mdi-robot</v-icon>
					</v-avatar>
					<v-card class="ai-card timeline-card" elevation="1">
						<ChatWorkflowTimeline :node-blocks="store.nodeBlocks" />
					</v-card>
				</div>

				<!-- ── Streaming spinner (before first node arrives) ── -->
				<div v-else-if="store.isStreaming" class="row ai-row">
					<v-avatar color="blue-darken-3" size="34" rounded="lg" class="avatar">
						<v-icon size="18" color="white">mdi-robot</v-icon>
					</v-avatar>
					<v-card class="ai-card" elevation="1">
						<div class="thinking-dots">
							<span class="dot" />
							<span class="dot dot--2" />
							<span class="dot dot--3" />
						</div>
					</v-card>
				</div>
			</div>
		</template>
	</div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import hljs from 'highlight.js';
import 'highlight.js/styles/github.css';
import sql from 'highlight.js/lib/languages/sql';
import python from 'highlight.js/lib/languages/python';
import json from 'highlight.js/lib/languages/json';
import { useChatStore } from '~/stores/chat';
import type { ResultData } from '~/services/resultSet/index';
import ChatWelcome from './ChatWelcome.vue';
import ChatResultSet from './ChatResultSet.vue';
import ChatMarkdownReport from './ChatMarkdownReport.vue';
import ChatWorkflowTimeline from './ChatWorkflowTimeline.vue';

hljs.registerLanguage('sql', sql);
hljs.registerLanguage('python', python);
hljs.registerLanguage('json', json);

const store = useChatStore();
const listRef = ref<HTMLElement | null>(null);

marked.setOptions({ gfm: true, breaks: true });

function renderMarkdown(content: string): string {
	if (!content) return '';
	return DOMPurify.sanitize(marked.parse(content) as string);
}

function safeParseJson(content: string): ResultData | null {
	try { return JSON.parse(content); } catch { return null; }
}

function escapeHtml(text: string): string {
	const div = document.createElement('div');
	div.textContent = text;
	return div.innerHTML;
}

function scrollToBottom() {
	nextTick(() => {
		if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight;
	});
}

watch(() => store.currentMessages.length, () => scrollToBottom());
watch(() => store.nodeBlocks.length, () => scrollToBottom());
watch(() => store.isStreaming, (v) => { if (v) scrollToBottom(); });
</script>

<style scoped>
/* ── Layout ──────────────────────────────────────────────────────────────────── */
.message-list {
	flex: 1;
	overflow-y: auto;
	display: flex;
	flex-direction: column;
}

.messages-inner {
	padding: 24px 32px;
	display: flex;
	flex-direction: column;
	gap: 20px;
	width: 100%;
}

/* ── Row (shared by user + AI) ───────────────────────────────────────────────── */
.row {
	display: flex;
	align-items: flex-start;
	gap: 10px;
}

.user-row {
	justify-content: flex-end;
}

.ai-row {
	justify-content: flex-start;
}

/* ── Avatar ──────────────────────────────────────────────────────────────────── */
.avatar {
	flex-shrink: 0;
	margin-top: 2px;
}

/* ── User card ───────────────────────────────────────────────────────────────── */
.user-card {
	background: #3b82f6 !important;
	color: white !important;
	padding: 10px 16px;
	border-radius: 16px 16px 4px 16px !important;
	font-size: 14px;
	line-height: 1.65;
	max-width: 60%;
	word-break: break-word;
}

/* ── AI card ─────────────────────────────────────────────────────────────────── */
.ai-card {
	padding: 12px 16px;
	border-radius: 4px 16px 16px 16px !important;
	font-size: 14px;
	line-height: 1.7;
	max-width: 75%;
	word-break: break-word;
	color: #1e293b;
	background: #fff !important;
}

/* Timeline card: no extra padding, let timeline handle its own */
.timeline-card {
	padding: 12px 14px;
	max-width: 80%;
}

/* ── Thinking dots ───────────────────────────────────────────────────────────── */
.thinking-dots {
	display: flex;
	align-items: center;
	gap: 5px;
	padding: 4px 2px;
}
.dot {
	width: 7px;
	height: 7px;
	background: #94a3b8;
	border-radius: 50%;
	animation: dotBounce 1.2s infinite;
}
.dot--2 { animation-delay: 0.2s; }
.dot--3 { animation-delay: 0.4s; }
@keyframes dotBounce {
	0%, 60%, 100% { transform: translateY(0); }
	30% { transform: translateY(-5px); }
}

/* ── Markdown body inside AI card ────────────────────────────────────────────── */
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) { font-weight: 700; margin: 12px 0 5px; line-height: 1.4; }
.md-body :deep(p) { margin-bottom: 7px; }
.md-body :deep(ul), .md-body :deep(ol) { padding-left: 20px; margin-bottom: 7px; }
.md-body :deep(li) { margin-bottom: 3px; }
.md-body :deep(code) { background: #f1f5f9; padding: 2px 6px; border-radius: 4px; font-size: 12.5px; font-family: 'Fira Code', monospace; }
.md-body :deep(pre) { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; overflow-x: auto; margin: 8px 0; }
.md-body :deep(pre code) { background: none; padding: 0; }
.md-body :deep(blockquote) { border-left: 3px solid #3b82f6; padding-left: 12px; color: #64748b; margin: 6px 0; }
.md-body :deep(table) { width: 100%; border-collapse: collapse; margin: 8px 0; }
.md-body :deep(th) { background: #f1f5f9; padding: 7px 12px; border: 1px solid #e2e8f0; font-weight: 600; font-size: 13px; text-align: left; }
.md-body :deep(td) { padding: 7px 12px; border: 1px solid #e2e8f0; font-size: 13px; }
.md-body :deep(tr:nth-child(even)) { background: #f8fafc; }
.md-body :deep(a) { color: #2563eb; text-decoration: underline; }
.md-body :deep(hr) { border: none; border-top: 1px solid #e2e8f0; margin: 12px 0; }
.md-body :deep(strong) { font-weight: 700; }

/* ── Scrollbar ───────────────────────────────────────────────────────────────── */
.custom-scrollbar::-webkit-scrollbar { width: 4px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #94a3b8; }
</style>
