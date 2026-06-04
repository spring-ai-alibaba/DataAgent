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
	<section class="page-shell">
		<KnowledgePageHeader
			title="提示词配置"
			subtitle="维护增强式提示词配置，支持多配置启用、批量操作与优先级管理。"
		>
			<template #actions>
				<v-btn
					class="text-none bg-white"
					style="border-color: #e2e8f0"
					variant="outlined"
					prepend-icon="mdi-refresh"
					:loading="loading"
					@click="loadConfigs"
				>
					刷新
				</v-btn>
				<v-btn
					v-if="selectedIds.length > 0"
					color="blue-darken-1"
					prepend-icon="mdi-check-circle"
					class="text-none"
					elevation="0"
					@click="batchEnable"
				>
					批量启用
				</v-btn>
				<v-btn
					v-if="selectedIds.length > 0"
					color="orange-darken-1"
					prepend-icon="mdi-pause-circle"
					class="text-none"
					elevation="0"
					@click="batchDisable"
				>
					批量禁用
				</v-btn>
				<v-btn
					color="blue-darken-3"
					prepend-icon="mdi-plus"
					class="text-none px-6"
					elevation="0"
					@click="openCreateDialog"
				>
					添加配置
				</v-btn>
			</template>
		</KnowledgePageHeader>

		<v-card variant="flat" border class="rounded-lg mb-4 pa-4">
			<div class="d-flex flex-wrap ga-3 align-center">
				<v-select
					v-model="selectedAgentId"
					:items="agentOptions"
					label="智能体"
					item-title="title"
					item-value="value"
					variant="outlined"
					density="compact"
					hide-details
					style="max-width: 220px"
					@update:model-value="handleFilterChange"
				/>
				<v-select
					v-model="promptType"
					:items="promptTypeOptions"
					label="提示词类型"
					item-title="title"
					item-value="value"
					variant="outlined"
					density="compact"
					hide-details
					style="max-width: 220px"
					@update:model-value="handleFilterChange"
				/>
				<v-text-field
					v-model="searchKeyword"
					placeholder="搜索名称、描述、内容"
					prepend-inner-icon="mdi-magnify"
					variant="outlined"
					density="compact"
					clearable
					hide-details
					class="search-field"
					style="max-width: 320px"
				/>
				<v-spacer />
				<v-chip
					color="blue-lighten-5"
					variant="flat"
					class="font-weight-medium"
				>
					{{ filteredConfigs.length }} 条
				</v-chip>
			</div>
		</v-card>

		<v-card variant="flat" border class="rounded-lg">
			<v-data-table
				v-model="selectedIds"
				:headers="headers"
				:items="filteredConfigs"
				item-value="id"
				show-select
				hover
				:loading="loading"
			>
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.description="{ item }">
					<v-tooltip
						v-if="item.description && item.description.length > 24"
						:text="item.description"
						location="top"
					>
						<template #activator="{ props }">
							<span
								v-bind="props"
								class="text-truncate d-inline-block"
								style="max-width: 180px; cursor: help"
							>
								{{ item.description }}
							</span>
						</template>
					</v-tooltip>
					<span v-else>{{ item.description || '—' }}</span>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.optimizationPrompt="{ item }">
					<v-tooltip
						v-if="
							item.optimizationPrompt && item.optimizationPrompt.length > 40
						"
						:text="item.optimizationPrompt"
						location="top"
					>
						<template #activator="{ props }">
							<span
								v-bind="props"
								class="text-truncate d-inline-block"
								style="max-width: 260px; cursor: help"
							>
								{{ item.optimizationPrompt }}
							</span>
						</template>
					</v-tooltip>
					<span v-else>{{ item.optimizationPrompt }}</span>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.enabled="{ item }">
					<v-chip
						:color="item.enabled ? 'success' : 'grey'"
						size="small"
						variant="tonal"
					>
						{{ item.enabled ? '启用' : '禁用' }}
					</v-chip>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.actions="{ item }">
					<div class="d-flex ga-1 align-center">
						<v-btn
							size="small"
							variant="text"
							color="blue-darken-1"
							icon="mdi-pencil"
							@click="editConfig(item)"
						/>
						<v-btn
							size="small"
							variant="text"
							color="amber-darken-2"
							icon="mdi-sort-numeric-descending"
							@click="openPriorityDialog(item)"
						/>
						<v-btn
							size="small"
							variant="text"
							:color="item.enabled ? 'orange-darken-1' : 'success'"
							:icon="item.enabled ? 'mdi-pause-circle' : 'mdi-check-circle'"
							@click="toggleEnabled(item)"
						>
							<v-tooltip activator="parent" location="top">{{
								item.enabled ? '禁用' : '启用'
							}}</v-tooltip>
						</v-btn>
						<v-btn
							size="small"
							variant="text"
							color="red-darken-1"
							icon="mdi-delete"
							@click="deleteConfig(item)"
						/>
					</div>
				</template>

				<template #no-data>
					<div class="d-flex flex-column align-center py-12">
						<v-icon
							icon="mdi-text-box-edit-outline"
							size="64"
							color="blue-lighten-3"
							class="mb-4"
						/>
						<p class="text-body-1 text-medium-emphasis mb-2">暂无提示词配置</p>
						<p class="text-body-2 text-disabled mb-6">
							点击「添加配置」开始创建增强提示词
						</p>
						<v-btn
							color="blue-darken-3"
							prepend-icon="mdi-plus"
							class="text-none"
							elevation="0"
							@click="openCreateDialog"
						>
							添加配置
						</v-btn>
					</div>
				</template>
			</v-data-table>
		</v-card>

		<v-dialog v-model="dialogVisible" max-width="760" persistent>
			<v-card rounded="lg">
				<v-card-title class="d-flex align-center pa-6 pb-4">
					<v-icon
						:icon="isEdit ? 'mdi-pencil-circle' : 'mdi-plus-circle'"
						color="blue-darken-2"
						class="mr-3"
						size="28"
					/>
					<span class="text-h6 font-weight-bold">{{
						isEdit ? '编辑提示词配置' : '添加提示词配置'
					}}</span>
					<v-spacer />
					<v-btn
						icon="mdi-close"
						variant="text"
						size="small"
						@click="closeDialog"
					/>
				</v-card-title>
				<v-divider />

				<v-card-text class="pa-6">
					<v-form ref="formRef">
						<div class="mb-4">
							<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">
								配置名称 <span class="text-error">*</span>
							</p>
							<v-text-field
								v-model="formData.name"
								placeholder="请输入配置名称"
								variant="outlined"
								density="compact"
								:rules="[(v) => !!v?.trim() || '配置名称不能为空']"
								hide-details="auto"
							/>
						</div>
						<div class="mb-4">
							<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">
								配置描述
							</p>
							<v-text-field
								v-model="formData.description"
								placeholder="请输入配置描述"
								variant="outlined"
								density="compact"
								hide-details="auto"
							/>
						</div>
						<div class="mb-4">
							<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">
								优化提示词内容 <span class="text-error">*</span>
							</p>
							<v-textarea
								v-model="formData.optimizationPrompt"
								placeholder="请输入优化提示词内容，支持模板变量"
								variant="outlined"
								density="compact"
								rows="5"
								:rules="[(v) => !!v?.trim() || '优化提示词不能为空']"
								hide-details="auto"
							/>
						</div>
						<v-row>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									优先级
								</p>
								<v-text-field
									v-model.number="formData.priority"
									type="number"
									min="0"
									max="100"
									variant="outlined"
									density="compact"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									显示顺序
								</p>
								<v-text-field
									v-model.number="formData.displayOrder"
									type="number"
									min="0"
									variant="outlined"
									density="compact"
									hide-details="auto"
								/>
							</v-col>
						</v-row>
					</v-form>
				</v-card-text>

				<v-divider />
				<v-card-actions class="pa-4 d-flex justify-end ga-2">
					<v-btn variant="outlined" class="text-none px-6" @click="closeDialog"
						>取消</v-btn
					>
					<v-btn
						color="blue-darken-3"
						class="text-none px-6"
						elevation="0"
						:loading="saveLoading"
						@click="saveConfig"
					>
						{{ isEdit ? '保存更新' : '立即创建' }}
					</v-btn>
				</v-card-actions>
			</v-card>
		</v-dialog>

		<v-dialog v-model="priorityDialogVisible" max-width="480" persistent>
			<v-card rounded="lg">
				<v-card-title class="d-flex align-center pa-6 pb-4">
					<v-icon
						icon="mdi-sort-numeric-descending"
						color="blue-darken-2"
						class="mr-3"
						size="26"
					/>
					<span class="text-h6 font-weight-bold">设置优先级</span>
					<v-spacer />
					<v-btn
						icon="mdi-close"
						variant="text"
						size="small"
						@click="closePriorityDialog"
					/>
				</v-card-title>
				<v-divider />
				<v-card-text class="pa-6">
					<v-text-field
						v-model.number="priorityValue"
						type="number"
						label="优先级 (0-100)"
						min="0"
						max="100"
						variant="outlined"
						density="compact"
						hide-details="auto"
					/>
				</v-card-text>
				<v-card-actions class="pa-4 d-flex justify-end ga-2">
					<v-btn
						variant="outlined"
						class="text-none"
						@click="closePriorityDialog"
						>取消</v-btn
					>
					<v-btn
						color="blue-darken-3"
						class="text-none"
						elevation="0"
						@click="updatePriority"
						>保存</v-btn
					>
				</v-card-actions>
			</v-card>
		</v-dialog>
	</section>
