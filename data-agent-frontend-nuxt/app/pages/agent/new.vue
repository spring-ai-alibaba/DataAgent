<template>
	<section class="page-shell">
		<KnowledgePageHeader
			title="新建智能体任务"
			subtitle="创建你的专属数据分析智能体，统一接入知识、提示词和语义能力。"
		>
			<template #actions>
				<v-btn
					class="text-none bg-white"
					style="border-color: #e2e8f0"
					variant="outlined"
					prepend-icon="mdi-arrow-left"
					@click="goBack"
				>
					返回列表
				</v-btn>
				<v-btn
					color="blue-darken-3"
					prepend-icon="mdi-plus"
					class="text-none px-6"
					elevation="0"
					:loading="loading"
					@click="createAgent"
				>
					{{ loading ? '创建中...' : '创建智能体' }}
				</v-btn>
			</template>
		</KnowledgePageHeader>

		<v-card variant="flat" border class="rounded-lg pa-6">
			<v-form ref="formRef">
				<div class="mb-6">
					<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">头像设置</p>
					<div class="d-flex align-center ga-4 flex-wrap">
						<v-avatar size="88" rounded="lg" class="avatar-preview">
							<v-img :src="agentForm.avatar" cover @error="handleImageError" />
						</v-avatar>
						<div class="d-flex ga-2">
							<v-btn variant="outlined" prepend-icon="mdi-refresh" class="text-none" @click="regenerateAvatar">
								重新生成
							</v-btn>
							<v-btn
								variant="outlined"
								prepend-icon="mdi-upload"
								class="text-none"
								:loading="uploading"
								@click="triggerFileUpload"
							>
								{{ uploading ? '上传中...' : '上传图片' }}
							</v-btn>
							<input
								ref="fileInput"
								type="file"
								accept="image/*"
								style="display: none"
								@change="handleFileUpload"
							/>
						</div>
					</div>
				</div>

				<v-row>
					<v-col cols="12" md="6">
						<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">智能体名称 <span class="text-error">*</span></p>
						<v-text-field
							v-model="agentForm.name"
							placeholder="请输入智能体名称"
							variant="outlined"
							density="compact"
							:rules="[v => !!v?.trim() || '智能体名称不能为空']"
							hide-details="auto"
						/>
					</v-col>
					<v-col cols="12" md="6">
						<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">分类 <span class="text-error">*</span></p>
						<v-text-field
							v-model="agentForm.category"
							placeholder="请输入智能体分类"
							variant="outlined"
							density="compact"
							:rules="[v => !!v?.trim() || '分类不能为空']"
							hide-details="auto"
						/>
					</v-col>
					<v-col cols="12">
						<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">描述</p>
						<v-textarea
							v-model="agentForm.description"
							placeholder="请输入智能体描述"
							variant="outlined"
							density="compact"
							rows="3"
							hide-details="auto"
						/>
					</v-col>
					<v-col cols="12">
						<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">智能体 Prompt</p>
						<v-textarea
							v-model="agentForm.prompt"
							placeholder="请输入智能体 Prompt"
							variant="outlined"
							density="compact"
							rows="4"
							hide-details="auto"
						/>
					</v-col>
					<v-col cols="12" md="6">
						<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">标签 <span class="text-error">*</span></p>
						<v-text-field
							v-model="agentForm.tags"
							placeholder="多个标签使用逗号分隔"
							variant="outlined"
							density="compact"
							:rules="[v => !!v?.trim() || '标签不能为空']"
							hide-details="auto"
						/>
					</v-col>
					<v-col cols="12" md="6">
						<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">状态</p>
						<v-select
							v-model="agentForm.status"
							:items="statusOptions"
							item-title="label"
							item-value="value"
							variant="outlined"
							density="compact"
							hide-details="auto"
						/>
					</v-col>
				</v-row>
			</v-form>
		</v-card>
	</section>
</template>

