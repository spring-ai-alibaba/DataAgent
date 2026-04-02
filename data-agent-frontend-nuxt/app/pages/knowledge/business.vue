<template>
	<section class="page-shell">
		<KnowledgePageHeader
			title="业务知识配置"
			subtitle="管理全局业务术语词汇表，支持同义词扩展与向量化召回。"
		>
			<template #actions>
				<v-btn
					class="text-none bg-white"
					style="border-color: #e2e8f0"
					variant="outlined"
					prepend-icon="mdi-refresh"
					:loading="loading"
					@click="loadBusinessKnowledge"
				>
					刷新
				</v-btn>
				<v-btn
					color="blue-darken-1"
					prepend-icon="mdi-sync"
					class="text-none px-6"
					elevation="0"
					:loading="refreshLoading"
					@click="handleRefreshVectorStore"
				>
					同步到向量库
				</v-btn>
				<v-btn
					color="blue-darken-3"
					prepend-icon="mdi-plus"
					class="text-none px-6"
					elevation="0"
					@click="openCreateDialog"
				>
					添加知识
				</v-btn>
			</template>
		</KnowledgePageHeader>

		<!-- 搜索栏 -->
		<v-card variant="flat" border class="rounded-lg mb-4 pa-4">
			<v-text-field
				v-model="searchKeyword"
				placeholder="请输入关键词搜索业务名词、描述或同义词..."
				prepend-inner-icon="mdi-magnify"
				variant="outlined"
				density="compact"
				clearable
				hide-details
				class="search-field"
				style="max-width: 400px"
				@keyup.enter="loadBusinessKnowledge"
				@click:clear="handleClearSearch"
			/>
		</v-card>

		<!-- 数据表格 -->
		<v-card variant="flat" border class="rounded-lg">
			<v-data-table
				:headers="headers"
				:items="businessKnowledgeList"
				item-value="id"
				hover
				:loading="loading"
				:items-per-page-options="[10, 25, 50]"
			>
				<!-- 向量化状态 -->
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.embeddingStatus="{ item }">
					<v-chip
						:color="getVectorStatusColor(item.embeddingStatus)"
						size="small"
						variant="tonal"
						class="font-weight-medium"
					>
						<v-icon
							v-if="item.embeddingStatus === 'FAILED'"
							icon="mdi-alert-circle"
							size="14"
							class="mr-1"
						/>
						<v-icon
							v-else-if="item.embeddingStatus === 'COMPLETED'"
							icon="mdi-check-circle"
							size="14"
							class="mr-1"
						/>
						<v-icon
							v-else-if="item.embeddingStatus === 'PROCESSING'"
							icon="mdi-loading mdi-spin"
							size="14"
							class="mr-1"
						/>
						{{ getVectorStatusLabel(item.embeddingStatus) }}
					</v-chip>
					<v-tooltip
						v-if="item.embeddingStatus === 'FAILED' && item.errorMsg"
						:text="item.errorMsg"
						location="top"
					>
						<template #activator="{ props }">
							<v-icon v-bind="props" icon="mdi-information-outline" size="16" color="error" class="ml-1 cursor-pointer" />
						</template>
					</v-tooltip>
				</template>

				<!-- 是否召回 -->
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.isRecall="{ item }">
					<v-chip
						:color="item.isRecall ? 'blue-darken-1' : 'grey'"
						size="small"
						variant="tonal"
						class="font-weight-medium"
					>
						<v-icon :icon="item.isRecall ? 'mdi-check' : 'mdi-minus'" size="14" class="mr-1" />
						{{ item.isRecall ? '召回中' : '未召回' }}
					</v-chip>
				</template>

				<!-- 同义词：超长截断 -->
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.synonyms="{ item }">
					<v-tooltip v-if="item.synonyms && item.synonyms.length > 30" :text="item.synonyms" location="top">
						<template #activator="{ props }">
							<span v-bind="props" class="text-truncate d-inline-block" style="max-width: 160px; cursor: help">
								{{ item.synonyms }}
							</span>
						</template>
					</v-tooltip>
					<span v-else>{{ item.synonyms || '—' }}</span>
				</template>

				<!-- 描述：超长截断 -->
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.description="{ item }">
					<v-tooltip v-if="item.description && item.description.length > 40" :text="item.description" location="top">
						<template #activator="{ props }">
							<span v-bind="props" class="text-truncate d-inline-block" style="max-width: 200px; cursor: help">
								{{ item.description }}
							</span>
						</template>
					</v-tooltip>
					<span v-else>{{ item.description || '—' }}</span>
				</template>

				<!-- 操作列 -->
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.actions="{ item }">
					<div class="d-flex ga-1 align-center">
						<v-btn
							size="small"
							variant="text"
							color="blue-darken-1"
							icon="mdi-pencil"
							@click="editKnowledge(item)"
						/>
						<v-btn
							v-if="item.embeddingStatus === 'FAILED'"
							size="small"
							variant="text"
							color="orange-darken-1"
							icon="mdi-reload"
							:loading="item.id !== undefined ? retryLoadingMap[item.id] : false"
							@click="retryEmbedding(item)"
						/>
						<v-btn
							v-if="item.isRecall"
							size="small"
							variant="text"
							color="grey-darken-1"
							icon="mdi-bookmark-off"
							@click="toggleRecall(item, false)"
						>
							<v-tooltip activator="parent" location="top">取消召回</v-tooltip>
						</v-btn>
						<v-btn
							v-else
							size="small"
							variant="text"
							color="blue-darken-1"
							icon="mdi-bookmark-plus"
							@click="toggleRecall(item, true)"
						>
							<v-tooltip activator="parent" location="top">设为召回</v-tooltip>
						</v-btn>
						<v-btn
							size="small"
							variant="text"
							color="red-darken-1"
							icon="mdi-delete"
							@click="deleteKnowledge(item)"
						/>
					</div>
				</template>

				<!-- 空状态 -->
				<template #no-data>
					<div class="d-flex flex-column align-center py-12">
						<v-icon icon="mdi-book-open-blank-variant" size="64" color="blue-lighten-3" class="mb-4" />
						<p class="text-body-1 text-medium-emphasis mb-2">暂无业务知识</p>
						<p class="text-body-2 text-disabled mb-6">点击「添加知识」开始配置业务术语词汇</p>
						<v-btn color="blue-darken-3" prepend-icon="mdi-plus" class="text-none" elevation="0" @click="openCreateDialog">
							添加知识
						</v-btn>
					</div>
				</template>
			</v-data-table>
		</v-card>

		<!-- 添加/编辑 Dialog -->
		<v-dialog v-model="dialogVisible" max-width="640" persistent>
			<v-card rounded="lg">
				<v-card-title class="d-flex align-center pa-6 pb-4">
					<v-icon
						:icon="isEdit ? 'mdi-pencil-circle' : 'mdi-plus-circle'"
						color="blue-darken-2"
						class="mr-3"
						size="28"
					/>
					<span class="text-h6 font-weight-bold">{{ isEdit ? '编辑业务知识' : '添加业务知识' }}</span>
					<v-spacer />
					<v-btn icon="mdi-close" variant="text" size="small" @click="closeDialog" />
				</v-card-title>

				<v-divider />

				<v-card-text class="pa-6">
					<v-form ref="formRef">
						<div class="mb-5">
							<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">
								业务名词 <span class="text-error">*</span>
							</p>
							<v-text-field
								v-model="knowledgeForm.businessTerm"
								placeholder="请输入业务名词，例如：月活用户、GMV"
								variant="outlined"
								density="compact"
								:rules="[v => !!v || '业务名词不能为空']"
								hide-details="auto"
							/>
						</div>

						<div class="mb-5">
							<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">
								描述 <span class="text-error">*</span>
							</p>
							<v-textarea
								v-model="knowledgeForm.description"
								placeholder="请输入业务知识描述，详细说明该术语的含义与用法"
								variant="outlined"
								density="compact"
								rows="3"
								:rules="[v => !!v || '描述不能为空']"
								hide-details="auto"
							/>
						</div>

						<div class="mb-2">
							<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">同义词</p>
							<v-textarea
								v-model="knowledgeForm.synonyms"
								placeholder="请输入同义词，多个同义词用逗号分隔，例如：MAU, 月活, 月活跃用户数"
								variant="outlined"
								density="compact"
								rows="2"
								hide-details="auto"
							/>
						</div>
					</v-form>
				</v-card-text>

				<v-divider />

				<v-card-actions class="pa-4 d-flex justify-end ga-2">
					<v-btn variant="outlined" class="text-none px-6" @click="dialogVisible = false">
						取消
					</v-btn>
					<v-btn
						color="blue-darken-3"
						class="text-none px-6"
						elevation="0"
						:loading="saveLoading"
						@click="saveKnowledge"
					>
						{{ isEdit ? '保存更新' : '立即创建' }}
					</v-btn>
				</v-card-actions>
			</v-card>
		</v-dialog>
	</section>
