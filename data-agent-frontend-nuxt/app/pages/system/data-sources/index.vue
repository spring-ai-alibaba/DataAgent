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
		<header class="d-flex align-center justify-space-between mb-8">
			<div>
				<h1 class="text-h4 font-weight-bold mb-1 text-slate-900">数据源配置</h1>
				<p class="text-body-2 text-medium-emphasis">
					管理全局数据库连接资源，配置连接信息与逻辑外键。
				</p>
			</div>
			<div class="d-flex ga-3">
				<v-btn
					variant="outlined"
					prepend-icon="mdi-refresh"
					:loading="loading"
					class="text-none bg-white"
					style="border-color: #e2e8f0"
					@click="fetchDatasources"
				>
					刷新
				</v-btn>
				<v-btn
					color="primary"
					prepend-icon="mdi-plus"
					class="text-none px-6"
					elevation="0"
					@click="openFormDialog('create')"
				>
					添加数据源
				</v-btn>
				<v-btn
					v-if="agentId"
					color="primary"
					prepend-icon="mdi-upload"
					class="text-none px-6"
					elevation="0"
					:loading="initStatus"
					@click="handleInitDatasource"
				>
					{{ initStatus ? '初始化中...' : '初始化数据源' }}
				</v-btn>
			</div>
		</header>

		<v-card variant="flat" border class="rounded-lg">
			<v-data-table
				v-model:expanded="expandedRows"
				:headers="headers"
				:items="datasourceList"
				item-value="id"
				show-expand
				hover
				:loading="loading"
				:items-per-page-options="[10, 25, 50, 100]"
				:footer-props="{
					'items-per-page-text': '每页显示：',
					'page-text': '{0}-{1} 共 {2} 条',
				}"
			>
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template
					#item.data-table-expand="{
						item,
						internalItem,
						toggleExpand,
						isExpanded,
					}"
				>
					<v-btn
						v-if="item.status === 'active' && item.testStatus === 'success'"
						icon
						variant="text"
						size="small"
						@click="toggleExpand(internalItem)"
					>
						<v-icon>{{
							isExpanded(internalItem) ? 'mdi-chevron-up' : 'mdi-chevron-down'
						}}</v-icon>
					</v-btn>
					<v-btn
						v-else
						icon
						variant="text"
						size="small"
						disabled
						class="expand-disabled"
					>
						<v-icon>mdi-chevron-down</v-icon>
					</v-btn>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.name="{ item }">
					<div class="d-flex align-center py-2">
						<v-avatar
							color="blue-lighten-5"
							rounded="lg"
							size="36"
							class="mr-3"
						>
							<v-icon color="primary" size="20">{{
								getDbIcon(item.type)
							}}</v-icon>
						</v-avatar>
						<div>
							<div class="font-weight-bold">{{ item.name }}</div>
							<div class="text-caption text-medium-emphasis">
								{{ item.host }}:{{ item.port }}
							</div>
						</div>
					</div>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.type="{ item }">
					<v-chip size="small" class="text-uppercase">{{ item.type }}</v-chip>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.status="{ item }">
					<v-chip
						:color="item.status === 'active' ? 'success' : 'default'"
						size="small"
						variant="flat"
						class="px-3"
					>
						{{ item.status === 'active' ? '启用' : '禁用' }}
					</v-chip>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.testStatus="{ item }">
					<v-chip
						:color="
							item.testStatus === 'success'
								? 'blue'
								: item.testStatus === 'fail'
									? 'error'
									: 'default'
						"
						size="small"
						variant="flat"
						class="px-3"
					>
						<span class="d-flex align-center">
							<span
								v-if="item.testStatus === 'success'"
								class="breathing-dot-green"
							/>
							{{ getStatusText(item.testStatus) }}
						</span>
					</v-chip>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.actions="{ item }">
					<div class="d-flex align-center justify-end ga-1">
						<v-btn
							variant="text"
							size="small"
							color="primary"
							class="text-none font-weight-bold"
							:loading="togglingStatusId === item.id"
							@click="handleToggleStatus(item)"
						>
							{{ item.status === 'active' ? '禁用' : '启用' }}
						</v-btn>
						<v-btn
							variant="text"
							size="small"
							color="primary"
							class="text-none font-weight-bold"
							:loading="testingId === item.id"
							@click="handleTestConnection(item)"
						>
							测试连接
						</v-btn>
						<v-btn
							variant="text"
							size="small"
							color="primary"
							class="text-none font-weight-bold"
							@click="openFkDialog(item)"
						>
							逻辑外键
						</v-btn>
						<v-btn
							icon="mdi-pencil-outline"
							variant="text"
							size="small"
							color="primary"
							@click="openFormDialog('edit', item)"
						/>
						<v-btn
							icon="mdi-delete-outline"
							variant="text"
							size="small"
							color="error"
							@click="handleDelete(item)"
						/>
					</div>
				</template>

				<template #expanded-row="{ columns, item }">
					<tr>
						<td :colspan="columns.length" class="bg-grey-lighten-5 pa-0">
							<ExpandedTableManager
								v-model:selected-tables="selectedTables[item.id!]"
								:all-tables="tableLists[item.id!] ?? []"
								:loading-tables="loadingTablesId === item.id"
								:fetch-error="!!(item.id && tableFetchError[item.id])"
								:updating="updatingTablesId === item.id"
								@update-tables="updateTables(item)"
								@retry="retryFetchTables(item)"
							/>
						</td>
					</tr>
				</template>
			</v-data-table>
		</v-card>

		<DatasourceFormDialog
			v-model="formDialogVisible"
			:is-edit="formDialogMode === 'edit'"
			:datasource="formDialogTarget"
			:saving="saving"
			@submit="handleFormSubmit"
		/>

		<ForeignKeyDialog
			v-model="fkDialogVisible"
			:datasource-id="fkDatasourceId"
			:datasource-name="fkDatasourceName"
		/>
	</section>
