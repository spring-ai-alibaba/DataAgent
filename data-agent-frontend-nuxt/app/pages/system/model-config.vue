<template>
	<v-container fluid class="pa-8 model-config-container">
		<!-- Header Section -->
		<header class="d-flex align-center justify-space-between mb-8">
			<div>
				<h1 class="text-h4 font-weight-bold mb-1 text-slate-900">模型服务</h1>
				<p class="text-body-2 text-medium-emphasis">
					连接 LLM 供应商，支持对话生成与向量检索。
				</p>
			</div>
			<div class="d-flex ga-3">
				<v-btn
					variant="outlined"
					prepend-icon="mdi-refresh"
					:loading="loading"
					@click="fetchConfigs"
					class="text-none"
					style="border-color: #e2e8f0"
				>
					刷新
				</v-btn>
				<v-btn
					color="black"
					prepend-icon="mdi-plus"
					class="text-none px-6"
					elevation="0"
					@click="openCreateDialog(activeTab)"
				>
					{{ activeTab === 'CHAT' ? '添加对话模型' : '添加嵌入模型' }}
				</v-btn>
			</div>
		</header>

		<!-- Tab Navigation (Segmented Toggle) -->
		<div class="d-flex justify-center mb-8">
			<v-btn-toggle
				v-model="activeTab"
				mandatory
				rounded="pill"
				color="primary"
				class="segmented-toggle border"
				density="comfortable"
				variant="flat"
			>
				<v-btn
					value="CHAT"
					variant="flat"
					class="px-8 text-none font-weight-bold"
					>对话模型</v-btn
				>
				<v-btn
					value="EMBEDDING"
					variant="flat"
					class="px-8 text-none font-weight-bold"
					>嵌入模型</v-btn
				>
			</v-btn-toggle>
		</div>

		<v-row justify="center">
			<v-col cols="12" xl="10">
				<!-- Hint Alert (Commented out) -->
				<!--
				<v-alert
					v-if="activeTab === 'EMBEDDING'"
					icon="mdi-information-outline"
					variant="tonal"
					color="blue-grey"
					class="mb-6 rounded-lg text-body-2"
					border="start"
				>
					提示：处理中文建议使用
					<strong>bge-large-zh</strong>；多语言场景推荐使用 OpenAI 系列。
				</v-alert>
				-->

				<!-- Loading State -->
				<div v-if="loading" class="d-flex flex-column ga-4">
					<v-skeleton-loader
						v-for="i in 3"
						:key="i"
						type="list-item-avatar-three-line"
						class="border rounded-lg"
					></v-skeleton-loader>
				</div>

				<!-- List Content -->
				<div v-else>
					<TransitionGroup name="list" tag="div" class="list-container">
						<div
							v-for="model in filteredModels"
							:key="model.id"
							class="list-item-wrap"
						>
							<v-card
								variant="outlined"
								class="model-item-card"
								:class="{ 'is-active': model.isActive }"
								rounded="lg"
							>
								<div class="pa-5 d-flex align-center">
									<!-- Icon -->
									<v-avatar
										:color="model.isActive ? 'primary' : 'grey-lighten-4'"
										:class="{ 'text-white': model.isActive }"
										size="48"
										rounded="lg"
										class="mr-4"
									>
										<v-icon
											:icon="
												model.modelType === 'CHAT'
													? 'mdi-chat-processing-outline'
													: 'mdi-database-search-outline'
											"
										></v-icon>
									</v-avatar>

									<!-- Info -->
									<div class="flex-grow-1">
										<div class="d-flex align-center mb-1">
											<span class="text-subtitle-1 font-weight-bold mr-2">{{
												model.modelName
											}}</span>
											<!-- 带有呼吸灯的默认标签 -->
											<v-chip
												v-if="model.isActive"
												size="x-small"
												color="primary"
												variant="flat"
												class="px-2 font-weight-bold d-inline-flex align-center"
											>
												<span class="breathing-dot"></span>
												默认
											</v-chip>
										</div>
										<div
											class="d-flex align-center text-caption text-medium-emphasis ga-4"
										>
											<span class="d-flex align-center">
												<v-icon size="14" class="mr-1">mdi-tray-full</v-icon>
												{{ providerLabel(model.provider) }}
											</span>
											<span class="d-flex align-center">
												<v-icon size="14" class="mr-1">mdi-link-variant</v-icon>
												{{ model.baseUrl || '默认终端地址' }}
											</span>
										</div>
									</div>

									<!-- Actions -->
									<div class="d-flex align-center ga-4">
										<v-btn
											v-if="!model.isActive"
											variant="outlined"
											color="primary"
											size="small"
											class="text-none font-weight-bold"
											style="border-width: 1px"
											:loading="activatingId === model.id"
											@click="handleActivate(model)"
										>
											设为默认
										</v-btn>

										<v-btn
											variant="outlined"
											size="small"
											class="text-none"
											style="border-color: #e2e8f0"
											@click="handleTestConnection(model)"
											:loading="testingId === model.id"
										>
											测试连接
										</v-btn>

										<v-divider vertical inset class="mx-1"></v-divider>

										<div class="d-flex align-center ga-1">
											<v-btn
												icon="mdi-pencil-outline"
												variant="text"
												size="small"
												color="grey-darken-1"
												@click="handleEdit(model)"
											></v-btn>
											<v-btn
												icon="mdi-delete-outline"
												variant="text"
												size="small"
												color="error"
												@click="handleDelete(model)"
											></v-btn>
										</div>
									</div>
								</div>
							</v-card>
						</div>

						<!-- Empty State (Inside TransitionGroup) -->
						<div
							v-if="filteredModels.length === 0"
							:key="activeTab + 'empty'"
							class="text-center py-16 border-dashed rounded-xl bg-white"
						>
							<v-icon
								icon="mdi-robot-vacuum-variant-off"
								size="64"
								color="grey-lighten-2"
								class="mb-4"
							></v-icon>
							<h3 class="text-h6 font-weight-medium text-grey-darken-1">
								暂无配置
							</h3>
							<p class="text-body-2 text-grey mb-6">
								您还没有在该分类下添加任何供应商
							</p>
							<v-btn
								color="black"
								variant="flat"
								@click="openCreateDialog(activeTab)"
								>立即添加</v-btn
							>
						</div>
					</TransitionGroup>
				</div>
			</v-col>
		</v-row>

		<!-- Config Dialog -->
		<v-dialog v-model="dialog.visible" max-width="500" persistent>
			<v-card rounded="lg" class="pa-2">
				<v-card-title
					class="d-flex align-center justify-space-between px-4 pt-4"
				>
					<span class="text-h6 font-weight-bold">{{ dialogTitle }}</span>
					<v-btn
						icon="mdi-close"
						variant="text"
						size="small"
						@click="closeDialog"
					/>
				</v-card-title>

				<v-card-text class="pt-4">
					<v-form ref="formRef" v-model="formValid" fast-fail>
						<v-row dense>
							<v-col cols="12">
								<span class="custom-label">模型供应商</span>
								<v-select
									v-model="form.provider"
									:items="providerOptions"
									variant="outlined"
									density="compact"
									:rules="[rules.required]"
								/>
							</v-col>
							<v-col cols="12">
								<span class="custom-label">模型名称</span>
								<v-text-field
									v-model="form.modelName"
									placeholder="例如: gpt-4o 或 deepseek-chat"
									variant="outlined"
									density="compact"
									:rules="[rules.required]"
								/>
							</v-col>
							<v-col cols="12">
								<span class="custom-label">API 密钥 (API Key)</span>
								<v-text-field
									v-model="form.apiKey"
									:type="showApiKey ? 'text' : 'password'"
									:append-inner-icon="showApiKey ? 'mdi-eye-off' : 'mdi-eye'"
									@click:append-inner="showApiKey = !showApiKey"
									placeholder="sk-..."
									variant="outlined"
									density="compact"
									:rules="form.provider === 'custom' ? [] : [rules.required]"
								/>
							</v-col>
							<v-col cols="12">
								<span class="custom-label">接口地址 (Base URL)</span>
								<v-text-field
									v-model="form.baseUrl"
									placeholder="https://api.example.com/v1"
									variant="outlined"
									density="compact"
									:rules="[rules.required]"
								/>
							</v-col>

							<!-- Extra fields from original but styled like new -->
							<v-col cols="12" v-if="form.modelType === 'CHAT'">
								<span class="custom-label">Completions 路径</span>
								<v-text-field
									v-model="form.completionsPath"
									placeholder="默认 /v1/chat/completions"
									variant="outlined"
									density="compact"
								/>
							</v-col>

							<v-col cols="12" v-if="form.modelType === 'EMBEDDING'">
								<span class="custom-label">Embeddings 路径</span>
								<v-text-field
									v-model="form.embeddingsPath"
									placeholder="默认 /v1/embeddings"
									variant="outlined"
									density="compact"
								/>
							</v-col>

							<v-col cols="6">
								<span class="custom-label"
									>温度系数: {{ form.temperature }}</span
								>
								<v-slider
									v-model="form.temperature"
									min="0"
									max="2"
									step="0.1"
									color="black"
									density="compact"
									hide-details
								/>
							</v-col>
							<v-col cols="6">
								<span class="custom-label">最大 Token 数</span>
								<v-text-field
									v-model.number="form.maxTokens"
									type="number"
									variant="outlined"
									density="compact"
									hide-details
									:rules="[rules.maxTokens]"
								/>
							</v-col>
						</v-row>
					</v-form>
				</v-card-text>

				<v-card-actions class="pa-4 pt-0">
					<v-spacer></v-spacer>
					<v-btn variant="text" class="text-none" @click="closeDialog"
						>取消</v-btn
					>
					<v-btn
						color="black"
						class="text-none px-8"
						elevation="0"
						:loading="saving"
						@click="handleSubmit"
						>确认保存</v-btn
					>
				</v-card-actions>
			</v-card>
		</v-dialog>
	</v-container>
