<template>
	<section class="page-shell">
		<!-- 顶部标题栏 -->
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
					@click="fetchDatasources"
					class="text-none bg-white"
					style="border-color: #e2e8f0"
				>
					刷新
				</v-btn>
				<v-btn
					color="primary"
					prepend-icon="mdi-plus"
					class="text-none px-6"
					elevation="0"
					@click="openDialog('create')"
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

		<!-- 数据表格 -->
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
				<!-- 自定义展开列：未启用或连接未成功时禁用 -->
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

				<!-- 名称与图标 -->
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

				<!-- 类型 -->
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.type="{ item }">
					<v-chip size="small" class="text-uppercase">{{ item.type }}</v-chip>
				</template>

				<!-- 启用/禁用状态 -->
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

				<!-- 连接状态 -->
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
							></span>
							{{ getStatusText(item.testStatus) }}
						</span>
					</v-chip>
				</template>

				<!-- 操作列 -->
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
							@click="openForeignKeyDialog(item)"
						>
							逻辑外键
						</v-btn>

						<v-btn
							icon="mdi-pencil-outline"
							variant="text"
							size="small"
							color="primary"
							@click="openDialog('edit', item)"
						></v-btn>

						<v-btn
							icon="mdi-delete-outline"
							variant="text"
							size="small"
							color="error"
							@click="handleDelete(item)"
						></v-btn>
					</div>
				</template>

				<!-- 展开行：数据表管理 -->
				<template #expanded-row="{ columns, item }">
					<tr>
						<td :colspan="columns.length" class="bg-grey-lighten-5 pa-0">
							<div class="expand-row-content pa-6">
								<div class="manage-tables-container">
									<div class="d-flex align-center justify-space-between mb-4">
										<div class="d-flex align-center">
											<v-icon size="20" class="mr-2 text-primary"
												>mdi-table-cog</v-icon
											>
											<span class="text-subtitle-1 font-weight-bold"
												>数据表管理</span
											>
											<span class="text-caption text-medium-emphasis ml-4">
												已选择
												{{
													(item.id ? selectedTables[item.id]?.length : 0) ?? 0
												}}
												个表
											</span>
										</div>
										<div class="ga-2 d-flex">
											<v-btn
												variant="text"
												size="small"
												@click="selectAllTables(item)"
												>全选</v-btn
											>
											<v-btn
												variant="text"
												size="small"
												@click="clearAllTables(item)"
												>清空</v-btn
											>
											<v-btn
												color="primary"
												size="small"
												elevation="0"
												class="px-4"
												:loading="updatingTablesId === item.id"
												@click="updateTables(item)"
											>
												更新数据表
											</v-btn>
										</div>
									</div>
									<v-divider class="mb-4"></v-divider>
									<div
										v-if="item.id && loadingTablesId === item.id"
										class="d-flex justify-center py-8"
									>
										<v-progress-circular
											indeterminate
											color="primary"
											size="32"
										></v-progress-circular>
									</div>
									<div
										v-else-if="item.id && tableFetchError[item.id]"
										class="empty-state error-state"
									>
										<v-icon size="48" color="error" class="mb-3"
											>mdi-database-off-outline</v-icon
										>
										<p class="text-body-2 text-medium-emphasis mb-3">
											数据获取失败
										</p>
										<p class="text-caption text-medium-emphasis mb-4">
											无法拉取数据表列表，请检查连接后重试
										</p>
										<v-btn
											color="primary"
											variant="outlined"
											size="small"
											@click="retryFetchTables(item)"
										>
											重试
										</v-btn>
									</div>
									<div v-else class="table-grid">
										<v-checkbox
											v-for="tbl in item.id ? (tableLists[item.id] ?? []) : []"
											:key="tbl"
											v-model="selectedTables[item.id!]"
											:label="tbl"
											:value="tbl"
											hide-details
											density="compact"
											color="primary"
										></v-checkbox>
									</div>
									<div
										v-if="
											!loadingTablesId &&
											!(item.id && tableFetchError[item.id]) &&
											(item.id ? (tableLists[item.id]?.length ?? 0) : 0) === 0
										"
										class="empty-state"
									>
										<v-icon size="40" color="grey" class="mb-2"
											>mdi-table-off</v-icon
										>
										<p class="text-body-2 text-medium-emphasis">
											暂无表数据，请确保数据源连接正常后刷新
										</p>
									</div>
								</div>
							</div>
						</td>
					</tr>
				</template>
			</v-data-table>
		</v-card>

		<!-- 添加/编辑数据源弹窗 -->
		<v-dialog v-model="dialog.visible" max-width="800" persistent>
			<v-card rounded="xl" class="pa-2">
				<v-card-title
					class="d-flex align-center justify-space-between px-4 pt-4"
				>
					<span class="text-h6 font-weight-bold">{{
						dialog.mode === 'create' ? '添加数据源' : '编辑数据源'
					}}</span>
					<v-btn
						icon="mdi-close"
						variant="text"
						size="small"
						@click="closeDialog"
					/>
				</v-card-title>

				<v-card-text class="pt-4">
					<v-form ref="formRef" v-model="formValid">
						<v-row dense>
							<v-col cols="12" md="6">
								<span class="custom-label">数据源名称 *</span>
								<v-text-field
									v-model="form.name"
									placeholder="请输入名称"
									variant="outlined"
									density="compact"
									:rules="[rules.required]"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<span class="custom-label">数据库类型 *</span>
								<v-select
									v-model="form.type"
									:items="[
										'mysql',
										'postgresql',
										'sqlserver',
										'dameng',
										'oracle',
									]"
									variant="outlined"
									density="compact"
									:rules="[rules.required]"
								/>
							</v-col>

							<v-col cols="12" md="8">
								<span class="custom-label">主机地址 *</span>
								<v-text-field
									v-model="form.host"
									placeholder="localhost 或 IP 地址"
									variant="outlined"
									density="compact"
									:rules="[rules.required]"
								/>
							</v-col>
							<v-col cols="12" md="4">
								<span class="custom-label">端口号 *</span>
								<v-text-field
									v-model.number="form.port"
									type="number"
									variant="outlined"
									density="compact"
									:rules="[rules.required]"
								/>
							</v-col>

							<v-col
								cols="12"
								:md="
									['postgresql', 'oracle'].includes(form.type || '') ? 6 : 12
								"
							>
								<span class="custom-label">数据库名 *</span>
								<v-text-field
									v-model="form.databaseName"
									placeholder="Database Name"
									variant="outlined"
									density="compact"
									:rules="[rules.required]"
								/>
							</v-col>
							<v-col
								cols="12"
								md="6"
								v-if="['postgresql', 'oracle'].includes(form.type || '')"
							>
								<span class="custom-label">Schema 名</span>
								<v-text-field
									v-model="form.schemaName"
									placeholder="如 public"
									variant="outlined"
									density="compact"
								/>
							</v-col>

							<v-col cols="12">
								<span class="custom-label">JDBC 连接地址 (可选)</span>
								<v-text-field
									v-model="form.connectionUrl"
									placeholder="若不填则自动生成"
									variant="outlined"
									density="compact"
								/>
							</v-col>

							<v-col cols="12" md="6">
								<span class="custom-label">用户名 *</span>
								<v-text-field
									v-model="form.username"
									placeholder="Username"
									variant="outlined"
									density="compact"
									:rules="[rules.required]"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<span class="custom-label">密码 *</span>
								<v-text-field
									v-model="form.password"
									type="password"
									placeholder="Password"
									variant="outlined"
									density="compact"
									:rules="[rules.required]"
								/>
							</v-col>

							<v-col cols="12">
								<span class="custom-label">描述信息</span>
								<v-textarea
									v-model="form.description"
									rows="2"
									placeholder="可选描述"
									variant="outlined"
									density="compact"
								/>
							</v-col>
						</v-row>
					</v-form>
					<div class="d-flex justify-end mt-4 ga-3">
						<v-btn variant="text" class="text-none" @click="closeDialog"
							>取消</v-btn
						>
						<v-btn
							color="primary"
							class="text-none px-8"
							elevation="0"
							@click="handleSubmit"
							:loading="saving"
						>
							{{ dialog.mode === 'create' ? '创建' : '保存' }}
						</v-btn>
					</div>
				</v-card-text>
			</v-card>
		</v-dialog>

		<!-- 逻辑外键弹窗 -->
		<v-dialog v-model="fkDialog.visible" max-width="900" persistent>
			<v-card rounded="xl" class="pa-4">
				<v-card-title
					class="d-flex align-center justify-space-between border-b pb-4"
				>
					<div class="d-flex align-center">
						<v-icon color="primary" class="mr-3"
							>mdi-relation-one-to-many</v-icon
						>
						<span class="font-weight-bold"
							>逻辑外键配置 - {{ fkDialog.datasourceName }}</span
						>
					</div>
					<v-btn
						icon="mdi-close"
						variant="text"
						size="small"
						@click="fkDialog.visible = false"
					></v-btn>
				</v-card-title>

				<v-card-text class="pa-6">
					<div class="mb-8">
						<div class="text-overline text-grey-darken-1 mb-2">
							已生效的关系列表
						</div>
						<v-table
							density="comfortable"
							class="border rounded-lg overflow-hidden"
						>
							<thead class="bg-grey-lighten-4">
								<tr>
									<th class="text-left">主表 (Source)</th>
									<th class="text-center">关系</th>
									<th class="text-left">关联表 (Target)</th>
									<th class="text-right">操作</th>
								</tr>
							</thead>
							<tbody>
								<tr v-for="(fk, index) in fkList" :key="index">
									<td>
										<div class="font-weight-bold text-blue-darken-2">
											{{ fk.sourceTableName }}
										</div>
										<div class="text-caption text-grey">
											{{ fk.sourceColumnName }}
										</div>
									</td>
									<td class="text-center">
										<v-icon size="16" color="grey">mdi-link-variant</v-icon>
										<div
											class="text-caption font-weight-bold text-grey-darken-2"
										>
											{{ fk.relationType }}
										</div>
									</td>
									<td>
										<div class="font-weight-bold text-green-darken-2">
											{{ fk.targetTableName }}
										</div>
										<div class="text-caption text-grey">
											{{ fk.targetColumnName }}
										</div>
									</td>
									<td class="text-right">
										<v-btn
											icon="mdi-trash-can-outline"
											variant="text"
											size="small"
											color="error"
											@click="handleDeleteRelation(fk.id!)"
											:loading="deletingRelationId === fk.id"
										></v-btn>
									</td>
								</tr>
								<tr v-if="fkList.length === 0">
									<td colspan="4" class="text-center text-grey py-4">
										暂无逻辑外键配置
									</td>
								</tr>
							</tbody>
						</v-table>
					</div>

					<!-- 新增关系表单 -->
					<div class="bg-blue-grey-lighten-5 pa-5 rounded-lg border">
						<v-row dense>
							<v-col cols="12" md="5">
								<v-select
									label="主表"
									v-model="fkForm.sourceTableName"
									:items="tableList"
									variant="outlined"
									density="compact"
									placeholder="请选择主表"
									clearable
									@update:model-value="fetchColumns($event ?? '', 'source')"
								></v-select>
								<v-select
									label="主表字段"
									v-model="fkForm.sourceColumnName"
									:items="sourceColumns"
									variant="outlined"
									density="compact"
									:disabled="!fkForm.sourceTableName"
									:loading="loadingSourceColumns"
									placeholder="先选择主表"
									clearable
								></v-select>
							</v-col>
							<v-col
								cols="12"
								md="2"
								class="d-flex align-center justify-center"
							>
								<v-icon color="grey-lighten-1" size="32"
									>mdi-arrow-right-bold</v-icon
								>
							</v-col>
							<v-col cols="12" md="5">
								<v-select
									label="关联表"
									v-model="fkForm.targetTableName"
									:items="tableList"
									variant="outlined"
									density="compact"
									placeholder="请选择关联表"
									clearable
									@update:model-value="fetchColumns($event ?? '', 'target')"
								></v-select>
								<v-select
									label="关联字段"
									v-model="fkForm.targetColumnName"
									:items="targetColumns"
									variant="outlined"
									density="compact"
									:disabled="!fkForm.targetTableName"
									:loading="loadingTargetColumns"
									placeholder="先选择关联表"
									clearable
								></v-select>
							</v-col>
							<v-col cols="12" class="d-flex ga-2 mt-2">
								<v-select
									label="关系"
									v-model="fkForm.relationType"
									:items="['1:1', '1:N', 'N:1']"
									variant="outlined"
									density="compact"
									class="flex-grow-1"
								></v-select>
								<v-btn
									color="primary"
									height="40"
									elevation="0"
									class="px-8"
									@click="handleAddRelation"
									:loading="addingRelation"
									:disabled="!isRelationFormValid"
									>添加关系</v-btn
								>
							</v-col>
						</v-row>
					</div>
				</v-card-text>
			</v-card>
		</v-dialog>
	</section>