</template>

<script setup lang="ts">
import businessKnowledgeService, {
	type BusinessKnowledgeVO,
	type CreateBusinessKnowledgeDTO,
	type UpdateBusinessKnowledgeDTO,
} from '~/services/businessKnowledge/index';
import { useCrudPage } from '~/composables/useCrudPage/index';

// ——— 常量 ———
const DEFAULT_AGENT_ID = 0;

// ——— 路由 ———
const route = useRoute();
const agentId = computed(() => Number(route.query.agentId) || DEFAULT_AGENT_ID);

// ——— 全局工具 ———
const { $tip } = useNuxtApp();
const { showConfirm } = useConfirm();

// ——— 额外状态 ———
const refreshLoading = ref(false);
const searchKeyword = ref('');
const currentEditId = ref<number | null>(null);
const retryLoadingMap = ref<Record<number, boolean>>({});

// ——— useCrudPage ———
const {
	loading,
	saveLoading,
	items: businessKnowledgeList,
	dialogVisible,
	isEdit,
	formRef,
	formData: knowledgeForm,
	loadItems: loadBusinessKnowledge,
	openCreateDialog: _openCreateDialog,
	openEditDialog,
	closeDialog,
	saveItem,
	deleteItem,
} = useCrudPage<BusinessKnowledgeVO, CreateBusinessKnowledgeDTO, UpdateBusinessKnowledgeDTO>({
	loadFn: () => businessKnowledgeService.list(agentId.value, searchKeyword.value || undefined),
	createFn: async (data) => { await businessKnowledgeService.create(data); return true; },
	updateFn: async (id, data) => { const r = await businessKnowledgeService.update(id, data); return r != null; },
	deleteFn: (id) => businessKnowledgeService.delete(id),
	defaultFormFactory: () => ({
		businessTerm: '',
		description: '',
		synonyms: '',
		isRecall: false,
		agentId: agentId.value,
	}),
});

