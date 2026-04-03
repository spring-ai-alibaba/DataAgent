<template>
	<div class="streaming-report">
		<div class="report-header">
			<v-icon color="primary" size="18" class="mr-2"
				>mdi-file-document-edit-outline</v-icon
			>
			<span>正在生成报告...</span>
			<span class="typing-indicator">
				<span class="typing-dot" />
				<span class="typing-dot typing-dot--2" />
				<span class="typing-dot typing-dot--3" />
			</span>
		</div>
		<div ref="bodyRef" class="report-body">
			<div class="markdown-body streaming" v-html="renderedHtml" />
		</div>
	</div>
</template>

<script setup lang="ts">
import { ref, watch, computed, nextTick, onBeforeUnmount } from 'vue';
import DOMPurify from 'dompurify';
import { renderMarkdownContent } from '~/utils/markdown';
import { useTypewriter } from '~/composables/useTypewriter';
import { useEchartsRenderer } from '~/composables/useEchartsRenderer';

const props = defineProps<{ content: string }>();
const bodyRef = ref<HTMLElement | null>(null);

const { displayedText, append, reset } = useTypewriter();
const { renderECharts } = useEchartsRenderer();

// Track what we've already fed to the typewriter
let lastFedLength = 0;

watch(
	() => props.content,
	(newVal, _oldVal) => {
		// If content was reset (new stream started), reset the typewriter
		if (!newVal || newVal.length < lastFedLength) {
			reset();
			lastFedLength = 0;
		}
		// Feed only the new delta to the typewriter queue
		if (newVal && newVal.length > lastFedLength) {
			const chunk = newVal.slice(lastFedLength);
			lastFedLength = newVal.length;
			append(chunk);
		}
	},
	{ immediate: true },
);

// Render markdown from the typewriter's displayed text (incremental)
// We use a throttled computed: only re-render when displayedText changes.
// This is much cheaper than re-rendering the full content on every SSE event.
const SANITIZE_OPTIONS = {
	ADD_TAGS: ['div'],
	ADD_ATTR: ['style', 'class', 'data-echarts-config'],
};

const renderedHtml = computed(() => {
	const text = displayedText.value;
	if (!text) return '';
	return DOMPurify.sanitize(
		renderMarkdownContent(text),
		SANITIZE_OPTIONS,
	) as string;
});

// After each render, try to initialize any completed echarts blocks
watch(renderedHtml, () => {
	nextTick(() => renderECharts(bodyRef.value));
});

onBeforeUnmount(() => {
	lastFedLength = 0;
});
</script>

<style scoped>
.streaming-report {
	background: white;
}

.report-header {
	display: flex;
	align-items: center;
	padding: 10px 14px;
	background: #f8fafc;
	border-bottom: 1px solid #e8edf2;
	font-size: 13.5px;
	font-weight: 600;
	color: #1e293b;
}

.typing-indicator {
	display: inline-flex;
	align-items: center;
	gap: 3px;
	margin-left: 8px;
}

.typing-dot {
	width: 4px;
	height: 4px;
	background: #3b82f6;
	border-radius: 50%;
	animation: typingBounce 1.2s infinite;
}
.typing-dot--2 {
	animation-delay: 0.2s;
}
.typing-dot--3 {
	animation-delay: 0.4s;
}
@keyframes typingBounce {
	0%,
	60%,
	100% {
		opacity: 0.3;
		transform: translateY(0);
	}
	30% {
		opacity: 1;
		transform: translateY(-2px);
	}
}

.report-body {
	padding: 16px;
	position: relative;
}

/* Blinking dot cursor — appended after last inline content via CSS ::after
 * Markdown renders block elements (p, h, li), so we target the last child.
 * The dot stays inline at the end of the last line of text.
 */
.markdown-body.streaming :deep(> :last-child::after) {
	content: '';
	display: inline-block;
	width: 6px;
	height: 6px;
	border-radius: 50%;
	background: #3b82f6;
	margin-left: 3px;
	vertical-align: middle;
	animation: cursorDotBlink 0.7s step-end infinite;
}
@keyframes cursorDotBlink {
	0%,
	100% {
		opacity: 1;
	}
	50% {
		opacity: 0;
	}
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

/* ── ECharts skeleton placeholder (while streaming) ────────────────────────── */
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