</template>

<script setup lang="ts">
import agentService from '~/services/agent/index';
import { promptService, type PromptConfig } from '~/services/prompt/index';
import { useCrudPage } from '~/composables/useCrudPage/index';

const route = useRoute();
const { $tip } = useNuxtApp();
const { showConfirm } = useConfirm();

// ——— 额外状态 ———
const priorityDialogVisible = ref(false);
const selectedAgentId = ref<number | undefined>(undefined);
const promptType = ref('report-generator');
const searchKeyword = ref('');
const selectedIds = ref<number[]>([]);
const editingId = ref<number | undefined>(undefined);
const priorityEditId = ref<number | undefined>(undefined);
const priorityValue = ref(0);
const rawConfigs = ref<PromptConfig[]>([]);
const agentOptions = ref<{ title: string; value: number }[]>([]);

const headers = [
	{ title: '名称', key: 'name', minWidth: '140px' },
	{ title: '描述', key: 'description', minWidth: '160px', sortable: false },
	{
		title: '优化提示词',
		key: 'optimizationPrompt',
		minWidth: '240px',
		sortable: false,
	},
	{ title: '优先级', key: 'priority', width: '90px' },
	{ title: '顺序', key: 'displayOrder', width: '90px' },
	{ title: '状态', key: 'enabled', width: '100px', sortable: false },
	{ title: '操作', key: 'actions', width: '170px', sortable: false },
];

