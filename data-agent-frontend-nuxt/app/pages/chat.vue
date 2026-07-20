/* * Copyright 2026 the original author or authors. * * Licensed under the
Apache License, Version 2.0 (the "License"); * you may not use this file except
in compliance with the License. * You may obtain a copy of the License at * *
https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable
law or agreed to in writing, software * distributed under the License is
distributed on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. * See the License for the specific language governing
permissions and * limitations under the License. */

<template>
	<div class="chat-page">
		<ChatSidebar />
		<div class="chat-body">
			<v-btn
				v-if="store.chatSidebarCollapsed"
				icon
				variant="text"
				size="small"
				class="sidebar-reopen-btn"
				title="展开历史会话"
				aria-label="展开历史会话"
				@click="store.chatSidebarCollapsed = false"
			>
				<v-icon size="18">mdi-history</v-icon>
			</v-btn>
			<ChatMessageList />
			<ChatInputArea />
		</div>
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
	} catch {
		/* ignore */
	}

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
		store.isReportStreaming = false;
		store.nodeBlocks = [];
		store.streamingReportContent = '';
		store.showHumanFeedback = false;
		store.feedbackContent = '';
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
	background: rgb(255 246 232 / 38%);
}

.chat-body {
	position: relative;
	flex: 1;
	display: flex;
	flex-direction: column;
	overflow: hidden;
	min-width: 0;
	background: rgb(255 246 232 / 76%);
	border-left: 1px solid var(--domus-line);
}

.sidebar-reopen-btn {
	position: absolute !important;
	top: 12px;
	left: 12px;
	z-index: 20;
	color: var(--domus-copper) !important;
	background: rgb(255 246 232 / 94%) !important;
	border: 1px solid var(--domus-line) !important;
	box-shadow: 0 3px 12px rgb(80 48 29 / 12%) !important;
}

.sidebar-reopen-btn:hover {
	background: rgb(199 131 47 / 14%) !important;
}

@media (max-width: 800px) {
	.chat-body {
		border-left: 0;
	}
}
</style>
