<template>
	<div class="markdown-report">
		<!-- Header -->
		<div class="report-header">
			<div class="report-title">
				<v-icon color="primary" size="18" class="mr-2"
					>mdi-file-document-outline</v-icon
				>
				<span>报告已生成</span>
				<v-btn-toggle
					v-model="format"
					mandatory
					density="compact"
					class="format-toggle ml-3"
				>
					<v-btn value="markdown" size="x-small" variant="text" class="fmt-btn"
						>Markdown</v-btn
					>
					<v-btn value="html" size="x-small" variant="text" class="fmt-btn"
						>HTML</v-btn
					>
				</v-btn-toggle>
			</div>
			<div class="report-actions">
				<v-btn
					size="x-small"
					variant="outlined"
					prepend-icon="mdi-download"
					title="下载 MD"
					@click="downloadMd"
				>
					MD
				</v-btn>
				<v-btn
					size="x-small"
					variant="outlined"
					color="success"
					prepend-icon="mdi-download"
					title="下载 HTML"
					@click="downloadHtml"
				>
					HTML
				</v-btn>
				<v-btn
					size="x-small"
					variant="outlined"
					color="primary"
					icon="mdi-fullscreen"
					title="全屏查看"
					@click="store.openReportFullscreen(content)"
				/>
			</div>
		</div>

		<!-- Body -->
		<div ref="reportBodyRef" class="report-body">
			<div
				v-if="format === 'markdown'"
				class="markdown-body"
				v-html="renderedContent"
			/>
			<iframe
				v-else
				ref="htmlIframeRef"
				class="html-iframe"
				sandbox="allow-scripts"
				title="HTML报告预览"
			/>
		</div>

		<!-- Fullscreen dialog -->
		<v-dialog v-model="store.showReportFullscreen" fullscreen>
			<v-card>
				<v-toolbar density="compact" color="white" border="b">
					<v-toolbar-title class="text-body-2 font-weight-bold">
						{{
							store.reportFormat === 'markdown' ? 'Markdown 报告' : 'HTML 报告'
						}}
					</v-toolbar-title>
					<v-spacer />
					<v-btn-toggle
						v-model="store.reportFormat"
						mandatory
						density="compact"
						class="mr-2"
					>
						<v-btn
							value="markdown"
							size="x-small"
							variant="text"
							class="fmt-btn"
							>Markdown</v-btn
						>
						<v-btn value="html" size="x-small" variant="text" class="fmt-btn"
							>HTML</v-btn
						>
					</v-btn-toggle>
					<v-btn
						icon
						variant="text"
						@click="store.showReportFullscreen = false"
					>
						<v-icon>mdi-close</v-icon>
					</v-btn>
				</v-toolbar>
				<v-card-text
					style="height: calc(100vh - 64px); overflow-y: auto; padding: 24px"
				>
					<div
						v-if="store.reportFormat === 'markdown'"
						class="markdown-body"
						v-html="renderMarkdown(store.fullscreenReportContent)"
					/>
					<iframe
						v-else
						ref="fullscreenIframeRef"
						style="width: 100%; height: 100%; min-height: 600px; border: none"
						sandbox="allow-scripts"
						title="HTML报告预览"
					/>
				</v-card-text>
			</v-card>
		</v-dialog>
	</div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';
import DOMPurify from 'dompurify';
import { renderMarkdownContent } from '~/utils/markdown';
import { buildReportHtml } from '~/utils/report-html-template';
import { useEchartsRenderer } from '~/composables/useEchartsRenderer';
import { useChatStore } from '~/stores/chat';

const props = defineProps<{ content: string }>();
const store = useChatStore();
const format = ref<'markdown' | 'html'>('markdown');
const reportBodyRef = ref<HTMLElement | null>(null);
const htmlIframeRef = ref<HTMLIFrameElement | null>(null);
const fullscreenIframeRef = ref<HTMLIFrameElement | null>(null);
const { renderECharts } = useEchartsRenderer();