</template>

<script setup lang="ts">
import type { VForm } from 'vuetify/components';
import modelConfigService, {
	type ModelConfig,
	type ModelType,
} from '@/services/modelConfig';

const { $tip } = useNuxtApp();

const providerOptions = [
	{ title: 'DeepSeek', value: 'deepseek' },
	{ title: 'Qwen', value: 'qwen' },
	{ title: 'OpenAI', value: 'openai' },
	{ title: 'Siliconflow', value: 'siliconflow' },
	{ title: 'Custom Provider', value: 'custom' },
];

const providerBaseUrlMap: Record<string, string> = {
	deepseek: 'https://api.deepseek.com',
	qwen: 'https://dashscope.aliyuncs.com/compatible-mode',
	openai: 'https://api.openai.com',
	siliconflow: 'https://api.siliconflow.cn',
	custom: '',
};

const loading = ref(false);
const configs = ref<ModelConfig[]>([]);
const activeTab = ref<ModelType>('CHAT');
const testingId = ref<number | null>(null);
const activatingId = ref<number | null>(null);
const deletingId = ref<number | null>(null);
const saving = ref(false);
const showApiKey = ref(false);

const formRef = ref<VForm | null>(null);
const formValid = ref(false);
const form = reactive<ModelConfig>({
	provider: providerOptions[0]?.value || 'deepseek',
	apiKey: '',
	baseUrl: providerBaseUrlMap[providerOptions[0]?.value || 'deepseek'] || '',
	modelName: '',
	modelType: 'CHAT',
	temperature: 0,
	maxTokens: 2000,
	completionsPath: '',
	embeddingsPath: '',
	isActive: false,
});

