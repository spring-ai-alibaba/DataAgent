<template>
	<div class="workflow-timeline">
		<!-- Timeline steps (vertical) -->
		<div class="timeline-steps">
			<div
				v-for="(step, idx) in timelineSteps"
				:key="step.nodeName"
				class="timeline-step"
				:class="{
					'is-active': step.status === 'active',
					'is-done': step.status === 'done',
				}"
			>
				<!-- Left: icon + connector -->
				<div class="step-left">
					<div class="step-icon">
						<v-icon v-if="step.status === 'done'" size="13" color="white">mdi-check</v-icon>
						<v-icon v-else-if="step.status === 'active'" size="13" color="white">mdi-dots-horizontal</v-icon>
						<v-icon v-else size="13">{{ step.icon }}</v-icon>
					</div>
					<div v-if="idx < timelineSteps.length - 1" class="connector-line" :class="{ 'is-filled': step.status === 'done' }" />
				</div>

				<!-- Right: label + content -->
				<div class="step-right">
					<div class="step-label">
						<span class="step-name">{{ step.label }}</span>
						<span v-if="step.status === 'active'" class="step-badge active">
							<span class="badge-dot" />进行中
						</span>
						<span v-else-if="step.status === 'done'" class="step-badge done">完成</span>
					</div>

					<!-- Active node: show full streaming content -->
					<div v-if="step.status === 'active' && activeBlock" class="step-detail">
						<!-- Result Set -->
						<ChatResultSet
							v-if="activeBlock[0]?.textType === 'RESULT_SET' && activeBlock[0]?.text"
							:data="safeParseJson(activeBlock[0].text)"
							:page-size="10"
						/>
						<!-- Markdown Report -->
						<div
							v-else-if="activeBlock[0]?.nodeName === 'ReportGeneratorNode' && activeBlock[0]?.textType === 'MARK_DOWN'"
							class="md-body"
							v-html="renderMarkdown(activeBlock[0].text || '')"
						/>
						<!-- Code -->
						<div
							v-else-if="['SQL', 'PYTHON', 'JSON'].includes(activeBlock[0]?.textType ?? '')"
							v-html="renderCode(activeBlock)"
						/>
						<!-- Plain text -->
						<div v-else class="text-body" v-html="renderText(activeBlock)" />
					</div>

					<!-- Done node: show short preview -->
					<div v-else-if="step.status === 'done' && step.previewText" class="step-preview">
						<span v-if="step.textType === 'SQL'" class="preview-tag sql">SQL</span>
						<span v-else-if="step.textType === 'PYTHON'" class="preview-tag python">Python</span>
						<span v-else-if="step.textType === 'JSON'" class="preview-tag json">JSON</span>
						<span class="preview-text">{{ step.previewText }}</span>
					</div>
				</div>
			</div>
		</div>
	</div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import hljs from 'highlight.js';
import type { GraphNodeResponse } from '~/services/graph/index';
import type { ResultData } from '~/services/resultSet/index';
import ChatResultSet from './ChatResultSet.vue';

const props = defineProps<{
	nodeBlocks: GraphNodeResponse[][];
}>();

interface NodeDef {
	nodeName: string;
	label: string;
	icon: string;
}

const NODE_DEFS: NodeDef[] = [
	{ nodeName: 'IntentRecognitionNode', label: '意图识别', icon: 'mdi-magnify' },
	{ nodeName: 'QueryEnhanceNode', label: '查询增强', icon: 'mdi-text-search' },
	{ nodeName: 'SchemaRecallNode', label: 'Schema 召回', icon: 'mdi-database-search' },
	{ nodeName: 'FeasibilityAssessmentNode', label: '可行性评估', icon: 'mdi-check-circle-outline' },
	{ nodeName: 'EvidenceRecallNode', label: '证据召回', icon: 'mdi-file-search-outline' },
	{ nodeName: 'TableRelationNode', label: '表关系分析', icon: 'mdi-table-network' },
	{ nodeName: 'PlannerNode', label: '制定计划', icon: 'mdi-clipboard-list-outline' },
	{ nodeName: 'HumanFeedbackNode', label: '人工反馈', icon: 'mdi-account-check-outline' },
	{ nodeName: 'PlanExecutorNode', label: '执行计划', icon: 'mdi-play-circle-outline' },
	{ nodeName: 'SqlGenerateNode', label: 'SQL 生成', icon: 'mdi-code-braces' },
	{ nodeName: 'SqlExecuteNode', label: 'SQL 执行', icon: 'mdi-database-arrow-right' },
	{ nodeName: 'PythonGenerateNode', label: 'Python 生成', icon: 'mdi-language-python' },
	{ nodeName: 'PythonAnalyzeNode', label: 'Python 分析', icon: 'mdi-chart-line' },
	{ nodeName: 'PythonExecuteNode', label: 'Python 执行', icon: 'mdi-play-outline' },
	{ nodeName: 'ReportGeneratorNode', label: '报告生成', icon: 'mdi-file-chart-outline' },
];