function renderMarkdown(md: string): string {
	if (!md) return '';
	return DOMPurify.sanitize(renderMarkdownContent(md), {
		ADD_TAGS: ['div'],
		ADD_ATTR: ['style', 'class', 'data-echarts-config'],
	});
}

function loadHtmlToIframe(
	iframe: HTMLIFrameElement | null,
	markdownContent: string,
) {
	if (!iframe) return;
	if (!markdownContent) {
		iframe.srcdoc =
			'<html><body style="padding:20px;color:#666;">暂无报告内容</body></html>';
		return;
	}
	const html = buildReportHtml(markdownContent);
	const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
	const url = URL.createObjectURL(blob);
	const onLoad = () => {
		URL.revokeObjectURL(url);
		iframe.removeEventListener('load', onLoad);
	};
	iframe.addEventListener('load', onLoad);
	iframe.src = url;
}

const renderedContent = computed(() => renderMarkdown(props.content));

watch(
	renderedContent,
	() => {
		nextTick(() => renderECharts(reportBodyRef.value));
	},
	{ immediate: true },
);

watch(format, (val) => {
	if (val === 'html') {
		nextTick(() => loadHtmlToIframe(htmlIframeRef.value, props.content));
	}
});

watch(
	() => store.reportFormat,
	(val) => {
		if (val === 'html') {
			nextTick(() =>
				loadHtmlToIframe(
					fullscreenIframeRef.value,
					store.fullscreenReportContent,
				),
			);
		}
	},
);

function downloadMd() {
	if (!props.content) return;
	const blob = new Blob([props.content], { type: 'text/markdown' });
	const url = URL.createObjectURL(blob);
	const a = document.createElement('a');
	a.href = url;
	a.download = `report_${Date.now()}.md`;
	document.body.appendChild(a);
	a.click();
	document.body.removeChild(a);
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

.report-actions :deep(.v-btn) {
	text-transform: none !important;
	letter-spacing: 0 !important;
}

/* ── Format toggle ───────────────────────────────────────────────────────────── */
.format-toggle {
	border: 1px solid #e2e8f0;
	border-radius: 6px;
	overflow: hidden;
}

.fmt-btn {
	text-transform: none !important;
	letter-spacing: 0 !important;
	font-size: 11.5px !important;
}

/* ── Body ────────────────────────────────────────────────────────────────────── */
.report-body {
	padding: 16px;
}
.html-iframe {
	display: block;
	width: 100%;
	min-height: 600px;
	border: none;
}

/* ── Markdown body ───────────────────────────────────────────────────────────── */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
	font-weight: 700;
	margin: 14px 0 6px;
	color: #0f172a;
}
.markdown-body :deep(h1) {
	font-size: 20px;
}
.markdown-body :deep(h2) {
	font-size: 17px;
}
.markdown-body :deep(h3) {
	font-size: 15px;
}
.markdown-body :deep(p) {
	margin-bottom: 10px;
	line-height: 1.75;
	color: #374151;
	font-size: 14px;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
	padding-left: 22px;
	margin-bottom: 10px;
}
.markdown-body :deep(li) {
	line-height: 1.7;
	font-size: 14px;
	color: #374151;
}
.markdown-body :deep(code:not(pre code)) {
	background: #f6f8fa;
	border: 1px solid #e1e4e8;
	padding: 2px 5px;
	border-radius: 3px;
	font-size: 12.5px;
	color: #e83e8c;
}
.markdown-body :deep(table) {
	width: 100%;
	border-collapse: collapse;
	margin: 10px 0;
	display: block;
	overflow-x: auto;
}
.markdown-body :deep(thead) {
	display: table-header-group;
}
.markdown-body :deep(tbody) {
	display: table-row-group;
}
.markdown-body :deep(tr) {
	display: table-row;
	border-top: 1px solid #c6cbd1;
}
.markdown-body :deep(th) {
	display: table-cell;
	background: #f1f5f9;
	padding: 8px 12px;
	border: 1px solid #e2e8f0;
	font-weight: 600;
	font-size: 13px;
	text-align: left;
}
.markdown-body :deep(td) {
	display: table-cell;
	padding: 8px 12px;
	border: 1px solid #e8edf2;
	font-size: 13px;
}
.markdown-body :deep(tr:nth-child(even) td) {
	background: #f8fafc;
}
.markdown-body :deep(blockquote) {
	border-left: 3px solid #3b82f6;
	padding: 8px 14px;
	margin-left: 0;
	background: #eff6ff;
	border-radius: 0 6px 6px 0;
	color: #374151;
}

