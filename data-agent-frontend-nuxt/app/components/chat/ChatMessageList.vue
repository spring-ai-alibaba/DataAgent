<template>
	<div ref="listRef" class="message-list custom-scrollbar">
		<!-- Welcome state when no session -->
		<ChatWelcome v-if="!store.currentSession" />

		<!-- Messages -->
		<template v-else>
			<div class="messages-inner">
				<div v-for="message in store.currentMessages" :key="message.id" class="message-wrapper">
					<!-- HTML message (raw node output saved as HTML) -->
					<div v-if="message.messageType === 'html'" class="ai-node-message" v-html="message.content" />

					<!-- Result Set message -->
					<div v-else-if="message.messageType === 'result-set'" class="result-set-message">
						<ChatResultSet :data="safeParseJson(message.content)" :page-size="store.requestOptions.pageSize" />
					</div>

					<!-- Markdown Report message -->
					<div v-else-if="message.messageType === 'markdown-report'" class="report-message">
						<ChatMarkdownReport :content="message.content" />
					</div>

					<!-- User text message -->
					<div v-else-if="message.role === 'user'" class="user-message-row">
						<div class="user-bubble">
							<span v-html="escapeHtml(message.content).replace(/\n/g, '<br>')" />
						</div>
						<div class="user-avatar">
							<v-avatar color="grey-darken-1" size="30" rounded="lg">
								<v-icon size="16" color="white">mdi-account</v-icon>
							</v-avatar>
						</div>
					</div>

					<!-- AI text message -->
					<div v-else class="ai-message-row">
						<div class="ai-avatar">
							<v-avatar color="blue-darken-3" size="30" rounded="lg">
								<v-icon size="16" color="white">mdi-robot</v-icon>
							</v-avatar>
						</div>
						<div class="ai-bubble" v-html="message.content" />
					</div>
				</div>

				<!-- Streaming response area -->
				<div v-if="store.isStreaming" class="streaming-area">
					<div class="streaming-header">
						<div class="streaming-dot" />
						<div class="streaming-dot streaming-dot--2" />
						<div class="streaming-dot streaming-dot--3" />
						<span class="streaming-label">智能体正在思考中...</span>
					</div>
					<div class="streaming-blocks">
						<template v-for="(nodeBlock, idx) in store.nodeBlocks" :key="idx">
							<!-- Markdown Report Node -->
							<div
								v-if="nodeBlock.length > 0 && nodeBlock[0].nodeName === 'ReportGeneratorNode' && nodeBlock[0].textType === 'MARK_DOWN'"
								class="node-block"
							>
								<div class="node-block-header">
									<v-icon size="13" class="mr-1">mdi-file-document-outline</v-icon>
									{{ nodeBlock[0].nodeName }}
								</div>
								<div class="node-block-body markdown-body" v-html="renderMarkdown(getMarkdownFromNode(nodeBlock))" />
							</div>

							<!-- Result Set Node -->
							<div
								v-else-if="nodeBlock.length > 0 && nodeBlock[0].textType === 'RESULT_SET'"
								class="node-block"
							>
								<div class="node-block-header">
									<v-icon size="13" class="mr-1">mdi-table</v-icon>
									{{ nodeBlock[0].nodeName }}
								</div>
								<div class="node-block-body">
									<ChatResultSet
										v-if="nodeBlock[0].text"
										:data="safeParseJson(nodeBlock[0].text)"
										:page-size="store.requestOptions.pageSize"
									/>
								</div>
							</div>

							<!-- Other Nodes -->
							<div v-else class="node-block" v-html="generateNodeHtml(nodeBlock)" />
						</template>
					</div>
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
import type { GraphNodeResponse } from '~/services/graph/index';
import { TextType } from '~/services/graph/index';
import type { ResultData } from '~/services/resultSet/index';
import ChatWelcome from './ChatWelcome.vue';
import ChatResultSet from './ChatResultSet.vue';
import ChatMarkdownReport from './ChatMarkdownReport.vue';

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

function getMarkdownFromNode(node: GraphNodeResponse[]): string {
	if (!node?.length) return '';
	const first = node[0];
	if (first.nodeName === 'ReportGeneratorNode' && first.textType === 'MARK_DOWN') {
		if (store.currentSession?.id) {
			// node text is updated in real-time in the store
			return first.text || '';
		}
	}
	return node.filter(n => n.textType === 'MARK_DOWN').map(n => n.text).join('');
}

