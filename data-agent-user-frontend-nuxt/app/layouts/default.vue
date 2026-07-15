<template>
	<v-app>
		<v-navigation-drawer v-model="drawer" :width="272" color="#111827">
			<div class="user-shell__brand">
				<v-avatar color="primary" size="38" rounded="lg">
					<v-icon icon="mdi-chart-box-outline" color="white" />
				</v-avatar>
				<div>
					<div class="user-shell__brand-name">智能问数</div>
					<div class="user-shell__brand-subtitle">DATA AGENT</div>
				</div>
			</div>

			<div class="user-shell__agent">
				<div class="user-shell__label">当前智能体</div>
				<v-select
					v-model="selectedAgentId"
					:items="agentOptions"
					:loading="loadingAgents"
					:no-data-text="loadingAgents ? '加载中' : '暂无已发布智能体'"
					item-title="title"
					item-value="value"
					variant="outlined"
					density="compact"
					hide-details
					placeholder="请选择智能体"
					class="agent-switcher"
					menu-icon="mdi-chevron-down"
					theme="dark"
					:menu-props="{
						contentClass: 'agent-switcher-menu',
						offset: [0, 8],
					}"
					:list-props="{ bgColor: '#1e293b', theme: 'dark' }"
					item-color="blue-lighten-2"
					@update:model-value="handleAgentSwitch"
				>
					<template #selection="{ item }">
						<div class="agent-option agent-option--selection d-flex align-center w-100">
							<v-avatar size="24" class="mr-2 agent-option__avatar">
								<v-img v-if="item.raw.avatar" :src="item.raw.avatar" cover />
								<v-icon
									v-else
									icon="mdi-robot"
									size="14"
									color="blue-lighten-3"
								/>
							</v-avatar>
							<div class="agent-option__text">
								<div class="agent-option__title agent-option__title--active">
									{{ item.raw.title }}
								</div>
								<div v-if="item.raw.subtitle" class="agent-option__subtitle">
									{{ item.raw.subtitle }}
								</div>
							</div>
						</div>
					</template>
					<template #item="{ props, item }">
						<v-list-item
							v-bind="props"
							:title="undefined"
							:subtitle="undefined"
							class="agent-option"
							:class="{
								'agent-option--active': item.raw.value === selectedAgentId,
							}"
						>
							<template #prepend>
								<v-avatar size="28" class="mr-2 agent-option__avatar">
									<v-img v-if="item.raw.avatar" :src="item.raw.avatar" cover />
									<v-icon
										v-else
										icon="mdi-robot"
										size="15"
										color="blue-lighten-3"
									/>
								</v-avatar>
							</template>
							<v-list-item-title class="agent-option__title">
								{{ item.raw.title }}
							</v-list-item-title>
							<v-list-item-subtitle v-if="item.raw.subtitle" class="agent-option__subtitle">
								<span class="agent-tags-text">{{ item.raw.subtitle }}</span>
							</v-list-item-subtitle>
							<template #append>
								<v-icon
									v-if="item.raw.value === selectedAgentId"
									icon="mdi-check"
									color="blue-lighten-2"
									size="16"
								/>
							</template>
						</v-list-item>
					</template>
				</v-select>
			</div>

			<v-list nav density="comfortable" bg-color="transparent" class="user-shell__nav">
				<v-list-item
					prepend-icon="mdi-chat-processing-outline"
					title="数据问答"
					:active="route.path === '/chat'"
					@click="navigate('/chat')"
				/>
				<v-list-item
					prepend-icon="mdi-cpu-64-bit"
					title="模型配置"
					:active="route.path === '/model-config'"
					@click="navigate('/model-config')"
				/>
			</v-list>

		</v-navigation-drawer>

		<v-app-bar flat border density="comfortable">
			<v-app-bar-nav-icon @click="drawer = !drawer" />
			<v-app-bar-title>{{ pageTitle }}</v-app-bar-title>
			<v-chip size="small" color="primary" variant="tonal">智能问数</v-chip>
			<div class="mr-4" />
		</v-app-bar>

		<v-main>
			<slot />
		</v-main>

		<ConfirmDialog
			v-model="dialogState.isVisible"
			:title="dialogState.title"
			:message="dialogState.message"
			:prepend-icon="dialogState.icon"
			:confirm-text="dialogState.confirmText"
			@confirm="handleGlobalConfirm"
		/>
		<Tip />
	</v-app>
</template>

<script setup lang="ts">
import agentService from '~/services/agent/index';
import { useConfirm } from '~/composables/useConfirm/index';

type AgentOption = {
	title: string;
	value: number;
	subtitle: string;
	avatar?: string;
};

