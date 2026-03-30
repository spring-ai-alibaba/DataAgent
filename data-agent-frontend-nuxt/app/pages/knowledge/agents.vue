<template>
	<section class="page-shell">
		<header class="d-flex align-center justify-space-between mb-8">
			<div>
				<h1 class="text-h4 font-weight-bold mb-1" style="color: #1565c0">智能体知识库</h1>
				<p class="text-body-2 text-medium-emphasis">
					维护智能体专属知识资源，支持文档上传、问答配置与向量召回。
				</p>
			</div>
			<div class="d-flex ga-3">
				<v-btn
					class="text-none bg-white"
					style="border-color: #e2e8f0"
					variant="outlined"
					prepend-icon="mdi-refresh"
					:loading="loading"
					@click="loadKnowledgeList"
				>
					刷新
				</v-btn>
				<v-btn
					:color="filterVisible ? 'blue-darken-1' : 'blue-grey-lighten-1'"
					prepend-icon="mdi-filter-variant"
					class="text-none px-6"
					elevation="0"
					@click="toggleFilter"
				>
					筛选
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
			</div>
		</header>

		<v-card variant="flat" border class="rounded-lg mb-4 pa-4">
			<div class="d-flex flex-wrap ga-3 align-center">
				<v-text-field
					v-model="queryParams.title"
					placeholder="请输入知识标题搜索"
					prepend-inner-icon="mdi-magnify"
					variant="outlined"
					density="compact"
					clearable
					hide-details
					class="search-field"
					style="max-width: 420px"
					@keyup.enter="handleSearch"
					@click:clear="handleSearch"
				/>
				<v-spacer />
				<v-chip color="blue-lighten-5" variant="flat" class="font-weight-medium">
					总数 {{ total }}
				</v-chip>
			</div>

			<v-expand-transition>
				<div v-show="filterVisible" class="mt-4 pt-4" style="border-top: 1px solid #e2e8f0">
					<div class="d-flex flex-wrap ga-3">
						<v-select
							v-model="queryParams.type"
							label="知识类型"
							:items="knowledgeTypeOptions"
							item-title="label"
							item-value="value"
							variant="outlined"
							density="compact"
							clearable
							hide-details
							style="max-width: 180px"
							@update:model-value="handleSearch"
						/>
						<v-select
							v-model="queryParams.embeddingStatus"
							label="处理状态"
							:items="embeddingStatusOptions"
							item-title="label"
							item-value="value"
							variant="outlined"
							density="compact"
							clearable
							hide-details
							style="max-width: 180px"
							@update:model-value="handleSearch"
						/>
						<v-btn
							variant="outlined"
							prepend-icon="mdi-filter-off"
							class="text-none"
							@click="clearFilters"
						>
							清空筛选
						</v-btn>
					</div>
				</div>
			</v-expand-transition>
		</v-card>

		<v-card variant="flat" border class="rounded-lg">
			<v-data-table
				:headers="headers"
				:items="knowledgeList"
				item-value="id"
				hover
				:loading="loading"
				hide-default-footer
			>
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.type="{ item }">
					<v-chip size="small" variant="tonal" :color="getTypeColor(item.type)">
						{{ getTypeLabel(item.type) }}
					</v-chip>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.embeddingStatus="{ item }">
					<v-chip size="small" variant="tonal" :color="getEmbeddingStatusColor(item.embeddingStatus)">
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
						{{ item.embeddingStatus || '未知' }}
					</v-chip>
					<v-tooltip v-if="item.embeddingStatus === 'FAILED' && item.errorMsg" :text="item.errorMsg" location="top">
						<template #activator="{ props }">
							<v-icon
								v-bind="props"
								icon="mdi-information-outline"
								size="16"
								color="error"
								class="ml-1 cursor-pointer"
							/>
						</template>
					</v-tooltip>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.isRecall="{ item }">
					<v-chip :color="item.isRecall ? 'blue-darken-1' : 'grey'" size="small" variant="tonal">
						{{ item.isRecall ? '已召回' : '未召回' }}
					</v-chip>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.actions="{ item }">
					<div class="d-flex ga-1 align-center">
						<v-btn
							size="small"
							variant="text"
							color="blue-darken-1"
							icon="mdi-cog"
							@click="editKnowledge(item)"
						/>
						<v-btn
							v-if="item.embeddingStatus === 'FAILED'"
							size="small"
							variant="text"
							color="orange-darken-1"
							icon="mdi-reload"
							:loading="item.id !== undefined ? retryLoadingMap[item.id] : false"
							@click="handleRetry(item)"
						/>
						<v-btn
							size="small"
							variant="text"
							:color="item.isRecall ? 'grey-darken-1' : 'blue-darken-1'"
							:icon="item.isRecall ? 'mdi-bookmark-off' : 'mdi-bookmark-plus'"
							@click="toggleStatus(item)"
						>
							<v-tooltip activator="parent" location="top">{{ item.isRecall ? '取消召回' : '设为召回' }}</v-tooltip>
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

				<template #no-data>
					<div class="d-flex flex-column align-center py-12">
						<v-icon icon="mdi-brain" size="64" color="blue-lighten-3" class="mb-4" />
						<p class="text-body-1 text-medium-emphasis mb-2">暂无智能体知识</p>
						<p class="text-body-2 text-disabled mb-6">点击「添加知识」为智能体补充知识资源</p>
						<v-btn color="blue-darken-3" prepend-icon="mdi-plus" class="text-none" elevation="0" @click="openCreateDialog">
							添加知识
						</v-btn>
					</div>
				</template>
			</v-data-table>

			<div class="d-flex align-center justify-end ga-4 px-4 py-4" style="border-top: 1px solid #e2e8f0">
				<v-select
					:model-value="queryParams.pageSize"
					:items="[10, 20, 50, 100]"
					variant="outlined"
					density="compact"
					hide-details
					style="max-width: 120px"
					@update:model-value="handleSizeChange"
				/>
				<v-pagination
					:model-value="queryParams.pageNum"
					:length="totalPages"
					density="comfortable"
					color="blue-darken-2"
					@update:model-value="handleCurrentChange"
				/>
			</div>
		</v-card>

		<v-dialog v-model="dialogVisible" max-width="820" persistent>
			<v-card rounded="lg">
				<v-card-title class="d-flex align-center pa-6 pb-4">
					<v-icon :icon="isEdit ? 'mdi-pencil-circle' : 'mdi-plus-circle'" color="blue-darken-2" class="mr-3" size="28" />
					<span class="text-h6 font-weight-bold">{{ isEdit ? '编辑知识' : '添加新知识' }}</span>
					<v-spacer />
					<v-btn icon="mdi-close" variant="text" size="small" @click="closeDialog" />
				</v-card-title>
				<v-divider />

				<v-card-text class="pa-6">
					<v-form ref="formRef">
						<div class="mb-5">
							<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">
								知识类型 <span class="text-error">*</span>
							</p>
							<v-select
								v-model="knowledgeForm.type"
								:items="knowledgeTypeOptions"
								item-title="label"
								item-value="value"
								placeholder="请选择知识类型"
								variant="outlined"
								density="compact"
								:disabled="isEdit"
								:rules="[v => !!v || '知识类型不能为空']"
								hide-details="auto"
								@update:model-value="handleTypeChange"
							/>
						</div>

						<v-alert
							v-if="knowledgeForm.type === 'QA'"
							type="info"
							variant="tonal"
							density="compact"
							class="mb-4"
						>
							请录入具体分析需求作为问题，并在答案中写出详细思考步骤与数据查找逻辑。
						</v-alert>
						<v-alert
							v-if="knowledgeForm.type === 'FAQ'"
							type="info"
							variant="tonal"
							density="compact"
							class="mb-4"
						>
							请针对业务术语、指标口径或常见歧义进行问答定义，用于统一 AI 判断标准。
						</v-alert>
						<v-alert
							v-if="knowledgeForm.type === 'DOCUMENT'"
							type="info"
							variant="tonal"
							density="compact"
							class="mb-4"
						>
							建议上传数据库表结构、码表映射字典或业务说明文档，便于 AI 检索字段含义。
						</v-alert>

						<div class="mb-5">
							<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">
								知识标题 <span class="text-error">*</span>
							</p>
							<v-text-field
								v-model="knowledgeForm.title"
								placeholder="为这份知识起一个易于识别的名称"
								variant="outlined"
								density="compact"
								:rules="[v => !!v?.trim() || '知识标题不能为空']"
								hide-details="auto"
							/>
						</div>

						<div v-if="knowledgeForm.type === 'DOCUMENT'" class="mb-5">
							<p v-if="!isEdit" class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">
								分块策略
							</p>
							<v-select
								v-if="!isEdit"
								v-model="knowledgeForm.splitterType"
								:items="splitterTypeOptions"
								item-title="label"
								item-value="value"
								variant="outlined"
								density="compact"
								hide-details
							/>

							<v-file-input
								v-if="!isEdit"
								v-model="selectedFile"
								label="上传文件"
								variant="outlined"
								density="compact"
								prepend-icon="mdi-paperclip"
								accept=".pdf,.doc,.docx,.txt,.md"
								show-size
								class="mt-4"
								hide-details="auto"
								@update:model-value="handleFileChange"
							/>

							<v-alert v-else type="info" variant="tonal" density="compact">
								文档类型知识不支持修改文件内容，如需修改请删除后重新创建。
							</v-alert>
						</div>

						<template v-if="knowledgeForm.type === 'QA' || knowledgeForm.type === 'FAQ'">
							<div class="mb-5">
								<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">
									问题 <span class="text-error">*</span>
								</p>
								<v-textarea
									v-model="knowledgeForm.question"
									placeholder="输入用户可能会问的问题"
									variant="outlined"
									density="compact"
									rows="2"
									:rules="[v => (knowledgeForm.type === 'QA' || knowledgeForm.type === 'FAQ') ? !!v?.trim() || '问题不能为空' : true]"
									hide-details="auto"
								/>
							</div>
							<div class="mb-2">
								<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">
									答案 <span class="text-error">*</span>
								</p>
								<v-textarea
									v-model="knowledgeForm.answer"
									placeholder="输入标准答案"
									variant="outlined"
									density="compact"
									rows="5"
									:rules="[v => (knowledgeForm.type === 'QA' || knowledgeForm.type === 'FAQ') ? !!v?.trim() || '答案不能为空' : true]"
									hide-details="auto"
								/>
							</div>
						</template>
					</v-form>
				</v-card-text>

				<v-divider />
				<v-card-actions class="pa-4 d-flex justify-end ga-2">
					<v-btn variant="outlined" class="text-none px-6" @click="closeDialog">取消</v-btn>
					<v-btn
						color="blue-darken-3"
						class="text-none px-6"
						elevation="0"
						:loading="saveLoading"
						@click="saveKnowledge"
					>
						{{ isEdit ? '保存更新' : '添加并处理' }}
					</v-btn>
				</v-card-actions>
			</v-card>
		</v-dialog>
	</section>
