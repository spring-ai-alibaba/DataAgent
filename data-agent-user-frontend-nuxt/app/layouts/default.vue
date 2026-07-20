<template>
	<v-app class="domus-app">
		<header class="product-header">
			<a
				class="brand-lockup"
				href="https://www.domus.cn/"
				target="_blank"
				rel="noopener noreferrer"
				aria-label="访问 Domus 官网"
				title="访问 Domus 官网"
			>
				<img src="/logo.png" alt="Domus" class="brand-mark" />
				<span class="brand-name">Domus</span>
				<span class="brand-product">智能问数</span>
			</a>

			<nav class="product-nav" aria-label="产品导航">
				<button
					type="button"
					class="product-nav__item"
					:class="{ 'product-nav__item--active': route.path === '/chat' }"
					@click="navigate('/chat')"
				>
					<v-icon icon="mdi-chat-processing-outline" size="17" />
					<span>智能问数</span>
				</button>
				<button
					type="button"
					class="product-nav__item"
					:class="{ 'product-nav__item--active': route.path === '/model-config' }"
					@click="navigate('/model-config')"
				>
					<v-icon icon="mdi-cpu-64-bit" size="17" />
					<span>模型服务</span>
				</button>
			</nav>

			<div class="agent-control">
				<span class="agent-control__label">当前智能体</span>
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
					placeholder="选择智能体"
					class="agent-switcher"
					menu-icon="mdi-chevron-down"
					:menu-props="{ contentClass: 'agent-switcher-menu', offset: [0, 8] }"
					@update:model-value="handleAgentSwitch"
				>
					<template #selection="{ item }">
						<div class="agent-option agent-option--selection">
							<v-avatar size="25" class="agent-option__avatar">
								<v-img v-if="item.raw.avatar" :src="item.raw.avatar" cover />
								<img v-else src="/logo-mark.png" alt="" />
							</v-avatar>
							<span class="agent-option__title">{{ item.raw.title }}</span>
						</div>
					</template>
					<template #item="{ props, item }">
						<v-list-item
							v-bind="props"
							:title="undefined"
							:subtitle="undefined"
							class="agent-option"
							:class="{ 'agent-option--active': item.raw.value === selectedAgentId }"
						>
							<template #prepend>
								<v-avatar size="30" class="agent-option__avatar">
									<v-img v-if="item.raw.avatar" :src="item.raw.avatar" cover />
									<img v-else src="/logo-mark.png" alt="" />
								</v-avatar>
							</template>
							<v-list-item-title class="agent-option__title">{{ item.raw.title }}</v-list-item-title>
							<v-list-item-subtitle v-if="item.raw.subtitle" class="agent-option__subtitle">
								{{ item.raw.subtitle }}
							</v-list-item-subtitle>
							<template #append>
								<v-icon v-if="item.raw.value === selectedAgentId" icon="mdi-check" color="primary" size="17" />
							</template>
						</v-list-item>
					</template>
				</v-select>
			</div>
		</header>

		<v-main class="product-main">
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
const loadingAgents = ref(false);
const selectedAgentId = ref<number>();
const agentOptions = ref<AgentOption[]>([]);

function routeAgentId() {
	const value = Number(route.query.agentId);
	return Number.isFinite(value) && value > 0 ? value : undefined;
}

