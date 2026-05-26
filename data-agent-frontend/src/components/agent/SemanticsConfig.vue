<!--
 * Copyright 2024-2026 the original author or authors.
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
-->

<template>
  <div class="semantic-workspace">
    <section class="hero-card">
      <div>
        <p class="hero-kicker">Structured Semantic Layer</p>
        <h2 class="hero-title">语义层配置工作台</h2>
        <p class="hero-desc">
          统一维护表、列、关系三类结构化语义，不再依赖旧的 semantic RAG / logical relation 兼容链。
        </p>
      </div>
      <div class="hero-meta">
        <span>当前数据源</span>
        <strong>{{ currentDatasource?.name || '未选择' }}</strong>
      </div>
    </section>

    <section class="toolbar-card">
      <div class="toolbar-grid">
        <el-select
          v-model="selectedDatasourceId"
          class="toolbar-field"
          placeholder="选择已绑定数据源"
          filterable
        >
          <el-option
            v-for="item in datasourceOptions"
            :key="item.id"
            :label="`${item.name} (${item.type || 'unknown'})`"
            :value="item.id"
          />
        </el-select>
        <el-input
          v-model="keyword"
          class="toolbar-field"
          placeholder="按表名、字段名、业务名或描述搜索"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-select
          v-if="activeTab === 'tables' && tableViewMode === 'columns'"
          v-model="selectedTableName"
          class="toolbar-field"
          placeholder="选择数据表"
          filterable
          clearable
        >
          <el-option v-for="table in physicalTables" :key="table" :label="table" :value="table" />
        </el-select>
        <el-select
          v-else-if="activeTab === 'relations'"
          v-model="relationFilterTable"
          class="toolbar-field"
          placeholder="按数据表筛选关系（可选）"
          filterable
          clearable
        >
          <el-option v-for="table in physicalTables" :key="table" :label="table" :value="table" />
        </el-select>
        <div v-else class="toolbar-placeholder"></div>
        <div class="toolbar-actions">
          <el-button @click="handleSearch">查询</el-button>
          <el-button type="primary" @click="handleOpenCreateDialog">{{ primaryActionLabel }}</el-button>
        </div>
      </div>
      <div v-if="datasourceOptions.length === 0" class="empty-tip">
        当前智能体还没有绑定数据源，请先到“数据源配置”中完成绑定。
      </div>
    </section>

    <section class="content-card">
      <el-tabs v-model="activeTab" class="semantic-tabs">
        <el-tab-pane label="表语义" name="tables">
          <template v-if="tableViewMode === 'list'">
            <div class="section-header">
              <div>
                <h3>表语义列表</h3>
                <p>维护表级业务名、同义词、描述和可见性。</p>
              </div>
              <el-tag type="primary" effect="plain">共 {{ tablePage.total }} 条</el-tag>
            </div>

            <el-table v-loading="tableLoading" :data="tableRows" class="semantic-table">
              <el-table-column prop="tableName" label="表名" min-width="180" />
              <el-table-column prop="businessName" label="业务名" min-width="180">
                <template #default="{ row }">
                  {{ row.businessName || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="synonyms" label="同义词" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.synonyms || '-' }}
                </template>
              </el-table-column>
              <el-table-column
                prop="businessDescription"
                label="业务描述"
                min-width="240"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.businessDescription || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="可见性" width="100">
                <template #default="{ row }">
                  <el-tag :type="flagTagType(row.isVisible)">
                    {{ flagLabel(row.isVisible) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="flagTagType(row.status)">
                    {{ statusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="更新时间" width="180">
                <template #default="{ row }">
                  {{ formatTime(row.updateTime || row.createdTime) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="240" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="viewTableColumns(row.tableName)">列语义</el-button>
                  <el-button link type="primary" @click="openTableDialog(row)">编辑</el-button>
                  <el-button link type="danger" @click="handleDeleteTable(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="pagination-wrap">
              <el-pagination
                background
                layout="total, sizes, prev, pager, next"
                :current-page="tablePage.pageNum"
                :page-size="tablePage.pageSize"
                :page-sizes="[10, 20, 50]"
                :total="tablePage.total"
                @current-change="handleTablePageChange"
                @size-change="handleTableSizeChange"
              />
            </div>
          </template>

          <template v-else>
            <div class="section-header column-subpage-header">
              <div class="column-subpage-title">
                <el-button link type="primary" @click="backToTableList">返回表语义</el-button>
                <div>
                  <h3>列语义列表</h3>
                  <p>为字段补充业务口径、描述、同义词和是否启用。</p>
                </div>
              </div>
              <el-tag type="primary" effect="plain">
                {{ selectedTableName ? `${selectedTableName} · ${columnPage.total} 条` : '请先选择表' }}
              </el-tag>
            </div>

            <div v-if="!selectedTableName" class="empty-panel">
              请先选择一个数据表，再查看或编辑该表的列语义。
            </div>
            <template v-else>
            <el-table v-loading="columnLoading" :data="columnRows" class="semantic-table">
              <el-table-column prop="columnName" label="字段名" min-width="180" />
              <el-table-column prop="businessName" label="业务名" min-width="180">
                <template #default="{ row }">
                  {{ row.businessName || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="dataType" label="数据类型" min-width="140">
                <template #default="{ row }">
                  {{ row.dataType || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="synonyms" label="同义词" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.synonyms || '-' }}
                </template>
              </el-table-column>
              <el-table-column
                prop="businessDescription"
                label="业务描述"
                min-width="240"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.businessDescription || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="可见性" width="100">
                <template #default="{ row }">
                  <el-tag :type="flagTagType(row.status)">
                    {{ statusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="更新时间" width="180">
                <template #default="{ row }">
                  {{ formatTime(row.updateTime || row.createdTime) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openColumnDialog(row)">编辑</el-button>
                  <el-button link type="danger" @click="handleDeleteColumn(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="pagination-wrap">
              <el-pagination
                background
                layout="total, sizes, prev, pager, next"
                :current-page="columnPage.pageNum"
                :page-size="columnPage.pageSize"
                :page-sizes="[10, 20, 50]"
                :total="columnPage.total"
                @current-change="handleColumnPageChange"
                @size-change="handleColumnSizeChange"
              />
            </div>
            </template>
          </template>
        </el-tab-pane>

        <el-tab-pane label="关系语义" name="relations">
          <div class="section-header">
            <div>
              <h3>关系语义列表</h3>
              <p>显式维护跨表关联，完全替代旧 logical relation 配置入口。</p>
            </div>
            <el-tag type="primary" effect="plain">共 {{ relationPage.total }} 条</el-tag>
          </div>

          <el-table v-loading="relationLoading" :data="relationRows" class="semantic-table">
            <el-table-column prop="sourceTableName" label="源表" min-width="160" />
            <el-table-column prop="sourceColumnNames" label="源字段" min-width="180" show-overflow-tooltip />
            <el-table-column prop="targetTableName" label="目标表" min-width="160" />
            <el-table-column prop="targetColumnNames" label="目标字段" min-width="180" show-overflow-tooltip />
            <el-table-column prop="relationType" label="关系类型" min-width="120">
              <template #default="{ row }">
                {{ row.relationType || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.description || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="flagTagType(row.status)">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="180">
              <template #default="{ row }">
                {{ formatTime(row.updateTime || row.createdTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRelationDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="handleDeleteRelation(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrap">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :current-page="relationPage.pageNum"
              :page-size="relationPage.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="relationPage.total"
              @current-change="handleRelationPageChange"
              @size-change="handleRelationSizeChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="tableDialogVisible" :title="editingTableId ? '编辑表语义' : '新增表语义'" width="720px">
      <el-form ref="tableFormRef" :model="tableForm" :rules="tableRules" label-width="110px">
        <el-form-item label="数据表" prop="tableName">
          <el-select
            v-model="tableForm.tableName"
            :disabled="Boolean(editingTableId)"
            filterable
            placeholder="选择物理表"
            style="width: 100%"
          >
            <el-option v-for="table in physicalTables" :key="table" :label="table" :value="table" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务名" prop="businessName">
          <el-input v-model="tableForm.businessName" placeholder="例如：订单主表" />
        </el-form-item>
        <el-form-item label="同义词" prop="synonyms">
          <el-input v-model="tableForm.synonyms" placeholder="多个同义词用逗号分隔" />
        </el-form-item>
        <el-form-item label="业务描述" prop="businessDescription">
          <el-input
            v-model="tableForm.businessDescription"
            type="textarea"
            :rows="4"
            placeholder="描述表的业务含义、口径和适用场景"
          />
        </el-form-item>
        <el-form-item label="表注释" prop="tableComment">
          <el-input
            v-model="tableForm.tableComment"
            type="textarea"
            :rows="3"
            placeholder="可补充物理表注释或额外说明"
          />
        </el-form-item>
        <el-form-item label="是否可见">
          <el-switch v-model="tableFormVisible" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="tableFormEnabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tableDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="tableSubmitting" @click="submitTableDialog">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="columnDialogVisible" :title="editingColumnId ? '编辑列语义' : '新增列语义'" width="760px">
      <el-form ref="columnFormRef" :model="columnForm" :rules="columnRules" label-width="110px">
        <el-form-item label="数据表" prop="tableName">
          <el-select
            v-model="columnForm.tableName"
            :disabled="Boolean(editingColumnId)"
            filterable
            placeholder="选择数据表"
            style="width: 100%"
            @change="handleColumnFormTableChange"
          >
            <el-option v-for="table in physicalTables" :key="table" :label="table" :value="table" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段名" prop="columnName">
          <el-select
            v-model="columnForm.columnName"
            :disabled="Boolean(editingColumnId)"
            filterable
            placeholder="选择字段"
            style="width: 100%"
          >
            <el-option
              v-for="column in columnFormTableColumns"
              :key="column"
              :label="column"
              :value="column"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="业务名" prop="businessName">
          <el-input v-model="columnForm.businessName" placeholder="例如：订单状态" />
        </el-form-item>
        <el-form-item label="同义词" prop="synonyms">
          <el-input v-model="columnForm.synonyms" placeholder="多个同义词用逗号分隔" />
        </el-form-item>
        <el-form-item label="数据类型" prop="dataType">
          <el-input v-model="columnForm.dataType" placeholder="例如：varchar(32)" />
        </el-form-item>
        <el-form-item label="业务描述" prop="businessDescription">
          <el-input
            v-model="columnForm.businessDescription"
            type="textarea"
            :rows="4"
            placeholder="说明字段口径、枚举语义或使用限制"
          />
        </el-form-item>
        <el-form-item label="字段注释" prop="columnComment">
          <el-input
            v-model="columnForm.columnComment"
            type="textarea"
            :rows="3"
            placeholder="补充物理字段注释"
          />
        </el-form-item>
        <el-form-item label="是否可见">
          <el-switch v-model="columnFormVisible" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="columnFormEnabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="columnDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="columnSubmitting" @click="submitColumnDialog">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="relationDialogVisible"
      :title="editingRelationId ? '编辑关系语义' : '新增关系语义'"
      width="760px"
    >
      <el-form ref="relationFormRef" :model="relationForm" :rules="relationRules" label-width="110px">
        <el-form-item label="源表" prop="sourceTableName">
          <el-select
            v-model="relationForm.sourceTableName"
            :disabled="Boolean(editingRelationId)"
            filterable
            placeholder="选择源表"
            style="width: 100%"
            @change="handleRelationSourceTableChange"
          >
            <el-option v-for="table in physicalTables" :key="table" :label="table" :value="table" />
          </el-select>
        </el-form-item>
        <el-form-item label="源字段" prop="sourceColumnNames">
          <el-select
            v-model="relationForm.sourceColumnNames"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择一个或多个源字段"
            style="width: 100%"
          >
            <el-option
              v-for="column in relationSourceColumns"
              :key="column"
              :label="column"
              :value="column"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标表" prop="targetTableName">
          <el-select
            v-model="relationForm.targetTableName"
            :disabled="Boolean(editingRelationId)"
            filterable
            placeholder="选择目标表"
            style="width: 100%"
            @change="handleRelationTargetTableChange"
          >
            <el-option v-for="table in physicalTables" :key="table" :label="table" :value="table" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标字段" prop="targetColumnNames">
          <el-select
            v-model="relationForm.targetColumnNames"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择与源字段数量一致的目标字段"
            style="width: 100%"
          >
            <el-option
              v-for="column in relationTargetColumns"
              :key="column"
              :label="column"
              :value="column"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关系类型" prop="relationType">
          <el-select v-model="relationForm.relationType" clearable placeholder="可选" style="width: 100%">
            <el-option label="1:1" value="1:1" />
            <el-option label="1:N" value="1:N" />
            <el-option label="N:1" value="N:1" />
            <el-option label="N:N" value="N:N" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明" prop="description">
          <el-input
            v-model="relationForm.description"
            type="textarea"
            :rows="4"
            placeholder="说明这条关系的业务语义"
          />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="relationFormEnabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="relationDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="relationSubmitting" @click="submitRelationDialog">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
  import { computed, defineComponent, onMounted, reactive, ref, watch } from 'vue';
  import type { FormInstance, FormRules } from 'element-plus';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import agentDatasourceService from '@/services/agentDatasource';
  import datasourceService, { type Datasource, type AgentDatasource } from '@/services/datasource';
  import structuredSemanticService, {
    type SemanticColumnItem,
    type SemanticColumnUpsertDTO,
    type SemanticRelationItem,
    type SemanticRelationUpsertDTO,
    type SemanticTableItem,
    type SemanticTableUpsertDTO,
  } from '@/services/structuredSemantic';

  interface TableFormState {
    tableName: string;
    businessName: string;
    synonyms: string;
    businessDescription: string;
    tableComment: string;
    isVisible: number;
    status: number;
  }

  interface ColumnFormState {
    tableName: string;
    columnName: string;
    businessName: string;
    synonyms: string;
    businessDescription: string;
    columnComment: string;
    dataType: string;
    isVisible: number;
    status: number;
  }

  interface RelationFormState {
    sourceTableName: string;
    sourceColumnNames: string[];
    targetTableName: string;
    targetColumnNames: string[];
    relationType: string;
    description: string;
    status: number;
  }

  const createEmptyTableForm = (): TableFormState => ({
    tableName: '',
    businessName: '',
    synonyms: '',
    businessDescription: '',
    tableComment: '',
    isVisible: 1,
    status: 1,
  });

  const createEmptyColumnForm = (): ColumnFormState => ({
    tableName: '',
    columnName: '',
    businessName: '',
    synonyms: '',
    businessDescription: '',
    columnComment: '',
    dataType: '',
    isVisible: 1,
    status: 1,
  });

  const createEmptyRelationForm = (): RelationFormState => ({
    sourceTableName: '',
    sourceColumnNames: [],
    targetTableName: '',
    targetColumnNames: [],
    relationType: '',
    description: '',
    status: 1,
  });

  export default defineComponent({
    name: 'AgentSemanticsConfig',
    props: {
      agentId: {
        type: Number,
        required: true,
      },
    },
    setup(props) {
      const activeTab = ref<'tables' | 'relations'>('tables');
      const tableViewMode = ref<'list' | 'columns'>('list');
      const keyword = ref('');
      const selectedDatasourceId = ref<number>();
      const datasourceOptions = ref<Datasource[]>([]);
      const physicalTables = ref<string[]>([]);
      const selectedTableName = ref('');
      const relationFilterTable = ref('');
      const tableColumnsCache = ref<Record<string, string[]>>({});

      const tableLoading = ref(false);
      const columnLoading = ref(false);
      const relationLoading = ref(false);

      const tableRows = ref<SemanticTableItem[]>([]);
      const columnRows = ref<SemanticColumnItem[]>([]);
      const relationRows = ref<SemanticRelationItem[]>([]);

      const tablePage = reactive({
        pageNum: 1,
        pageSize: 10,
        total: 0,
      });
      const columnPage = reactive({
        pageNum: 1,
        pageSize: 10,
        total: 0,
      });
      const relationPage = reactive({
        pageNum: 1,
        pageSize: 10,
        total: 0,
      });

      const tableDialogVisible = ref(false);
      const columnDialogVisible = ref(false);
      const relationDialogVisible = ref(false);
      const tableSubmitting = ref(false);
      const columnSubmitting = ref(false);
      const relationSubmitting = ref(false);
      const editingTableId = ref<number>();
      const editingColumnId = ref<number>();
      const editingRelationId = ref<number>();

      const tableFormRef = ref<FormInstance>();
      const columnFormRef = ref<FormInstance>();
      const relationFormRef = ref<FormInstance>();

      const tableForm = reactive<TableFormState>(createEmptyTableForm());
      const columnForm = reactive<ColumnFormState>(createEmptyColumnForm());
      const relationForm = reactive<RelationFormState>(createEmptyRelationForm());

      const relationSourceColumns = ref<string[]>([]);
      const relationTargetColumns = ref<string[]>([]);
      const columnFormTableColumns = ref<string[]>([]);

      const currentDatasource = computed(() =>
        datasourceOptions.value.find(item => item.id === selectedDatasourceId.value),
      );

      const primaryActionLabel = computed(() => {
        if (activeTab.value === 'tables') {
          return tableViewMode.value === 'columns' ? '新增列语义' : '新增表语义';
        }
        return '新增关系语义';
      });

      const tableFormVisible = computed({
        get: () => tableForm.isVisible === 1,
        set: value => {
          tableForm.isVisible = value ? 1 : 0;
        },
      });

      const tableFormEnabled = computed({
        get: () => tableForm.status === 1,
        set: value => {
          tableForm.status = value ? 1 : 0;
        },
      });

      const columnFormVisible = computed({
        get: () => columnForm.isVisible === 1,
        set: value => {
          columnForm.isVisible = value ? 1 : 0;
        },
      });

      const columnFormEnabled = computed({
        get: () => columnForm.status === 1,
        set: value => {
          columnForm.status = value ? 1 : 0;
        },
      });

      const relationFormEnabled = computed({
        get: () => relationForm.status === 1,
        set: value => {
          relationForm.status = value ? 1 : 0;
        },
      });

      const tableRules: FormRules<TableFormState> = {
        tableName: [{ required: true, message: '请选择数据表', trigger: 'change' }],
      };

      const columnRules: FormRules<ColumnFormState> = {
        tableName: [{ required: true, message: '请选择数据表', trigger: 'change' }],
        columnName: [{ required: true, message: '请选择字段', trigger: 'change' }],
      };

      const relationRules: FormRules<RelationFormState> = {
        sourceTableName: [{ required: true, message: '请选择源表', trigger: 'change' }],
        sourceColumnNames: [{ required: true, message: '请选择源字段', trigger: 'change' }],
        targetTableName: [{ required: true, message: '请选择目标表', trigger: 'change' }],
        targetColumnNames: [{ required: true, message: '请选择目标字段', trigger: 'change' }],
      };

      const formatTime = (value?: string) => value || '-';

      const normalizeText = (value: string) => {
        const trimmed = value.trim();
        return trimmed || undefined;
      };

      const splitColumns = (value?: string) =>
        (value || '')
          .split(',')
          .map(item => item.trim())
          .filter(Boolean);

      const joinColumns = (value: string[]) => value.map(item => item.trim()).filter(Boolean).join(',');

      const flagTagType = (value?: number) => (value === 1 ? 'success' : 'info');
      const flagLabel = (value?: number) => (value === 1 ? '显示' : '隐藏');
      const statusLabel = (value?: number) => (value === 1 ? '启用' : '停用');

      const requireDatasourceId = (): number => {
        if (!selectedDatasourceId.value) {
          throw new Error('请先选择数据源');
        }
        return selectedDatasourceId.value;
      };

      const fetchAgentDatasources = async () => {
        const bindings = await agentDatasourceService.getAgentDatasource(props.agentId);
        datasourceOptions.value = (bindings || [])
          .map((item: AgentDatasource) => item.datasource)
          .filter((item): item is Datasource => Boolean(item && item.id));
        if (!selectedDatasourceId.value && datasourceOptions.value.length > 0) {
          const activeDatasource = (bindings || []).find(item => item.isActive === 1)?.datasource;
          selectedDatasourceId.value = activeDatasource?.id || datasourceOptions.value[0].id;
        }
      };

      const fetchPhysicalTables = async () => {
        if (!selectedDatasourceId.value) {
          physicalTables.value = [];
          selectedTableName.value = '';
          return;
        }
        try {
          physicalTables.value = await datasourceService.getDatasourceTables(selectedDatasourceId.value);
          if (physicalTables.value.length === 0) {
            selectedTableName.value = '';
          }
          else if (!physicalTables.value.includes(selectedTableName.value)) {
            selectedTableName.value = physicalTables.value[0];
          }
        } catch (error) {
          physicalTables.value = [];
          selectedTableName.value = '';
          ElMessage.error(error instanceof Error ? error.message : '加载物理表失败');
        }
      };

      const fetchTableColumns = async (tableName: string): Promise<string[]> => {
        const datasourceId = requireDatasourceId();
        if (!tableName) {
          return [];
        }
        if (tableColumnsCache.value[tableName]) {
          return tableColumnsCache.value[tableName];
        }
        const columns = await datasourceService.getTableColumns(datasourceId, tableName);
        tableColumnsCache.value[tableName] = columns;
        return columns;
      };

      const loadTables = async () => {
        if (!selectedDatasourceId.value) {
          tableRows.value = [];
          tablePage.total = 0;
          return;
        }
        tableLoading.value = true;
        try {
          const response = await structuredSemanticService.listTables({
            agentId: props.agentId,
            datasourceId: selectedDatasourceId.value,
            keyword: normalizeText(keyword.value),
            pageNum: tablePage.pageNum,
            pageSize: tablePage.pageSize,
          });
          tableRows.value = response.data || [];
          tablePage.total = response.total || 0;
        } catch (error) {
          tableRows.value = [];
          tablePage.total = 0;
          ElMessage.error(error instanceof Error ? error.message : '加载表语义失败');
        } finally {
          tableLoading.value = false;
        }
      };

      const loadColumns = async () => {
        if (!selectedDatasourceId.value || !selectedTableName.value) {
          columnRows.value = [];
          columnPage.total = 0;
          return;
        }
        columnLoading.value = true;
        try {
          const response = await structuredSemanticService.listColumns({
            agentId: props.agentId,
            datasourceId: selectedDatasourceId.value,
            tableName: selectedTableName.value,
            keyword: normalizeText(keyword.value),
            pageNum: columnPage.pageNum,
            pageSize: columnPage.pageSize,
          });
          columnRows.value = response.data || [];
          columnPage.total = response.total || 0;
        } catch (error) {
          columnRows.value = [];
          columnPage.total = 0;
          ElMessage.error(error instanceof Error ? error.message : '加载列语义失败');
        } finally {
          columnLoading.value = false;
        }
      };

      const loadRelations = async () => {
        if (!selectedDatasourceId.value) {
          relationRows.value = [];
          relationPage.total = 0;
          return;
        }
        relationLoading.value = true;
        try {
          const response = await structuredSemanticService.listRelations({
            agentId: props.agentId,
            datasourceId: selectedDatasourceId.value,
            tableName: normalizeText(relationFilterTable.value),
            keyword: normalizeText(keyword.value),
            pageNum: relationPage.pageNum,
            pageSize: relationPage.pageSize,
          });
          relationRows.value = response.data || [];
          relationPage.total = response.total || 0;
        } catch (error) {
          relationRows.value = [];
          relationPage.total = 0;
          ElMessage.error(error instanceof Error ? error.message : '加载关系语义失败');
        } finally {
          relationLoading.value = false;
        }
      };

      const loadCurrentTabData = async () => {
        if (activeTab.value === 'tables') {
          if (tableViewMode.value === 'columns') {
            await loadColumns();
            return;
          }
          await loadTables();
          return;
        }
        await loadRelations();
      };

      const handleSearch = async () => {
        tablePage.pageNum = 1;
        columnPage.pageNum = 1;
        relationPage.pageNum = 1;
        await loadCurrentTabData();
      };

      const resetTableForm = () => {
        Object.assign(tableForm, createEmptyTableForm());
        editingTableId.value = undefined;
      };

      const resetColumnForm = () => {
        Object.assign(columnForm, createEmptyColumnForm());
        editingColumnId.value = undefined;
        columnFormTableColumns.value = [];
      };

      const resetRelationForm = () => {
        Object.assign(relationForm, createEmptyRelationForm());
        editingRelationId.value = undefined;
        relationSourceColumns.value = [];
        relationTargetColumns.value = [];
      };

      const handleOpenCreateDialog = async () => {
        if (!selectedDatasourceId.value) {
          ElMessage.warning('请先选择数据源');
          return;
        }
        if (activeTab.value === 'tables' && tableViewMode.value === 'list') {
          resetTableForm();
          tableDialogVisible.value = true;
          return;
        }
        if (activeTab.value === 'tables') {
          if (!selectedTableName.value) {
            ElMessage.warning('请先选择数据表');
            return;
          }
          resetColumnForm();
          columnForm.tableName = selectedTableName.value || '';
          if (columnForm.tableName) {
            columnFormTableColumns.value = await fetchTableColumns(columnForm.tableName);
          }
          columnDialogVisible.value = true;
          return;
        }
        resetRelationForm();
        relationDialogVisible.value = true;
      };

      const openTableDialog = (row: SemanticTableItem) => {
        editingTableId.value = row.id;
        Object.assign(tableForm, {
          tableName: row.tableName,
          businessName: row.businessName || '',
          synonyms: row.synonyms || '',
          businessDescription: row.businessDescription || '',
          tableComment: row.tableComment || '',
          isVisible: row.isVisible ?? 1,
          status: row.status ?? 1,
        });
        tableDialogVisible.value = true;
      };

      const viewTableColumns = async (tableName: string) => {
        if (!tableName) {
          return;
        }
        activeTab.value = 'tables';
        tableViewMode.value = 'columns';
        if (selectedTableName.value !== tableName) {
          selectedTableName.value = tableName;
          return;
        }
        columnPage.pageNum = 1;
        await loadColumns();
      };

      const backToTableList = async () => {
        tableViewMode.value = 'list';
        columnPage.pageNum = 1;
        await loadTables();
      };

      const openColumnDialog = async (row: SemanticColumnItem) => {
        editingColumnId.value = row.id;
        Object.assign(columnForm, {
          tableName: row.tableName,
          columnName: row.columnName,
          businessName: row.businessName || '',
          synonyms: row.synonyms || '',
          businessDescription: row.businessDescription || '',
          columnComment: row.columnComment || '',
          dataType: row.dataType || '',
          isVisible: 1,
          status: row.status ?? 1,
        });
        columnFormTableColumns.value = await fetchTableColumns(row.tableName);
        columnDialogVisible.value = true;
      };

      const openRelationDialog = async (row: SemanticRelationItem) => {
        editingRelationId.value = row.id;
        Object.assign(relationForm, {
          sourceTableName: row.sourceTableName,
          sourceColumnNames: splitColumns(row.sourceColumnNames),
          targetTableName: row.targetTableName,
          targetColumnNames: splitColumns(row.targetColumnNames),
          relationType: row.relationType || '',
          description: row.description || '',
          status: row.status ?? 1,
        });
        relationSourceColumns.value = await fetchTableColumns(row.sourceTableName);
        relationTargetColumns.value = await fetchTableColumns(row.targetTableName);
        relationDialogVisible.value = true;
      };

      const submitTableDialog = async () => {
        if (!tableFormRef.value) {
          return;
        }
        const valid = await tableFormRef.value.validate().catch(() => false);
        if (!valid) {
          return;
        }
        tableSubmitting.value = true;
        try {
          const dto: SemanticTableUpsertDTO = {
            agentId: props.agentId,
            datasourceId: requireDatasourceId(),
            tableName: tableForm.tableName,
            businessName: normalizeText(tableForm.businessName),
            synonyms: normalizeText(tableForm.synonyms),
            businessDescription: normalizeText(tableForm.businessDescription),
            tableComment: normalizeText(tableForm.tableComment),
            isVisible: tableForm.isVisible,
            status: tableForm.status,
          };
          if (editingTableId.value) {
            await structuredSemanticService.updateTable(editingTableId.value, dto);
            ElMessage.success('表语义已更新');
          } else {
            await structuredSemanticService.createTable(dto);
            ElMessage.success('表语义已创建');
          }
          tableDialogVisible.value = false;
          await loadTables();
        } catch (error) {
          ElMessage.error(error instanceof Error ? error.message : '保存表语义失败');
        } finally {
          tableSubmitting.value = false;
        }
      };

      const submitColumnDialog = async () => {
        if (!columnFormRef.value) {
          return;
        }
        const valid = await columnFormRef.value.validate().catch(() => false);
        if (!valid) {
          return;
        }
        columnSubmitting.value = true;
        try {
          const dto: SemanticColumnUpsertDTO = {
            agentId: props.agentId,
            datasourceId: requireDatasourceId(),
            tableName: columnForm.tableName,
            columnName: columnForm.columnName,
            businessName: normalizeText(columnForm.businessName),
            synonyms: normalizeText(columnForm.synonyms),
            businessDescription: normalizeText(columnForm.businessDescription),
            columnComment: normalizeText(columnForm.columnComment),
            dataType: normalizeText(columnForm.dataType),
            isVisible: columnForm.isVisible,
            status: columnForm.status,
          };
          if (editingColumnId.value) {
            await structuredSemanticService.updateColumn(editingColumnId.value, dto);
            ElMessage.success('列语义已更新');
          } else {
            await structuredSemanticService.createColumn(dto);
            ElMessage.success('列语义已创建');
          }
          selectedTableName.value = columnForm.tableName;
          columnDialogVisible.value = false;
          await loadColumns();
        } catch (error) {
          ElMessage.error(error instanceof Error ? error.message : '保存列语义失败');
        } finally {
          columnSubmitting.value = false;
        }
      };

      const submitRelationDialog = async () => {
        if (!relationFormRef.value) {
          return;
        }
        if (relationForm.sourceColumnNames.length !== relationForm.targetColumnNames.length) {
          ElMessage.warning('源字段和目标字段数量必须一致');
          return;
        }
        const valid = await relationFormRef.value.validate().catch(() => false);
        if (!valid) {
          return;
        }
        relationSubmitting.value = true;
        try {
          const dto: SemanticRelationUpsertDTO = {
            agentId: props.agentId,
            datasourceId: requireDatasourceId(),
            sourceTableName: relationForm.sourceTableName,
            sourceColumnNames: joinColumns(relationForm.sourceColumnNames),
            targetTableName: relationForm.targetTableName,
            targetColumnNames: joinColumns(relationForm.targetColumnNames),
            relationType: normalizeText(relationForm.relationType),
            description: normalizeText(relationForm.description),
            status: relationForm.status,
          };
          if (editingRelationId.value) {
            await structuredSemanticService.updateRelation(editingRelationId.value, dto);
            ElMessage.success('关系语义已更新');
          } else {
            await structuredSemanticService.createRelation(dto);
            ElMessage.success('关系语义已创建');
          }
          relationDialogVisible.value = false;
          await loadRelations();
        } catch (error) {
          ElMessage.error(error instanceof Error ? error.message : '保存关系语义失败');
        } finally {
          relationSubmitting.value = false;
        }
      };

      const handleDeleteTable = async (row: SemanticTableItem) => {
        if (!row.id) {
          return;
        }
        try {
          await ElMessageBox.confirm(`确定删除表语义 ${row.tableName} 吗？`, '提示', {
            type: 'warning',
          });
          await structuredSemanticService.deleteTable(row.id);
          ElMessage.success('表语义已删除');
          await loadTables();
        } catch (error) {
          if (error !== 'cancel') {
            ElMessage.error(error instanceof Error ? error.message : '删除表语义失败');
          }
        }
      };

      const handleDeleteColumn = async (row: SemanticColumnItem) => {
        if (!row.id) {
          return;
        }
        try {
          await ElMessageBox.confirm(`确定删除列语义 ${row.tableName}.${row.columnName} 吗？`, '提示', {
            type: 'warning',
          });
          await structuredSemanticService.deleteColumn(row.id);
          ElMessage.success('列语义已删除');
          await loadColumns();
        } catch (error) {
          if (error !== 'cancel') {
            ElMessage.error(error instanceof Error ? error.message : '删除列语义失败');
          }
        }
      };

      const handleDeleteRelation = async (row: SemanticRelationItem) => {
        if (!row.id) {
          return;
        }
        try {
          await ElMessageBox.confirm(
            `确定删除关系 ${row.sourceTableName} -> ${row.targetTableName} 吗？`,
            '提示',
            {
              type: 'warning',
            },
          );
          await structuredSemanticService.deleteRelation(row.id);
          ElMessage.success('关系语义已删除');
          await loadRelations();
        } catch (error) {
          if (error !== 'cancel') {
            ElMessage.error(error instanceof Error ? error.message : '删除关系语义失败');
          }
        }
      };

      const handleColumnFormTableChange = async (tableName: string) => {
        columnForm.columnName = '';
        columnFormTableColumns.value = tableName ? await fetchTableColumns(tableName) : [];
      };

      const handleRelationSourceTableChange = async (tableName: string) => {
        relationForm.sourceColumnNames = [];
        relationSourceColumns.value = tableName ? await fetchTableColumns(tableName) : [];
      };

      const handleRelationTargetTableChange = async (tableName: string) => {
        relationForm.targetColumnNames = [];
        relationTargetColumns.value = tableName ? await fetchTableColumns(tableName) : [];
      };

      const handleTablePageChange = async (page: number) => {
        tablePage.pageNum = page;
        await loadTables();
      };

      const handleTableSizeChange = async (size: number) => {
        tablePage.pageSize = size;
        tablePage.pageNum = 1;
        await loadTables();
      };

      const handleColumnPageChange = async (page: number) => {
        columnPage.pageNum = page;
        await loadColumns();
      };

      const handleColumnSizeChange = async (size: number) => {
        columnPage.pageSize = size;
        columnPage.pageNum = 1;
        await loadColumns();
      };

      const handleRelationPageChange = async (page: number) => {
        relationPage.pageNum = page;
        await loadRelations();
      };

      const handleRelationSizeChange = async (size: number) => {
        relationPage.pageSize = size;
        relationPage.pageNum = 1;
        await loadRelations();
      };

      watch(selectedDatasourceId, async () => {
        tableColumnsCache.value = {};
        relationFilterTable.value = '';
        tableViewMode.value = 'list';
        await fetchPhysicalTables();
        await loadCurrentTabData();
      });

      watch(selectedTableName, async () => {
        if (activeTab.value === 'tables' && tableViewMode.value === 'columns') {
          columnPage.pageNum = 1;
          await loadColumns();
        }
      });

      watch(relationFilterTable, async () => {
        if (activeTab.value === 'relations') {
          relationPage.pageNum = 1;
          await loadRelations();
        }
      });

      watch(activeTab, async tab => {
        if (tab !== 'tables') {
          tableViewMode.value = 'list';
        }
        await loadCurrentTabData();
      });

      onMounted(async () => {
        await fetchAgentDatasources();
        await fetchPhysicalTables();
        await loadTables();
      });

      return {
        activeTab,
        tableViewMode,
        keyword,
        selectedDatasourceId,
        datasourceOptions,
        currentDatasource,
        primaryActionLabel,
        physicalTables,
        selectedTableName,
        relationFilterTable,
        tableLoading,
        columnLoading,
        relationLoading,
        tableRows,
        columnRows,
        relationRows,
        tablePage,
        columnPage,
        relationPage,
        tableDialogVisible,
        columnDialogVisible,
        relationDialogVisible,
        tableSubmitting,
        columnSubmitting,
        relationSubmitting,
        tableFormRef,
        columnFormRef,
        relationFormRef,
        tableForm,
        columnForm,
        relationForm,
        tableFormVisible,
        tableFormEnabled,
        columnFormVisible,
        columnFormEnabled,
        relationFormEnabled,
        tableRules,
        columnRules,
        relationRules,
        relationSourceColumns,
        relationTargetColumns,
        columnFormTableColumns,
        editingTableId,
        editingColumnId,
        editingRelationId,
        handleSearch,
        handleOpenCreateDialog,
        backToTableList,
        openTableDialog,
        viewTableColumns,
        openColumnDialog,
        openRelationDialog,
        submitTableDialog,
        submitColumnDialog,
        submitRelationDialog,
        handleDeleteTable,
        handleDeleteColumn,
        handleDeleteRelation,
        handleColumnFormTableChange,
        handleRelationSourceTableChange,
        handleRelationTargetTableChange,
        handleTablePageChange,
        handleTableSizeChange,
        handleColumnPageChange,
        handleColumnSizeChange,
        handleRelationPageChange,
        handleRelationSizeChange,
        flagTagType,
        flagLabel,
        statusLabel,
        formatTime,
      };
    },
  });
</script>

<style scoped>
  .semantic-workspace {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .hero-card,
  .toolbar-card,
  .content-card {
    background: linear-gradient(145deg, #ffffff 0%, #f8fbff 100%);
    border: 1px solid #dbe7f3;
    border-radius: 20px;
    box-shadow: 0 20px 45px rgba(31, 41, 55, 0.06);
  }

  .hero-card {
    display: flex;
    justify-content: space-between;
    gap: 24px;
    padding: 28px 32px;
    background:
      radial-gradient(circle at top right, rgba(14, 165, 233, 0.18), transparent 30%),
      linear-gradient(145deg, #ffffff 0%, #eef6ff 100%);
  }

  .hero-kicker {
    color: #0369a1;
    font-size: 12px;
    letter-spacing: 0.18em;
    text-transform: uppercase;
    margin-bottom: 8px;
  }

  .hero-title {
    font-size: 28px;
    color: #0f172a;
    margin-bottom: 10px;
  }

  .hero-desc {
    max-width: 760px;
    color: #475569;
    line-height: 1.7;
  }

  .hero-meta {
    min-width: 200px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    padding: 20px;
    border-radius: 16px;
    background-color: rgba(255, 255, 255, 0.75);
    border: 1px solid rgba(14, 165, 233, 0.18);
    color: #64748b;
  }

  .hero-meta strong {
    margin-top: 8px;
    color: #0f172a;
    font-size: 18px;
  }

  .toolbar-card,
  .content-card {
    padding: 24px;
  }

  .toolbar-grid {
    display: grid;
    grid-template-columns: minmax(240px, 300px) minmax(220px, 1fr) minmax(220px, 260px) auto;
    gap: 16px;
    align-items: center;
  }

  .toolbar-field {
    width: 100%;
  }

  .toolbar-placeholder {
    min-height: 1px;
  }

  .toolbar-actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }

  .semantic-tabs :deep(.el-tabs__header) {
    margin-bottom: 24px;
  }

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 16px;
    margin-bottom: 20px;
  }

  .section-header h3 {
    font-size: 20px;
    color: #0f172a;
    margin-bottom: 6px;
  }

  .section-header p {
    color: #64748b;
  }

  .column-subpage-header {
    align-items: center;
  }

  .column-subpage-title {
    display: flex;
    align-items: flex-start;
    gap: 12px;
  }

  .column-subpage-title :deep(.el-button) {
    padding-left: 0;
  }

  .semantic-table {
    width: 100%;
  }

  .pagination-wrap {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }

  .empty-tip,
  .empty-panel {
    color: #64748b;
    line-height: 1.8;
  }

  .empty-panel {
    padding: 28px 20px;
    background: #f8fafc;
    border-radius: 14px;
  }

  @media (max-width: 1200px) {
    .toolbar-grid {
      grid-template-columns: 1fr;
    }

    .toolbar-actions {
      justify-content: flex-start;
    }
  }

  @media (max-width: 1024px) {
    .hero-card {
      flex-direction: column;
    }
  }
</style>