</template>

<script setup lang="ts">
import axios from 'axios';
import agentKnowledgeService, {
	type AgentKnowledge,
	type AgentKnowledgeQueryDTO,
} from '~/services/agentKnowledge/index';

interface KnowledgeForm extends AgentKnowledge {
	answer?: string;
	splitterType?: string;
}

const DEFAULT_AGENT_ID = 0;
const route = useRoute();
const agentId = computed(() => Number(route.query.agentId) || DEFAULT_AGENT_ID);

const { $tip } = useNuxtApp();
const { showConfirm } = useConfirm();

const loading = ref(false);
const saveLoading = ref(false);
const dialogVisible = ref(false);
const isEdit = ref(false);
const filterVisible = ref(false);
const total = ref(0);
const currentEditId = ref<number | null>(null);
const selectedFile = ref<File | null>(null);
const retryLoadingMap = ref<Record<number, boolean>>({});
const knowledgeList = ref<AgentKnowledge[]>([]);
const formRef = ref();

const queryParams = reactive<AgentKnowledgeQueryDTO>({
	agentId: agentId.value,
	title: '',
	type: '',
	embeddingStatus: '',
	pageNum: 1,
	pageSize: 10,
});

const knowledgeForm = ref<KnowledgeForm>({
	agentId: agentId.value,
	title: '',
	content: '',
	type: 'DOCUMENT',
	isRecall: true,
	question: '',
	answer: '',
	splitterType: 'recursive',
});