</template>

<script setup lang="ts">
import type { VForm } from 'vuetify/components';
import datasourceService, {
	type Datasource,
	type LogicalRelation,
} from '@/services/datasource';
import { useTipStore } from '~/stores/tips';
import agentDatasourceService from '@/services/agentDatasource';

const route = useRoute();
const tipStore = useTipStore();
const loading = ref(false);
const saving = ref(false);
const testingId = ref<number | null>(null);
const togglingStatusId = ref<number | null>(null);
const datasourceList = ref<Datasource[]>([]);
const expandedRows = ref<readonly string[]>([]);
const tableLists = ref<Record<number, string[]>>({});
const selectedTables = ref<Record<number, string[]>>({});
const loadingTablesId = ref<number | null>(null);
/** 记录获取表失败的数据源 ID */
const tableFetchError = ref<Record<number, boolean>>({});
const updatingTablesId = ref<number | null>(null);
const initStatus = ref(false);
/** 从路由参数获取 agentId */
const agentId = computed(() => {
	const id = route.params.agentId || route.query.agentId;
	return id ? String(id) : null;
});

const dialog = reactive({
	visible: false,
	mode: 'create' as 'create' | 'edit',
});

const formRef = ref<VForm | null>(null);
const formValid = ref(false);
const form = reactive<Datasource>({
	name: '',
	type: 'mysql',
	host: '',
	port: 3306,
	databaseName: '',
	schemaName: '',
	username: '',
	password: '',
	description: '',
	connectionUrl: '',
});