function openCreateDialog() {
	// Ensure agentId is current before opening
	_openCreateDialog();
	knowledgeForm.value.agentId = agentId.value;
}

// ——— 表格列定义 ———
const headers = [
	{ title: 'ID', key: 'id', width: '70px', sortable: true },
	{ title: '业务名词', key: 'businessTerm', minWidth: '130px' },
	{ title: '描述', key: 'description', minWidth: '180px', sortable: false },
	{ title: '同义词', key: 'synonyms', minWidth: '160px', sortable: false },
	{ title: '向量化状态', key: 'embeddingStatus', width: '140px', sortable: false },
	{ title: '召回状态', key: 'isRecall', width: '120px', sortable: false },
	{ title: '创建时间', key: 'createdTime', width: '160px' },
	{ title: '操作', key: 'actions', width: '160px', sortable: false },
];

// ——— 工具函数 ———
function getVectorStatusColor(status?: string): string {
	switch (status) {
		case 'COMPLETED': return 'success';
		case 'FAILED': return 'error';
		case 'PENDING': return 'warning';
		case 'PROCESSING': return 'blue-darken-1';
		default: return 'grey';
	}
}

function getVectorStatusLabel(status?: string): string {
	switch (status) {
		case 'COMPLETED': return '已完成';
		case 'FAILED': return '失败';
		case 'PENDING': return '等待中';
		case 'PROCESSING': return '处理中';
		default: return '未知';
	}
}

