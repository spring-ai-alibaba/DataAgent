/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

<template>
	<div ref="timelineRef" class="workflow-timeline">
		<!-- Title + global toggle -->
		<div class="timeline-title-bar">
			<v-card-title class="timeline-title pa-0">
				<v-icon size="18" color="blue" class="mr-1"
					>mdi-rocket-launch-outline</v-icon
				>
				任务开始
			</v-card-title>
			<v-btn
				variant="outlined"
				size="x-small"
				color="grey"
				class="toggle-all-btn"
				:prepend-icon="
					allExpanded
						? 'mdi-unfold-less-horizontal'
						: 'mdi-unfold-more-horizontal'
				"
				@click="toggleAll"
			>
				{{ allExpanded ? '折叠全部' : '展开全部' }}
			</v-btn>
		</div>

		<v-timeline density="compact" side="end" truncate-line="both">
			<v-timeline-item
				v-for="step in timelineSteps"
				:key="step.nodeName"
				:dot-color="dotColor(step.status)"
				:icon="dotIcon(step.status)"
				size="small"
			>
				<!-- Step header: clickable to toggle -->
				<div class="step-header" @click="toggleStep(step.nodeName)">
					<div class="step-header-left">
						<span class="step-label">{{ step.label }}</span>
						<span v-if="step.status === 'active'" class="step-badge active">
							<span class="badge-dot" />进行中
						</span>
						<span v-else-if="step.status === 'done'" class="step-badge done"
							>完成</span
						>
					</div>
					<v-icon size="16" color="#94a3b8">
						{{ step.expanded ? 'mdi-chevron-up' : 'mdi-chevron-down' }}
					</v-icon>
				</div>

				<!-- Collapsible content -->
				<v-expand-transition>
					<div
						v-show="step.expanded"
						class="step-content"
						:class="{ 'is-muted': step.status === 'done' && !step.isReport }"
					>
						<!-- Result Set -->
						<ChatResultSet
							v-if="
								step.block[0]?.textType === 'RESULT_SET' && step.block[0]?.text
							"
							:data="safeParseJson(step.block[0].text)"
							:page-size="10"
						/>
						<!-- Report node: show brief status, not full content -->
						<div v-else-if="step.isReport" class="text-body report-brief">
							<v-icon size="14" color="#16a34a" class="mr-1"
								>mdi-file-chart-outline</v-icon
							>
							<span v-if="step.status === 'active'"
								>正在生成报告，内容在下方实时展示...</span
							>
							<span v-else>报告已生成完毕，查看下方报告卡片</span>
						</div>
						<!-- Pure code block (all items share same code type) -->
						<div
							v-else-if="isPureCodeBlock(step.block)"
							v-html="renderCode(step.block)"
						/>
						<!-- Mixed content: text with possible embedded JSON/code -->
						<div
							v-else
							class="text-body"
							v-html="renderTextWithJsonDetection(step.block)"
						/>
					</div>
				</v-expand-transition>
			</v-timeline-item>
		</v-timeline>
	</div>
</template>

<script setup lang="ts">
import hljs from 'highlight.js';
import DOMPurify from 'dompurify';
import { useEchartsRenderer } from '~/composables/useEchartsRenderer';
import type { GraphNodeResponse } from '~/services/graph/index';
import type { ResultData } from '~/services/resultSet/index';
import ChatResultSet from './ChatResultSet.vue';

const props = withDefaults(
	defineProps<{
		nodeBlocks: GraphNodeResponse[][];
		completed?: boolean;
	}>(),
	{
		completed: false,
	},
);

const expandedSteps = ref<Record<string, boolean>>({});

const allExpanded = computed(() => {
	const steps = timelineSteps.value;
	if (steps.length === 0) return false;
	return steps.some((s) => s.expanded);
});

function toggleAll() {
	const shouldExpand = !allExpanded.value;
	for (const step of timelineSteps.value) {
		expandedSteps.value[step.nodeName] = shouldExpand;
	}
}

function toggleStep(nodeName: string) {
	const defaultExpanded = getDefaultExpanded(nodeName);
	expandedSteps.value[nodeName] = !(
		expandedSteps.value[nodeName] ?? defaultExpanded
	);
}