function formatNodeContent(node: GraphNodeResponse[]): string {
	let content = '';
	for (let idx = 0; idx < node.length; idx++) {
		const item = node[idx];
		if (!item) continue;
		if (item.textType === TextType.HTML) {
			content += item.text;
		} else if (item.textType === TextType.TEXT) {
			content += escapeHtml(item.text).replace(/\n/g, '<br>');
		} else if (item.textType === TextType.JSON || item.textType === TextType.PYTHON || item.textType === TextType.SQL) {
			let pre = '';
			let p = idx;
			for (; p < node.length; p++) {
				if (node[p]?.textType !== item.textType) break;
				pre += node[p]?.text || '';
			}
			try {
				const lang = item.textType.toLowerCase();
				const highlighted = hljs.highlight(pre, { language: lang });
				content += `<pre class="code-block"><div class="code-header"><span class="code-lang">${lang}</span><button class="code-copy-btn" onclick="(function(btn){const t=btn.previousElementSibling.nextElementSibling?.textContent||'';const o=btn.textContent;navigator.clipboard.writeText(t).then(()=>{btn.textContent='已复制!';setTimeout(()=>btn.textContent=o,2000)}).catch(()=>{btn.textContent='失败';setTimeout(()=>btn.textContent=o,2000)})})(this)">复制</button></div><span hidden>${escapeHtml(pre)}</span><code class="hljs ${lang}">${highlighted.value}</code></pre>`;
			} catch {
				content += `<pre class="code-block"><code>${escapeHtml(pre)}</code></pre>`;
			}
			idx = p < node.length ? p - 1 : node.length;
		} else if (item.textType === TextType.MARK_DOWN) {
			let md = '';
			let p = idx;
			for (; p < node.length; p++) {
				if (node[p]?.textType !== TextType.MARK_DOWN) break;
				md += node[p]?.text || '';
			}
			content += `<div class="markdown-body">${renderMarkdown(md)}</div>`;
			idx = p < node.length ? p - 1 : node.length;
		} else if (item.textType === TextType.RESULT_SET) {
			continue; // handled by component
		} else {
			content += escapeHtml(item.text);
		}
	}
	return content;
}

function generateNodeHtml(node: GraphNodeResponse[]): string {
	if (!node?.length) return '';
	const content = formatNodeContent(node);
	return `<div class="node-block"><div class="node-block-header">${node[0]?.nodeName || '节点'}</div><div class="node-block-body">${content}</div></div>`;
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
	gap: 16px;
	max-width: 860px;
	width: 100%;
	margin: 0 auto;
}

/* ── User message ─────────────────────────────────────────────────────────────── */
.user-message-row {
	display: flex;
	align-items: flex-end;
	justify-content: flex-end;
	gap: 10px;
}
.user-bubble {
	background: #3b82f6;
	color: white;
	padding: 10px 16px;
	border-radius: 18px 18px 4px 18px;
	font-size: 14px;
	line-height: 1.6;
	max-width: 68%;
	word-break: break-word;
}
.user-avatar {
	flex-shrink: 0;
	align-self: flex-start;
	margin-top: 2px;
}

/* ── AI message ──────────────────────────────────────────────────────────────── */
.ai-message-row {
	display: flex;
	align-items: flex-start;
	gap: 10px;
}
.ai-avatar {
	flex-shrink: 0;
	margin-top: 2px;
}
.ai-bubble {
	background: #f8fafc;
	border: 1px solid #e2e8f0;
	padding: 10px 16px;
	border-radius: 18px 18px 18px 4px;
	font-size: 14px;
	line-height: 1.6;
	max-width: 78%;
	word-break: break-word;
	color: #1e293b;
}

/* ── AI node message (raw HTML blocks) ───────────────────────────────────────── */
.ai-node-message {
	border: 1px solid #e8edf2;
	border-radius: 10px;
	overflow: hidden;
	background: white;
	font-size: 14px;
}