function handleClearSearch() {
	searchKeyword.value = '';
	loadBusinessKnowledge();
}

function editKnowledge(knowledge: BusinessKnowledgeVO) {
	currentEditId.value = knowledge.id ?? null;
	openEditDialog(knowledge);
}

async function saveKnowledge() {
	const createData: CreateBusinessKnowledgeDTO = {
		businessTerm: knowledgeForm.value.businessTerm,
		description: knowledgeForm.value.description,
		synonyms: knowledgeForm.value.synonyms,
		isRecall: knowledgeForm.value.isRecall,
		agentId: agentId.value,
	};
	const updateData: UpdateBusinessKnowledgeDTO = {
		businessTerm: knowledgeForm.value.businessTerm,
		description: knowledgeForm.value.description,
		synonyms: knowledgeForm.value.synonyms,
		agentId: agentId.value,
	};
	const ok = await saveItem(createData, updateData, currentEditId.value);
	if (ok) {
		$tip(isEdit.value ? '更新成功' : '创建成功');
	} else {
		$tip(`${isEdit.value ? '更新' : '创建'}失败，请重试`, { color: 'error' });
	}
}

function deleteKnowledge(knowledge: BusinessKnowledgeVO) {
	if (!knowledge.id) return;
	showConfirm({
		title: '删除确认',
		message: `确定要删除业务知识「${knowledge.businessTerm}」吗？此操作不可恢复。`,
		confirmText: '确定删除',
		icon: 'mdi-delete',
		onConfirm: async () => {
			const ok = await deleteItem(knowledge.id!);
			if (ok) {
				$tip('删除成功');
			} else {
				$tip('删除失败', { color: 'error' });
			}
		},
	});
}

async function toggleRecall(knowledge: BusinessKnowledgeVO, isRecall: boolean) {
	if (!knowledge.id) return;
	try {
		const result = await businessKnowledgeService.recallKnowledge(knowledge.id, isRecall);
		if (result) {
			$tip(`${isRecall ? '已设为召回' : '已取消召回'}`);
			knowledge.isRecall = isRecall;
		} else {
			$tip('操作失败', { color: 'error' });
		}
	} catch {
		$tip('操作失败', { color: 'error' });
	}
}

async function retryEmbedding(knowledge: BusinessKnowledgeVO) {
	if (!knowledge.id) return;
	retryLoadingMap.value[knowledge.id] = true;
	try {
		const result = await businessKnowledgeService.retryEmbedding(knowledge.id);
		if (result) {
			$tip('重试向量化成功');
			await loadBusinessKnowledge();
		} else {
			$tip('重试向量化失败', { color: 'error' });
		}
	} catch {
		$tip('重试向量化失败', { color: 'error' });
	} finally {
		retryLoadingMap.value[knowledge.id] = false;
	}
}

function handleRefreshVectorStore() {
	showConfirm({
		title: '确认同步',
		message: '如果所有向量状态正常，即无需同步。确定要清除现有数据并开始重新同步吗？',
		confirmText: '确定同步',
		icon: 'mdi-sync',
		onConfirm: async () => {
			refreshLoading.value = true;
			try {
				const result = await businessKnowledgeService.refreshAllKnowledgeToVectorStore(
					agentId.value.toString(),
				);
				if (result) {
					$tip('同步到向量库成功');
				} else {
					$tip('同步到向量库失败', { color: 'error' });
				}
			} catch {
				$tip('同步到向量库失败', { color: 'error' });
			} finally {
				refreshLoading.value = false;
			}
		},
	});
}

// ——— 生命周期 ———
onMounted(() => loadBusinessKnowledge());
</script>

<style scoped>
</style>