</template>

<script setup lang="ts">
import datasourceService, { type Datasource } from '@/services/datasource';
import agentDatasourceService from '@/services/agentDatasource';
import DatasourceFormDialog from './DatasourceFormDialog.vue';
import ForeignKeyDialog from './ForeignKeyDialog.vue';
import ExpandedTableManager from './ExpandedTableManager.vue';

const route = useRoute();
const { showConfirm } = useConfirm();
const { $tip } = useNuxtApp();

const loading = ref(false);
const saving = ref(false);
const testingId = ref<number | null>(null);
const togglingStatusId = ref<number | null>(null);
const datasourceList = ref<Datasource[]>([]);
const expandedRows = ref<readonly string[]>([]);
const tableLists = ref<Record<number, string[]>>({});
const selectedTables = ref<Record<number, string[]>>({});
const loadingTablesId = ref<number | null>(null);
const tableFetchError = ref<Record<number, boolean>>({});
const updatingTablesId = ref<number | null>(null);
const initStatus = ref(false);

const agentId = computed(() => {
	const id = route.params.agentId || route.query.agentId;
	return id ? String(id) : null;
});

const formDialogVisible = ref(false);
const formDialogMode = ref<'create' | 'edit'>('create');
const formDialogTarget = ref<Datasource | null>(null);

const fkDialogVisible = ref(false);
const fkDatasourceId = ref(0);
const fkDatasourceName = ref('');

const headers = [
	{ title: '名称', key: 'name', align: 'start' as const },
	{ title: '类型', key: 'type', align: 'center' as const },
	{ title: '状态', key: 'status', align: 'center' as const },
	{ title: '连接状态', key: 'testStatus', align: 'center' as const },
	{ title: '操作', key: 'actions', align: 'end' as const, sortable: false },
];

function getDbIcon(type: string | undefined) {
	if (type === 'mysql') return 'mdi-database';
	if (type === 'postgresql') return 'mdi-elephant';
	if (type === 'oracle') return 'mdi-alpha-o-circle';
	return 'mdi-database-outline';
}

function getStatusText(status: string | undefined) {
	if (status === 'success') return '连接成功';
	if (status === 'fail') return '连接失败';
	return '未测试';
}

