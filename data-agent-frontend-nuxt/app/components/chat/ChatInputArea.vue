<template>
	<div class="input-area">
		<!-- Status / Info bar -->
		<div class="status-bar">
			<div class="status-chips">
				<div class="status-chip">
					<svg width="14" height="14" viewBox="0 0 14 14" fill="none" class="chip-icon">
						<rect x="1" y="2" width="12" height="10" rx="2" stroke="#64748b" stroke-width="1.2" />
						<path d="M4 6h6M4 8.5h4" stroke="#64748b" stroke-width="1" stroke-linecap="round" />
					</svg>
					JDBC: 核心库 (MySQL)
				</div>
				<div v-if="store.activeChatModel" class="status-chip status-chip--model">
					<v-icon size="13" color="#3b82f6" class="mr-1">mdi-lightning-bolt</v-icon>
					Chat: {{ store.activeChatModel }}
					<v-icon size="12" color="#64748b" class="ml-1">mdi-chevron-down</v-icon>
				</div>
			</div>
		</div>

		<!-- Textarea -->
		<div class="textarea-wrap">
			<textarea
				ref="textareaRef"
				v-model="inputText"
				class="chat-textarea"
				:disabled="store.isStreaming || store.showHumanFeedback"
				placeholder="在这里提问，例如：'分析上月各产品的销售增长情况'..."
				rows="3"
				@keydown.enter.exact.prevent="handleSend"
				@input="autoResize"
			/>
		</div>

		<!-- Bottom action bar -->
		<div class="action-bar">
			<div class="action-bar-left">
				<button class="action-icon-btn" title="更多选项" @click="toggleOptions">
					<v-icon size="18" color="#64748b">mdi-plus</v-icon>
				</button>
				<button class="action-icon-btn" title="附件">
					<v-icon size="18" color="#64748b">mdi-paperclip</v-icon>
				</button>
				<!-- Extra options -->
				<transition name="fade">
					<div v-show="showOptions" class="extra-options">
						<label class="option-chip" :class="{ active: store.requestOptions.humanFeedback }">
							<input
								v-model="store.requestOptions.humanFeedback"
								type="checkbox"
								:disabled="store.requestOptions.nl2sqlOnly || store.isStreaming"
								class="hidden-checkbox"
							/>
							<v-icon size="11">mdi-account-check-outline</v-icon>
							人工反馈
						</label>
						<label class="option-chip" :class="{ active: store.requestOptions.nl2sqlOnly }">
							<input
								v-model="store.requestOptions.nl2sqlOnly"
								type="checkbox"
								:disabled="store.isStreaming"
								class="hidden-checkbox"
								@change="onNl2sqlChange"
							/>
							<v-icon size="11">mdi-database-search-outline</v-icon>
							仅NL2SQL
						</label>
						<label class="option-chip" :class="{ active: store.requestOptions.showSqlResults }">
							<input
								v-model="store.requestOptions.showSqlResults"
								type="checkbox"
								:disabled="store.isStreaming"
								class="hidden-checkbox"
							/>
							<v-icon size="11">mdi-table-eye</v-icon>
							显示SQL结果
						</label>
					</div>
				</transition>
			</div>

			<div class="action-bar-right">
				<button
					v-if="!store.isStreaming"
					class="send-btn"
					:disabled="!inputText.trim() || store.showHumanFeedback"
					@click="handleSend"
				>
					发送需求
					<svg width="16" height="16" viewBox="0 0 16 16" fill="none" class="send-icon">
						<path d="M2 8h12M10 4l4 4-4 4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
					</svg>
				</button>
				<button v-else class="stop-btn" @click="handleStop">
					<v-icon size="16" color="white">mdi-stop</v-icon>
					停止
				</button>
			</div>
		</div>

		<!-- Human Feedback Panel -->
		<transition name="slide-up">
			<div v-if="store.showHumanFeedback" class="human-feedback-panel">
				<div class="feedback-header">
					<v-icon color="warning" size="16" class="mr-1">mdi-account-question-outline</v-icon>
					<span>请确认执行计划</span>
				</div>
				<textarea
					v-model="store.feedbackContent"
					class="feedback-textarea"
					rows="2"
					placeholder="输入您的反馈意见（留空表示接受计划）"
				/>
				<div class="feedback-actions">
					<button class="feedback-btn feedback-btn--accept" @click="store.submitFeedback(false, store.feedbackContent)">
						<v-icon size="14" class="mr-1">mdi-check</v-icon>接受计划
					</button>
					<button class="feedback-btn feedback-btn--reject" @click="store.submitFeedback(true, store.feedbackContent)">
						<v-icon size="14" class="mr-1">mdi-close</v-icon>拒绝重规划
					</button>
				</div>
			</div>
		</transition>
	</div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useChatStore } from '~/stores/chat';

