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
	<div v-if="currentAgentId" class="chat-page">
		<ChatSidebar />
		<div class="chat-body">
			<ChatMessageList />
			<ChatInputArea />
		</div>
	</div>
	<div v-else class="empty-agent-state">
		<v-icon icon="mdi-robot-off-outline" size="48" color="grey-lighten-1" />
		<h2>暂无可用智能体</h2>
		<p>请联系管理员发布智能体后再开始数据问答。</p>
	</div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, watch, computed } from 'vue';
import { useChatStore } from '~/stores/chat';
import agentService from '~/services/agent/index';
import ChatSidebar from '~/components/chat/ChatSidebar.vue';
import ChatMessageList from '~/components/chat/ChatMessageList.vue';
import ChatInputArea from '~/components/chat/ChatInputArea.vue';

const route = useRoute();
const store = useChatStore();

const currentAgentId = computed(() => {
	const q = route.query.agentId;
	return q ? Number(q) : undefined;
});

async function init(agentId: number) {
	store.currentAgentId = agentId;

	// Load agent info
	try {
		const agent = await agentService.get(agentId);
		if (agent) {
			store.currentAgentName = agent.name || '';
			store.currentAgentAvatar = agent.avatar || '';
			store.currentAgentDescription = agent.description || '';
		}
	} catch { /* ignore */ }

	// Load active model — handled by store.loadSessions

	store.connectSessionStream(agentId);
	await store.loadSessions(agentId);
}

onMounted(async () => {
	if (currentAgentId.value) await init(currentAgentId.value);
});

watch(currentAgentId, async (newId, oldId) => {
	if (newId && newId !== oldId) {
		store.sessions = [];
		store.currentSession = null;
		store.currentMessages = [];
		store.isStreaming = false;
		store.nodeBlocks = [];
		await init(newId);
	}
});

onUnmounted(() => {
	store.disconnectSessionStream();
});
</script>

<style scoped>
.chat-page {
	display: flex;
	height: calc(100vh - 64px);
	overflow: hidden;
	background: #f1f5f9;
}

.chat-body {
	flex: 1;
	display: flex;
	flex-direction: column;
	overflow: hidden;
	min-width: 0;
	background: #fff;
}

.empty-agent-state {
	height: calc(100vh - 49px);
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 12px;
	background: #f8fafc;
	color: #64748b;
}

.empty-agent-state h2 {
	margin: 4px 0 0;
	font-size: 20px;
	color: #334155;
}

.empty-agent-state p {
	margin: 0;
	font-size: 14px;
}
</style>