// 逻辑外键相关状态
const fkDialog = reactive({
	visible: false,
	datasourceId: 0,
	datasourceName: '',
});
const fkList = ref<LogicalRelation[]>([]);
const tableList = ref<string[]>([]);
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

const headers = [
	{ title: '名称', key: 'name', align: 'start' as const },
	{ title: '类型', key: 'type', align: 'center' as const },
	{ title: '状态', key: 'status', align: 'center' as const },
	{ title: '连接状态', key: 'testStatus', align: 'center' as const },
	{ title: '操作', key: 'actions', align: 'end' as const, sortable: false },
];

const rules = {
	required: (v: unknown) => !!v || '此项必填',
};

const isRelationFormValid = computed(() => {
	return (
		fkForm.sourceTableName &&
		fkForm.sourceColumnName &&
		fkForm.targetTableName &&
		fkForm.targetColumnName &&
		fkForm.relationType
	);
});

const getDbIcon = (type: string | undefined) => {
	if (type === 'mysql') return 'mdi-database';
	if (type === 'postgresql') return 'mdi-elephant';
	if (type === 'oracle') return 'mdi-alpha-o-circle';
	return 'mdi-database-outline';
};

const getStatusText = (status: string | undefined) => {
	if (status === 'success') return '连接成功';
	if (status === 'fail') return '连接失败';
	return '未测试';
};