const store = useChatStore();
const inputText = ref('');
const textareaRef = ref<HTMLTextAreaElement | null>(null);
const showOptions = ref(false);

function toggleOptions() {
	showOptions.value = !showOptions.value;
}

function onNl2sqlChange() {
	if (store.requestOptions.nl2sqlOnly) {
		store.requestOptions.humanFeedback = false;
	}
}

function autoResize() {
	const el = textareaRef.value;
	if (!el) return;
	el.style.height = 'auto';
	el.style.height = Math.min(el.scrollHeight, 200) + 'px';
}

async function handleSend() {
	const query = inputText.value.trim();
	if (!query) return;
	if (!store.currentSession) return;
	if (store.isStreaming) return;

	inputText.value = '';
	nextTick(() => {
		if (textareaRef.value) textareaRef.value.style.height = 'auto';
	});

	try {
		await store.sendMessage(query);
	} catch (e) {
		console.error('发送失败', e);
	}
}

async function handleStop() {
	try {
		await store.stopStreaming();
	} catch (e) {
		console.error('停止失败', e);
	}
}
</script>

<style scoped>
.input-area {
	flex-shrink: 0;
	background: white;
	border-top: 1px solid #e8edf2;
	padding: 12px 32px 16px;
}

/* ── Status bar ──────────────────────────────────────────────────────────────── */
.status-bar {
	margin-bottom: 10px;
}
.status-chips {
	display: flex;
	align-items: center;
	gap: 8px;
	flex-wrap: wrap;
}
.status-chip {
	display: inline-flex;
	align-items: center;
	gap: 5px;
	padding: 4px 10px;
	background: #f1f5f9;
	border: 1px solid #e2e8f0;
	border-radius: 20px;
	font-size: 12.5px;
	color: #475569;
}
.status-chip--model {
	background: #eff6ff;
	border-color: #bfdbfe;
	color: #1d4ed8;
	cursor: pointer;
}
.status-chip--model:hover {
	background: #dbeafe;
}
.chip-icon {
	flex-shrink: 0;
}

/* ── Textarea ────────────────────────────────────────────────────────────────── */
.textarea-wrap {
	background: #f8fafc;
	border: 1.5px solid #e2e8f0;
	border-radius: 14px;
	overflow: hidden;
	transition: border-color 0.15s;
}
.textarea-wrap:focus-within {
	border-color: #3b82f6;
	background: #fff;
}
.chat-textarea {
	display: block;
	width: 100%;
	padding: 14px 16px 8px;
	background: none;
	border: none;
	outline: none;
	resize: none;
	font-size: 14.5px;
	line-height: 1.6;
	color: #1e293b;
	font-family: inherit;
	min-height: 80px;
}
.chat-textarea::placeholder {
	color: #94a3b8;
}
.chat-textarea:disabled {
	opacity: 0.6;
	cursor: not-allowed;
}