const headers = [
	{ title: '标题', key: 'title', minWidth: '170px' },
	{ title: '类型', key: 'type', width: '110px', sortable: false },
	{ title: '处理状态', key: 'embeddingStatus', width: '150px', sortable: false },
	{ title: '召回状态', key: 'isRecall', width: '110px', sortable: false },
	{ title: '创建时间', key: 'createdTime', width: '170px' },
	{ title: '操作', key: 'actions', width: '170px', sortable: false },
];

const knowledgeTypeOptions = [
	{ label: '文档', value: 'DOCUMENT' },
	{ label: '问答对', value: 'QA' },
	{ label: '常见问题', value: 'FAQ' },
];

const embeddingStatusOptions = [
	{ label: '已完成', value: 'COMPLETED' },
	{ label: '处理中', value: 'PROCESSING' },
	{ label: '失败', value: 'FAILED' },
	{ label: '等待中', value: 'PENDING' },
];

const splitterTypeOptions = [
	{ label: 'Token 分块', value: 'token' },
	{ label: '递归分块', value: 'recursive' },
	{ label: '句子分块', value: 'sentence' },
	{ label: '段落分块', value: 'paragraph' },
	{ label: '语义分块', value: 'semantic' },
];

const totalPages = computed(() => {
	const pageSize = queryParams.pageSize || 10;
	return Math.max(1, Math.ceil(total.value / pageSize));
});

