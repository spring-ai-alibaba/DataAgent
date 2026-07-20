/* * Copyright 2026 the original author or authors. * * Licensed under the
Apache License, Version 2.0 (the "License"); * you may not use this file except
in compliance with the License. * You may obtain a copy of the License at * *
https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable
law or agreed to in writing, software * distributed under the License is
distributed on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. * See the License for the specific language governing
permissions and * limitations under the License. */

<template>
	<div class="input-area">
		<div class="status-bar">
			<div class="status-chips">
				<div class="ds-chip-wrap" @click.stop>
					<div
						class="status-chip status-chip--ds"
						:class="{ disabled: store.isStreaming }"
						@click="toggleDsMenu"
					>
						<v-icon size="13" color="#64748b">mdi-database-outline</v-icon>
						<span>{{ store.activeDatasource?.name || '选择数据源' }}</span>
						<v-icon size="13" color="#94a3b8">{{
							showDsMenu ? 'mdi-chevron-up' : 'mdi-chevron-down'
						}}</v-icon>
					</div>
					<div v-if="showDsMenu" class="chip-dropdown">
						<div
							v-for="ds in store.allDatasources"
							:key="ds.id"
							class="chip-dropdown-item"
							:class="{ active: store.activeDatasource?.id === ds.id }"
							@click="selectDs(ds)"
						>
							<span class="item-name">{{ ds.name }}</span>
							<span class="item-tag">{{ ds.type?.toUpperCase() }}</span>
						</div>
					</div>
				</div>

				<div class="ds-chip-wrap" @click.stop>
					<div
						class="status-chip status-chip--model"
						:class="{
							disabled: store.isStreaming || store.chatModels.length === 0,
						}"
						@click="toggleModelMenu"
					>
						<v-icon size="13" color="#3b82f6">mdi-lightning-bolt</v-icon>
						<span>{{
							store.activeModelConfig?.modelName || '选择AI模型'
						}}</span>
						<v-icon size="13" color="#94a3b8">{{
							showModelMenu ? 'mdi-chevron-up' : 'mdi-chevron-down'
						}}</v-icon>
					</div>
					<div v-if="showModelMenu" class="chip-dropdown">
						<div
							v-for="m in store.chatModels"
							:key="m.id"
							class="chip-dropdown-item"
							:class="{ active: store.activeModelConfig?.id === m.id }"
							@click="selectModel(m)"
						>
							<span class="item-name">{{ m.modelName }}</span>
							<span class="item-tag">{{ m.provider }}</span>
						</div>
					</div>
				</div>
			</div>
		</div>

		<div class="textarea-wrap">
			<div v-if="store.awaitingClarification" class="clarification-tip">
				<v-icon size="14" color="#d97706">mdi-chat-alert-outline</v-icon>
				<span>当前正在补充查询条件，你的下一条消息将作为澄清回答</span>
			</div>
			<textarea
				ref="textareaRef"
				v-model="inputText"
				class="chat-textarea"
				:disabled="store.isStreaming || store.showHumanFeedback"
				:placeholder="inputPlaceholder"
				rows="3"
				@keydown.enter.exact.prevent="handleSend"
				@input="autoResize"
			/>
		</div>

		<div class="action-bar">
			<div class="action-bar-left">
				<div class="extra-options">
					<label
						class="option-chip"
						:class="{ active: store.requestOptions.thinkingEnabled }"
						title="为本次任务启用模型推理，需模型服务支持"
					>
						<input
							v-model="store.requestOptions.thinkingEnabled"
							type="checkbox"
							:disabled="store.isStreaming"
							class="hidden-checkbox"
						/>
						<v-icon size="11">mdi-head-cog-outline</v-icon>
						深度思考
					</label>
					<div
						v-if="store.requestOptions.thinkingEnabled"
						class="reasoning-segment"
						role="group"
						aria-label="思考强度"
					>
						<button
							type="button"
							class="reasoning-segment__option"
							:class="{
								active: store.requestOptions.reasoningEffort === 'high',
							}"
							:disabled="store.isStreaming"
							:aria-pressed="store.requestOptions.reasoningEffort === 'high'"
							@click="store.requestOptions.reasoningEffort = 'high'"
						>
							高
						</button>
						<button
							type="button"
							class="reasoning-segment__option"
							:class="{
								active: store.requestOptions.reasoningEffort === 'max',
							}"
							:disabled="store.isStreaming"
							:aria-pressed="store.requestOptions.reasoningEffort === 'max'"
							@click="store.requestOptions.reasoningEffort = 'max'"
						>
							最高
						</button>
					</div>
				</div>
			</div>
			<div class="action-bar-right">
				<v-btn
					v-if="!store.isStreaming"
					class="send-btn"
					:disabled="!inputText.trim() || store.showHumanFeedback"
					@click="handleSend"
				>
					发送
					<v-icon size="16" class="ml-1">mdi-arrow-right</v-icon>
				</v-btn>
				<v-btn v-else class="stop-btn" @click="handleStop">
					<v-icon size="16" color="white">mdi-stop</v-icon>
					停止
				</v-btn>
			</div>
		</div>

		<Transition name="slide-up">
			<div v-if="store.showHumanFeedback" class="human-feedback-panel">
				<div class="feedback-header">
					<v-icon color="warning" size="16" class="mr-1"
						>mdi-account-question-outline</v-icon
					>
					<span>请确认执行计划</span>
				</div>
				<textarea
					v-model="store.feedbackContent"
					class="feedback-textarea"
					rows="2"
					placeholder="输入您的反馈意见（留空表示接受计划）"
				/>
				<div class="feedback-actions">
					<v-btn
						class="feedback-btn feedback-btn--accept"
						@click="store.submitFeedback(false, store.feedbackContent)"
					>
						<v-icon size="14" class="mr-1">mdi-check</v-icon>接受计划
					</v-btn>
					<v-btn
						class="feedback-btn feedback-btn--reject"
						@click="store.submitFeedback(true, store.feedbackContent)"
					>
						<v-icon size="14" class="mr-1">mdi-close</v-icon>拒绝并重规划
					</v-btn>
				</div>
			</div>
		</Transition>
	</div>