function getDefaultExpanded(nodeName: string): boolean {
	if (!props.completed) return true;
	if (nodeName === 'ReportGeneratorNode') return true;
	return false;
}

interface NodeDef {
	nodeName: string;
	label: string;
	icon: string;
}

const NODE_LABEL_MAP: Record<string, NodeDef> = {
	IntentRecognitionNode: {
		nodeName: 'IntentRecognitionNode',
		label: '意图识别',
		icon: 'mdi-magnify',
	},
	QueryEnhanceNode: {
		nodeName: 'QueryEnhanceNode',
		label: '查询增强',
		icon: 'mdi-text-search',
	},
	SchemaRecallNode: {
		nodeName: 'SchemaRecallNode',
		label: 'Schema 召回',
		icon: 'mdi-database-search',
	},
	FeasibilityAssessmentNode: {
		nodeName: 'FeasibilityAssessmentNode',
		label: '可行性评估',
		icon: 'mdi-check-circle-outline',
	},
	EvidenceRecallNode: {
		nodeName: 'EvidenceRecallNode',
		label: '证据召回',
		icon: 'mdi-file-search-outline',
	},
	TableRelationNode: {
		nodeName: 'TableRelationNode',
		label: '表关系分析',
		icon: 'mdi-table-network',
	},
	PlannerNode: {
		nodeName: 'PlannerNode',
		label: '制定计划',
		icon: 'mdi-clipboard-list-outline',
	},
	HumanFeedbackNode: {
		nodeName: 'HumanFeedbackNode',
		label: '人工反馈',
		icon: 'mdi-account-check-outline',
	},
	PlanExecutorNode: {
		nodeName: 'PlanExecutorNode',
		label: '执行计划',
		icon: 'mdi-play-circle-outline',
	},
	SqlGenerateNode: {
		nodeName: 'SqlGenerateNode',
		label: 'SQL 生成',
		icon: 'mdi-code-braces',
	},
	SemanticConsistencyNode: {
		nodeName: 'SemanticConsistencyNode',
		label: '语义一致性校验',
		icon: 'mdi-check-decagram',
	},
	SqlExecuteNode: {
		nodeName: 'SqlExecuteNode',
		label: 'SQL 执行',
		icon: 'mdi-database-arrow-right',
	},
	PythonGenerateNode: {
		nodeName: 'PythonGenerateNode',
		label: 'Python 生成',
		icon: 'mdi-language-python',
	},
	PythonAnalyzeNode: {
		nodeName: 'PythonAnalyzeNode',
		label: 'Python 分析',
		icon: 'mdi-chart-line',
	},
	PythonExecuteNode: {
		nodeName: 'PythonExecuteNode',
		label: 'Python 执行',
		icon: 'mdi-play-outline',
	},
	ReportGeneratorNode: {
		nodeName: 'ReportGeneratorNode',
		label: '报告生成',
		icon: 'mdi-file-chart-outline',
	},
};

interface TimelineStep extends NodeDef {
	status: 'pending' | 'active' | 'done';
	block: GraphNodeResponse[];
	expanded: boolean;
	isReport: boolean;
}

const timelineSteps = computed<TimelineStep[]>(() => {
	const seen = new Set<string>();
	const orderedNodeNames: string[] = [];
	for (const block of props.nodeBlocks) {
		const name = block[0]?.nodeName;
		if (name && !seen.has(name)) {
			seen.add(name);
			orderedNodeNames.push(name);
		}
	}

	if (orderedNodeNames.length === 0) return [];
	const lastIdx = orderedNodeNames.length - 1;

	return orderedNodeNames.map((nodeName, idx) => {
		const def = NODE_LABEL_MAP[nodeName] || {
			nodeName,
			label: nodeName,
			icon: 'mdi-lightning-bolt',
		};
		const block =
			props.nodeBlocks.find((b) => b[0]?.nodeName === nodeName) || [];
		const isReport = nodeName === 'ReportGeneratorNode';

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
			expanded: expandedSteps.value[nodeName] ?? getDefaultExpanded(nodeName),
			isReport,
		};
	});
});