const dialog = reactive<{
	visible: boolean;
	mode: 'create' | 'edit';
	presetTab: ModelType;
}>({
	visible: false,
	mode: 'create',
	presetTab: 'CHAT',
});

const rules = {
	required: (value: string | number | null | undefined) =>
		value !== null && value !== undefined && value !== ''
			? true
			: '该字段为必填项',
	maxTokens: (value: number) =>
		value >= 100 && value <= 10000 ? true : 'Token 范围需在 100 - 10000 之间',
};

const dialogTitle = computed(() =>
	dialog.mode === 'edit' ? '编辑模型配置' : '新增模型配置',
);

const filteredModels = computed(() =>
	configs.value.filter((model) => model.modelType === activeTab.value),
);

const addButtonLabel = computed(() =>
	activeTab.value === 'CHAT' ? '添加对话模型实例' : '添加嵌入模型实例',
);

const providerLabel = (value: string) => {
	const item = providerOptions.find((option) => option.value === value);
	return item ? item.title : value;
};

const resetForm = (type: ModelType) => {
	form.provider = providerOptions[0]?.value || 'deepseek';
	form.apiKey = '';
	form.baseUrl = providerBaseUrlMap[form.provider] || '';
	form.modelName = '';
	form.modelType = type;
	form.temperature = 0;
	form.maxTokens = 2000;
	form.completionsPath = '';
	form.embeddingsPath = '';
	form.isActive = false;
};

