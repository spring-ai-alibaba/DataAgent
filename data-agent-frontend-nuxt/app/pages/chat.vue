<template>
	<div class="chat-page">
		<ChatSidebar />
		<div class="chat-body">
			<ChatMessageList />
			<ChatInputArea />
		</div>
	</div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, watch } from 'vue';
import { useChatStore } from '~/stores/chat';
import agentService from '~/services/agent/index';
import modelConfigService from '~/services/modelConfig/index';
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

	// Load active model
	try {
		const configs = await modelConfigService.list();
		const active = configs.find(c => c.modelType === 'CHAT' && c.isActive);
		store.activeChatModel = active?.modelName || '';
	} catch { /* ignore */ }

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
</style>