function dotColor(status: string): string {
	if (status === 'done') return 'green';
	if (status === 'active') return 'blue-darken-2';
	return 'grey-lighten-1';
}

function dotIcon(status: string): string {
	if (status === 'done') return 'mdi-check';
	if (status === 'active') return 'mdi-dots-horizontal';
	return '';
}

function safeParseJson(content: string): ResultData | null {
	try {
		return JSON.parse(content);
	} catch {
		return null;
	}
}

function escapeHtml(text: string): string {
	const div = document.createElement('div');
	div.textContent = text;
	return div.innerHTML;
}

const timelineRef = ref<HTMLElement | null>(null);
const { renderECharts } = useEchartsRenderer();

const CODE_TEXT_TYPES = new Set(['SQL', 'PYTHON', 'JSON']);

function isPureCodeBlock(block: GraphNodeResponse[]): boolean {
	return (
		block.length > 0 && block.every((n) => CODE_TEXT_TYPES.has(n.textType))
	);
}

const SANITIZE_OPTIONS = {
	ADD_TAGS: ['pre', 'code'],
	ADD_ATTR: ['class'],
	RETURN_TRUSTED_TYPE: false as const,
};

function renderCode(block: GraphNodeResponse[]): string {
	const lang = (block[0]?.textType || 'text').toLowerCase();
	const code = block.map((n) => n.text).join('');
	try {
		const h = hljs.highlight(code, { language: lang });
		return DOMPurify.sanitize(
			`<pre class="tl-code"><code class="hljs ${lang}">${h.value}</code></pre>`,
			SANITIZE_OPTIONS,
		) as string;
	} catch {
		return DOMPurify.sanitize(
			`<pre class="tl-code"><code>${escapeHtml(code)}</code></pre>`,
			SANITIZE_OPTIONS,
		) as string;
	}
}

function tryExtractJson(
	text: string,
): { before: string; json: string; after: string } | null {
	const start = text.indexOf('{');
	const end = text.lastIndexOf('}');
	if (start === -1 || end === -1 || end <= start) return null;
	const candidate = text.substring(start, end + 1);
	try {
		JSON.parse(candidate);
		return {
			before: text.substring(0, start).trim(),
			json: candidate,
			after: text.substring(end + 1).trim(),
		};
	} catch {
		return null;
	}
}

function renderTextWithJsonDetection(block: GraphNodeResponse[]): string {
	const fullText = block.map((n) => n.text).join('');

	const extracted = tryExtractJson(fullText);
	if (extracted) {
		const parts: string[] = [];
		if (extracted.before) {
			parts.push(
				`<div class="text-body">${escapeHtml(extracted.before).replace(/\n/g, '<br>')}</div>`,
			);
		}
		try {
			const formatted = JSON.stringify(JSON.parse(extracted.json), null, 2);
			const h = hljs.highlight(formatted, { language: 'json' });
			parts.push(
				`<pre class="tl-code"><code class="hljs json">${h.value}</code></pre>`,
			);
		} catch {
			parts.push(
				`<pre class="tl-code"><code>${escapeHtml(extracted.json)}</code></pre>`,
			);
		}
		if (extracted.after) {
			parts.push(
				`<div class="text-body">${escapeHtml(extracted.after).replace(/\n/g, '<br>')}</div>`,
			);
		}
		return DOMPurify.sanitize(parts.join(''), SANITIZE_OPTIONS) as string;
	}

	return DOMPurify.sanitize(
		`<div class="text-body">${escapeHtml(fullText).replace(/\n/g, '<br>')}</div>`,
		SANITIZE_OPTIONS,
	) as string;
}

watch(
	() => props.nodeBlocks,
	() => {
		nextTick(() => renderECharts(timelineRef.value));
	},
	{ deep: true },
);
</script>

<style scoped>
.workflow-timeline {
	width: 100%;
}

/* ── Title bar ───────────────────────────────────────────────────────────────── */
.timeline-title-bar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 8px;
	padding: 0 2px;
}

.timeline-title {
	font-size: 15px !important;
	font-weight: 700;
	color: #2563eb;
	display: flex;
	align-items: center;
	line-height: 1;
}