const showTip = (text: string, color: 'success' | 'error' = 'success') => {
	tipStore.show(text, { color: color === 'error' ? 'error' : 'success' });
};

const fetchDatasources = async () => {
	loading.value = true;
	try {
		datasourceList.value = await datasourceService.getAllDatasource();
	} catch {
		showTip('获取数据源列表失败', 'error');
	} finally {
		loading.value = false;
	}
};

const openDialog = (mode: 'create' | 'edit', item?: Datasource) => {
	dialog.mode = mode;
	dialog.visible = true;
	if (mode === 'edit' && item) {
		Object.assign(form, item);
	} else {
		// Reset form
		form.id = undefined;
		form.name = '';
		form.type = 'mysql';
		form.host = '';
		form.port = 3306;
		form.databaseName = '';
		form.schemaName = '';
		form.username = '';
		form.password = '';
		form.description = '';
		form.connectionUrl = '';
	}
};

const closeDialog = () => {
	dialog.visible = false;
};

const handleSubmit = async () => {
	const { valid } = (await formRef.value?.validate()) || { valid: false };
	if (!valid) return;

	saving.value = true;
	try {
		if (dialog.mode === 'create') {
			await datasourceService.createDatasource(form);
			showTip('创建成功');
		} else if (form.id) {
			await datasourceService.updateDatasource(form.id, form);
			showTip('更新成功');
		}
		closeDialog();
		fetchDatasources();
	} catch {
		showTip('操作失败，请检查网络或参数', 'error');
	} finally {
		saving.value = false;
	}
};

const handleDelete = (item: Datasource) => {
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
					showTip('删除成功');
					fetchDatasources();
				} else {
					showTip(res.message || '删除失败', 'error');
				}
			} catch {
				showTip('删除失败', 'error');
			}
		},
	});
};

