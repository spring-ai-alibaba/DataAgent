<template>
	<div class="markdown-report">
		<!-- Header -->
		<div class="report-header">
			<div class="report-title">
				<v-icon color="primary" size="18" class="mr-2">mdi-file-document-outline</v-icon>
				<span>报告已生成</span>
				<div class="format-toggle">
					<button
						:class="['fmt-btn', { active: format === 'markdown' }]"
						@click="format = 'markdown'"
					>Markdown</button>
					<button
						:class="['fmt-btn', { active: format === 'html' }]"
						@click="format = 'html'"
					>HTML</button>
				</div>
			</div>
			<div class="report-actions">
				<button class="report-action-btn" title="下载 MD" @click="downloadMd">
					<v-icon size="14" class="mr-1">mdi-download</v-icon>MD
				</button>
				<button class="report-action-btn report-action-btn--success" title="下载 HTML" @click="downloadHtml">
					<v-icon size="14" class="mr-1">mdi-download</v-icon>HTML
				</button>
				<button class="report-action-btn report-action-btn--info" title="全屏查看" @click="store.openReportFullscreen(content)">
					<v-icon size="14">mdi-fullscreen</v-icon>
				</button>
			</div>
		</div>

		<!-- Body -->
		<div class="report-body">
			<div v-if="format === 'markdown'" class="markdown-body" v-html="renderedContent" />
			<iframe
				v-else
				:srcdoc="content"
				class="html-iframe"
				sandbox="allow-scripts allow-same-origin"
			/>
		</div>

		<!-- Fullscreen dialog -->
		<v-dialog v-model="store.showReportFullscreen" fullscreen>
			<v-card>
				<v-toolbar density="compact" color="white" border="b">
					<v-toolbar-title class="text-body-2 font-weight-bold">
						{{ store.reportFormat === 'markdown' ? 'Markdown 报告' : 'HTML 报告' }}
					</v-toolbar-title>
					<v-spacer />
					<div class="format-toggle mr-2">
						<button :class="['fmt-btn', { active: store.reportFormat === 'markdown' }]" @click="store.reportFormat = 'markdown'">Markdown</button>
						<button :class="['fmt-btn', { active: store.reportFormat === 'html' }]" @click="store.reportFormat = 'html'">HTML</button>
					</div>
					<v-btn icon variant="text" @click="store.showReportFullscreen = false">
						<v-icon>mdi-close</v-icon>
					</v-btn>
				</v-toolbar>
				<v-card-text style="height: calc(100vh - 64px); overflow-y: auto; padding: 24px">
					<div v-if="store.reportFormat === 'markdown'" class="markdown-body" v-html="renderMarkdown(store.fullscreenReportContent)" />
					<iframe
						v-else
						:srcdoc="store.fullscreenReportContent"
						style="width: 100%; height: 100%; min-height: 600px; border: none"
						sandbox="allow-scripts allow-same-origin"
					/>
				</v-card-text>
			</v-card>
		</v-dialog>
	</div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import { useChatStore } from '~/stores/chat';

const props = defineProps<{ content: string }>();
const store = useChatStore();
const format = ref<'markdown' | 'html'>('markdown');

marked.setOptions({ gfm: true, breaks: true });
function renderMarkdown(md: string): string {
	if (!md) return '';
	return DOMPurify.sanitize(marked.parse(md) as string);
}

const renderedContent = computed(() => renderMarkdown(props.content));

function downloadMd() {
	if (!props.content) return;
	const blob = new Blob([props.content], { type: 'text/markdown' });
	const url = URL.createObjectURL(blob);
	const a = document.createElement('a');
	a.href = url; a.download = `report_${Date.now()}.md`;
	document.body.appendChild(a); a.click(); document.body.removeChild(a);
	URL.revokeObjectURL(url);
}

async function downloadHtml() {
	if (!props.content) return;
	try {
		await store.downloadHtmlReport(props.content);
	} catch (e) {
		console.error('下载HTML报告失败', e);
	}
}
</script>

<style scoped>
.markdown-report {
	background: white;
}

/* ── Header ──────────────────────────────────────────────────────────────────── */
.report-header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding: 10px 14px;
	background: #f8fafc;
	border-bottom: 1px solid #e8edf2;
	flex-wrap: wrap;
	gap: 8px;
}
.report-title {
	display: flex;
	align-items: center;
	font-size: 13.5px;
	font-weight: 600;
	color: #1e293b;
	gap: 0;
}
.report-actions {
	display: flex;
	align-items: center;
	gap: 6px;
}
.report-action-btn {
	display: inline-flex;
	align-items: center;
	padding: 4px 10px;
	background: white;
	border: 1px solid #e2e8f0;
	border-radius: 6px;
	font-size: 12px;
	color: #475569;
	cursor: pointer;
	transition: background 0.1s;
}
.report-action-btn:hover { background: #f1f5f9; }
.report-action-btn--success { color: #16a34a; border-color: #bbf7d0; }
.report-action-btn--success:hover { background: #f0fdf4; }
.report-action-btn--info { color: #2563eb; border-color: #bfdbfe; }
.report-action-btn--info:hover { background: #eff6ff; }

/* ── Format toggle ───────────────────────────────────────────────────────────── */
.format-toggle {
	display: inline-flex;
	background: #f1f5f9;
	border: 1px solid #e2e8f0;
	border-radius: 6px;
	overflow: hidden;
	margin-left: 12px;
}
.fmt-btn {
	padding: 3px 10px;
	font-size: 11.5px;
	background: none;
	border: none;
	cursor: pointer;
	color: #64748b;
	transition: background 0.1s, color 0.1s;
}
.fmt-btn.active {
	background: white;
	color: #2563eb;
	font-weight: 600;
}

/* ── Body ────────────────────────────────────────────────────────────────────── */
.report-body {
	padding: 16px;
}
.html-iframe {
	width: 100%;
	min-height: 400px;
	border: none;
}

/* ── Markdown body ───────────────────────────────────────────────────────────── */
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) { font-weight: 700; margin: 14px 0 6px; color: #0f172a; }
.markdown-body :deep(h1) { font-size: 20px; }
.markdown-body :deep(h2) { font-size: 17px; }
.markdown-body :deep(h3) { font-size: 15px; }
.markdown-body :deep(p) { margin-bottom: 10px; line-height: 1.75; color: #374151; font-size: 14px; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 22px; margin-bottom: 10px; }
.markdown-body :deep(li) { line-height: 1.7; font-size: 14px; color: #374151; }
.markdown-body :deep(code) { background: #f1f5f9; padding: 2px 5px; border-radius: 4px; font-size: 12.5px; }
.markdown-body :deep(pre) { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 14px; overflow-x: auto; margin: 10px 0; }
.markdown-body :deep(pre code) { background: none; padding: 0; font-size: 13px; }
.markdown-body :deep(table) { width: 100%; border-collapse: collapse; margin: 10px 0; }
.markdown-body :deep(th) { background: #f1f5f9; padding: 8px 12px; border: 1px solid #e2e8f0; font-weight: 600; font-size: 13px; text-align: left; }
.markdown-body :deep(td) { padding: 8px 12px; border: 1px solid #e8edf2; font-size: 13px; }
.markdown-body :deep(tr:nth-child(even) td) { background: #f8fafc; }
.markdown-body :deep(blockquote) { border-left: 3px solid #3b82f6; padding: 8px 14px; margin-left: 0; background: #eff6ff; border-radius: 0 6px 6px 0; color: #374151; }
</style>