const promptTypeOptions = [
	{ title: '报表生成', value: 'report-generator' },
	{ title: '任务规划', value: 'planner' },
	{ title: 'SQL 生成', value: 'sql-generator' },
	{ title: '通用问答', value: 'general-chat' },
];

const filteredConfigs = computed(() => {
	const keyword = searchKeyword.value.trim().toLowerCase();
	if (!keyword) return rawConfigs.value;
	return rawConfigs.value.filter((item) =>
		[item.name, item.description, item.optimizationPrompt]
			.filter(Boolean)
			.some((text) => String(text).toLowerCase().includes(keyword)),
	);
});

// ——— useCrudPage ———
const {
	loading,
	saveLoading,
	dialogVisible,
	isEdit,
	formRef,
	formData,
	openCreateDialog: _openCreateDialog,
	closeDialog: _closeDialog,
} = useCrudPage<PromptConfig>({
	loadFn: async () => {
		const list = await promptService.listByType(
			promptType.value,
			selectedAgentId.value,
		);
		list.sort((a, b) => {
			const orderDiff = (a.displayOrder ?? 0) - (b.displayOrder ?? 0);
			if (orderDiff !== 0) return orderDiff;
			return (b.priority ?? 0) - (a.priority ?? 0);
		});
		rawConfigs.value = list;
		selectedIds.value = [];
		return list;
	},
	defaultFormFactory: () => ({
		name: '',
		description: '',
		optimizationPrompt: '',
		priority: 0,
		displayOrder: 0,
		enabled: true,
		promptType: promptType.value,
		agentId: selectedAgentId.value ?? null,
		creator: 'user',
	}),
});