/* ── Action bar ──────────────────────────────────────────────────────────────── */
.action-bar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 6px 4px 0;
}
.action-bar-left {
	display: flex;
	align-items: center;
	gap: 4px;
	flex-wrap: wrap;
}
.action-icon-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 32px;
	height: 32px;
	background: none;
	border: none;
	cursor: pointer;
	border-radius: 6px;
	transition: background 0.1s;
}
.action-icon-btn:hover {
	background: #f1f5f9;
}

/* ── Extra options ───────────────────────────────────────────────────────────── */
.extra-options {
	display: flex;
	align-items: center;
	gap: 6px;
	flex-wrap: wrap;
}
.option-chip {
	display: inline-flex;
	align-items: center;
	gap: 4px;
	padding: 3px 10px;
	background: #f8fafc;
	border: 1px solid #e2e8f0;
	border-radius: 16px;
	font-size: 12px;
	color: #64748b;
	cursor: pointer;
	transition: border-color 0.1s, background 0.1s;
	user-select: none;
}
.option-chip:hover {
	border-color: #3b82f6;
	color: #3b82f6;
}
.option-chip.active {
	background: #eff6ff;
	border-color: #3b82f6;
	color: #2563eb;
}
.hidden-checkbox {
	position: absolute;
	opacity: 0;
	width: 0;
	height: 0;
}

/* ── Send button ─────────────────────────────────────────────────────────────── */
.send-btn {
	display: inline-flex;
	align-items: center;
	gap: 8px;
	padding: 10px 24px;
	background: #2563eb;
	color: white;
	border: none;
	border-radius: 24px;
	font-size: 14px;
	font-weight: 600;
	cursor: pointer;
	transition: background 0.15s, opacity 0.15s;
	white-space: nowrap;
}
.send-btn:hover:not(:disabled) {
	background: #1d4ed8;
}
.send-btn:disabled {
	opacity: 0.4;
	cursor: not-allowed;
}
.send-icon {
	flex-shrink: 0;
}

/* ── Stop button ─────────────────────────────────────────────────────────────── */
.stop-btn {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	padding: 10px 20px;
	background: #ef4444;
	color: white;
	border: none;
	border-radius: 24px;
	font-size: 14px;
	font-weight: 600;
	cursor: pointer;
	transition: background 0.15s;
}
.stop-btn:hover {
	background: #dc2626;
}

/* ── Human feedback ──────────────────────────────────────────────────────────── */
.human-feedback-panel {
	margin-top: 10px;
	background: #fffbeb;
	border: 1px solid #fde68a;
	border-radius: 10px;
	padding: 12px 14px;
}
.feedback-header {
	display: flex;
	align-items: center;
	font-size: 13px;
	font-weight: 600;
	color: #92400e;
	margin-bottom: 8px;
}
.feedback-textarea {
	width: 100%;
	background: white;
	border: 1px solid #fde68a;
	border-radius: 6px;
	padding: 8px 10px;
	font-size: 13px;
	resize: none;
	outline: none;
	color: #1e293b;
	font-family: inherit;
	margin-bottom: 8px;
}
.feedback-actions {
	display: flex;
	gap: 8px;
}
.feedback-btn {
	display: inline-flex;
	align-items: center;
	padding: 6px 16px;
	border-radius: 6px;
	font-size: 12.5px;
	font-weight: 600;
	border: none;
	cursor: pointer;
	transition: opacity 0.1s;
}
.feedback-btn--accept {
	background: #22c55e;
	color: white;
}
.feedback-btn--reject {
	background: white;
	color: #ef4444;
	border: 1px solid #ef4444;
}
.feedback-btn:hover {
	opacity: 0.85;
}

/* ── Transitions ─────────────────────────────────────────────────────────────── */
.fade-enter-active, .fade-leave-active { transition: opacity 0.15s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.slide-up-enter-active, .slide-up-leave-active { transition: all 0.2s ease; }
.slide-up-enter-from, .slide-up-leave-to { transform: translateY(10px); opacity: 0; }
</style>