function navigate(path: string) {
	const query = selectedAgentId.value ? { agentId: String(selectedAgentId.value) } : undefined;
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
		const requestedExists = agentOptions.value.some((agent) => agent.value === requestedId);
		selectedAgentId.value = requestedExists ? requestedId : agentOptions.value[0]?.value;

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
.product-header {
	position: fixed;
	inset: 0 0 auto;
	z-index: 1005;
	display: grid;
	grid-template-columns: minmax(220px, 1fr) auto minmax(260px, 1fr);
	align-items: center;
	gap: 24px;
	height: var(--domus-header-height);
	padding: 10px clamp(20px, 4vw, 58px);
	background: rgb(244 234 220 / 92%);
	border-bottom: 1px solid var(--domus-line);
	backdrop-filter: blur(16px);
}

.brand-lockup {
	display: inline-flex;
	align-items: center;
	justify-self: start;
	gap: 10px;
	min-width: 0;
	padding: 0;
	border: 0;
	background: transparent;
	cursor: pointer;
	color: inherit;
	text-decoration: none;
}

.brand-lockup:hover .brand-mark {
	transform: scale(1.04);
}

.brand-mark {
	width: 54px;
	height: 54px;
	object-fit: contain;
	transition: transform 0.18s ease;
}

.brand-name {
	font-size: 27px;
	font-weight: 700;
	line-height: 1;
}

.brand-product {
	padding-left: 10px;
	border-left: 1px solid var(--domus-line-strong);
	color: var(--domus-muted);
	font-size: 12px;
	font-weight: 700;
	white-space: nowrap;
}

.product-nav {
	display: flex;
	gap: 4px;
	padding: 6px;
	border: 1px solid var(--domus-line);
	border-radius: 999px;
	background: rgb(255 246 232 / 42%);
}

.product-nav__item {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	height: 38px;
	padding: 0 16px;
	border: 0;
	border-radius: 999px;
	background: transparent;
	color: var(--domus-muted);
	font-size: 14px;
	cursor: pointer;
	white-space: nowrap;
}

.product-nav__item:hover,
.product-nav__item--active {
	background: rgb(37 27 21 / 7%);
	color: var(--domus-ink);
}

.product-nav__item--active {
	font-weight: 700;
}

.agent-control {
	display: flex;
	align-items: center;
	justify-self: end;
	gap: 10px;
	min-width: 0;
}

.agent-control__label {
	color: var(--domus-muted);
	font-size: 11px;
	font-weight: 700;
	white-space: nowrap;
}

.agent-switcher {
	width: min(230px, 20vw);
}

.agent-switcher :deep(.v-field) {
	border-radius: 999px;
	background: var(--domus-paper);
}

.agent-switcher :deep(.v-field__outline) {
	--v-field-border-opacity: 0.2;
}

.agent-option {
	color: var(--domus-ink);
}

.agent-option--selection {
	display: flex;
	align-items: center;
	min-width: 0;
}

.agent-option__avatar {
	margin-right: 8px;
	border: 1px solid var(--domus-line);
	background: var(--domus-cream);
}

.agent-option__avatar img {
	width: 100%;
	height: 100%;
	object-fit: cover;
}

.agent-option__title {
	overflow: hidden;
	color: var(--domus-ink);
	font-size: 13px;
	font-weight: 700;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.agent-option__subtitle {
	color: var(--domus-muted);
	font-size: 11px;
}

.product-main {
	padding-top: var(--domus-header-height);
	background: transparent;
}

@media (max-width: 900px) {
	.product-header {
		grid-template-columns: auto 1fr auto;
		gap: 10px;
		padding-inline: 14px;
	}

	.brand-name,
	.brand-product,
	.agent-control__label {
		display: none;
	}

	.brand-mark {
		width: 42px;
		height: 42px;
	}

	.product-nav {
		justify-self: center;
	}

	.product-nav__item {
		padding-inline: 11px;
	}

	.product-nav__item .v-icon {
		display: none;
	}

	.agent-switcher {
		width: 48px;
	}

	.agent-switcher :deep(.v-field__input) {
		padding-inline-start: 8px;
	}

	.agent-option__title,
	.agent-switcher :deep(.v-field__append-inner) {
		display: none;
	}

	.agent-option__avatar {
		margin-right: 0;
	}
}

@media (max-width: 430px) {
	.product-header {
		grid-template-columns: 1fr auto;
		height: 72px;
	}

	.brand-lockup {
		display: none;
	}

	.product-nav {
		justify-self: start;
	}

	.product-main {
		padding-top: 72px;
	}
}
</style>