/* ── Code block with header ─────────────────────────────────────────────────── */
.markdown-body :deep(.code-block-wrapper) {
	margin: 10px 0;
	border: 1px solid #e1e4e8;
	border-radius: 6px;
	overflow: auto;
	background: #f6f8fa;
}
.markdown-body :deep(.code-block-header) {
	display: flex;
	justify-content: space-between;
	align-items: center;
	background: #f6f8fa;
	padding: 6px 10px;
	border-bottom: 1px solid #e1e4e8;
	font-size: 11px;
}
.markdown-body :deep(.code-language) {
	color: #6a737d;
	font-weight: 600;
	font-family: 'Monaco', 'Menlo', monospace;
	font-size: 10px;
	text-transform: uppercase;
}
.markdown-body :deep(.code-copy-button) {
	background: transparent;
	border: 1px solid #d1d5da;
	padding: 3px 10px;
	border-radius: 4px;
	font-size: 10px;
	cursor: pointer;
	transition: all 0.2s;
	color: #24292e;
}
.markdown-body :deep(.code-copy-button:hover) {
	background: #f3f4f6;
	border-color: #c6cbd1;
}
.markdown-body :deep(.code-copy-button.copied) {
	background: #28a745;
	border-color: #28a745;
	color: white;
}
.markdown-body :deep(pre.hljs) {
	margin: 0;
	padding: 10px;
	overflow-x: auto;
	overflow-y: hidden;
	background: #f6f8fa;
	font-size: 12px;
	line-height: 1.4;
	white-space: pre;
}
.markdown-body :deep(pre.hljs code) {
	display: block;
	padding: 0;
	margin: 0;
	background: transparent;
	border: none;
	font-family: 'Monaco', 'Menlo', monospace;
	color: inherit;
	white-space: pre;
	min-width: max-content;
}

/* ── ECharts containers ─────────────────────────────────────────────────────── */
:deep(.md-echarts) {
	margin: 10px 0;
	border-radius: 6px;
}

/* ── ECharts skeleton placeholder ──────────────────────────────────────────── */
:deep(.md-echarts-skeleton) {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 10px;
	margin: 10px 0;
	height: 120px;
	border-radius: 8px;
	border: 1px dashed #cbd5e1;
	background: linear-gradient(90deg, #f8fafc 25%, #f1f5f9 50%, #f8fafc 75%);
	background-size: 200% 100%;
	animation: skeletonShimmer 1.6s ease-in-out infinite;
	color: #94a3b8;
	font-size: 13px;
}
:deep(.md-echarts-skeleton-icon) {
	font-size: 22px;
	animation: spinPulse 1.6s ease-in-out infinite;
}
:deep(.md-echarts-skeleton-text) {
	font-weight: 500;
	letter-spacing: 0.3px;
}
@keyframes skeletonShimmer {
	0% {
		background-position: 200% 0;
	}
	100% {
		background-position: -200% 0;
	}
}
@keyframes spinPulse {
	0%,
	100% {
		opacity: 1;
		transform: scale(1);
	}
	50% {
		opacity: 0.5;
		transform: scale(0.9);
	}
}
</style>
