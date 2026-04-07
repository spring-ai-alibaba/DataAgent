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
	<v-app id="app">
		<v-main>
			<BaseDrawer v-model="drawer" :drawer-width="280">
				<template #drawer>
					<div class="d-flex flex-column h-100">
						<div class="pa-4 border-b border-white-5">
							<div class="d-flex align-center mb-3">
								<v-avatar color="primary" size="36" class="mr-3 rounded-lg">
									<v-icon icon="mdi-robot" color="white" size="22" />
								</v-avatar>
								<div>
									<div class="text-subtitle-2 font-weight-bold text-white">
										Spring AI Alibaba
									</div>
									<div
										class="text-caption text-blue-lighten-3 font-weight-bold brand-subtitle"
									>
										DATA AGENT
									</div>
								</div>
							</div>

							<div class="agent-switcher-box">
								<p
									class="text-caption text-blue-lighten-3 mb-2 font-weight-bold"
								>
									当前选择智能体
								</p>
								<v-select
									v-model="selectedAgentId"
									:items="agentOptions"
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
										<div
											class="agent-option agent-option--selection d-flex align-center w-100"
										>
											<v-avatar size="24" class="mr-2 border border-white-10">
												<v-img
													v-if="item.raw.avatar"
													:src="item.raw.avatar"
													cover
												/>
												<v-icon
													v-else
													icon="mdi-robot"
													size="14"
													color="blue-lighten-3"
												/>
											</v-avatar>
											<div class="agent-option__text">
												<div
													class="agent-option__title agent-option__title--active"
												>
													{{ item.raw.title }}
												</div>
												<div class="agent-option__subtitle">
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
												'agent-option--active':
													item.raw.value === selectedAgentId,
											}"
										>
											<template #prepend>
												<v-avatar size="28" class="mr-2 border border-white-10">
													<v-img
														v-if="item.raw.avatar"
														:src="item.raw.avatar"
														cover
													/>
													<v-icon
														v-else
														icon="mdi-robot"
														size="15"
														color="blue-lighten-3"
													/>
												</v-avatar>
											</template>
											<v-list-item-title class="agent-option__title">{{
												item.raw.title
											}}</v-list-item-title>
											<v-list-item-subtitle class="agent-option__subtitle">
												<span class="agent-tags-text">{{
													item.raw.subtitle
												}}</span>
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
						</div>

						<v-list
							v-model:opened="openedGroups"
							density="compact"
							nav
							class="flex-grow-1 pa-2 px-4 custom-scrollbar bg-transparent"
							theme="dark"
						>
							<v-list-item
								prepend-icon="mdi-chat-processing-outline"
								title="数据问答"
								:active="isActive('/chat')"
								class="rounded-lg mb-1 navigation-item"
								color="primary"
								@click="navigateToPath('/chat')"
							/>
							<!-- <v-list-item
								prepend-icon="mdi-chart-box-outline"
								:active="isActive('/dashboard')"
								class="rounded-lg mb-1 navigation-item"
								color="primary"
								title="数据看板"
								@click="navigateToPath('/dashboard')"
							/> -->
							<v-list-item
								prepend-icon="mdi-auto-fix"
								:active="isActive('/prompt-config')"
								class="rounded-lg mb-1 navigation-item"
								color="primary"
								title="提示词配置"
								@click="navigateToPath('/prompt-config')"
							/>

							<v-list-group value="knowledge">
								<template #activator="{ props }">
									<v-list-item
										v-bind="props"
										title="知识库管理"
										class="text-overline text-slate-500 mt-4"
									/>
								</template>
								<v-list-item
									prepend-icon="mdi-book-open-variant"
									title="业务知识配置"
									:active="isActive('/knowledge/business')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/knowledge/business')"
								/>
								<v-list-item
									prepend-icon="mdi-brain"
									title="智能体知识库"
									:active="isActive('/knowledge/agents')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/knowledge/agents')"
								/>
								<v-list-item
									prepend-icon="mdi-vector-intersection"
									title="语义模型配置"
									:active="isActive('/knowledge/semantic-models')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/knowledge/semantic-models')"
								/>
							</v-list-group>

							<v-list-group value="system">
								<template #activator="{ props }">
									<v-list-item
										v-bind="props"
										title="通用设置"
										class="text-overline text-slate-500 mt-2"
									/>
								</template>
								<v-list-item
									prepend-icon="mdi-robot-outline"
									title="智能体管理"
									:active="isActive('/system/agents')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/system/agents')"
								/>
								<v-list-item
									prepend-icon="mdi-database-refresh-outline"
									title="数据连接"
									:active="isActive('/system/data-sources')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/system/data-sources')"
								/>
								<v-list-item
									prepend-icon="mdi-cpu-64-bit"
									title="模型配置"
									:active="isActive('/system/model-config')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/system/model-config')"
								/>
							</v-list-group>

							<div class="mt-6 pt-4 border-t border-white/5">
								<v-list-item
									color="primary"
									density="compact"
									:active="isActive('/agent/new')"
									variant="flat"
									class="rounded-xl mx-2 shadow-lg bg-blue-grey-darken-4 new-agent-item"
									@click="navigateToPath('/agent/new')"
								>
									<div class="d-flex align-center justify-center gap-2 w-100">
										<v-icon icon="mdi-plus-box-outline" size="16" rounded />
										<span class="font-weight-bold text-caption mx-1"
											>新建智能体</span
										>
									</div>
								</v-list-item>
							</div>
						</v-list>

						<!-- <div class="pa-2 border-t border-white/5">
							<v-list-item class="rounded-lg navigation-item logout-item" color="red-lighten-2" @click="logout">
								<template #prepend>
									<v-avatar size="24" color="grey-darken-3"><v-icon icon="mdi-account" size="14" color="white" /></v-avatar>
								</template>
								<v-list-item-title class="text-caption font-weight-bold ms-2">root</v-list-item-title>
								<template #append><v-icon icon="mdi-logout" size="24" color="red" /></template>
							</v-list-item>
						</div> -->
					</div>
				</template>

				<template #header="{ toggle, isOpen }">
					<v-btn icon variant="text" size="small" class="mr-2" @click="toggle">
						<v-icon :icon="isOpen ? 'mdi-menu-open' : 'mdi-menu'" />
					</v-btn>
					<div class="text-subtitle-1 font-weight-medium text-grey-darken-3">
						{{ currentRouteTitle }}
					</div>
					<v-spacer />
					<v-chip
						size="small"
						variant="outlined"
						color="primary"
						class="font-weight-bold"
						>Alibaba Edition</v-chip
					>
				</template>

				<slot />
			</BaseDrawer>
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
import BaseDrawer from '../components/BaseDrawer/index.vue';
import agentService from '~/services/agent/index';
import modelConfigService from '~/services/modelConfig/index';