</template>

<script setup lang="ts">
import { useChatStore } from '~/stores/chat';

const store = useChatStore();
const inputText = ref('');
const textareaRef = ref<HTMLTextAreaElement | null>(null);
const showDsMenu = ref(false);
const showModelMenu = ref(false);
const inputPlaceholder = computed(() =>
	store.awaitingClarification
		? '请继续补充时间范围、统计对象或指标口径...'
		: '在这里提问，例如：分析上个月各产品的销售增长情况...',
);

function toggleDsMenu() {
	if (store.isStreaming) return;
	showDsMenu.value = !showDsMenu.value;
	if (showDsMenu.value) showModelMenu.value = false;
}

function toggleModelMenu() {
	if (store.isStreaming || store.chatModels.length === 0) return;
	showModelMenu.value = !showModelMenu.value;
	if (showModelMenu.value) showDsMenu.value = false;
}

async function selectDs(ds: (typeof store.allDatasources)[0]) {
	showDsMenu.value = false;
	await store.switchDatasource(ds);
}

async function selectModel(m: (typeof store.chatModels)[0]) {
	showModelMenu.value = false;
	if (m.id !== undefined) await store.switchModel(m.id);
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

function closeMenus() {
	showDsMenu.value = false;
	showModelMenu.value = false;
}

onMounted(() => document.addEventListener('click', closeMenus));
onUnmounted(() => document.removeEventListener('click', closeMenus));
</script>

<style scoped>
.input-area {
	flex-shrink: 0;
	background: white;
	border-top: 1px solid #e8edf2;
	padding: 12px 32px 16px;
}

.status-bar {
	margin-bottom: 10px;
}

.status-chips {
	display: flex;
	align-items: center;
	gap: 8px;
	flex-wrap: wrap;
}

.ds-chip-wrap {
	position: relative;
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
	cursor: pointer;
	user-select: none;
	white-space: nowrap;
	transition:
		border-color 0.1s,
		background 0.1s;
}

.status-chip:hover:not(.disabled) {
	border-color: #94a3b8;
}

.status-chip.disabled {
	opacity: 0.5;
	cursor: not-allowed;
}

.status-chip--model {
	background: #eff6ff;
	border-color: #bfdbfe;
	color: #1d4ed8;
}

.status-chip--model:hover:not(.disabled) {
	border-color: #93c5fd;
}

.chip-dropdown {
	position: absolute;
	top: calc(100% + 4px);
	left: 0;
	z-index: 999;
	background: white;
	border: 1px solid #e2e8f0;
	border-radius: 10px;
	box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
	min-width: 200px;
	max-width: 300px;
	max-height: 280px;
	overflow-y: auto;
	padding: 4px 0;
}

.chip-dropdown-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 8px;
	padding: 7px 14px;
	font-size: 13px;
	color: #334155;
	cursor: pointer;
	transition: background 0.1s;
}

