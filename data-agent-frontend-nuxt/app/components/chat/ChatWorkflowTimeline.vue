<template>
	<div ref="timelineRef" class="workflow-timeline">
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
					<div class="step-label" @click="toggleStep(step.nodeName)" style="cursor: pointer; display: flex; align-items: center; width: 100%;">
						<span class="step-name">{{ step.label }}</span>
						<span v-if="step.status === 'active'" class="step-badge active">
							<span class="badge-dot" />进行中
						</span>
						<span v-else-if="step.status === 'done'" class="step-badge done">完成</span>
						<v-icon size="16" color="#cbd5e1" class="ml-auto">
							{{ step.expanded ? 'mdi-chevron-up' : 'mdi-chevron-down' }}
						</v-icon>
					</div>

					<!-- Content: show if expanded -->
					<div v-show="step.expanded" class="step-detail" :class="{ 'is-completed-step': step.status === 'done' || completed }">
						<!-- Result Set -->
						<ChatResultSet
							v-if="step.block[0]?.textType === 'RESULT_SET' && step.block[0]?.text"
							:data="safeParseJson(step.block[0].text)"
							:page-size="10"
						/>
						<!-- Markdown Report -->
						<div
							v-else-if="step.block[0]?.nodeName === 'ReportGeneratorNode' && step.block[0]?.textType === 'MARK_DOWN'"
							class="md-body"
							v-html="renderMarkdown(step.block[0].text || '')"
						/>
						<!-- Code -->
						<div
							v-else-if="['SQL', 'PYTHON', 'JSON'].includes(step.block[0]?.textType ?? '')"
							v-html="renderCode(step.block)"
						/>
						<!-- Plain text -->
						<div v-else class="text-body" v-html="renderText(step.block)" />
					</div>
				</div>
			</div>
		</div>
	</div>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue';
import DOMPurify from 'dompurify';
import hljs from 'highlight.js';
import { renderMarkdownContent } from '~/utils/markdown';
import { useEchartsRenderer } from '~/composables/useEchartsRenderer';
import type { GraphNodeResponse } from '~/services/graph/index';
import type { ResultData } from '~/services/resultSet/index';
import ChatResultSet from './ChatResultSet.vue';

const props = withDefaults(defineProps<{
	nodeBlocks: GraphNodeResponse[][];
	completed?: boolean;
}>(), {
	completed: false
});

const expandedSteps = ref<Record<string, boolean>>({});

function toggleStep(nodeName: string) {
	expandedSteps.value[nodeName] = !(expandedSteps.value[nodeName] ?? true);
}

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
	block: GraphNodeResponse[];
	expanded: boolean;
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
		const block = props.nodeBlocks.find(b => b[0]?.nodeName === def.nodeName) || [];
		
		let status: 'pending' | 'active' | 'done' = 'pending';
		if (props.completed) {
			status = 'done';
		} else {
			status = idx < lastIdx ? 'done' : 'active';
		}

		return {
			...def,
			status,
			block,
			expanded: expandedSteps.value[def.nodeName] ?? true,
		};
	});
});

function safeParseJson(content: string): ResultData | null {
	try { return JSON.parse(content); } catch { return null; }
}

function escapeHtml(text: string): string {
	const div = document.createElement('div');
	div.textContent = text;
	return div.innerHTML;
}

const timelineRef = ref<HTMLElement | null>(null);
const { renderECharts } = useEchartsRenderer();

function renderMarkdown(content: string): string {
	if (!content) return '';
	return DOMPurify.sanitize(renderMarkdownContent(content), { ADD_TAGS: ['div'], ADD_ATTR: ['style', 'class'] });
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

watch(() => props.nodeBlocks, () => {
	nextTick(() => renderECharts(timelineRef.value));
}, { deep: true });
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

.is-completed-step .text-body,
.is-completed-step .md-body {
	color: #94a3b8;
	font-style: italic;
}
.is-completed-step .md-body :deep(code) {
	font-style: normal;
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
.md-body :deep(code:not(pre code)) { background: #f6f8fa; border: 1px solid #e1e4e8; padding: 1px 5px; border-radius: 3px; font-size: 12px; color: #e83e8c; }
.md-body :deep(table) { width: 100%; border-collapse: collapse; margin: 6px 0; display: block; overflow-x: auto; }
.md-body :deep(thead) { display: table-header-group; }
.md-body :deep(tbody) { display: table-row-group; }
.md-body :deep(tr) { display: table-row; border-top: 1px solid #c6cbd1; }
.md-body :deep(th) { display: table-cell; background: #f1f5f9; padding: 6px 10px; border: 1px solid #e2e8f0; font-weight: 600; font-size: 12px; }
.md-body :deep(td) { display: table-cell; padding: 6px 10px; border: 1px solid #e2e8f0; font-size: 12px; }

/* ── Code block with header ─────────────────────────────────────────────────── */
.md-body :deep(.code-block-wrapper) { margin: 8px 0; border: 1px solid #e1e4e8; border-radius: 6px; overflow: hidden; background: #f6f8fa; }
.md-body :deep(.code-block-header) { display: flex; justify-content: space-between; align-items: center; background: #f6f8fa; padding: 4px 10px; border-bottom: 1px solid #e1e4e8; font-size: 11px; }
.md-body :deep(.code-language) { color: #6a737d; font-weight: 600; font-family: 'Monaco', 'Menlo', monospace; font-size: 10px; text-transform: uppercase; }
.md-body :deep(.code-copy-button) { background: transparent; border: 1px solid #d1d5da; padding: 2px 8px; border-radius: 4px; font-size: 10px; cursor: pointer; transition: all 0.2s; color: #24292e; }
.md-body :deep(.code-copy-button:hover) { background: #f3f4f6; border-color: #c6cbd1; }
.md-body :deep(.code-copy-button.copied) { background: #28a745; border-color: #28a745; color: white; }
.md-body :deep(pre.hljs) { margin: 0; padding: 8px 10px; overflow: auto; background: #f6f8fa; font-size: 11px; line-height: 1.35; }
.md-body :deep(pre.hljs code) { display: block; padding: 0; margin: 0; background: transparent; border: none; font-family: 'Monaco', 'Menlo', monospace; color: inherit; }

/* ── ECharts containers ─────────────────────────────────────────────────────── */
:deep(.md-echarts) { margin: 8px 0; border-radius: 6px; }
</style>