/* ── Report / result set wrappers ────────────────────────────────────────────── */
.report-message, .result-set-message {
	border: 1px solid #e8edf2;
	border-radius: 10px;
	overflow: hidden;
	background: white;
}

/* ── Streaming ───────────────────────────────────────────────────────────────── */
.streaming-area {
	margin-top: 4px;
}
.streaming-header {
	display: flex;
	align-items: center;
	gap: 6px;
	padding: 10px 14px;
	background: #f0f7ff;
	border-radius: 10px;
	margin-bottom: 8px;
}
.streaming-dot {
	width: 6px;
	height: 6px;
	background: #3b82f6;
	border-radius: 50%;
	animation: bounce 1.2s infinite;
}
.streaming-dot--2 { animation-delay: 0.2s; }
.streaming-dot--3 { animation-delay: 0.4s; }
@keyframes bounce {
	0%, 60%, 100% { transform: translateY(0); }
	30% { transform: translateY(-5px); }
}
.streaming-label {
	font-size: 12.5px;
	color: #3b82f6;
	font-weight: 500;
	margin-left: 2px;
}
.streaming-blocks {
	display: flex;
	flex-direction: column;
	gap: 8px;
}

/* ── Node blocks ─────────────────────────────────────────────────────────────── */
.node-block {
	border: 1px solid #e8edf2;
	border-radius: 10px;
	overflow: hidden;
	background: white;
}
:deep(.node-block) {
	border: 1px solid #e8edf2;
	border-radius: 10px;
	overflow: hidden;
	background: white;
	margin-bottom: 8px;
}
.node-block-header, :deep(.node-block-header) {
	background: #f0f7ff;
	padding: 7px 14px;
	font-size: 12.5px;
	font-weight: 600;
	color: #2563eb;
	border-bottom: 1px solid #e8edf2;
	display: flex;
	align-items: center;
}
.node-block-body, :deep(.node-block-body) {
	padding: 12px 14px;
	font-size: 13.5px;
	line-height: 1.7;
	white-space: pre-wrap;
	word-break: break-word;
	color: #1e293b;
}

/* ── Code blocks inside node body ────────────────────────────────────────────── */
:deep(.code-block) {
	background: #f8fafc;
	border: 1px solid #e2e8f0;
	border-radius: 8px;
	overflow: hidden;
	margin: 8px 0;
	white-space: pre;
}
:deep(.code-header) {
	display: flex;
	justify-content: space-between;
	align-items: center;
	background: #f1f5f9;
	padding: 6px 12px;
	border-bottom: 1px solid #e2e8f0;
}
:deep(.code-lang) {
	font-size: 11px;
	color: #64748b;
	font-weight: 600;
	text-transform: uppercase;
}
:deep(.code-copy-btn) {
	font-size: 11px;
	color: #64748b;
	background: none;
	border: 1px solid #e2e8f0;
	border-radius: 4px;
	padding: 2px 8px;
	cursor: pointer;
}
:deep(.code-copy-btn:hover) {
	background: #e2e8f0;
}
:deep(.code-block code) {
	display: block;
	padding: 12px;
	font-size: 13px;
	overflow-x: auto;
}

/* ── Markdown body ───────────────────────────────────────────────────────────── */
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) { font-weight: 700; margin: 12px 0 6px; }
.markdown-body :deep(p) { margin-bottom: 8px; line-height: 1.7; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin-bottom: 8px; }
.markdown-body :deep(code) { background: #f1f5f9; padding: 1px 5px; border-radius: 4px; font-size: 12.5px; }
.markdown-body :deep(pre) { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 12px; overflow-x: auto; margin: 8px 0; }
.markdown-body :deep(pre code) { background: none; padding: 0; }
.markdown-body :deep(table) { width: 100%; border-collapse: collapse; margin: 8px 0; }
.markdown-body :deep(th) { background: #f1f5f9; padding: 7px 12px; border: 1px solid #e2e8f0; font-weight: 600; font-size: 12.5px; }
.markdown-body :deep(td) { padding: 7px 12px; border: 1px solid #e2e8f0; font-size: 12.5px; }

/* ── Scrollbar ───────────────────────────────────────────────────────────────── */
.custom-scrollbar::-webkit-scrollbar { width: 4px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #94a3b8; }
</style>