.chip-dropdown-item:hover {
	background: #f1f5f9;
}

.chip-dropdown-item.active {
	background: #eff6ff;
	color: #2563eb;
	font-weight: 500;
}

.item-name {
	flex: 1;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.item-tag {
	flex-shrink: 0;
	font-size: 11px;
	color: #94a3b8;
	background: #f1f5f9;
	border-radius: 4px;
	padding: 1px 5px;
}

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

.clarification-tip {
	display: flex;
	align-items: center;
	gap: 6px;
	padding: 10px 14px 0;
	font-size: 12.5px;
	color: #b45309;
	background: #fffbeb;
}

.chat-textarea {
	display: block;
	width: 100%;
	padding: 14px 16px 8px;
	background: none;
	border: none;
	outline: none;
	resize: vertical;
	font-size: 14.5px;
	line-height: 1.6;
	color: #1e293b;
	font-family: inherit;
	min-height: 80px;
	max-height: 300px;
}

.chat-textarea::placeholder {
	color: #94a3b8;
}

.chat-textarea:disabled {
	opacity: 0.6;
	cursor: not-allowed;
}

.action-bar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 6px 4px 0;
}

.action-bar-left,
.extra-options {
	display: flex;
	align-items: center;
	gap: 6px;
}

.option-chip {
	display: inline-flex;
	align-items: center;
	gap: 4px;
	padding: 3px 10px;
	border: 1px solid var(--domus-line, #dccbb5);
	border-radius: 16px;
	background: var(--domus-paper, #fff6e8);
	color: var(--domus-muted, #77695c);
	font-size: 12px;
	cursor: pointer;
	user-select: none;
}

.option-chip.active {
	border-color: var(--domus-amber, #c7832f);
	background: rgb(199 131 47 / 12%);
	color: var(--domus-copper, #8d5633);
}

.hidden-checkbox {
	position: absolute;
	width: 0;
	height: 0;
	opacity: 0;
}

.reasoning-segment {
	display: inline-flex;
	align-items: center;
	height: 28px;
	padding: 2px;
	border: 1px solid var(--domus-line, #dccbb5);
	border-radius: 999px;
	background: rgb(255 246 232 / 82%);
	box-shadow: inset 0 1px 0 rgb(255 255 255 / 55%);
	flex: 0 0 auto;
}

.reasoning-segment__option {
	min-width: 34px;
	height: 22px;
	padding: 0 9px;
	border: 0;
	border-radius: 999px;
	background: transparent;
	color: var(--domus-muted, #77695c);
	font-family: inherit;
	font-size: 12px;
	line-height: 22px;
	cursor: pointer;
}

.reasoning-segment__option.active {
	background: var(--domus-copper, #8d5633);
	color: var(--domus-paper, #fff6e8);
	box-shadow: 0 1px 3px rgb(80 48 29 / 18%);
}

.reasoning-segment__option:focus-visible {
	outline: 2px solid var(--domus-amber, #c7832f);
	outline-offset: 1px;
}

.reasoning-segment__option:disabled {
	cursor: not-allowed;
	opacity: 0.55;
}

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
	transition:
		background 0.15s,
		opacity 0.15s;
	white-space: nowrap;
}

.send-btn:hover:not(:disabled) {
	background: #1d4ed8;
}

.send-btn:disabled {
	opacity: 0.4;
	cursor: not-allowed;
}

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

.fade-enter-active,
.fade-leave-active {
	transition: opacity 0.15s;
}

.fade-enter-from,
.fade-leave-to {
	opacity: 0;
}

.slide-up-enter-active,
.slide-up-leave-active {
	transition: all 0.2s ease;
}

.slide-up-enter-from,
.slide-up-leave-to {
	transform: translateY(10px);
	opacity: 0;
}
</style>