const fetchConfigs = async () => {
	loading.value = true;
	try {
		const response = await modelConfigService.list();
		console.log(response);
		configs.value = response || [];
	} catch (error) {
		$tip('获取模型配置失败，请稍后重试', {
			icon: 'mdi-alert-circle',
			color: 'error',
		});
		configs.value = [];
	} finally {
		loading.value = false;
	}
};

const openCreateDialog = (type: ModelType) => {
	dialog.mode = 'create';
	dialog.visible = true;
	dialog.presetTab = type;
	resetForm(type);
};

const handleEdit = (model: ModelConfig) => {
	dialog.mode = 'edit';
	dialog.visible = true;
	dialog.presetTab = model.modelType;
	Object.assign(form, model);
};

const closeDialog = () => {
	dialog.visible = false;
	showApiKey.value = false;
};

const submitConfig = async (isUpdate: boolean) => {
	saving.value = true;
	try {
		let result;
		if (isUpdate) {
			result = await modelConfigService.update(form);
		} else {
			result = await modelConfigService.add(form);
		}

		if (result.success) {
			$tip(isUpdate ? '配置更新成功' : '配置创建成功');
			closeDialog();
			fetchConfigs();
		} else {
			$tip(result.message || '操作失败，请重试', {
				icon: 'mdi-alert-circle',
				color: 'error',
			});
		}
	} catch (error) {
		$tip('请求失败，请检查网络', { icon: 'mdi-alert-circle', color: 'error' });
	} finally {
		saving.value = false;
	}
};

const handleSubmit = async () => {
	const validateResult = await formRef.value?.validate();
	if (!validateResult?.valid) return;
	await submitConfig(dialog.mode === 'edit');
};

const handleDelete = async (model: ModelConfig) => {
	if (!model.id) {
		$tip('模型ID不存在', { icon: 'mdi-alert-circle', color: 'error' });
		return;
	}
	deletingId.value = model.id ?? null;
	showConfirm({
		title: '确认删除',
		message: `你确认要删除 ${model.modelName} 吗？`,
		icon: 'mdi-help-circle',
		confirmText: '确认',
		onConfirm: async () => {
			const result = await modelConfigService.delete(
				model.id as unknown as number,
			);
			if (result.success) {
				$tip('模型已删除');
				fetchConfigs();
			} else {
				$tip(result.message || '删除失败', {
					icon: 'mdi-alert-circle',
					color: 'error',
				});
			}
			deletingId.value = null;
		},
	});
};