function getTypeLabel(type?: string) {
	switch (type) {
		case 'DOCUMENT':
			return '文档';
		case 'QA':
			return '问答对';
		case 'FAQ':
			return 'FAQ';
		default:
			return type || '未知';
	}
}

function getTypeColor(type?: string) {
	switch (type) {
		case 'DOCUMENT':
			return 'blue-darken-1';
		case 'QA':
			return 'indigo';
		case 'FAQ':
			return 'cyan-darken-1';
		default:
			return 'grey';
	}
}

function getEmbeddingStatusColor(status?: string) {
	switch (status) {
		case 'COMPLETED':
			return 'success';
		case 'PROCESSING':
			return 'blue-darken-1';
		case 'FAILED':
			return 'error';
		case 'PENDING':
			return 'warning';
		default:
			return 'grey';
	}
}

function toggleFilter() {
	filterVisible.value = !filterVisible.value;
}

function clearFilters() {
	queryParams.type = '';
	queryParams.embeddingStatus = '';
	handleSearch();
}

function handleSearch() {
	queryParams.pageNum = 1;
	loadKnowledgeList();
}

function handleSizeChange(val: number | string | null) {
	queryParams.pageSize = Number(val) || 10;
	queryParams.pageNum = 1;
	loadKnowledgeList();
}

function handleCurrentChange(val: number) {
	queryParams.pageNum = val;
	loadKnowledgeList();
}

function handleTypeChange() {
	knowledgeForm.value.content = '';
	knowledgeForm.value.question = '';
	knowledgeForm.value.answer = '';
	selectedFile.value = null;
}

function handleFileChange(file: File | File[] | null) {
	if (Array.isArray(file)) {
		selectedFile.value = file[0] || null;
		return;
	}
	selectedFile.value = file;
}

function resetForm() {
	knowledgeForm.value = {
		agentId: agentId.value,
		title: '',
		content: '',
		type: 'DOCUMENT',
		isRecall: true,
		question: '',
		answer: '',
		splitterType: 'recursive',
	};
	selectedFile.value = null;
	currentEditId.value = null;
}

function openCreateDialog() {
	isEdit.value = false;
	resetForm();
	dialogVisible.value = true;
}

function closeDialog() {
	dialogVisible.value = false;
	resetForm();
}

function editKnowledge(knowledge: AgentKnowledge) {
	isEdit.value = true;
	currentEditId.value = knowledge.id ?? null;
	knowledgeForm.value = {
		...knowledge,
		answer: knowledge.type === 'QA' || knowledge.type === 'FAQ' ? knowledge.content : '',
		splitterType: 'recursive',
	};
	dialogVisible.value = true;
}

async function loadKnowledgeList() {
	loading.value = true;
	queryParams.agentId = agentId.value;
	try {
		const result = await agentKnowledgeService.queryByPage({
			...queryParams,
			type: queryParams.type || '',
			embeddingStatus: queryParams.embeddingStatus || '',
		});
		if (result.success) {
			knowledgeList.value = result.data || [];
			total.value = result.total || 0;
		} else {
			$tip(result.message || '加载知识列表失败', { color: 'error' });
		}
	} catch {
		$tip('加载知识列表失败', { color: 'error' });
	} finally {
		loading.value = false;
	}
}