interface TimelineStep extends NodeDef {
	status: 'pending' | 'active' | 'done';
	previewText: string;
	textType: string;
}

const timelineSteps = computed<TimelineStep[]>(() => {
	const appearedNodes = props.nodeBlocks.map(b => b[0]?.nodeName).filter(Boolean) as string[];
	const orderedDefs: NodeDef[] = [];
	for (const def of NODE_DEFS) {
		if (appearedNodes.includes(def.nodeName)) orderedDefs.push(def);
	}
	for (const nodeName of appearedNodes) {
		if (!orderedDefs.find(d => d.nodeName === nodeName)) {
			orderedDefs.push({ nodeName, label: nodeName, icon: 'mdi-lightning-bolt' });
		}
	}
	if (orderedDefs.length === 0) return [];
	const lastIdx = orderedDefs.length - 1;
	return orderedDefs.map((def, idx) => {
		const block = props.nodeBlocks.find(b => b[0]?.nodeName === def.nodeName);
		const status: 'pending' | 'active' | 'done' = idx < lastIdx ? 'done' : 'active';
		return {
			...def,
			status,
			previewText: getPreviewText(block || []),
			textType: block?.[0]?.textType || '',
		};
	});
});

const activeBlock = computed<GraphNodeResponse[] | null>(() => {
	if (!props.nodeBlocks.length) return null;
	return props.nodeBlocks[props.nodeBlocks.length - 1] || null;
});

function getPreviewText(block: GraphNodeResponse[]): string {
	const text = block.map(n => n.text).join('').trim();
	return text.length > 80 ? text.slice(0, 80) + '...' : text;
}

function safeParseJson(content: string): ResultData | null {
	try { return JSON.parse(content); } catch { return null; }
}

function escapeHtml(text: string): string {
	const div = document.createElement('div');
	div.textContent = text;
	return div.innerHTML;
}

function renderMarkdown(content: string): string {
	if (!content) return '';
	return DOMPurify.sanitize(marked.parse(content) as string);
}

function renderCode(block: GraphNodeResponse[]): string {
	const lang = (block[0]?.textType || 'text').toLowerCase();
	const code = block.map(n => n.text).join('');
	try {
		const h = hljs.highlight(code, { language: lang });
		return `<pre class="tl-code"><code class="hljs ${lang}">${h.value}</code></pre>`;
	} catch {
		return `<pre class="tl-code"><code>${escapeHtml(code)}</code></pre>`;
	}
}

function renderText(block: GraphNodeResponse[]): string {
	return block.map(n => escapeHtml(n.text).replace(/\n/g, '<br>')).join('');
}
</script>

<style scoped>
.workflow-timeline {
	width: 100%;
}

/* ── Steps ───────────────────────────────────────────────────────────────────── */
.timeline-steps {
	display: flex;
	flex-direction: column;
	gap: 0;
	padding: 4px 0;
}

.timeline-step {
	display: flex;
	flex-direction: row;
	align-items: flex-start;
	gap: 12px;
}

/* ── Left column: icon + vertical line ───────────────────────────────────────── */
.step-left {
	display: flex;
	flex-direction: column;
	align-items: center;
	flex-shrink: 0;
	width: 28px;
}

.step-icon {
	width: 28px;
	height: 28px;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	border: 2px solid #e2e8f0;
	background: #f8fafc;
	flex-shrink: 0;
	transition: all 0.25s;
	z-index: 1;
}

.is-done .step-icon {
	background: #3b82f6;
	border-color: #3b82f6;
}

.is-active .step-icon {
	background: #2563eb;
	border-color: #2563eb;
	animation: activePulse 1.5s infinite;
}