const handleActivate = async (model: ModelConfig) => {
	if (!model.id) return;
	if (
		model.modelType === 'EMBEDDING' &&
		!window.confirm('切换嵌入模型会导致现有向量数据失效，确定继续吗？')
	) {
		return;
	}

	activatingId.value = model.id;
	try {
		const result = await modelConfigService.activate(model.id);
		if (result.success) {
			$tip('已设置为默认模型');
			fetchConfigs();
		} else {
			$tip(result.message || '设置失败', {
				icon: 'mdi-alert-circle',
				color: 'error',
			});
		}
	} catch (error) {
		$tip('操作失败，请检查网络', { icon: 'mdi-alert-circle', color: 'error' });
	} finally {
		activatingId.value = null;
	}
};

const handleTestConnection = async (model: ModelConfig) => {
	testingId.value = model.id ?? null;
	try {
		const result = await modelConfigService.testConnection(model);
		if (result.success) {
			$tip(result.message || '连接测试成功');
		} else {
			$tip(result.message || '连接测试失败', {
				icon: 'mdi-alert-circle',
				color: 'error',
			});
		}
	} catch (error) {
		$tip('连接测试失败，请检查网络', {
			icon: 'mdi-alert-circle',
			color: 'error',
		});
	} finally {
		testingId.value = null;
	}
};

const watchProviderChange = (value: string) => {
	if (value && value !== 'custom') {
		form.baseUrl = providerBaseUrlMap[value] || '';
	}
};

watch(
	() => form.provider,
	(value) => watchProviderChange(value),
);

onMounted(fetchConfigs);
</script>

<style scoped>
.model-config-container {
	background-color: #f8fafc;
	min-height: 100%;
}

.text-slate-900 {
	color: #0f172a;
}

.model-item-card {
	transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
	border: 1px solid #e2e8f0 !important;
	background-color: #ffffff !important;
}

.model-item-card:hover {
	border-color: #94a3b8 !important;
	transform: translateY(-2px);
	box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
}

.model-item-card.is-active {
	border-color: #2563eb !important;
	background-color: #f0f7ff !important;
}

.border-dashed {
	border: 2px dashed #e2e8f0 !important;
}

.v-tabs {
	border-bottom: 1px solid #e2e8f0;
}

.v-tab {
	text-transform: none !important;
	font-weight: 500 !important;
	letter-spacing: 0 !important;
}

/* 列表容器需要相对定位，方便子元素离开时绝对定位 */
.list-container {
	position: relative;
}

/* 所有的过渡和位移都在 0.4s 内完成 */
.list-enter-active,
.list-leave-active,
.list-move {
	transition: all 0.4s cubic-bezier(0.55, 0, 0.1, 1);
}

/* 入场动画：透明度增加 + 从下方滑入 */
.list-enter-from {
	opacity: 0;
	transform: scale(0.9) translateY(20px);
}

/* 离场动画：透明度减少 + 向上滑出 */
.list-leave-to {
	opacity: 0;
	transform: scale(0.9) translateY(-20px);
}

/* 关键修复：离开时的元素必须绝对定位，否则下方元素无法平滑位移 */
.list-leave-active {
	position: absolute;
	width: 100%; /* 保持宽度一致，防止绝对定位后缩成一团 */
}

/* 分段开关样式优化 */
.segmented-control {
	background-color: #f1f5f9 !important;
	padding: 4px !important;
	height: 48px !important;
	border: none !important;
}

.segmented-control .v-btn {
	border: none !important;
	height: 40px !important;
	font-weight: 600 !important;
	letter-spacing: 0.02em !important;
	color: #64748b !important;
}

.segmented-control .v-btn--selected {
	background-color: #ffffff !important;
	color: #0f172a !important;
	box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05) !important;
}
</style>
