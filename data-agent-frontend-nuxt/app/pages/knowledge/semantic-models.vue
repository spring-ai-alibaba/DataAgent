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
			title="语义模型配置"
			subtitle="维护字段语义映射，统一业务口径，提升 SQL 生成准确性。"
		>
			<template #actions>
				<v-btn
					class="text-none bg-white"
					style="border-color: #e2e8f0"
					variant="outlined"
					prepend-icon="mdi-refresh"
					:loading="loading"
					@click="loadSemanticModels"
				>
					刷新
				</v-btn>
				<v-btn
					color="blue-darken-1"
					prepend-icon="mdi-upload"
					class="text-none px-6"
					elevation="0"
					@click="openBatchImportDialog"
				>
					批量导入
				</v-btn>
				<v-btn
					color="blue-darken-3"
					prepend-icon="mdi-plus"
					class="text-none px-6"
					elevation="0"
					@click="openCreateDialog"
				>
					添加语义模型
				</v-btn>
			</template>
		</KnowledgePageHeader>

		<v-card variant="flat" border class="rounded-lg mb-4 pa-4">
			<div class="d-flex flex-wrap ga-3 align-center">
				<v-text-field
					v-model="searchKeyword"
					placeholder="请输入关键词搜索表名、字段名、业务名"
					prepend-inner-icon="mdi-magnify"
					variant="outlined"
					density="compact"
					clearable
					hide-details
					class="search-field"
					style="max-width: 420px"
					@keyup.enter="loadSemanticModels"
					@click:clear="loadSemanticModels"
				/>
				<v-btn
					v-if="selectedModelIds.length > 0"
					variant="tonal"
					color="red-darken-1"
					prepend-icon="mdi-delete"
					class="text-none"
					@click="batchDeleteModels"
				>
					批量删除 ({{ selectedModelIds.length }})
				</v-btn>
				<v-spacer />
				<v-chip
					color="blue-lighten-5"
					variant="flat"
					class="font-weight-medium"
				>
					总数 {{ semanticModelList.length }}
				</v-chip>
			</div>
		</v-card>

		<v-card variant="flat" border class="rounded-lg">
			<v-data-table
				v-model="selectedModelIds"
				:headers="headers"
				:items="semanticModelList"
				item-value="id"
				hover
				:loading="loading"
				show-select
			>
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.status="{ item }">
					<v-chip
						:color="item.status === 1 ? 'success' : 'grey'"
						size="small"
						variant="tonal"
					>
						{{ item.status === 1 ? '启用' : '停用' }}
					</v-chip>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.synonyms="{ item }">
					<v-tooltip
						v-if="item.synonyms && item.synonyms.length > 30"
						:text="item.synonyms"
						location="top"
					>
						<template #activator="{ props }">
							<span
								v-bind="props"
								class="text-truncate d-inline-block"
								style="max-width: 180px; cursor: help"
							>
								{{ item.synonyms }}
							</span>
						</template>
					</v-tooltip>
					<span v-else>{{ item.synonyms || '—' }}</span>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.createdTime="{ item }">
					{{ formatDateTime(item.createdTime || item.updateTime) }}
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.actions="{ item }">
					<div class="d-flex ga-1 align-center">
						<v-btn
							size="small"
							variant="text"
							color="blue-darken-1"
							icon="mdi-pencil"
							@click="editModel(item)"
						/>
						<v-btn
							size="small"
							variant="text"
							:color="item.status === 1 ? 'orange-darken-1' : 'success'"
							:icon="item.status === 1 ? 'mdi-pause-circle' : 'mdi-play-circle'"
							@click="toggleStatus(item, item.status === 1 ? 0 : 1)"
						>
							<v-tooltip activator="parent" location="top">{{
								item.status === 1 ? '停用' : '启用'
							}}</v-tooltip>
						</v-btn>
						<v-btn
							size="small"
							variant="text"
							color="red-darken-1"
							icon="mdi-delete"
							@click="deleteModel(item)"
						/>
					</div>
				</template>

				<template #no-data>
					<div class="d-flex flex-column align-center py-12">
						<v-icon
							icon="mdi-vector-intersection"
							size="64"
							color="blue-lighten-3"
							class="mb-4"
						/>
						<p class="text-body-1 text-medium-emphasis mb-2">暂无语义模型</p>
						<p class="text-body-2 text-disabled mb-6">
							点击「添加语义模型」开始配置字段语义映射
						</p>
						<v-btn
							color="blue-darken-3"
							prepend-icon="mdi-plus"
							class="text-none"
							elevation="0"
							@click="openCreateDialog"
						>
							添加语义模型
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
						isEdit ? '编辑语义模型' : '添加语义模型'
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
						<v-row>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									表名 <span class="text-error">*</span>
								</p>
								<v-text-field
									v-model="modelForm.tableName"
									placeholder="请输入表名"
									variant="outlined"
									density="compact"
									:rules="[(v) => !!v || '表名不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									字段名 <span class="text-error">*</span>
								</p>
								<v-text-field
									v-model="modelForm.columnName"
									placeholder="请输入数据库字段名"
									variant="outlined"
									density="compact"
									:rules="[(v) => !!v || '字段名不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									业务名称 <span class="text-error">*</span>
								</p>
								<v-text-field
									v-model="modelForm.businessName"
									placeholder="请输入业务名称"
									variant="outlined"
									density="compact"
									:rules="[(v) => !!v || '业务名称不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									数据类型 <span class="text-error">*</span>
								</p>
								<v-text-field
									v-model="modelForm.dataType"
									placeholder="如：int, varchar(64)"
									variant="outlined"
									density="compact"
									:rules="[(v) => !!v || '数据类型不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									同义词
								</p>
								<v-textarea
									v-model="modelForm.synonyms"
									placeholder="多个同义词请用逗号分隔"
									variant="outlined"
									density="compact"
									rows="2"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									业务描述
								</p>
								<v-textarea
									v-model="modelForm.businessDescription"
									placeholder="描述该字段业务意义和口径"
									variant="outlined"
									density="compact"
									rows="3"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									字段注释
								</p>
								<v-textarea
									v-model="modelForm.columnComment"
									placeholder="数据库原始字段注释"
									variant="outlined"
									density="compact"
									rows="2"
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
						@click="saveModel"
					>
						{{ isEdit ? '保存更新' : '立即创建' }}
					</v-btn>
				</v-card-actions>
			</v-card>
		</v-dialog>

		<v-dialog v-model="batchImportDialogVisible" max-width="760">
			<v-card rounded="lg">
				<v-card-title class="d-flex align-center pa-6 pb-4">
					<v-icon
						icon="mdi-upload"
						color="blue-darken-2"
						class="mr-3"
						size="28"
					/>
					<span class="text-h6 font-weight-bold">批量导入语义模型</span>
					<v-spacer />
					<v-btn
						icon="mdi-close"
						variant="text"
						size="small"
						@click="batchImportDialogVisible = false"
					/>
				</v-card-title>
				<v-divider />
				<v-card-text class="pa-6">
					<v-file-input
						v-model="importFile"
						label="上传 Excel 文件"
						accept=".xlsx,.xls"
						prepend-icon="mdi-file-excel"
						variant="outlined"
						density="compact"
						show-size
						hide-details="auto"
					/>
					<div class="d-flex ga-2 mt-4">
						<v-btn
							variant="tonal"
							color="blue-darken-1"
							prepend-icon="mdi-download"
							class="text-none"
							@click="downloadExcelTemplate"
						>
							下载模板
						</v-btn>
						<v-btn
							color="blue-darken-3"
							prepend-icon="mdi-upload"
							class="text-none"
							elevation="0"
							:loading="importLoading"
							@click="executeExcelImport"
						>
							开始导入
						</v-btn>
					</div>
				</v-card-text>
			</v-card>
		</v-dialog>
	</section>