function toggleStatus(knowledge: AgentKnowledge) {
	if (!knowledge.id) return;
	const nextRecallStatus = !knowledge.isRecall;
	showConfirm({
		title: '状态变更确认',
		message: `确定要${nextRecallStatus ? '设为召回' : '取消召回'}「${knowledge.title}」吗？`,
		confirmText: '确认',
		onConfirm: async () => {
			const result = await agentKnowledgeService.updateRecallStatus(
				knowledge.id!,
				nextRecallStatus,
			);
			if (result) {
				knowledge.isRecall = nextRecallStatus;
				$tip(`${nextRecallStatus ? '设为召回' : '取消召回'}成功`);
			} else {
				$tip('操作失败', { color: 'error' });
			}
		},
	});
}

async function handleRetry(knowledge: AgentKnowledge) {
	if (!knowledge.id) return;
	retryLoadingMap.value[knowledge.id] = true;
	try {
		const success = await agentKnowledgeService.retryEmbedding(knowledge.id);
		if (success) {
			$tip('重试请求已发送');
			await loadKnowledgeList();
		} else {
			$tip('重试失败', { color: 'error' });
		}
	} catch {
		$tip('重试失败', { color: 'error' });
	} finally {
		retryLoadingMap.value[knowledge.id] = false;
	}
}

function deleteKnowledge(knowledge: AgentKnowledge) {
	if (!knowledge.id) return;
	showConfirm({
		title: '删除确认',
		message: `确定要删除知识「${knowledge.title}」吗？此操作不可恢复。`,
		confirmText: '确定删除',
		icon: 'mdi-delete',
		onConfirm: async () => {
			const result = await agentKnowledgeService.delete(knowledge.id!);
			if (result) {
				$tip('删除成功');
				await loadKnowledgeList();
			} else {
				$tip('删除失败', { color: 'error' });
			}
		},
	});
}

async function saveKnowledge() {
	const validateResult = await formRef.value?.validate();
	const valid = validateResult?.valid;
	if (!valid) return;

	if (knowledgeForm.value.type === 'DOCUMENT' && !isEdit.value && !selectedFile.value) {
		$tip('请上传文件', { color: 'warning' });
		return;
	}

	if ((knowledgeForm.value.type === 'QA' || knowledgeForm.value.type === 'FAQ') && !knowledgeForm.value.question?.trim()) {
		$tip('请输入问题', { color: 'warning' });
		return;
	}

	if ((knowledgeForm.value.type === 'QA' || knowledgeForm.value.type === 'FAQ') && !knowledgeForm.value.answer?.trim()) {
		$tip('请输入答案', { color: 'warning' });
		return;
	}

	saveLoading.value = true;
	try {
		if (isEdit.value && currentEditId.value) {
			const updateData = {
				...knowledgeForm.value,
				type: knowledgeForm.value.type?.toUpperCase(),
				content:
					knowledgeForm.value.type === 'QA' || knowledgeForm.value.type === 'FAQ'
						? knowledgeForm.value.answer
						: knowledgeForm.value.content,
			};
			const result = await agentKnowledgeService.update(currentEditId.value, updateData);
			if (result) {
				$tip('更新成功');
			} else {
				$tip('更新失败', { color: 'error' });
				return;
			}
		} else {
			const formData = new FormData();
			formData.append('agentId', String(agentId.value));
			formData.append('title', knowledgeForm.value.title || '');
			formData.append('type', knowledgeForm.value.type || 'DOCUMENT');
			formData.append('isRecall', knowledgeForm.value.isRecall ? '1' : '0');

			if (knowledgeForm.value.type === 'DOCUMENT' && selectedFile.value) {
				formData.append('file', selectedFile.value);
				if (knowledgeForm.value.splitterType) {
					formData.append('splitterType', knowledgeForm.value.splitterType);
				}
			} else {
				formData.append('question', knowledgeForm.value.question || '');
				formData.append('content', knowledgeForm.value.answer || '');
			}

			const response = await axios.post('/api/agent-knowledge/create', formData, {
				headers: { 'Content-Type': 'multipart/form-data' },
			});

			if (response.data.success) {
				$tip('创建成功');
			} else {
				$tip(response.data.message || '创建失败', { color: 'error' });
				return;
			}
		}

		dialogVisible.value = false;
		await loadKnowledgeList();
	} catch {
		$tip(`${isEdit.value ? '更新' : '创建'}失败`, { color: 'error' });
	} finally {
		saveLoading.value = false;
	}
}

onMounted(() => loadKnowledgeList());
</script>

<style scoped>
.page-shell {
	padding: 32px;
}

.search-field :deep(.v-field__outline) {
	--v-field-border-color: #e2e8f0;
}
</style>