const { dialogState, handleGlobalConfirm } = useConfirm();
const route = useRoute();
const router = useRouter();
const drawer = ref(true);
const loadingAgents = ref(false);
const selectedAgentId = ref<number>();
const agentOptions = ref<AgentOption[]>([]);

const pageTitle = computed(() =>
	route.path === '/model-config' ? '模型配置' : '数据问答',
);

function routeAgentId() {
	const value = Number(route.query.agentId);
	return Number.isFinite(value) && value > 0 ? value : undefined;
}

function navigate(path: string) {
	const query = selectedAgentId.value
		? { agentId: String(selectedAgentId.value) }
		: undefined;
	router.push({ path, query });
}

function handleAgentSwitch(value: number | string | undefined) {
	const id = Number(value);
	if (!Number.isFinite(id) || id <= 0) return;
	selectedAgentId.value = id;
	router.push({ path: route.path, query: { ...route.query, agentId: String(id) } });
}

async function loadPublishedAgents() {
	loadingAgents.value = true;
	try {
		const agents = await agentService.list('published');
		agentOptions.value = agents
			.filter((agent) => agent.id !== undefined && agent.id > 0)
			.map((agent) => ({
				title: agent.name || `Agent ${agent.id}`,
				value: agent.id as number,
				subtitle: agent.tags || '',
				avatar: agent.avatar || undefined,
			}));

		const requestedId = routeAgentId();
		const requestedExists = agentOptions.value.some(
			(agent) => agent.value === requestedId,
		);
		selectedAgentId.value = requestedExists
			? requestedId
			: agentOptions.value[0]?.value;

		if (selectedAgentId.value !== requestedId) {
			await router.replace({
				path: route.path,
				query: selectedAgentId.value
					? { ...route.query, agentId: String(selectedAgentId.value) }
					: {},
			});
		}
	} finally {
		loadingAgents.value = false;
	}
}

onMounted(loadPublishedAgents);

watch(
	() => route.query.agentId,
	() => {
		const id = routeAgentId();
		if (id && agentOptions.value.some((agent) => agent.value === id)) {
			selectedAgentId.value = id;
		}
	},
);
</script>

<style scoped>
.user-shell__brand {
	display: flex;
	align-items: center;
	gap: 12px;
	padding: 22px 20px 18px;
	border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.user-shell__brand-name {
	color: #fff;
	font-size: 15px;
	font-weight: 700;
}

.user-shell__brand-subtitle,
.user-shell__label {
	color: #94a3b8;
	font-size: 11px;
}

.user-shell__brand-subtitle {
	margin-top: 2px;
}

.user-shell__agent {
	padding: 18px 16px 10px;
}

.user-shell__label {
	margin: 0 4px 8px;
	font-weight: 700;
	color: #bfdbfe;
}

.agent-switcher :deep(.v-field) {
	background: rgba(30, 41, 59, 0.8);
	border-radius: 8px;
}

.agent-switcher :deep(.v-field__input),
.agent-switcher :deep(.v-field-label),
.agent-switcher :deep(.v-icon) {
	color: #dbeafe;
}

:deep(.agent-switcher-menu) {
	background: #1e293b !important;
	border: 1px solid rgba(59, 130, 246, 0.3) !important;
	border-radius: 8px !important;
	overflow: hidden;
}

:deep(.agent-switcher-menu .v-list) {
	background: transparent !important;
	padding: 4px !important;
}

:deep(.agent-switcher-menu .v-list-item) {
	min-height: 48px !important;
	margin-bottom: 2px !important;
	border-radius: 6px !important;
}

:deep(.agent-switcher-menu .v-list-item:hover),
:deep(.agent-switcher-menu .agent-option--active) {
	background: rgba(59, 130, 246, 0.12) !important;
}

.agent-option__avatar {
	border: 1px solid rgba(255, 255, 255, 0.12);
	background: rgba(51, 65, 85, 0.9);
}

.agent-option__text {
	min-width: 0;
	flex: 1;
}

.agent-option__title {
	max-width: 170px;
	overflow: hidden;
	font-size: 13px;
	font-weight: 600;
	line-height: 1.2;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.agent-option__title--active {
	color: #60a5fa;
}

.agent-option__subtitle {
	max-width: 170px;
	margin-top: 2px;
	overflow: hidden;
	color: #94a3b8;
	font-size: 10px;
	line-height: 1.2;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.agent-tags-text {
	padding: 1px 6px;
	border-radius: 4px;
	background: rgba(59, 130, 246, 0.15);
	color: #93c5fd;
}

.user-shell__nav {
	padding: 10px 12px;
}

.user-shell__nav :deep(.v-list-item) {
	margin-bottom: 4px;
	border-radius: 6px;
}

</style>