const { dialogState, handleGlobalConfirm } = useConfirm();
const drawer = ref(true);
const router = useRouter();
const route = useRoute();
// 默认都展开
const openedGroups = ref(['knowledge', 'system']);

type DrawerAgentOption = {
	id: number;
	name: string;
	title: string;
	value: number;
	subtitle: string;
	avatar?: string;
	tags?: string;
};

const agents = ref<DrawerAgentOption[]>([]);
const selectedAgentId = ref<number | undefined>(undefined);
const globalChatModelName = ref('');

const routeTitleMap: Record<string, string> = {
	'/chat': '数据问答',
	'/dashboard': '数据看板',
	'/prompt-config': '提示词配置',
	'/knowledge/business': '业务知识配置',
	'/knowledge/agents': '智能体知识库',
	'/knowledge/semantic-models': '语义模型配置',
	'/system/data-sources': '数据连接',
	'/system/model-config': '模型配置',
	'/system/settings': '通用设置',
	'/agent/new': '新建智能体',
};

const agentOptions = computed(() => agents.value);

const currentRouteTitle = computed(() => {
	if (route.path.startsWith('/agent/') && route.path !== '/agent/new') {
		return '智能体详情';
	}
	return routeTitleMap[route.path] || 'Data Agent';
});

function parseRouteAgentId() {
	const pathId = Number(route.params.id);
	if (
		route.path.startsWith('/agent/') &&
		route.path !== '/agent/new' &&
		Number.isFinite(pathId) &&
		pathId > 0
	) {
		return pathId;
	}
	const queryId = Number(route.query.agentId);
	if (Number.isFinite(queryId) && queryId > 0) {
		return queryId;
	}
	return undefined;
}

function getQueryWithAgentId(agentId?: number) {
	const query: Record<string, string> = {};
	Object.keys(route.query).forEach((key) => {
		const value = route.query[key];
		if (key === 'agentId') return;
		if (Array.isArray(value)) {
			if (value[0]) query[key] = String(value[0]);
		} else if (value !== undefined) {
			query[key] = String(value);
		}
	});
	if (agentId) query.agentId = String(agentId);
	return query;
}

function applyAgentToCurrentRoute(agentId: number, replace = false) {
	if (route.path === '/agent/new') return;
	const target =
		route.path.startsWith('/agent/') && route.path !== '/agent/new'
			? { path: `/agent/${agentId}` }
			: { path: route.path, query: getQueryWithAgentId(agentId) };
	if (replace) {
		router.replace(target);
	} else {
		router.push(target);
	}
}

function navigateToPath(path: string) {
	if (path === '/agent/new') {
		if (route.path !== path) router.push({ path });
		return;
	}
	if (
		route.path === path &&
		path.startsWith('/agent/') &&
		selectedAgentId.value
	) {
		return;
	}
	if (selectedAgentId.value) {
		if (path.startsWith('/agent/') && path !== '/agent/new') {
			router.push({ path: `/agent/${selectedAgentId.value}` });
			return;
		}
		router.push({ path, query: { agentId: String(selectedAgentId.value) } });
		return;
	}
	router.push({ path });
}