<script setup lang="ts">
import agentService from '~/services/agent/index';
import { fileUploadApi } from '~/services/fileUpload/index';

const router = useRouter();
const { $tip } = useNuxtApp();

const loading = ref(false);
const uploading = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);
const formRef = ref();

const statusOptions = [
	{ label: '待发布', value: 'draft' },
	{ label: '已发布', value: 'published' },
	{ label: '已下线', value: 'offline' },
];

const agentForm = reactive({
	name: '',
	description: '',
	avatar: '',
	category: '',
	tags: '',
	prompt: '',
	status: 'draft',
	humanReviewEnabled: false,
});

function generateFallbackAvatar(): string {
	const colors = ['3B82F6', '8B5CF6', '10B981', 'F59E0B', 'EF4444', '6366F1'];
	const letters = ['AI', '数据', '智能', 'DA', 'BI', 'ML'];
	const randomColor = colors[Math.floor(Math.random() * colors.length)];
	const randomLetter = letters[Math.floor(Math.random() * letters.length)];
	const svg = `<svg width="200" height="200" xmlns="http://www.w3.org/2000/svg"><rect width="200" height="200" fill="#${randomColor}"/><text x="100" y="120" font-family="Arial, sans-serif" font-size="48" font-weight="bold" text-anchor="middle" fill="white">${randomLetter}</text></svg>`;
	return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

function regenerateAvatar() {
	agentForm.avatar = generateFallbackAvatar();
}

function handleImageError() {
	agentForm.avatar = generateFallbackAvatar();
}

function triggerFileUpload() {
	fileInput.value?.click();
}

async function handleFileUpload(event: Event) {
	const target = event.target as HTMLInputElement;
	const file = target.files?.[0];
	if (!file) return;
	if (!file.type.startsWith('image/')) {
		$tip('请选择图片文件', { color: 'error' });
		return;
	}
	if (file.size > 5 * 1024 * 1024) {
		$tip('图片大小不能超过5MB', { color: 'error' });
		return;
	}

	try {
		uploading.value = true;
		const reader = new FileReader();
		reader.onload = e => {
			if (e.target?.result) agentForm.avatar = String(e.target.result);
		};
		reader.readAsDataURL(file);

		const response = await fileUploadApi.uploadAvatar(file);
		if (response.success && response.url) {
			agentForm.avatar = response.url;
			$tip('头像上传成功');
		} else {
			throw new Error(response.message || '上传失败');
		}
	} catch (error) {
		$tip(`头像上传失败: ${error instanceof Error ? error.message : '未知错误'}`, {
			color: 'error',
		});
		agentForm.avatar = generateFallbackAvatar();
	} finally {
		uploading.value = false;
		if (fileInput.value) fileInput.value.value = '';
	}
}

function goBack() {
	router.push('/agent');
}

async function createAgent() {
	const validateResult = await formRef.value?.validate();
	const valid = validateResult?.valid;
	if (!valid) return;

	loading.value = true;
	try {
		const payload = {
			name: agentForm.name.trim(),
			description: agentForm.description.trim(),
			avatar: agentForm.avatar.trim(),
			category: agentForm.category.trim(),
			tags: agentForm.tags.trim(),
			prompt: agentForm.prompt.trim(),
			status: agentForm.status,
			humanReviewEnabled: agentForm.humanReviewEnabled ? 1 : 0,
		};
		const result = await agentService.create(payload);
		$tip(`智能体创建成功！状态：${payload.status === 'published' ? '已发布' : '草稿'}`);
		await router.push({ path: '/chat', query: { agentId: result.id } });
	} catch {
		$tip('创建失败，请重试', { color: 'error' });
	} finally {
		loading.value = false;
	}
}

onMounted(() => {
	agentForm.avatar = generateFallbackAvatar();
});
</script>

<style scoped>
.page-shell {
	padding: 32px;
}

.avatar-preview {
	border: 2px solid #e5e7eb;
}
</style>