async function loadConfigs() {
	loading.value = true;
	try {
		rawConfigs.value = await promptService.listByType(
			promptType.value,
			selectedAgentId.value,
		);
		rawConfigs.value.sort((a, b) => {
			const orderA = a.displayOrder ?? 0;
			const orderB = b.displayOrder ?? 0;
			if (orderA !== orderB) return orderA - orderB;
			return (b.priority ?? 0) - (a.priority ?? 0);
		});
		selectedIds.value = [];
	} catch {
		$tip('加载提示词配置失败', { color: 'error', icon: 'mdi-alert-circle' });
	} finally {
		loading.value = false;
	}
}

function resetFormData() {
	formData.value = {
		name: '',
		description: '',
		optimizationPrompt: '',
		priority: 0,
		displayOrder: 0,
		enabled: true,
		promptType: promptType.value,
		agentId: selectedAgentId.value ?? null,
		creator: 'user',
	};
	editingId.value = undefined;
}

function openCreateDialog() {
	_openCreateDialog();
	formData.value.promptType = promptType.value;
	formData.value.agentId = selectedAgentId.value ?? null;
	editingId.value = undefined;
}

function editConfig(config: PromptConfig) {
	isEdit.value = true;
	editingId.value = config.id;
	formData.value = {
		...config,
		promptType: promptType.value,
		agentId: selectedAgentId.value ?? null,
	};
	dialogVisible.value = true;
}

function closeDialog() {
	_closeDialog();
	resetFormData();
}

function handleFilterChange() {
	loadConfigs();
}

async function saveConfig() {
	const validateResult = await formRef.value?.validate();
	const valid = validateResult?.valid;
	if (!valid) return;

	saveLoading.value = true;
	try {
		const payload: PromptConfig = {
			...formData.value,
			id: editingId.value,
			promptType: promptType.value,
			agentId: selectedAgentId.value ?? null,
			enabled: formData.value.enabled ?? true,
			creator: formData.value.creator || 'user',
		};
		const result = await promptService.save(payload);
		if (!result.success) {
			$tip(result.message || `${isEdit.value ? '更新' : '创建'}失败`, {
				color: 'error',
				icon: 'mdi-alert-circle',
			});
			return;
		}
		$tip(result.message || `${isEdit.value ? '更新' : '创建'}成功`);
		dialogVisible.value = false;
		resetFormData();
		await loadConfigs();
	} catch {
		$tip(`${isEdit.value ? '更新' : '创建'}失败`, {
			color: 'error',
			icon: 'mdi-alert-circle',
		});
	} finally {
		saveLoading.value = false;
	}
}