@keyframes activePulse {
	0%, 100% { box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15); }
	50% { box-shadow: 0 0 0 7px rgba(59, 130, 246, 0.08); }
}

.connector-line {
	width: 2px;
	flex: 1;
	min-height: 16px;
	background: #e2e8f0;
	margin: 2px 0;
	transition: background 0.3s;
}

.connector-line.is-filled {
	background: #3b82f6;
}

/* ── Right column ────────────────────────────────────────────────────────────── */
.step-right {
	flex: 1;
	padding-bottom: 12px;
	min-width: 0;
}

.step-label {
	display: flex;
	align-items: center;
	gap: 6px;
	height: 28px;
}

.step-name-en {
	font-size: 13px;
	font-weight: 500;
	color: #475569;
}

.step-name-sep {
	font-size: 13px;
	color: #94a3b8;
	margin: 0 1px;
}

.step-name-zh {
	font-size: 13px;
	font-weight: 600;
	color: #2563eb;
	background: #dbeafe;
	padding: 1px 6px;
	border-radius: 4px;
}

.is-done .step-name-en { color: #64748b; }
.is-done .step-name-zh { color: #2563eb; }

.is-active .step-name-en { color: #1e293b; font-weight: 600; }
.is-active .step-name-zh { color: #1d4ed8; background: #bfdbfe; }

/* ── Badge ───────────────────────────────────────────────────────────────────── */
.step-badge {
	display: inline-flex;
	align-items: center;
	gap: 4px;
	font-size: 10.5px;
	padding: 2px 7px;
	border-radius: 10px;
}

.step-badge.active {
	background: #dbeafe;
	color: #1d4ed8;
}

.step-badge.done {
	background: #dcfce7;
	color: #15803d;
}

.badge-dot {
	width: 5px;
	height: 5px;
	background: #2563eb;
	border-radius: 50%;
	animation: dotBlink 1s infinite;
}

@keyframes dotBlink {
	0%, 100% { opacity: 1; }
	50% { opacity: 0.3; }
}

/* ── Active detail ───────────────────────────────────────────────────────────── */
.step-detail {
	margin-top: 6px;
	font-size: 13px;
	line-height: 1.65;
	color: #1e293b;
}

.text-body {
	white-space: pre-wrap;
	word-break: break-word;
}

.italic-gray {
	color: #94a3b8;
	font-style: italic;
}

:deep(.tl-code) {
	background: #f8fafc;
	border: 1px solid #e2e8f0;
	border-radius: 8px;
	padding: 10px 12px;
	font-size: 12.5px;
	overflow-x: auto;
	white-space: pre;
	margin: 4px 0 0;
}

/* ── Markdown inside active detail ──────────────────────────────────────────── */
.md-body :deep(h1), .md-body :deep(h2), .md-body :deep(h3) { font-weight: 700; margin: 10px 0 4px; }
.md-body :deep(p) { margin-bottom: 6px; }
.md-body :deep(ul), .md-body :deep(ol) { padding-left: 18px; margin-bottom: 6px; }
.md-body :deep(code) { background: #f1f5f9; padding: 1px 5px; border-radius: 4px; font-size: 12px; }
.md-body :deep(pre) { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 10px; overflow-x: auto; margin: 6px 0; }
.md-body :deep(pre code) { background: none; padding: 0; }
.md-body :deep(table) { width: 100%; border-collapse: collapse; margin: 6px 0; }
.md-body :deep(th) { background: #f1f5f9; padding: 6px 10px; border: 1px solid #e2e8f0; font-weight: 600; font-size: 12px; }
.md-body :deep(td) { padding: 6px 10px; border: 1px solid #e2e8f0; font-size: 12px; }

/* ── Done preview ────────────────────────────────────────────────────────────── */
.step-preview {
	display: flex;
	align-items: center;
	gap: 5px;
	margin-top: 2px;
}

.preview-tag {
	font-size: 10px;
	padding: 1px 6px;
	border-radius: 4px;
	font-weight: 600;
	flex-shrink: 0;
}

.preview-tag.sql { background: #fef3c7; color: #d97706; }
.preview-tag.python { background: #e0f2fe; color: #0284c7; }
.preview-tag.json { background: #f3e8ff; color: #9333ea; }

.preview-text {
	font-size: 12px;
	color: #94a3b8;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}
</style>
