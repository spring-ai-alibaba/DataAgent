<template>
	<div class="expand-row-content pa-6">
		<div class="manage-tables-container">
			<div class="d-flex align-center justify-space-between mb-4">
				<div class="d-flex align-center">
					<v-icon size="20" class="mr-2 text-primary">mdi-table-cog</v-icon>
					<span class="text-subtitle-1 font-weight-bold">数据表管理</span>
					<span class="text-caption text-medium-emphasis ml-4">
						已选择 {{ selectedTables.length }} 个表
					</span>
				</div>
				<div class="ga-2 d-flex">
					<v-btn variant="text" size="small" @click="selectAll">全选</v-btn>
					<v-btn variant="text" size="small" @click="clearAll">清空</v-btn>
					<v-btn color="primary" size="small" elevation="0" class="px-4" :loading="updating" @click="$emit('update-tables')">
						更新数据表
					</v-btn>
				</div>
			</div>
			<v-divider class="mb-4" />

			<div v-if="loadingTables" class="d-flex justify-center py-8">
				<v-progress-circular indeterminate color="primary" size="32" />
			</div>

			<div v-else-if="fetchError" class="empty-state error-state">
				<v-icon size="48" color="error" class="mb-3">mdi-database-off-outline</v-icon>
				<p class="text-body-2 text-medium-emphasis mb-3">数据获取失败</p>
				<p class="text-caption text-medium-emphasis mb-4">无法拉取数据表列表，请检查连接后重试</p>
				<v-btn color="primary" variant="outlined" size="small" @click="$emit('retry')">重试</v-btn>
			</div>

			<div v-else class="table-grid">
				<v-checkbox
					v-for="tbl in allTables"
					:key="tbl"
					v-model="selectedTables"
					:label="tbl"
					:value="tbl"
					hide-details
					density="compact"
					color="primary"
				/>
			</div>

			<div v-if="!loadingTables && !fetchError && allTables.length === 0" class="empty-state">
				<v-icon size="40" color="grey" class="mb-2">mdi-table-off</v-icon>
				<p class="text-body-2 text-medium-emphasis">暂无表数据，请确保数据源连接正常后刷新</p>
			</div>
		</div>
	</div>
</template>

<script setup lang="ts">
const selectedTables = defineModel<string[]>('selectedTables', { default: () => [] });

const props = defineProps<{
	allTables: string[];
	loadingTables: boolean;
	fetchError: boolean;
	updating: boolean;
}>();

defineEmits<{
	'update-tables': [];
	retry: [];
}>();

function selectAll() {
	selectedTables.value = [...props.allTables];
}

function clearAll() {
	selectedTables.value = [];
}
</script>

<style scoped>
.manage-tables-container {
	background: white;
	border-radius: 12px;
	border: 1px solid #e2e8f0;
	box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.02);
	padding: 24px;
}

.table-grid {
	display: grid;
	grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
	gap: 12px;
}

.empty-state,
.error-state {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	min-height: 160px;
	padding: 24px;
}

.expand-row-content {
	animation: expandSlideDown 0.3s ease-out;
}

@keyframes expandSlideDown {
	from { max-height: 0; opacity: 0; overflow: hidden; }
	to { max-height: 800px; opacity: 1; overflow: visible; }
}
</style>