const handleToggleStatus = async (item: Datasource) => {
	if (!item.id) return;
	togglingStatusId.value = item.id;
	const newStatus = item.status === 'active' ? 'inactive' : 'active';
	try {
		await datasourceService.updateDatasource(item.id, {
			...item,
			status: newStatus,
		});
		item.status = newStatus;
		showTip(newStatus === 'active' ? '已启用' : '已禁用');
	} catch {
		showTip('操作失败', 'error');
	} finally {
		togglingStatusId.value = null;
	}
};

const handleTestConnection = async (item: Datasource) => {
	if (!item.id) return;
	testingId.value = item.id;
	try {
		const res = await datasourceService.testConnection(item.id);
		if (res.success) {
			showTip('连接测试成功');
			item.testStatus = 'success';
		} else {
			showTip('连接测试失败', 'error');
			item.testStatus = 'fail';
		}
	} catch {
		showTip('连接测试请求失败', 'error');
		item.testStatus = 'fail';
	} finally {
		testingId.value = null;
	}
};

// 逻辑外键相关方法
const openForeignKeyDialog = async (item: Datasource) => {
	if (!item.id) return;
	fkDialog.datasourceId = item.id;
	fkDialog.datasourceName = item.name || '';
	fkDialog.visible = true;

	// 重置表单
	fkForm.sourceTableName = '';
	fkForm.sourceColumnName = '';
	fkForm.targetTableName = '';
	fkForm.targetColumnName = '';
	sourceColumns.value = [];
	targetColumns.value = [];

	// 加载数据
	try {
		const [relationsRes, tables] = await Promise.all([
			datasourceService.getLogicalRelations(item.id),
			datasourceService.getDatasourceTables(item.id),
		]);

		if (relationsRes.success) {
			fkList.value = relationsRes.data || [];
		}
		tableList.value = tables || [];
	} catch {
		showTip('加载外键配置失败', 'error');
	}
};

const fetchColumns = async (tableName: string, type: 'source' | 'target') => {
	if (type === 'source') {
		fkForm.sourceColumnName = '';
		sourceColumns.value = [];
	} else {
		fkForm.targetColumnName = '';
		targetColumns.value = [];
	}
	if (!fkDialog.datasourceId || !tableName) return;

	if (type === 'source') loadingSourceColumns.value = true;
	else loadingTargetColumns.value = true;
	try {
		const columns = await datasourceService.getTableColumns(
			fkDialog.datasourceId,
			tableName,
		);
		if (type === 'source') {
			sourceColumns.value = columns;
		} else {
			targetColumns.value = columns;
		}
	} catch {
		if (type === 'source') sourceColumns.value = [];
		else targetColumns.value = [];
	} finally {
		if (type === 'source') loadingSourceColumns.value = false;
		else loadingTargetColumns.value = false;
	}
};

const handleAddRelation = async () => {
	if (!fkDialog.datasourceId) return;
	addingRelation.value = true;
	try {
		const res = await datasourceService.addLogicalRelation(
			fkDialog.datasourceId,
			{
				...fkForm,
				description: '',
			},
		);
		if (res.success && res.data) {
			fkList.value.push(res.data);
			showTip('添加关系成功');
			// 重置表单
			fkForm.sourceTableName = '';
			fkForm.sourceColumnName = '';
			fkForm.targetTableName = '';
			fkForm.targetColumnName = '';
		} else {
			showTip(res.message || '添加失败', 'error');
		}
	} catch {
		showTip('添加失败', 'error');
	} finally {
		addingRelation.value = false;
	}
};

const handleDeleteRelation = (relationId: number) => {
	if (!fkDialog.datasourceId) return;
	showConfirm({
		title: '删除确认',
		message: '确定要删除该逻辑外键吗？',
		confirmText: '删除',
		icon: 'mdi-alert-circle',
		onConfirm: async () => {
			deletingRelationId.value = relationId;
			try {
				const res = await datasourceService.deleteLogicalRelation(
					fkDialog.datasourceId,
					relationId,
				);
				if (res.success) {
					fkList.value = fkList.value.filter((item) => item.id !== relationId);
					showTip('删除成功');
				} else {
					showTip(res.message || '删除失败', 'error');
				}
			} catch {
				showTip('删除失败', 'error');
			} finally {
				deletingRelationId.value = null;
			}
		},
	});
};