async function fetchDatasources() {
	loading.value = true;
	try {
		datasourceList.value = await datasourceService.getAllDatasource();
	} catch {
		$tip('获取数据源列表失败', { color: 'error', icon: 'mdi-alert-circle' });
	} finally {
		loading.value = false;
	}
}

function openFormDialog(mode: 'create' | 'edit', item?: Datasource) {
	formDialogMode.value = mode;
	formDialogTarget.value = mode === 'edit' && item ? { ...item } : null;
	formDialogVisible.value = true;
}

async function handleFormSubmit(data: Datasource) {
	saving.value = true;
	try {
		if (formDialogMode.value === 'create') {
			await datasourceService.createDatasource(data);
			$tip('创建成功');
		} else if (data.id) {
			await datasourceService.updateDatasource(data.id, data);
			$tip('更新成功');
		}
		formDialogVisible.value = false;
		fetchDatasources();
	} catch {
		$tip('操作失败，请检查网络或参数', {
			color: 'error',
			icon: 'mdi-alert-circle',
		});
	} finally {
		saving.value = false;
	}
}

function handleDelete(item: Datasource) {
	if (!item.id) return;
	showConfirm({
		title: '删除确认',
		message: `确定要删除数据源「${item.name}」吗？此操作不可恢复。`,
		confirmText: '删除',
		icon: 'mdi-alert-circle',
		onConfirm: async () => {
			try {
				const res = await datasourceService.deleteDatasource(item.id!);
				if (res.success) {
					$tip('删除成功');
					fetchDatasources();
				} else
					$tip(res.message || '删除失败', {
						color: 'error',
						icon: 'mdi-alert-circle',
					});
			} catch {
				$tip('删除失败', { color: 'error', icon: 'mdi-alert-circle' });
			}
		},
	});
}

async function handleToggleStatus(item: Datasource) {
	if (!item.id) return;
	togglingStatusId.value = item.id;
	const newStatus = item.status === 'active' ? 'inactive' : 'active';
	try {
		await datasourceService.updateDatasource(item.id, {
			...item,
			status: newStatus,
		});
		item.status = newStatus;
		$tip(newStatus === 'active' ? '已启用' : '已禁用');
	} catch {
		$tip('操作失败', { color: 'error', icon: 'mdi-alert-circle' });
	} finally {
		togglingStatusId.value = null;
	}
}

async function handleTestConnection(item: Datasource) {
	if (!item.id) return;
	testingId.value = item.id;
	try {
		const res = await datasourceService.testConnection(item.id);
		if (res.success) {
			$tip('连接测试成功');
			item.testStatus = 'success';
		} else {
			$tip('连接测试失败', { color: 'error', icon: 'mdi-alert-circle' });
			item.testStatus = 'fail';
		}
	} catch {
		$tip('连接测试请求失败', { color: 'error', icon: 'mdi-alert-circle' });
		item.testStatus = 'fail';
	} finally {
		testingId.value = null;
	}
}

function openFkDialog(item: Datasource) {
	if (!item.id) return;
	fkDatasourceId.value = item.id;
	fkDatasourceName.value = item.name || '';
	fkDialogVisible.value = true;
}

// ── 展开行相关 ──────────────────────────────────────────────────────────────

function getDatasourceFromRow(row: unknown): Datasource | null {
	if (row && typeof row === 'object' && 'id' in row) {
		const id = (row as Datasource).id;
		return (
			datasourceList.value.find((ds) => ds.id === id) ?? (row as Datasource)
		);
	}
	const id =
		typeof row === 'number'
			? row
			: typeof row === 'string'
				? Number(row)
				: null;
	if (id != null && !Number.isNaN(id))
		return datasourceList.value.find((ds) => ds.id === id) ?? null;
	return null;
}

