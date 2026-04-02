<template>
	<v-dialog :model-value="modelValue" max-width="900" persistent @update:model-value="$emit('update:modelValue', $event)">
		<v-card rounded="xl" class="pa-4">
			<v-card-title class="d-flex align-center justify-space-between border-b pb-4">
				<div class="d-flex align-center">
					<v-icon color="primary" class="mr-3">mdi-relation-one-to-many</v-icon>
					<span class="font-weight-bold">逻辑外键配置 - {{ datasourceName }}</span>
				</div>
				<v-btn icon="mdi-close" variant="text" size="small" @click="$emit('update:modelValue', false)" />
			</v-card-title>

			<v-card-text class="pa-6">
				<div class="mb-8">
					<div class="text-overline text-grey-darken-1 mb-2">已生效的关系列表</div>
					<v-table density="comfortable" class="border rounded-lg overflow-hidden">
						<thead class="bg-grey-lighten-4">
							<tr>
								<th class="text-left">主表 (Source)</th>
								<th class="text-center">关系</th>
								<th class="text-left">关联表 (Target)</th>
								<th class="text-right">操作</th>
							</tr>
						</thead>
						<tbody>
							<tr v-for="(fk, index) in relations" :key="index">
								<td>
									<div class="font-weight-bold text-blue-darken-2">{{ fk.sourceTableName }}</div>
									<div class="text-caption text-grey">{{ fk.sourceColumnName }}</div>
								</td>
								<td class="text-center">
									<v-icon size="16" color="grey">mdi-link-variant</v-icon>
									<div class="text-caption font-weight-bold text-grey-darken-2">{{ fk.relationType }}</div>
								</td>
								<td>
									<div class="font-weight-bold text-green-darken-2">{{ fk.targetTableName }}</div>
									<div class="text-caption text-grey">{{ fk.targetColumnName }}</div>
								</td>
								<td class="text-right">
									<v-btn icon="mdi-trash-can-outline" variant="text" size="small" color="error" :loading="deletingRelationId === fk.id" @click="handleDelete(fk.id!)" />
								</td>
							</tr>
							<tr v-if="relations.length === 0">
								<td colspan="4" class="text-center text-grey py-4">暂无逻辑外键配置</td>
							</tr>
						</tbody>
					</v-table>
				</div>

				<div class="bg-blue-grey-lighten-5 pa-5 rounded-lg border">
					<v-row dense>
						<v-col cols="12" md="5">
							<v-select label="主表" v-model="fkForm.sourceTableName" :items="tables" variant="outlined" density="compact" placeholder="请选择主表" clearable @update:model-value="fetchColumns($event ?? '', 'source')" />
							<v-select label="主表字段" v-model="fkForm.sourceColumnName" :items="sourceColumns" variant="outlined" density="compact" :disabled="!fkForm.sourceTableName" :loading="loadingSourceColumns" placeholder="先选择主表" clearable />
						</v-col>
						<v-col cols="12" md="2" class="d-flex align-center justify-center">
							<v-icon color="grey-lighten-1" size="32">mdi-arrow-right-bold</v-icon>
						</v-col>
						<v-col cols="12" md="5">
							<v-select label="关联表" v-model="fkForm.targetTableName" :items="tables" variant="outlined" density="compact" placeholder="请选择关联表" clearable @update:model-value="fetchColumns($event ?? '', 'target')" />
							<v-select label="关联字段" v-model="fkForm.targetColumnName" :items="targetColumns" variant="outlined" density="compact" :disabled="!fkForm.targetTableName" :loading="loadingTargetColumns" placeholder="先选择关联表" clearable />
						</v-col>
						<v-col cols="12" class="d-flex ga-2 mt-2">
							<v-select label="关系" v-model="fkForm.relationType" :items="['1:1', '1:N', 'N:1']" variant="outlined" density="compact" class="flex-grow-1" />
							<v-btn color="primary" height="40" elevation="0" class="px-8" :loading="addingRelation" :disabled="!isFormValid" @click="handleAdd">添加关系</v-btn>
						</v-col>
					</v-row>
				</div>
			</v-card-text>
		</v-card>
	</v-dialog>