</template>

<script setup lang="ts">
import semanticModelService, {
	type SemanticModel,
	type SemanticModelAddDto,
} from '~/services/semanticModel/index';
import { useCrudPage } from '~/composables/useCrudPage/index';

const DEFAULT_AGENT_ID = 0;
const route = useRoute();
const agentId = computed(() => Number(route.query.agentId) || DEFAULT_AGENT_ID);

const { $tip } = useNuxtApp();
const { showConfirm } = useConfirm();

// ——— 额外状态 ———
const importLoading = ref(false);
const batchImportDialogVisible = ref(false);
const importFile = ref<File | null>(null);
const selectedModelIds = ref<number[]>([]);
const currentEditId = ref<number | null>(null);

// ——— useCrudPage ———
const searchKeyword = ref('');

const {
	loading,
	saveLoading,
	items: semanticModelList,
	dialogVisible,
	isEdit,
	formRef,
	formData: modelForm,
	loadItems: loadSemanticModels,
	openCreateDialog: _openCreateDialog,
	openEditDialog,
	closeDialog,
	saveItem,
	deleteItem,
} = useCrudPage<SemanticModel, SemanticModelAddDto, SemanticModel>({
	loadFn: () =>
		semanticModelService.list(agentId.value, searchKeyword.value || undefined),
	createFn: (data) => semanticModelService.create(data),
	updateFn: (id, data) => semanticModelService.update(id, data),
	deleteFn: (id) => semanticModelService.delete(id),
	defaultFormFactory: () => ({
		agentId: agentId.value,
		tableName: '',
		columnName: '',
		businessName: '',
		synonyms: '',
		businessDescription: '',
		columnComment: '',
		dataType: '',
		status: 1,
	}),
});

function openCreateDialog() {
	_openCreateDialog();
	modelForm.value.agentId = agentId.value;
}