function handleAgentSwitch(value: number | string | undefined) {
	const id = Number(value);
	if (!Number.isFinite(id) || id <= 0) return;
	selectedAgentId.value = id;
	applyAgentToCurrentRoute(id);
}

const isActive = (path: string) => route.path === path;

async function loadGlobalModelName() {
	try {
		const configs = await modelConfigService.list();
		const activeChat = configs.find(
			(item) => item.modelType === 'CHAT' && item.isActive,
		);
		globalChatModelName.value = activeChat?.modelName || '';
	} catch (e) {
		console.error('Failed to load global model name', e);
	}
}

async function loadAgents() {
	const list = await agentService.list();
	agents.value = list
		.filter((item) => item.id !== undefined && item.id > 0)
		.map((item) => {
			const raw = item as unknown as Record<string, unknown>;
			return {
				id: item.id as number,
				name: item.name || `Agent ${item.id}`,
				title: item.name || `Agent ${item.id}`,
				value: item.id as number,
				subtitle: typeof raw.tags === 'string' ? raw.tags : '',
				avatar: typeof raw.avatar === 'string' ? raw.avatar : undefined,
				tags: typeof raw.tags === 'string' ? raw.tags : '',
			};
		});
}

function syncSelectedFromRoute() {
	const routeAgentId = parseRouteAgentId();
	if (routeAgentId && agents.value.some((item) => item.id === routeAgentId)) {
		selectedAgentId.value = routeAgentId;
	}
}

onMounted(async () => {
	await loadGlobalModelName();
	await loadAgents();
	syncSelectedFromRoute();
	const firstAgent = agents.value[0];
	if (!selectedAgentId.value && firstAgent?.id) {
		selectedAgentId.value = firstAgent.id;
		applyAgentToCurrentRoute(selectedAgentId.value, true);
	}
});

watch(
	() => route.fullPath,
	() => {
		syncSelectedFromRoute();
	},
);
</script>

<style scoped>
.border-white-5 {
	border-color: rgba(255, 255, 255, 0.05) !important;
}

.brand-subtitle {
	font-size: 10px;
	letter-spacing: 1px;
}

.agent-switcher :deep(.v-field) {
	background: rgba(30, 41, 59, 0.8);
	border-radius: 10px;
}

.agent-switcher :deep(.v-field__input),
.agent-switcher :deep(.v-field-label),
.agent-switcher :deep(.v-icon) {
	color: #dbeafe;
}

:deep(.agent-switcher-menu) {
	background: #1e293b !important;
	border: 1px solid rgba(59, 130, 246, 0.3) !important;
	border-radius: 12px !important;
	overflow: hidden;
}

:deep(.agent-switcher-menu .v-list) {
	background: transparent !important;
	padding: 4px !important;
}

:deep(.agent-switcher-menu .v-list-item) {
	border-radius: 8px !important;
	margin-bottom: 2px !important;
	min-height: 44px !important;
}

:deep(.agent-switcher-menu .v-list-item:hover) {
	background: rgba(59, 130, 246, 0.12) !important;
}

.agent-option__text {
	min-width: 0;
	flex: 1;
}

.agent-option__title {
	font-size: 13px;
	line-height: 1.2;
	font-weight: 600;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	max-width: 170px;
}

.agent-option__title--active {
	color: #60a5fa;
}

.agent-option__subtitle {
	font-size: 10px;
	line-height: 1.2;
	color: #94a3b8;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	max-width: 170px;
	margin-top: 2px;
}

.agent-tags-text {
	background: rgba(59, 130, 246, 0.15);
	color: #93c5fd;
	padding: 1px 6px;
	border-radius: 4px;
	font-size: 9px;
	border: 1px solid rgba(59, 130, 246, 0.2);
}

.agent-option--selection .agent-option__title {
	max-width: 145px;
}

.agent-option--selection .agent-option__subtitle {
	max-width: 145px;
}

.navigation-item {
	--v-list-item-padding-start: 16px;
	--v-list-item-min-height: 36px;
}

.navigation-sub-item {
	--v-list-item-padding-start: 28px;
	--v-list-item-min-height: 36px;
}

.custom-scrollbar::-webkit-scrollbar {
	width: 4px;
}

.custom-scrollbar::-webkit-scrollbar-track {
	background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
	background: rgba(255, 255, 255, 0.1);
	border-radius: 4px;
}

.custom-scrollbar::-webkit-scrollbar-thumb:hover {
	background: rgba(255, 255, 255, 0.2);
}

:deep(.v-list-group__items .v-list-item) {
	padding-inline-start: 16px !important;
}

:deep(.flex-grow-1.v-list .v-list-item) {
	min-height: 36px !important;
}

:deep(.v-list-item__spacer) {
	width: 12px !important;
}
</style>