</template>

<script setup lang="ts">
import datasourceService from '@/services/datasource';
import type { LogicalRelation } from '@/services/datasource';

const props = defineProps<{
	modelValue: boolean;
	datasourceId: number;
	datasourceName: string;
}>();

defineEmits<{
	'update:modelValue': [value: boolean];
}>();

const relations = ref<LogicalRelation[]>([]);
const tables = ref<string[]>([]);
const sourceColumns = ref<string[]>([]);
const targetColumns = ref<string[]>([]);
const loadingSourceColumns = ref(false);
const loadingTargetColumns = ref(false);
const addingRelation = ref(false);
const deletingRelationId = ref<number | null>(null);

const fkForm = reactive({
	sourceTableName: '',
	sourceColumnName: '',
	targetTableName: '',
	targetColumnName: '',
	relationType: '1:N',
});

const isFormValid = computed(() =>
	fkForm.sourceTableName && fkForm.sourceColumnName &&
	fkForm.targetTableName && fkForm.targetColumnName && fkForm.relationType,
);

const { $tip } = useNuxtApp();

watch(() => props.modelValue, async (visible) => {
	if (!visible || !props.datasourceId) return;
	resetForm();
	try {
		const [relationsRes, tableData] = await Promise.all([
			datasourceService.getLogicalRelations(props.datasourceId),
			datasourceService.getDatasourceTables(props.datasourceId),
		]);
		if (relationsRes.success) relations.value = relationsRes.data || [];
		tables.value = tableData || [];
	} catch {
		$tip('加载外键配置失败', { color: 'error' });
	}
});

function resetForm() {
	fkForm.sourceTableName = '';
	fkForm.sourceColumnName = '';
	fkForm.targetTableName = '';
	fkForm.targetColumnName = '';
	sourceColumns.value = [];
	targetColumns.value = [];
}

async function fetchColumns(tableName: string, type: 'source' | 'target') {
	if (type === 'source') { fkForm.sourceColumnName = ''; sourceColumns.value = []; }
	else { fkForm.targetColumnName = ''; targetColumns.value = []; }
	if (!props.datasourceId || !tableName) return;

	if (type === 'source') loadingSourceColumns.value = true;
	else loadingTargetColumns.value = true;
	try {
		const columns = await datasourceService.getTableColumns(props.datasourceId, tableName);
		if (type === 'source') sourceColumns.value = columns;
		else targetColumns.value = columns;
	} catch {
		if (type === 'source') sourceColumns.value = [];
		else targetColumns.value = [];
	} finally {
		if (type === 'source') loadingSourceColumns.value = false;
		else loadingTargetColumns.value = false;
	}
}

async function handleAdd() {
	if (!props.datasourceId) return;
	addingRelation.value = true;
	try {
		const res = await datasourceService.addLogicalRelation(props.datasourceId, { ...fkForm, description: '' });
		if (res.success && res.data) {
			relations.value.push(res.data);
			$tip('添加关系成功');
			resetForm();
		} else {
			$tip(res.message || '添加失败', { color: 'error' });
		}
	} catch {
		$tip('添加失败', { color: 'error' });
	} finally {
		addingRelation.value = false;
	}
}

function handleDelete(relationId: number) {
	const { showConfirm } = useConfirm();
	showConfirm({
		title: '删除确认',
		message: '确定要删除该逻辑外键吗？',
		confirmText: '删除',
		icon: 'mdi-alert-circle',
		onConfirm: async () => {
			deletingRelationId.value = relationId;
			try {
				const res = await datasourceService.deleteLogicalRelation(props.datasourceId, relationId);
				if (res.success) {
					relations.value = relations.value.filter(item => item.id !== relationId);
					$tip('删除成功');
				} else {
					$tip(res.message || '删除失败', { color: 'error' });
				}
			} catch {
				$tip('删除失败', { color: 'error' });
			} finally {
				deletingRelationId.value = null;
			}
		},
	});
}
</script>