.toggle-all-btn {
	font-size: 11px !important;
	text-transform: none !important;
	letter-spacing: 0 !important;
}

/* ── Step header ─────────────────────────────────────────────────────────────── */
.step-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	cursor: pointer;
	padding: 2px 0;
	user-select: none;
}

.step-header-left {
	display: flex;
	align-items: center;
	gap: 8px;
}

.step-label {
	font-size: 13px;
	font-weight: 600;
	color: #1e293b;
}

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
	0%,
	100% {
		opacity: 1;
	}
	50% {
		opacity: 0.3;
	}
}

/* ── Step content ────────────────────────────────────────────────────────────── */
.step-content {
	margin-top: 6px;
	font-size: 13px;
	line-height: 1.65;
	color: #1e293b;
	min-width: 0;
	overflow: hidden;
}

.text-body {
	white-space: pre-wrap;
	word-break: break-word;
}

.is-muted .text-body {
	color: #94a3b8;
	font-style: italic;
}

.report-body {
	color: #1e293b !important;
	font-style: normal !important;
}

.report-brief {
	display: flex;
	align-items: center;
	color: #64748b !important;
	font-style: normal !important;
	font-size: 12.5px;
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

/* ── Markdown inside step content ────────────────────────────────────────────── */
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) {
	font-weight: 700;
	margin: 10px 0 4px;
}
.md-body :deep(p) {
	margin-bottom: 6px;
}
.md-body :deep(ul),
.md-body :deep(ol) {
	padding-left: 18px;
	margin-bottom: 6px;
}
.md-body :deep(code:not(pre code)) {
	background: #f6f8fa;
	border: 1px solid #e1e4e8;
	padding: 1px 5px;
	border-radius: 3px;
	font-size: 12px;
	color: #e83e8c;
}
.md-body :deep(table) {
	width: 100%;
	border-collapse: collapse;
	margin: 6px 0;
	display: block;
	overflow-x: auto;
}
.md-body :deep(thead) {
	display: table-header-group;
}
.md-body :deep(tbody) {
	display: table-row-group;
}
.md-body :deep(tr) {
	display: table-row;
	border-top: 1px solid #c6cbd1;
}
.md-body :deep(th) {
	display: table-cell;
	background: #f1f5f9;
	padding: 6px 10px;
	border: 1px solid #e2e8f0;
	font-weight: 600;
	font-size: 12px;
}
.md-body :deep(td) {
	display: table-cell;
	padding: 6px 10px;
	border: 1px solid #e2e8f0;
	font-size: 12px;
}

/* ── Code block with header ─────────────────────────────────────────────────── */
.md-body :deep(.code-block-wrapper) {
	margin: 8px 0;
	border: 1px solid #e1e4e8;
	border-radius: 6px;
	overflow: auto;
	background: #f6f8fa;
}
.md-body :deep(.code-block-header) {
	display: flex;
	justify-content: space-between;
	align-items: center;
	background: #f6f8fa;
	padding: 4px 10px;
	border-bottom: 1px solid #e1e4e8;
	font-size: 11px;
}
.md-body :deep(.code-language) {
	color: #6a737d;
	font-weight: 600;
	font-family: 'Monaco', 'Menlo', monospace;
	font-size: 10px;
	text-transform: uppercase;
}
.md-body :deep(.code-copy-button) {
	background: transparent;
	border: 1px solid #d1d5da;
	padding: 2px 8px;
	border-radius: 4px;
	font-size: 10px;
	cursor: pointer;
	transition: all 0.2s;
	color: #24292e;
}
.md-body :deep(.code-copy-button:hover) {
	background: #f3f4f6;
	border-color: #c6cbd1;
}
.md-body :deep(.code-copy-button.copied) {
	background: #28a745;
	border-color: #28a745;
	color: white;
}
.md-body :deep(pre.hljs) {
	margin: 0;
	padding: 8px 10px;
	overflow-x: auto;
	overflow-y: hidden;
	background: #f6f8fa;
	font-size: 11px;
	line-height: 1.35;
	white-space: pre;
}
.md-body :deep(pre.hljs code) {
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
	margin: 8px 0;
	border-radius: 6px;
}
</style>