// 将展开行解析为数据源对象（v-data-table 的 expanded 可能为 id 或完整对象）
const getDatasourceFromRow = (row: unknown): Datasource | null => {
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
	if (id != null && !Number.isNaN(id)) {
		return datasourceList.value.find((ds) => ds.id === id) ?? null;
	}
	return null;
};

// 过滤：仅允许连接成功且已启用的行展开，否则移除并提示
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
			const validList = list.filter((row) => {
				const ds = getDatasourceFromRow(row);
				return (
					ds && ds.status === 'active' && (ds.testStatus ?? '') === 'success'
				);
			});
			expandedRows.value = validList;
			const hasInactive = invalidRows.some((row) => {
				const ds = getDatasourceFromRow(row);
				return ds && ds.status !== 'active';
			});
			const hasConnFail = invalidRows.some((row) => {
				const ds = getDatasourceFromRow(row);
				return ds && (ds.testStatus ?? '') !== 'success';
			});
			if (hasInactive && hasConnFail)
				showTip('请先启用数据源并测试连接成功后再展开', 'error');
			else if (hasInactive)
				showTip('请先启用数据源后再展开数据表管理', 'error');
			else showTip('请先测试连接成功后再展开数据表管理', 'error');
		}
	},
	{ immediate: false },
);

// 展开行时拉取该数据源的表列表
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

const fetchTablesForDatasource = async (datasourceId: number) => {
	loadingTablesId.value = datasourceId;
	tableFetchError.value = { ...tableFetchError.value, [datasourceId]: false };
	try {
		const tables = await datasourceService.getDatasourceTables(datasourceId);
		tableLists.value[datasourceId] = tables ?? [];
		if (!selectedTables.value[datasourceId]) {
			selectedTables.value[datasourceId] = [];
		}
	} catch {
		tableLists.value[datasourceId] = [];
		selectedTables.value[datasourceId] = [];
		tableFetchError.value = { ...tableFetchError.value, [datasourceId]: true };
	} finally {
		loadingTablesId.value = null;
	}
};

const retryFetchTables = (item: Datasource) => {
	if (!item.id) return;
	fetchTablesForDatasource(item.id);
};

const selectAllTables = (item: Datasource) => {
	if (!item.id) return;
	const tables = tableLists.value[item.id] ?? [];
	selectedTables.value[item.id] = [...tables];
};

const clearAllTables = (item: Datasource) => {
	if (item.id) selectedTables.value[item.id] = [];
};

const updateTables = async (item: Datasource) => {
	if (!item.id) return;
	updatingTablesId.value = item.id;
	try {
		// 全局数据源暂无后端接口保存表选择，此处仅做本地状态持久化提示
		showTip(
			`已选择 ${selectedTables.value[item.id]?.length ?? 0} 个表（表选择在智能体关联数据源时可配置）`,
		);
	} finally {
		updatingTablesId.value = null;
	}
};

const handleInitDatasource = async () => {
	if (!agentId.value) {
		showTip('缺少智能体ID，无法初始化数据源', 'error');
		return;
	}
	initStatus.value = true;
	try {
		// 先检查是否有启用的数据源
		const activeRes = await agentDatasourceService.getActiveAgentDatasource(
			agentId.value,
		);
		if (!activeRes.success || !activeRes.data) {
			showTip('当前智能体没有启用的数据源！请先添加并启用数据源', 'error');
			return;
		}
		const activeDatasource = activeRes.data;
		if (
			!activeDatasource.selectTables ||
			activeDatasource.selectTables.length === 0
		) {
			showTip(
				'当前启用的数据源没有选择相应的数据表！请先选择数据表并更新',
				'error',
			);
			return;
		}

		// 执行初始化
		const res = await agentDatasourceService.initSchema(agentId.value);
		if (res.success) {
			showTip('初始化数据源成功');
		} else {
			showTip(res.message || '初始化数据源失败', 'error');
		}
	} catch (error: unknown) {
		const errMsg = error instanceof Error ? error.message : '初始化数据源失败';
		showTip(errMsg, 'error');
	} finally {
		initStatus.value = false;
	}
};

onMounted(() => {
	fetchDatasources();
});
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

.expand-disabled {
	opacity: 0.4;
	cursor: not-allowed;
}

/* 展开行动画：从顶部滑入 */
.expand-row-content {
	animation: expandSlideDown 0.3s ease-out;
}

@keyframes expandSlideDown {
	from {
		max-height: 0;
		opacity: 0;
		overflow: hidden;
	}
	to {
		max-height: 800px;
		opacity: 1;
		overflow: visible;
	}
}
</style>