watch(
	expandedRows,
	(rows) => {
		const list = Array.isArray(rows) ? rows : [];
		const invalidRows: unknown[] = [];
		for (const row of list) {
			const ds = getDatasourceFromRow(row);
			const canExpand =
				ds && ds.status === 'active' && (ds.testStatus ?? '') === 'success';
			if (!canExpand && ds) invalidRows.push(row);
		}
		if (invalidRows.length > 0) {
			expandedRows.value = list.filter((row) => {
				const ds = getDatasourceFromRow(row);
				return (
					ds && ds.status === 'active' && (ds.testStatus ?? '') === 'success'
				);
			});
			const hasInactive = invalidRows.some((row) => {
				const ds = getDatasourceFromRow(row);
				return ds && ds.status !== 'active';
			});
			const hasConnFail = invalidRows.some((row) => {
				const ds = getDatasourceFromRow(row);
				return ds && (ds.testStatus ?? '') !== 'success';
			});
			if (hasInactive && hasConnFail)
				$tip('请先启用数据源并测试连接成功后再展开', {
					color: 'error',
					icon: 'mdi-alert-circle',
				});
			else if (hasInactive)
				$tip('请先启用数据源后再展开数据表管理', {
					color: 'error',
					icon: 'mdi-alert-circle',
				});
			else
				$tip('请先测试连接成功后再展开数据表管理', {
					color: 'error',
					icon: 'mdi-alert-circle',
				});
		}
	},
	{ immediate: false },
);

watch(
	expandedRows,
	async (rows) => {
		const list = Array.isArray(rows) ? rows : [];
		for (const row of list) {
			const dsId =
				typeof row === 'object' && row !== null && 'id' in row && row.id != null
					? row.id
					: Number(row);
			if (dsId && !tableLists.value[dsId]) {
				selectedTables.value[dsId] = selectedTables.value[dsId] ?? [];
				await fetchTablesForDatasource(dsId);
			}
		}
	},
	{ immediate: false },
);

async function fetchTablesForDatasource(datasourceId: number) {
	loadingTablesId.value = datasourceId;
	tableFetchError.value = { ...tableFetchError.value, [datasourceId]: false };
	try {
		const tables = await datasourceService.getDatasourceTables(datasourceId);
		tableLists.value[datasourceId] = tables ?? [];
		if (!selectedTables.value[datasourceId])
			selectedTables.value[datasourceId] = [];
	} catch {
		tableLists.value[datasourceId] = [];
		selectedTables.value[datasourceId] = [];
		tableFetchError.value = { ...tableFetchError.value, [datasourceId]: true };
	} finally {
		loadingTablesId.value = null;
	}
}

function retryFetchTables(item: Datasource) {
	if (item.id) fetchTablesForDatasource(item.id);
}

async function updateTables(item: Datasource) {
	if (!item.id) return;
	updatingTablesId.value = item.id;
	try {
		$tip(
			`已选择 ${selectedTables.value[item.id]?.length ?? 0} 个表（表选择在智能体关联数据源时可配置）`,
		);
	} finally {
		updatingTablesId.value = null;
	}
}

async function handleInitDatasource() {
	if (!agentId.value) {
		$tip('缺少智能体ID，无法初始化数据源', {
			color: 'error',
			icon: 'mdi-alert-circle',
		});
		return;
	}
	initStatus.value = true;
	try {
		const activeRes = await agentDatasourceService.getActiveAgentDatasource(
			agentId.value,
		);
		if (!activeRes.success || !activeRes.data) {
			$tip('当前智能体没有启用的数据源！请先添加并启用数据源', {
				color: 'error',
				icon: 'mdi-alert-circle',
			});
			return;
		}
		const activeDatasource = activeRes.data;
		if (
			!activeDatasource.selectTables ||
			activeDatasource.selectTables.length === 0
		) {
			$tip('当前启用的数据源没有选择相应的数据表！请先选择数据表并更新', {
				color: 'error',
				icon: 'mdi-alert-circle',
			});
			return;
		}
		const res = await agentDatasourceService.initSchema(agentId.value);
		if (res.success) $tip('初始化数据源成功');
		else
			$tip(res.message || '初始化数据源失败', {
				color: 'error',
				icon: 'mdi-alert-circle',
			});
	} catch (error: unknown) {
		const errMsg = error instanceof Error ? error.message : '初始化数据源失败';
		$tip(errMsg, { color: 'error', icon: 'mdi-alert-circle' });
	} finally {
		initStatus.value = false;
	}
}

onMounted(() => {
	fetchDatasources();
});
</script>

<style scoped>
.expand-disabled {
	opacity: 0.4;
	cursor: not-allowed;
}
</style>