const headers = [
	{ title: '表名', key: 'tableName', minWidth: '120px' },
	{ title: '字段名', key: 'columnName', minWidth: '130px' },
	{ title: '业务名称', key: 'businessName', minWidth: '140px' },
	{ title: '同义词', key: 'synonyms', minWidth: '170px', sortable: false },
	{ title: '数据类型', key: 'dataType', width: '110px' },
	{ title: '状态', key: 'status', width: '100px', sortable: false },
	{ title: '创建时间', key: 'createdTime', width: '180px' },
	{ title: '操作', key: 'actions', width: '140px', sortable: false },
];

function formatDateTime(dateTime?: string) {
	if (!dateTime) return '-';
	try {
		return new Date(dateTime).toLocaleString('zh-CN', {
			year: 'numeric',
			month: '2-digit',
			day: '2-digit',
			hour: '2-digit',
			minute: '2-digit',
			second: '2-digit',
			hour12: false,
		});
	} catch {
		return dateTime;
	}
}

function editModel(model: SemanticModel) {
	currentEditId.value = model.id || null;
	openEditDialog(model);
}

function deleteModel(model: SemanticModel) {
	if (!model.id) return;
	showConfirm({
		title: '删除确认',
		message: `确定要删除语义模型「${model.businessName}」吗？此操作不可恢复。`,
		confirmText: '确定删除',
		icon: 'mdi-delete',
		onConfirm: async () => {
			const ok = await deleteItem(model.id!);
			if (ok) {
				$tip('删除成功');
			} else {
				$tip('删除失败', { color: 'error', icon: 'mdi-alert-circle' });
			}
		},
	});
}

function toggleStatus(model: SemanticModel, status: number) {
	if (!model.id) return;
	const ids = [model.id];
	showConfirm({
		title: `${status === 1 ? '启用' : '停用'}确认`,
		message: `确定要${status === 1 ? '启用' : '停用'}语义模型「${model.businessName}」吗？`,
		confirmText: '确认',
		onConfirm: async () => {
			let result = false;
			if (status === 1) {
				result = await semanticModelService.enable(ids);
			} else {
				result = await semanticModelService.disable(ids);
			}
			if (result) {
				model.status = status;
				$tip(`${status === 1 ? '启用' : '停用'}成功`);
			} else {
				$tip(`${status === 1 ? '启用' : '停用'}失败`, {
					color: 'error',
					icon: 'mdi-alert-circle',
				});
			}
		},
	});
}

function batchDeleteModels() {
	if (selectedModelIds.value.length === 0) return;
	const ids = [...selectedModelIds.value];
	showConfirm({
		title: '批量删除确认',
		message: `确定要删除选中的 ${ids.length} 个语义模型吗？`,
		confirmText: '确定删除',
		icon: 'mdi-delete',
		onConfirm: async () => {
			const result = await semanticModelService.batchDelete(ids);
			if (result) {
				$tip(`成功删除 ${ids.length} 个语义模型`);
				selectedModelIds.value = [];
				await loadSemanticModels();
			} else {
				$tip('批量删除失败', { color: 'error', icon: 'mdi-alert-circle' });
			}
		},
	});
}

async function saveModel() {
	const createData: SemanticModelAddDto = {
		agentId: agentId.value,
		tableName: modelForm.value.tableName,
		columnName: modelForm.value.columnName,
		businessName: modelForm.value.businessName,
		synonyms: modelForm.value.synonyms,
		businessDescription: modelForm.value.businessDescription,
		columnComment: modelForm.value.columnComment,
		dataType: modelForm.value.dataType,
	};
	const updateData: SemanticModel = {
		...modelForm.value,
		id: currentEditId.value ?? undefined,
	};
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	const ok = await saveItem(createData as any, updateData, currentEditId.value);
	if (ok) {
		$tip(isEdit.value ? '更新成功' : '创建成功');
	} else {
		$tip(`${isEdit.value ? '更新' : '创建'}失败`, {
			color: 'error',
			icon: 'mdi-alert-circle',
		});
	}
}

async function downloadExcelTemplate() {
	try {
		await semanticModelService.downloadTemplate();
		$tip('模板下载成功');
	} catch {
		$tip('模板下载失败', { color: 'error', icon: 'mdi-alert-circle' });
	}
}

async function executeExcelImport() {
	if (!importFile.value) {
		$tip('请先选择 Excel 文件', { color: 'warning' });
		return;
	}
	importLoading.value = true;
	try {
		const result = await semanticModelService.importExcel(
			importFile.value,
			agentId.value,
		);
		$tip(
			`导入完成：成功 ${result.successCount} 条，失败 ${result.failCount} 条`,
		);
		if (result.errors?.length) {
			$tip(`部分失败：${result.errors[0]}`, { color: 'warning' });
		}
		batchImportDialogVisible.value = false;
		importFile.value = null;
		await loadSemanticModels();
	} catch {
		$tip('Excel 导入失败', { color: 'error', icon: 'mdi-alert-circle' });
	} finally {
		importLoading.value = false;
	}
}

// Note: loadSemanticModels passes searchKeyword on each call

function openBatchImportDialog() {
	batchImportDialogVisible.value = true;
	importFile.value = null;
}

onMounted(() => loadSemanticModels());
</script>

<style scoped></style>
