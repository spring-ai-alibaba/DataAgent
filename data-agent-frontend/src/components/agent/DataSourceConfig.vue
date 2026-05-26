<!--
 * Copyright 2025 the original author or authors.
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
  <div style="padding: 20px">
    <div style="margin-bottom: 20px">
      <h2>数据源配置</h2>
    </div>
    <el-divider />

    <div style="margin-bottom: 30px">
      <el-row style="display: flex; justify-content: space-between; align-items: center">
        <el-col :span="12">
          <h3>数据源列表</h3>
        </el-col>
        <el-col :span="12" style="text-align: right">
          <el-button @click="dialogVisible = true" size="large" type="primary" round :icon="Plus">
            添加数据源
          </el-button>
          <el-button
            @click="initAgentDatasource"
            v-if="!initStatus"
            size="large"
            type="primary"
            round
            :icon="UploadFilled"
          >
            初始化数据源
          </el-button>
          <el-button v-else size="large" type="primary" round loading>初始化中...</el-button>
        </el-col>
      </el-row>
    </div>

    <el-table :data="datasource" style="width: 100%" border @expand-change="handleExpandChange">
      <el-table-column type="expand" width="100" label="选择数据表">
        <template #default="scope">
          <div
            v-if="scope.row.status === 'active'"
            style="padding: 20px; background: #f8f9fa; border-radius: 8px"
          >
            <div
              style="
                margin-bottom: 15px;
                display: flex;
                justify-content: space-between;
                align-items: center;
              "
            >
              <h4 style="margin: 0">数据表管理</h4>
              <el-button
                @click="loadDatasourceTables(scope.row)"
                size="small"
                type="primary"
                :loading="tableLoadingStates[scope.row.id]"
                round
              >
                刷新表列表
              </el-button>
            </div>

            <div v-if="tableLists[scope.row.id] && tableLists[scope.row.id].length > 0">
              <el-checkbox-group v-model="selectedTables[scope.row.id]">
                <el-row :gutter="10">
                  <el-col
                    v-for="table in tableLists[scope.row.id]"
                    :key="table"
                    :span="6"
                    style="margin-bottom: 10px"
                  >
                    <el-checkbox :label="table" size="large">
                      {{ table }}
                    </el-checkbox>
                  </el-col>
                </el-row>
              </el-checkbox-group>

              <div style="margin-top: 20px; text-align: right">
                <el-button
                  @click="updateDatasourceTables(scope.row)"
                  size="small"
                  type="success"
                  :loading="updateLoadingStates[scope.row.id]"
                  round
                >
                  更新数据表
                </el-button>
                <el-button
                  @click="openColumnVisibilityDialog(scope.row)"
                  size="small"
                  type="warning"
                  round
                  plain
                >
                  字段可见性
                </el-button>
                <el-button
                  @click="selectAllTables(scope.row)"
                  size="small"
                  type="primary"
                  round
                  plain
                >
                  全选
                </el-button>
                <el-button @click="clearAllTables(scope.row)" size="small" type="info" round plain>
                  清空
                </el-button>
              </div>
            </div>
            <div
              v-else-if="tableLoadingStates[scope.row.id]"
              style="text-align: center; padding: 20px"
            >
              <el-icon class="is-loading" style="font-size: 24px"><Loading /></el-icon>
              <div style="margin-top: 10px; color: #666">正在加载表列表...</div>
            </div>
            <div v-else style="text-align: center; padding: 20px; color: #999">
              <el-icon style="font-size: 24px"><FolderOpened /></el-icon>
              <div style="margin-top: 10px">暂无表数据，请点击刷新表列表</div>
            </div>
          </div>
          <div v-else style="padding: 20px; text-align: center; color: #999">
            <el-icon style="font-size: 24px"><Lock /></el-icon>
            <div style="margin-top: 10px">请先启用数据源以管理表</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="数据源名称" min-width="120px" />
      <el-table-column prop="type" label="数据源类型" min-width="100px" />
      <el-table-column prop="connectionUrl" label="连接地址" min-width="200px">
        <template #default="scope">
          <el-tooltip
            :content="scope.row.connectionUrl"
            placement="top"
            :disabled="!scope.row.connectionUrl || scope.row.connectionUrl.length <= 50"
          >
            <span class="connection-url-text">
              {{ scope.row.connectionUrl ? truncateText(scope.row.connectionUrl, 50) : '-' }}
            </span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="连接状态" min-width="50px">
        <template #default="scope">
          <el-tag :type="scope.row.testStatus === 'success' ? 'success' : 'danger'" round>
            {{ scope.row.testStatus === 'success' ? '连接成功' : '连接失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="40px">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'active' ? 'success' : 'info'" round>
            {{ scope.row.status === 'active' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="100px" />
      <el-table-column label="操作" min-width="120px">
        <template #default="scope">
          <el-button
            v-if="scope.row.status === 'active'"
            @click="changeDatasource(scope.row, false)"
            size="small"
            type="warning"
            round
            plain
          >
            禁用
          </el-button>
          <el-button
            v-else
            @click="changeDatasource(scope.row, true)"
            size="small"
            type="success"
            round
            plain
          >
            启用
          </el-button>
          <el-button
            @click="testConnection(scope.row)"
            :disabled="scope.row.status !== 'active'"
            size="small"
            type="primary"
            round
            plain
          >
            测试连接
          </el-button>
          <el-button
            @click="removeAgentDatasource(scope.row)"
            size="small"
            type="danger"
            round
            plain
          >
            移除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <!-- 添加数据源Dialog -->
  <el-dialog v-model="dialogVisible" title="添加数据源" width="1000">
    <el-tabs v-model="dialogActiveName" type="card" stretch>
      <el-tab-pane label="选择已有数据源" name="select">
        <!-- todo: 添加分页和查询 -->
        <el-table
          @current-change="handleSelectDatasourceChange"
          :data="allDatasource"
          highlight-current-row
          style="width: 100%"
        >
          <el-table-column property="name" label="数据源名称" width="150" />
          <el-table-column property="type" label="数据源类型" width="100" />
          <el-table-column property="host" label="Host" width="100" />
          <el-table-column property="port" label="Port" width="80" />
          <el-table-column property="description" label="描述" width="300" />
          <el-table-column label="操作" width="150">
            <template #default="scope">
              <el-button @click="editDatasource(scope.row)" size="small" type="primary" round plain>
                修改
              </el-button>
              <el-button
                @click="deleteDatasource(scope.row)"
                size="small"
                type="danger"
                round
                plain
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-divider />
        <div style="text-align: right">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="addSelectDatasource">添加选中数据源</el-button>
        </div>
      </el-tab-pane>
      <el-tab-pane label="添加新数据源" name="add">
        <el-row :gutter="20">
          <el-col :span="12">
            <div class="form-item">
              <label>数据源名称 *</label>
              <el-input v-model="newDatasource.name" placeholder="请输入数据源名称" size="large" />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="form-item">
              <label>数据源类型 *</label>
              <el-select
                v-model="newDatasource.type"
                placeholder="请选择数据源类型"
                style="width: 100%"
                size="large"
              >
                <el-option
                  v-for="type in datasourceTypes"
                  :key="type.typeName"
                  :label="type.displayName"
                  :value="type.typeName"
                />
              </el-select>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <div class="form-item">
              <label>主机地址 *</label>
              <el-input
                v-model="newDatasource.host"
                placeholder="例如：localhost 或 192.168.1.100"
                size="large"
              />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="form-item">
              <label>端口号 *</label>
              <el-input-number
                v-model="newDatasource.port"
                :min="0"
                :max="65535"
                size="large"
                style="width: 100%"
              />
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <div class="form-item">
              <label v-if="newDatasource.type === 'postgresql'">数据库名 *</label>
              <label v-else>数据库名 *</label>
              <el-input
                v-model="newDatasource.databaseName"
                :placeholder="
                  newDatasource.type === 'postgresql' ? '例如：postgres' : '请输入数据库名称'
                "
                size="large"
              />
            </div>
          </el-col>
          <el-col
            :span="12"
            v-if="newDatasource.type === 'postgresql' || newDatasource.type === 'oracle'"
          >
            <div class="form-item">
              <label>Schema 名 *</label>
              <el-input
                v-model="schemaName"
                :placeholder="newDatasource.type === 'postgresql' ? '例如：public' : '例如：SYSTEM'"
                size="large"
              />
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col>
            <div class="form-item">
              <label>连接地址</label>
              <el-input
                v-model="newDatasource.connectionUrl"
                placeholder="请输入JDBC地址（若不填则自动生成）"
                size="large"
              />
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <div class="form-item">
              <label>用户名 *</label>
              <el-input
                v-model="newDatasource.username"
                placeholder="请输入数据库用户名"
                size="large"
              />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="form-item">
              <label>密码 *</label>
              <el-input
                v-model="newDatasource.password"
                placeholder="请输入数据库密码"
                size="large"
                show-password
              />
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="30">
          <el-col :span="24">
            <div class="form-item">
              <label>描述</label>
              <el-input
                v-model="newDatasource.description"
                :rows="4"
                type="textarea"
                placeholder="请输入数据源描述（可选）"
                size="large"
              />
            </div>
          </el-col>
        </el-row>

        <el-divider />
        <div style="text-align: right">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="createNewDatasource">创建并添加</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
  <el-dialog v-model="editDialogVisible" title="编辑数据源" width="1000">
    <el-row :gutter="20">
      <el-col :span="12">
        <div class="form-item">
          <label>数据源名称 *</label>
          <el-input v-model="editingDatasource.name" placeholder="请输入数据源名称" size="large" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="form-item">
          <label>数据源类型 *</label>
          <el-select
            v-model="editingDatasource.type"
            placeholder="请选择数据源类型"
            style="width: 100%"
            size="large"
          >
            <el-option
              v-for="type in datasourceTypes"
              :key="type.typeName"
              :label="type.displayName"
              :value="type.typeName"
            />
          </el-select>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col :span="12">
        <div class="form-item">
          <label>主机地址 *</label>
          <el-input
            v-model="editingDatasource.host"
            placeholder="例如：localhost 或 192.168.1.100"
            size="large"
          />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="form-item">
          <label>端口号 *</label>
          <el-input-number
            v-model="editingDatasource.port"
            :min="0"
            :max="65535"
            size="large"
            style="width: 100%"
          />
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col :span="12">
        <div class="form-item">
          <label v-if="editingDatasource.type === 'postgresql'">数据库名 *</label>
          <label v-else>数据库名 *</label>
          <el-input
            v-model="editingDatasource.databaseName"
            :placeholder="
              editingDatasource.type === 'postgresql' ? '例如：postgres' : '请输入数据库名称'
            "
            size="large"
          />
        </div>
      </el-col>
      <el-col
        :span="12"
        v-if="editingDatasource.type === 'postgresql' || editingDatasource.type === 'oracle'"
      >
        <div class="form-item">
          <label>Schema 名 *</label>
          <el-input
            v-model="schemaNameEdit"
            :placeholder="editingDatasource.type === 'postgresql' ? '例如：public' : '例如：SYSTEM'"
            size="large"
          />
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col>
        <div class="form-item">
          <label>连接地址</label>
          <el-input
            v-model="editingDatasource.connectionUrl"
            placeholder="请输入JDBC地址（若不填则自动生成）"
            size="large"
          />
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col :span="12">
        <div class="form-item">
          <label>用户名 *</label>
          <el-input
            v-model="editingDatasource.username"
            placeholder="请输入数据库用户名"
            size="large"
          />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="form-item">
          <label>密码 *</label>
          <el-input
            v-model="editingDatasource.password"
            placeholder="请输入数据库密码"
            size="large"
            show-password
          />
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="30">
      <el-col :span="24">
        <div class="form-item">
          <label>描述</label>
          <el-input
            v-model="editingDatasource.description"
            :rows="4"
            type="textarea"
            placeholder="请输入数据源描述（可选）"
            size="large"
          />
        </div>
      </el-col>
    </el-row>

    <el-divider />
    <div style="text-align: right">
      <el-button @click="editDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveEditDatasource">保存修改</el-button>
    </div>
  </el-dialog>

  <el-dialog
    v-model="columnDialogVisible"
    title="字段可见性配置"
    width="900px"
    :close-on-click-modal="false"
  >
    <div v-if="currentColumnDatasource">
      <div style="margin-bottom: 16px; padding: 12px; background: #f5f7fa; border-radius: 6px">
        <div style="font-size: 14px; color: #606266">
          当前数据源：
          <span style="font-weight: 600; color: #303133">
            {{ currentColumnDatasource.name }}
          </span>
        </div>
      </div>

      <div
        v-if="currentColumnTables.length === 0"
        style="text-align: center; color: #909399; padding: 32px 0"
      >
        当前没有已保存的数据表，请先配置并保存数据表。
      </div>

      <div v-else style="display: flex; flex-direction: column; gap: 16px">
        <div
          v-for="tableName in currentColumnTables"
          :key="tableName"
          style="border: 1px solid #ebeef5; border-radius: 8px; padding: 16px"
        >
          <div
            style="
              display: flex;
              justify-content: space-between;
              align-items: center;
              margin-bottom: 12px;
              gap: 12px;
              flex-wrap: wrap;
            "
          >
            <div>
              <div style="font-size: 15px; font-weight: 600; color: #303133">{{ tableName }}</div>
              <div style="font-size: 12px; color: #909399; margin-top: 4px">
                关闭限制时，默认该表所有字段可见；开启后仅允许下方勾选字段可见。
              </div>
            </div>
            <el-switch
              :model-value="columnRestrictionEnabled[currentColumnDatasource.id]?.[tableName]"
              active-text="限制字段"
              inactive-text="全部字段"
              @change="toggleColumnRestriction(tableName, $event)"
            />
          </div>

          <div
            v-if="columnLoadingStates[getColumnLoadingKey(currentColumnDatasource.id, tableName)]"
            style="padding: 16px 0"
          >
            <el-skeleton :rows="2" animated />
          </div>

          <div
            v-else-if="
              !(columnOptionsByDatasource[currentColumnDatasource.id]?.[tableName] || []).length
            "
            style="padding: 12px 0; color: #909399"
          >
            未加载到字段信息。
          </div>

          <div v-else>
            <div style="margin-bottom: 10px; text-align: right">
              <el-button
                size="small"
                type="primary"
                plain
                :disabled="!columnRestrictionEnabled[currentColumnDatasource.id]?.[tableName]"
                @click="selectAllColumnsForTable(tableName)"
              >
                全选字段
              </el-button>
              <el-button
                size="small"
                plain
                :disabled="!columnRestrictionEnabled[currentColumnDatasource.id]?.[tableName]"
                @click="clearColumnsForTable(tableName)"
              >
                清空字段
              </el-button>
            </div>

            <el-checkbox-group
              v-model="selectedColumns[currentColumnDatasource.id][tableName]"
              :disabled="!columnRestrictionEnabled[currentColumnDatasource.id]?.[tableName]"
            >
              <el-row :gutter="10">
                <el-col
                  v-for="column in columnOptionsByDatasource[currentColumnDatasource.id]?.[
                    tableName
                  ] || []"
                  :key="column"
                  :span="8"
                  style="margin-bottom: 10px"
                >
                  <el-checkbox :label="column">{{ column }}</el-checkbox>
                </el-col>
              </el-row>
            </el-checkbox-group>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div style="text-align: right">
        <el-button @click="columnDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingColumnVisibility" @click="saveDatasourceColumns">
          保存字段可见性
        </el-button>
      </div>
    </template>
  </el-dialog>

</template>

<script lang="ts">
  import { defineComponent, ref, onMounted, Ref, watch } from 'vue';
  import {
    Plus,
    UploadFilled,
    Loading,
    FolderOpened,
    Lock,
  } from '@element-plus/icons-vue';
  import datasourceService from '@/services/datasource';
  import { Datasource, AgentDatasource, DatasourceType } from '@/services/datasource';
  import { ApiResponse } from '@/services/common';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import agentDatasourceService from '@/services/agentDatasource';

  export default defineComponent({
    name: 'AgentDataSourceConfig',
    props: {
      agentId: {
        type: Number,
        required: true,
      },
    },
    setup(props) {
      // 当前Agent关联的数据源列表
      const datasource: Ref<Datasource[]> = ref([]);
      const initStatus: Ref<boolean> = ref(false);
      const dialogVisible: Ref<boolean> = ref(false);
      const dialogActiveName: Ref<string> = ref('select');
      // 所有数据源列表
      const allDatasource: Ref<Datasource[]> = ref([]);
      const newDatasource: Ref<Datasource> = ref({ port: 3306 } as Datasource);
      const selectedDatasourceId: Ref<number | null> = ref(null);
      const editDialogVisible: Ref<boolean> = ref(false);
      const editingDatasource: Ref<Datasource> = ref({} as Datasource);

      // PostgreSQL/Oracle 额外的schema字段
      const schemaName: Ref<string> = ref('');
      const schemaNameEdit: Ref<string> = ref('');

      // 数据表管理相关状态
      const tableLists: Ref<Record<number, string[]>> = ref({});
      const selectedTables: Ref<Record<number, string[]>> = ref({});
      const tableLoadingStates: Ref<Record<number, boolean>> = ref({});
      const updateLoadingStates: Ref<Record<number, boolean>> = ref({});
      const agentDatasourceList: Ref<AgentDatasource[]> = ref([]);
      const selectedColumns: Ref<Record<number, Record<string, string[]>>> = ref({});
      const columnOptionsByDatasource: Ref<Record<number, Record<string, string[]>>> = ref({});
      const columnRestrictionEnabled: Ref<Record<number, Record<string, boolean>>> = ref({});
      const columnLoadingStates: Ref<Record<string, boolean>> = ref({});
      const columnDialogVisible: Ref<boolean> = ref(false);
      const currentColumnDatasource: Ref<Datasource | null> = ref(null);
      const currentColumnTables: Ref<string[]> = ref([]);
      const savingColumnVisibility: Ref<boolean> = ref(false);

      // 数据源类型列表
      const datasourceTypes: Ref<DatasourceType[]> = ref([]);

      watch(dialogVisible, newValue => {
        if (newValue) {
          loadAllDatasource();
          loadDatasourceTypes();
          newDatasource.value = { port: 3306 } as Datasource;
          schemaName.value = '';
        }
      });

      watch(editDialogVisible, newValue => {
        if (newValue) {
          loadDatasourceTypes();
        }
      });

      // 初始化Agent数据源列表
      const loadAgentDatasource = async () => {
        selectedDatasourceId.value = null;
        try {
          const response = await agentDatasourceService.getAgentDatasource(props.agentId);
          agentDatasourceList.value = response || [];
          const agentDatasource: AgentDatasource[] = response || [];
          datasource.value = agentDatasource.map(item => {
            const datasourceItem = { ...item.datasource };
            datasourceItem.status = item.isActive === 1 ? 'active' : 'inactive';

            if (item.datasource?.id) {
              if (item.selectTables) {
                selectedTables.value[item.datasource.id] = [...item.selectTables];
              }
              selectedColumns.value[item.datasource.id] = Object.entries(
                item.selectColumns || {},
              ).reduce<Record<string, string[]>>((result, [tableName, columns]) => {
                result[tableName] = [...columns];
                return result;
              }, {});
              columnRestrictionEnabled.value[item.datasource.id] = {};
              Object.keys(selectedColumns.value[item.datasource.id]).forEach(tableName => {
                columnRestrictionEnabled.value[item.datasource.id][tableName] = true;
              });
            }

            return datasourceItem;
          });
        } catch (error) {
          ElMessage.error('加载当前智能体的数据源列表失败');
          console.error('Failed to load datasource:', error);
        }
      };

      const getAgentDatasourceByDatasourceId = (
        datasourceId: number,
      ): AgentDatasource | undefined => {
        return agentDatasourceList.value.find(item => item.datasource?.id === datasourceId);
      };

      const getErrorMessage = (error: unknown, fallback: string): string => {
        if (error instanceof Error && error.message.trim()) {
          return error.message;
        }
        return fallback;
      };

      const applyAgentDatasourceSnapshot = (snapshot: AgentDatasource): void => {
        const datasourceId = snapshot.datasource?.id;
        if (!datasourceId || !snapshot.datasource) {
          return;
        }

        const nextSnapshot: AgentDatasource = {
          ...snapshot,
          selectTables: [...(snapshot.selectTables || [])],
          selectColumns: Object.entries(snapshot.selectColumns || {}).reduce<
            Record<string, string[]>
          >((result, [tableName, columns]) => {
            result[tableName] = [...columns];
            return result;
          }, {}),
        };

        const agentDatasourceIndex = agentDatasourceList.value.findIndex(
          item => item.datasource?.id === datasourceId,
        );
        if (agentDatasourceIndex >= 0) {
          agentDatasourceList.value[agentDatasourceIndex] = nextSnapshot;
        } else {
          agentDatasourceList.value.push(nextSnapshot);
        }

        const datasourceSnapshot: Datasource = {
          ...nextSnapshot.datasource,
          status: nextSnapshot.isActive === 1 ? 'active' : 'inactive',
        };
        const datasourceIndex = datasource.value.findIndex(item => item.id === datasourceId);
        if (datasourceIndex >= 0) {
          datasource.value[datasourceIndex] = datasourceSnapshot;
        } else {
          datasource.value.push(datasourceSnapshot);
        }

        selectedTables.value[datasourceId] = [...(nextSnapshot.selectTables || [])];
        selectedColumns.value[datasourceId] = Object.entries(
          nextSnapshot.selectColumns || {},
        ).reduce<Record<string, string[]>>((result, [tableName, columns]) => {
          result[tableName] = [...columns];
          return result;
        }, {});
        columnRestrictionEnabled.value[datasourceId] = {};
        (nextSnapshot.selectTables || []).forEach(tableName => {
          columnRestrictionEnabled.value[datasourceId][tableName] =
            (nextSnapshot.selectColumns?.[tableName] || []).length > 0;
        });
      };

      const getSelectedTablesForDatasource = (datasourceId: number): string[] => {
        const currentTables = selectedTables.value[datasourceId];
        if (currentTables && currentTables.length > 0) {
          return [...currentTables];
        }
        return [...(getAgentDatasourceByDatasourceId(datasourceId)?.selectTables || [])];
      };

      const normalizeNameList = (values: string[] = []): string[] => {
        return [...values]
          .map(value => value.trim())
          .filter(Boolean)
          .sort((left, right) => left.localeCompare(right));
      };

      const hasPendingTableChanges = (datasourceRow: Datasource): boolean => {
        if (!datasourceRow.id) {
          return false;
        }
        const savedTables = normalizeNameList(
          getAgentDatasourceByDatasourceId(datasourceRow.id)?.selectTables || [],
        );
        const currentTables = normalizeNameList(selectedTables.value[datasourceRow.id] || []);
        return savedTables.join('|') !== currentTables.join('|');
      };

      const resolveConfiguredColumns = (
        selectColumns: Record<string, string[]> | undefined,
        tableName: string,
      ): string[] => {
        if (!selectColumns) {
          return [];
        }
        if (selectColumns[tableName]) {
          return [...selectColumns[tableName]];
        }
        const matchedKey = Object.keys(selectColumns).find(
          key => key.toLowerCase() === tableName.toLowerCase(),
        );
        return matchedKey ? [...(selectColumns[matchedKey] || [])] : [];
      };

      const getColumnLoadingKey = (datasourceId: number, tableName: string): string => {
        return `${datasourceId}:${tableName}`;
      };

      const loadColumnsForTable = async (
        datasourceId: number,
        tableName: string,
      ): Promise<void> => {
        const loadingKey = getColumnLoadingKey(datasourceId, tableName);
        columnLoadingStates.value[loadingKey] = true;
        try {
          const columns = await agentDatasourceService.getVisibleTableColumns(
            String(props.agentId),
            datasourceId,
            tableName,
          );
          if (!columnOptionsByDatasource.value[datasourceId]) {
            columnOptionsByDatasource.value[datasourceId] = {};
          }
          columnOptionsByDatasource.value[datasourceId][tableName] = columns;
        } catch (error) {
          ElMessage.error(getErrorMessage(error, `加载表 ${tableName} 的字段失败`));
          console.error('Failed to load datasource columns:', error);
        } finally {
          columnLoadingStates.value[loadingKey] = false;
        }
      };

      const handleSelectDatasourceChange = (value: Datasource) => {
        if (value === null || value === undefined) {
          selectedDatasourceId.value = null;
        } else {
          selectedDatasourceId.value = value.id;
        }
      };

      const loadAllDatasource = async () => {
        try {
          const response = await datasourceService.getAllDatasource();
          allDatasource.value = response || [];
        } catch (error) {
          ElMessage.error('加载所有数据源列表失败');
          console.error('Failed to load all datasource:', error);
        }
      };

      // 加载数据源类型列表
      const loadDatasourceTypes = async () => {
        try {
          const response = await datasourceService.getDatasourceTypes();
          if (response.success && response.data) {
            datasourceTypes.value = response.data;
          }
        } catch (error) {
          ElMessage.error('加载数据源类型失败');
          console.error('Failed to load datasource types:', error);
        }
      };

      // 初始化Agent数据源
      const initAgentDatasource = async () => {
        initStatus.value = true;
        try {
          try {
            // 获取智能体配置的启用数据源
            const usedDatasource: AgentDatasource =
              await agentDatasourceService.getActiveAgentDatasource(props.agentId);

            if (usedDatasource.datasource == null && usedDatasource.datasourceId == null) {
              ElMessage.warning(
                '当前智能体没有启用的数据源！请添加一个新数据源，或者启用已有的数据源',
              );
              return;
            } else if (
              usedDatasource.selectTables == null ||
              usedDatasource.selectTables.length === 0
            ) {
              ElMessage.warning(
                '当前启用的数据源没有选择相应的数据表！请点击相应数据源左侧按钮，选择相应数据表并更新！',
              );
              return;
            }
          } catch {
            ElMessage.warning(
              '当前智能体没有启用的数据源！请添加一个新数据源，或者启用已有的数据源',
            );
            return;
          }

          const response: ApiResponse<null> = await agentDatasourceService.initSchema(
            props.agentId,
          );
          if (response.success === undefined || response.success == null || !response.success) {
            ElMessage.error(`初始化数据源失败`);
            throw new Error('初始化数据源失败');
          }

          ElMessage.success('初始化当前智能体的数据源成功');
        } catch (error) {
          ElMessage.error('初始化当前智能体的数据源失败');
          console.error('Failed to init datasource:', error);
        } finally {
          initStatus.value = false;
        }
      };

      // 更改数据源状态
      const changeDatasource = async (row: Datasource, active: boolean) => {
        const datasourceId = row.id;
        if (!datasourceId) {
          ElMessage.error('数据源ID不存在，无法切换状态');
          return;
        }
        try {
          if (active) {
            const response: ApiResponse = await agentDatasourceService.addDatasourceToAgent(
              String(props.agentId),
              datasourceId,
            );
            if (response.success) {
              ElMessage.success('已切换到对应数据源');
              await loadAgentDatasource();
            } else {
              ElMessage.error(response.message || '切换数据源失败！');
              console.error('Failed to switch datasource:', response);
            }
          } else {
            const activeDatasourceCount = datasource.value.filter(
              item => item.status === 'active',
            ).length;
            if (row.status === 'active' && activeDatasourceCount <= 1) {
              ElMessage.warning('当前智能体必须至少保留一个启用中的数据源');
              return;
            }

            const response: ApiResponse = await agentDatasourceService.toggleDatasourceForAgent(
              String(props.agentId),
              { datasourceId, isActive: false },
            );
            if (response.success) {
              ElMessage.success('操作成功！');
              await loadAgentDatasource();
            } else {
              ElMessage.error(response.message || '操作失败！');
              console.error('Failed to disable datasource:', response);
            }
          }
        } catch (error) {
          ElMessage.error(getErrorMessage(error, active ? '切换数据源失败！' : '操作失败！'));
          console.error('Failed to change datasource:', error);
        }
      };

      // 测试数据源连接
      const testConnection = async (row: Datasource) => {
        const datasourceId = row.id;
        if (row.status !== 'active') {
          ElMessage.warning('禁用的数据源无需测试连接，请先启用');
          return;
        }
        try {
          const response: ApiResponse = await datasourceService.testConnection(datasourceId);
          if (response.success) {
            ElMessage.success('测试连接成功！');
            row.testStatus = 'success';
          } else {
            ElMessage.error('测试连接失败！');
            console.error('Failed to test connection:', response);
            row.testStatus = 'fail';
          }
        } catch (error) {
          ElMessage.error('测试连接失败！');
          console.error('Failed to test connection:', error);
          row.testStatus = 'fail';
        }
      };

      // 移除Agent数据源
      const removeAgentDatasource = async (row: Datasource) => {
        const datasourceId = row.id;

        try {
          await ElMessageBox.confirm('是否要删除当前数据源吗？', '提示', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning',
          });
        } catch (error) {
          return;
        }

        try {
          const response: ApiResponse = await agentDatasourceService.removeDatasourceFromAgent(
            props.agentId,
            datasourceId,
          );
          if (response.success) {
            ElMessage.success('移除成功！');
            datasource.value = datasource.value.filter(item => item.id !== datasourceId);
          } else {
            ElMessage.error('移除失败！');
            console.error('Failed to remove datasource:', response);
          }
        } catch (error) {
          ElMessage.error('移除失败！');
          console.error('Failed to remove datasource:', error);
        }
      };

      const addDatasourceToAgent = async (datasourceId: number) => {
        try {
          await agentDatasourceService.addDatasourceToAgent(props.agentId, datasourceId);
          await loadAgentDatasource();
          ElMessage.success('添加数据源成功');
          dialogVisible.value = false;
        } catch (error) {
          ElMessage.error('添加数据源失败');
          console.error('Failed to add datasource:', error);
        }
      };

      const addSelectDatasource = async () => {
        const datasourceId = selectedDatasourceId.value;
        if (datasourceId === null || datasourceId === undefined) {
          ElMessage.warning('请选择一个数据源');
          return;
        }
        await addDatasourceToAgent(datasourceId);
      };

      const validateDatasourceForm = (
        datasourceForm: Datasource,
        needsSchema: boolean = false,
        schemaValue: string = '',
      ): string[] => {
        const errors: string[] = [];

        if (!datasourceForm.name || datasourceForm.name.trim() === '') {
          errors.push('数据源名称不能为空');
        }

        if (!datasourceForm.type) {
          errors.push('请选择数据源类型');
        }

        if (!datasourceForm.host || datasourceForm.host.trim() === '') {
          errors.push('主机地址不能为空');
        }

        if (!datasourceForm.port || datasourceForm.port <= 0 || datasourceForm.port > 65535) {
          errors.push('请输入有效的端口号（1-65535）');
        }

        if (!datasourceForm.databaseName || datasourceForm.databaseName.trim() === '') {
          errors.push('数据库名不能为空');
        }

        if (needsSchema && (!schemaValue || schemaValue.trim() === '')) {
          errors.push('Schema 名不能为空');
        }

        if (!datasourceForm.username || datasourceForm.username.trim() === '') {
          errors.push('用户名不能为空');
        }

        if (!datasourceForm.password || datasourceForm.password.trim() === '') {
          errors.push('密码不能为空');
        }

        return errors;
      };

      const createNewDatasource = async () => {
        const needsSchema =
          newDatasource.value.type === 'postgresql' || newDatasource.value.type === 'oracle';
        const formErrors: string[] = validateDatasourceForm(
          newDatasource.value,
          needsSchema,
          schemaName.value,
        );
        if (formErrors.length > 0) {
          ElMessage.error(formErrors.join('\r\n'));
          return;
        }
        try {
          // 如果是PostgreSQL或Oracle，合并数据库名和schema名
          if (needsSchema && schemaName.value) {
            newDatasource.value.databaseName = `${newDatasource.value.databaseName}|${schemaName.value}`;
          }
          const datasource: Datasource = await datasourceService.createDatasource(
            newDatasource.value,
          );
          const id = datasource.id;
          if (id === null || id === undefined) {
            throw new Error('创建数据源失败');
          }
          await addDatasourceToAgent(id);
        } catch (error) {
          ElMessage.error('创建数据源失败');
          console.error('Failed to create datasource:', error);
        }
        dialogVisible.value = false;
      };
      const editDatasource = (row: Datasource) => {
        editingDatasource.value = JSON.parse(JSON.stringify(row));
        // 如果是PostgreSQL或Oracle，分离数据库名和schema名
        const needsSchema =
          editingDatasource.value.type === 'postgresql' ||
          editingDatasource.value.type === 'oracle';
        if (needsSchema && editingDatasource.value.databaseName) {
          const parts = editingDatasource.value.databaseName.split('|');
          if (parts.length === 2) {
            editingDatasource.value.databaseName = parts[0];
            schemaNameEdit.value = parts[1];
          } else {
            schemaNameEdit.value = '';
          }
        } else {
          schemaNameEdit.value = '';
        }
        editDialogVisible.value = true;
      };

      const saveEditDatasource = async () => {
        const needsSchema =
          editingDatasource.value.type === 'postgresql' ||
          editingDatasource.value.type === 'oracle';
        const formErrors: string[] = validateDatasourceForm(
          editingDatasource.value,
          needsSchema,
          schemaNameEdit.value,
        );
        if (formErrors.length > 0) {
          ElMessage.error(formErrors.join('\n'));
          return;
        }

        try {
          // 如果是PostgreSQL或Oracle，合并数据库名和schema名
          if (needsSchema && schemaNameEdit.value) {
            editingDatasource.value.databaseName = `${editingDatasource.value.databaseName}|${schemaNameEdit.value}`;
          }
          const response: Datasource = await datasourceService.updateDatasource(
            editingDatasource.value.id!,
            editingDatasource.value,
          );
          if (response && response.id) {
            ElMessage.success('修改成功！');
            const index = allDatasource.value.findIndex(
              item => item.id === editingDatasource.value.id,
            );
            if (index >= 0) {
              allDatasource.value[index] = response;
            }
            editDialogVisible.value = false;
          } else {
            ElMessage.error('修改失败！');
            console.error('Failed to update datasource:', response);
          }
        } catch (error) {
          ElMessage.error('修改失败！');
          console.error('Failed to update datasource:', error);
        }
      };

      const deleteDatasource = async (row: Datasource) => {
        const datasourceId = row.id;

        try {
          await ElMessageBox.confirm('删除后无法恢复，确定要删除该数据源吗？', '确认删除', {
            confirmButtonText: '删除',
            cancelButtonText: '取消',
            type: 'warning',
          });
        } catch (error) {
          return;
        }

        try {
          const response: ApiResponse<void> = await datasourceService.deleteDatasource(
            datasourceId!,
          );
          if (response.success) {
            ElMessage.success('删除成功！');
            allDatasource.value = allDatasource.value.filter(item => item.id !== datasourceId);
          } else {
            ElMessage.error('删除失败！');
            console.error('Failed to delete datasource:', response);
          }
        } catch (error) {
          ElMessage.error('删除失败！');
          console.error('Failed to delete datasource:', error);
        }
      };

      // 加载数据源的表列表
      const loadDatasourceTables = async (datasource: Datasource) => {
        if (!datasource.id) return;
        if (datasource.status !== 'active') {
          ElMessage.warning('禁用的数据源无需加载表结构，请先启用');
          return;
        }

        tableLoadingStates.value[datasource.id] = true;
        try {
          const tables = await datasourceService.getDatasourceTables(datasource.id);
          tableLists.value[datasource.id] = tables;

          // 如果没有初始化已选择的表，则使用当前已选择的表
          if (!selectedTables.value[datasource.id]) {
            const agentDatasource = agentDatasourceList.value.find(
              item => item.datasource?.id === datasource.id,
            );
            selectedTables.value[datasource.id] = agentDatasource?.selectTables || [];
          }

          ElMessage.success(`成功加载 ${tables.length} 个表`);
        } catch (error) {
          ElMessage.error('加载表列表失败');
          console.error('Failed to load datasource tables:', error);
        } finally {
          tableLoadingStates.value[datasource.id] = false;
        }
      };

      // 更新数据源的表列表
      const updateDatasourceTables = async (datasource: Datasource) => {
        if (!datasource.id) return;

        updateLoadingStates.value[datasource.id] = true;
        try {
          const response = await agentDatasourceService.updateDatasourceTables(
            String(props.agentId),
            {
              datasourceId: datasource.id,
              tables: selectedTables.value[datasource.id] || [],
            },
          );

          if (response.success && response.data) {
            applyAgentDatasourceSnapshot(response.data);
            ElMessage.success('数据表更新成功');
          } else {
            ElMessage.error(response.message || '数据表更新失败');
          }
        } catch (error) {
          ElMessage.error(getErrorMessage(error, '数据表更新失败'));
          console.error('Failed to update datasource tables:', error);
        } finally {
          updateLoadingStates.value[datasource.id] = false;
        }
      };

      const toggleColumnRestriction = (tableName: string, enabled: boolean | string | number) => {
        const datasourceId = currentColumnDatasource.value?.id;
        if (!datasourceId) {
          return;
        }
        if (!columnRestrictionEnabled.value[datasourceId]) {
          columnRestrictionEnabled.value[datasourceId] = {};
        }
        columnRestrictionEnabled.value[datasourceId][tableName] = Boolean(enabled);
        if (!enabled) {
          selectedColumns.value[datasourceId][tableName] = [];
        }
      };

      const openColumnVisibilityDialog = async (datasourceRow: Datasource) => {
        if (!datasourceRow.id) {
          return;
        }
        if (hasPendingTableChanges(datasourceRow)) {
          ElMessage.warning('请先点击“更新数据表”保存当前表配置，再设置字段可见性');
          return;
        }

        const tables = getSelectedTablesForDatasource(datasourceRow.id);
        if (tables.length === 0) {
          ElMessage.warning('请先选择并保存数据表，再配置字段可见性');
          return;
        }

        currentColumnDatasource.value = datasourceRow;
        currentColumnTables.value = [...tables];

        if (!selectedColumns.value[datasourceRow.id]) {
          selectedColumns.value[datasourceRow.id] = {};
        }
        if (!columnRestrictionEnabled.value[datasourceRow.id]) {
          columnRestrictionEnabled.value[datasourceRow.id] = {};
        }
        if (!columnOptionsByDatasource.value[datasourceRow.id]) {
          columnOptionsByDatasource.value[datasourceRow.id] = {};
        }

        const agentDatasource = getAgentDatasourceByDatasourceId(datasourceRow.id);
        tables.forEach(tableName => {
          const configuredColumns = resolveConfiguredColumns(
            agentDatasource?.selectColumns,
            tableName,
          );
          selectedColumns.value[datasourceRow.id][tableName] = configuredColumns;
          columnRestrictionEnabled.value[datasourceRow.id][tableName] =
            configuredColumns.length > 0;
        });

        await Promise.all(
          tables.map(tableName => loadColumnsForTable(datasourceRow.id!, tableName)),
        );
        columnDialogVisible.value = true;
      };

      const selectAllColumnsForTable = (tableName: string) => {
        const datasourceId = currentColumnDatasource.value?.id;
        if (!datasourceId) {
          return;
        }
        selectedColumns.value[datasourceId][tableName] = [
          ...(columnOptionsByDatasource.value[datasourceId]?.[tableName] || []),
        ];
      };

      const clearColumnsForTable = (tableName: string) => {
        const datasourceId = currentColumnDatasource.value?.id;
        if (!datasourceId) {
          return;
        }
        selectedColumns.value[datasourceId][tableName] = [];
      };

      const saveDatasourceColumns = async () => {
        const datasourceId = currentColumnDatasource.value?.id;
        if (!datasourceId) {
          return;
        }

        const invalidTables = currentColumnTables.value.filter(tableName => {
          return (
            columnRestrictionEnabled.value[datasourceId]?.[tableName] &&
            !(selectedColumns.value[datasourceId]?.[tableName] || []).length
          );
        });
        if (invalidTables.length > 0) {
          ElMessage.warning(`请至少为以下数据表选择一个字段：${invalidTables.join('、')}`);
          return;
        }

        savingColumnVisibility.value = true;
        try {
          const tables = currentColumnTables.value
            .filter(tableName => columnRestrictionEnabled.value[datasourceId]?.[tableName])
            .map(tableName => ({
              tableName,
              columns: [...(selectedColumns.value[datasourceId]?.[tableName] || [])],
            }));

          const response = await agentDatasourceService.updateDatasourceColumns(
            String(props.agentId),
            {
              datasourceId,
              tables,
            },
          );

          if (response.success && response.data) {
            applyAgentDatasourceSnapshot(response.data);
            ElMessage.success('字段可见性更新成功');
            columnDialogVisible.value = false;
          } else {
            ElMessage.error(response.message || '字段可见性更新失败');
          }
        } catch (error) {
          ElMessage.error(getErrorMessage(error, '字段可见性更新失败'));
          console.error('Failed to update datasource columns:', error);
        } finally {
          savingColumnVisibility.value = false;
        }
      };

      // 全选表
      const selectAllTables = (datasource: Datasource) => {
        if (!datasource.id || !tableLists.value[datasource.id]) return;
        selectedTables.value[datasource.id] = [...tableLists.value[datasource.id]];
      };

      // 清空选择的表
      const clearAllTables = (datasource: Datasource) => {
        if (!datasource.id) return;
        selectedTables.value[datasource.id] = [];
      };

      // 文本截断函数
      const truncateText = (text: string, maxLength: number): string => {
        if (!text || text.length <= maxLength) {
          return text;
        }
        return text.substring(0, maxLength) + '...';
      };

      // 处理表格展开事件
      const handleExpandChange = (row: Datasource, expandedRows: Datasource[]) => {
        // 如果当前行被展开（在expandedRows数组中），则自动加载表列表
        if (expandedRows.includes(row) && row.status === 'active' && row.id) {
          loadDatasourceTables(row);
        }
      };

      onMounted(() => {
        loadAgentDatasource();
      });

      return {
        props,
        Plus,
        UploadFilled,
        Loading,
        FolderOpened,
        Lock,
        datasource,
        initStatus,
        dialogVisible,
        dialogActiveName,
        allDatasource,
        newDatasource,
        editDialogVisible,
        editingDatasource,
        tableLists,
        selectedTables,
        selectedColumns,
        columnOptionsByDatasource,
        columnRestrictionEnabled,
        columnLoadingStates,
        columnDialogVisible,
        currentColumnDatasource,
        currentColumnTables,
        savingColumnVisibility,
        tableLoadingStates,
        updateLoadingStates,
        initAgentDatasource,
        changeDatasource,
        testConnection,
        removeAgentDatasource,
        loadAllDatasource,
        addSelectDatasource,
        createNewDatasource,
        handleSelectDatasourceChange,
        editDatasource,
        saveEditDatasource,
        deleteDatasource,
        loadDatasourceTables,
        updateDatasourceTables,
        openColumnVisibilityDialog,
        saveDatasourceColumns,
        selectAllColumnsForTable,
        clearColumnsForTable,
        toggleColumnRestriction,
        getColumnLoadingKey,
        selectAllTables,
        clearAllTables,
        truncateText,
        handleExpandChange,
        // PostgreSQL/Oracle Schema字段
        schemaName,
        schemaNameEdit,
        // 数据源类型
        datasourceTypes,
        loadDatasourceTypes,
      };
    },
  });
</script>

<style scoped></style>