function toggleEnabled(config: PromptConfig) {
	if (!config.id) return;
	const toEnable = !config.enabled;
	showConfirm({
		title: `${toEnable ? '启用' : '禁用'}确认`,
		message: `确定要${toEnable ? '启用' : '禁用'}配置「${config.name}」吗？`,
		confirmText: '确认',
		onConfirm: async () => {
			const result = toEnable
				? await promptService.enable(config.id!)
				: await promptService.disable(config.id!);
			if (result.success) {
				$tip(result.message || `${toEnable ? '启用' : '禁用'}成功`);
				await loadConfigs();
			} else {
				$tip(result.message || `${toEnable ? '启用' : '禁用'}失败`, {
					color: 'error',
					icon: 'mdi-alert-circle',
				});
			}
		},
	});
}

function deleteConfig(config: PromptConfig) {
	if (!config.id) return;
	showConfirm({
		title: '删除确认',
		message: `确定要删除配置「${config.name}」吗？此操作不可恢复。`,
		confirmText: '确定删除',
		icon: 'mdi-delete',
		onConfirm: async () => {
			const result = await promptService.delete(config.id!);
			if (result.success) {
				$tip(result.message || '删除成功');
				await loadConfigs();
			} else {
				$tip(result.message || '删除失败', {
					color: 'error',
					icon: 'mdi-alert-circle',
				});
			}
		},
	});
}

function batchEnable() {
	if (selectedIds.value.length === 0) return;
	showConfirm({
		title: '批量启用确认',
		message: `确定要启用选中的 ${selectedIds.value.length} 条配置吗？`,
		confirmText: '确认启用',
		onConfirm: async () => {
			const result = await promptService.batchEnable(selectedIds.value);
			if (result.success) {
				$tip(result.message || '批量启用成功');
				await loadConfigs();
			} else {
				$tip(result.message || '批量启用失败', {
					color: 'error',
					icon: 'mdi-alert-circle',
				});
			}
		},
	});
}

function batchDisable() {
	if (selectedIds.value.length === 0) return;
	showConfirm({
		title: '批量禁用确认',
		message: `确定要禁用选中的 ${selectedIds.value.length} 条配置吗？`,
		confirmText: '确认禁用',
		onConfirm: async () => {
			const result = await promptService.batchDisable(selectedIds.value);
			if (result.success) {
				$tip(result.message || '批量禁用成功');
				await loadConfigs();
			} else {
				$tip(result.message || '批量禁用失败', {
					color: 'error',
					icon: 'mdi-alert-circle',
				});
			}
		},
	});
}

function openPriorityDialog(config: PromptConfig) {
	if (!config.id) return;
	priorityEditId.value = config.id;
	priorityValue.value = config.priority ?? 0;
	priorityDialogVisible.value = true;
}

function closePriorityDialog() {
	priorityDialogVisible.value = false;
	priorityEditId.value = undefined;
	priorityValue.value = 0;
}

async function updatePriority() {
	if (!priorityEditId.value) return;
	try {
		const result = await promptService.updatePriority(
			priorityEditId.value,
			priorityValue.value,
		);
		if (result.success) {
			$tip(result.message || '优先级更新成功');
			closePriorityDialog();
			await loadConfigs();
		} else {
			$tip(result.message || '优先级更新失败', {
				color: 'error',
				icon: 'mdi-alert-circle',
			});
		}
	} catch {
		$tip('优先级更新失败', { color: 'error', icon: 'mdi-alert-circle' });
	}
}

async function resolveAgent() {
	const routeAgentId = Number(route.query.agentId);
	const agents = await agentService.list();
	agentOptions.value = agents
		.filter((item) => item.id !== undefined && item.id > 0)
		.map((item) => ({
			title: item.name || `Agent ${item.id}`,
			value: item.id as number,
		}));
	if (Number.isFinite(routeAgentId) && routeAgentId > 0) {
		selectedAgentId.value = routeAgentId;
		return;
	}
	selectedAgentId.value = agentOptions.value[0]?.value;
}

onMounted(async () => {
	try {
		await resolveAgent();
	} catch {
		$tip('加载智能体列表失败', { color: 'error', icon: 'mdi-alert-circle' });
	}
	await loadConfigs();
});
</script>

<style scoped></style>
