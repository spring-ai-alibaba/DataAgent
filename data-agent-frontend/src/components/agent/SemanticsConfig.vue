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
  <!-- todo: 添加分页 -->
  <div style="padding: 20px">
    <div style="margin-bottom: 20px">
      <h2>语义模型管理</h2>
    </div>
    <el-divider />

    <div style="margin-bottom: 30px">
      <el-row style="display: flex; justify-content: space-between; align-items: center">
        <el-col :span="12">
          <h3>语义模型列表</h3>
          <el-button
            v-if="selectedModels.length > 0"
            @click="batchDeleteModels"
            size="default"
            type="danger"
            plain
            :icon="Delete"
            style="margin-left: 10px"
          >
            批量删除 ({{ selectedModels.length }})
          </el-button>
        </el-col>
        <el-col :span="12" style="text-align: right">
          <el-input
            v-model="searchKeyword"
            placeholder="请输入关键词，并按回车搜索"
            style="width: 250px; margin-right: 10px"
            clearable
            @clear="handleSearch"
            @keyup.enter="handleSearch"
            size="large"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button @click="openBatchImportDialog" size="large" type="success" round>
            <el-icon><UploadFilled /></el-icon>
            批量导入
          </el-button>
          <el-button @click="openCreateDialog" size="large" type="primary" round :icon="Plus">
            添加语义模型
          </el-button>
        </el-col>
      </el-row>
    </div>

    <el-table
      :data="semanticModelList"
      style="width: 100%"
      border
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" />
      <el-table-column prop="id" label="ID" min-width="60px" />
      <el-table-column prop="tableName" label="表名" min-width="120px" />
      <el-table-column prop="columnName" label="数据库字段名" min-width="120px" />
      <el-table-column prop="businessName" label="业务名称" min-width="120px" />
      <el-table-column prop="synonyms" label="同义词" min-width="120px" />
      <el-table-column prop="dataType" label="数据类型" min-width="80px" />
      <el-table-column label="状态" min-width="80px">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'info'" round>
            {{ scope.row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="160px">
        <template #default="scope">
          {{ formatDateTime(scope.row.createdTime || scope.row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="180px">
        <template #default="scope">
          <el-button @click="editModel(scope.row)" size="small" type="primary" round plain>
            编辑
          </el-button>
          <el-button
            v-if="scope.row.status === 0"
            @click="toggleStatus(scope.row, 1)"
            size="small"
            type="success"
            round
            plain
          >
            启用
          </el-button>
          <el-button
            v-else
            @click="toggleStatus(scope.row, 0)"
            size="small"
            type="warning"
            round
            plain
          >
            停用
          </el-button>
          <el-button @click="deleteModel(scope.row)" size="small" type="danger" round plain>
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <!-- 添加/编辑语义模型Dialog -->
  <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑语义模型' : '添加语义模型'" width="800">
    <el-form :model="modelForm" label-width="120px" ref="modelFormRef">
      <el-form-item label="表名" prop="tableName" required>
        <el-input v-model="modelForm.tableName" placeholder="请输入表名" />
      </el-form-item>

      <el-form-item label="数据库字段名" prop="columnName" required>
        <el-input v-model="modelForm.columnName" placeholder="请输入数据库字段名" />
      </el-form-item>

      <el-form-item label="业务名称" prop="businessName" required>
        <el-input v-model="modelForm.businessName" placeholder="请输入业务名称" />
      </el-form-item>

      <el-form-item label="同义词" prop="synonyms">
        <el-input
          v-model="modelForm.synonyms"
          type="textarea"
          :rows="2"
          placeholder="请输入同义词，多个同义词用逗号分隔"
        />
      </el-form-item>

      <el-form-item label="业务描述" prop="businessDescription">
        <el-input
          v-model="modelForm.businessDescription"
          type="textarea"
          :rows="3"
          placeholder="请输入业务描述，用于向LLM解释字段的业务含义"
        />
      </el-form-item>

      <el-form-item label="字段注释" prop="columnComment">
        <el-input
          v-model="modelForm.columnComment"
          type="textarea"
          :rows="2"
          placeholder="请输入数据库字段的原始注释"
        />
      </el-form-item>

      <el-form-item label="数据类型" prop="dataType" required>
        <el-input v-model="modelForm.dataType" placeholder="请输入数据类型，如：int, varchar(20)" />
      </el-form-item>
    </el-form>

    <template #footer>
      <div style="text-align: right">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveModel">
          {{ isEdit ? '更新' : '创建' }}
        </el-button>
      </div>
    </template>
  </el-dialog>

  <!-- 批量导入Dialog -->
  <BatchImportDialog
    v-model="batchImportDialogVisible"
    title="批量导入语义模型"
    :json-template="jsonTemplate"
    :validate-json="validateJson"
    :validate-excel-file="validateExcelFile"
    :on-json-import="executeBatchImport"
    :on-excel-import="executeExcelImport"
    :on-download-excel-template="downloadExcelTemplate"
    @imported="handleBatchImported"
  />
</template>

<script lang="ts">
  import { defineComponent, ref, onMounted, Ref } from 'vue';
  import { Plus, Search, UploadFilled, Delete } from '@element-plus/icons-vue';
  import BatchImportDialog from './BatchImportDialog.vue';
  import semanticModelService, {
    SemanticModel,
    SemanticModelAddDto,
    SemanticModelImportItem,
  } from '@/services/semanticModel';
  import { ElMessage, ElMessageBox } from 'element-plus';

  export default defineComponent({
    name: 'AgentSemanticsConfig',
    components: {
      UploadFilled,
      Search,
      BatchImportDialog,
    },
    props: {
      agentId: {
        type: Number,
        required: true,
      },
    },
    setup(props) {
      const semanticModelList: Ref<SemanticModel[]> = ref([]);
      const dialogVisible: Ref<boolean> = ref(false);
      const isEdit: Ref<boolean> = ref(false);
      const searchKeyword: Ref<string> = ref('');
      const selectedModels: Ref<SemanticModel[]> = ref([]);
      const modelForm: Ref<SemanticModel> = ref({
        tableName: '',
        columnName: '',
        businessName: '',
        synonyms: '',
        businessDescription: '',
        columnComment: '',
        dataType: '',
        status: 1,
        agentId: props.agentId,
      } as SemanticModel);

      const currentEditId: Ref<number | null> = ref(null);

      const openCreateDialog = () => {
        isEdit.value = false;
        modelForm.value = {
          tableName: '',
          columnName: '',
          businessName: '',
          synonyms: '',
          businessDescription: '',
          columnComment: '',
          dataType: '',
          status: 1,
          agentId: props.agentId,
        } as SemanticModel;
        dialogVisible.value = true;
      };

      // 处理表格选择变化
      const handleSelectionChange = (selection: SemanticModel[]) => {
        selectedModels.value = selection;
      };

      // 批量删除语义模型
      const batchDeleteModels = async () => {
        if (selectedModels.value.length === 0) {
          ElMessage.warning('请先选择要删除的语义模型');
          return;
        }

        try {
          await ElMessageBox.confirm(
            `确定要删除选中的 ${selectedModels.value.length} 个语义模型吗？`,
            '确认批量删除',
            {
              confirmButtonText: '确定',
              cancelButtonText: '取消',
              type: 'warning',
            },
          );

          const ids = selectedModels.value
            .map(model => model.id)
            .filter(id => id !== undefined) as number[];
          const result = await semanticModelService.batchDelete(ids);
          if (result) {
            ElMessage.success(`成功删除 ${ids.length} 个语义模型`);
            selectedModels.value = [];
            await loadSemanticModels();
          } else {
            ElMessage.error('批量删除失败');
          }
        } catch {
          // 用户取消操作时不显示错误消息
        }
      };

      // 处理搜索
      const handleSearch = () => {
        loadSemanticModels();
      };

      // 加载语义模型列表
      const loadSemanticModels = async () => {
        try {
          semanticModelList.value = await semanticModelService.list(
            props.agentId,
            searchKeyword.value || undefined,
          );
        } catch (error) {
          ElMessage.error('加载语义模型列表失败');
          console.error('Failed to load semantic models:', error);
        }
      };

      // 编辑语义模型
      const editModel = (model: SemanticModel) => {
        isEdit.value = true;
        currentEditId.value = model.id || null;
        modelForm.value = { ...model };
        dialogVisible.value = true;
      };

      // 删除语义模型
      const deleteModel = async (model: SemanticModel) => {
        if (!model.id) return;

        try {
          await ElMessageBox.confirm(
            `确定要删除语义模型 "${model.businessName}" 吗？`,
            '确认删除',
            {
              confirmButtonText: '确定',
              cancelButtonText: '取消',
              type: 'warning',
            },
          );

          const result = await semanticModelService.delete(model.id);
          if (result) {
            ElMessage.success('删除成功');
            await loadSemanticModels();
          } else {
            ElMessage.error('删除失败');
          }
        } catch {
          // 用户取消操作时不显示错误消息
        }
      };

      // 切换状态
      const toggleStatus = async (model: SemanticModel, status: number) => {
        if (!model.id) return;

        try {
          const ids = [model.id];
          let result = false;

          if (status === 1) {
            result = await semanticModelService.enable(ids);
          } else {
            result = await semanticModelService.disable(ids);
          }

          if (result) {
            ElMessage.success(`${status === 1 ? '启用' : '停用'}成功`);
            model.status = status;
          } else {
            ElMessage.error(`${status === 1 ? '启用' : '停用'}失败`);
          }
        } catch (error) {
          ElMessage.error(`${status === 1 ? '启用' : '停用'}失败`);
          console.error('Failed to toggle status:', error);
        }
      };

      // 保存语义模型
      const saveModel = async () => {
        try {
          if (isEdit.value && currentEditId.value) {
            const formData: SemanticModel = {
              ...modelForm.value,
              id: currentEditId.value,
            };
            const result = await semanticModelService.update(currentEditId.value, formData);
            if (result) {
              ElMessage.success('更新成功');
            } else {
              ElMessage.error('更新失败');
              return;
            }
          } else {
            const formData: SemanticModelAddDto = {
              agentId: props.agentId,
              tableName: modelForm.value.tableName,
              columnName: modelForm.value.columnName,
              businessName: modelForm.value.businessName,
              synonyms: modelForm.value.synonyms,
              businessDescription: modelForm.value.businessDescription,
              columnComment: modelForm.value.columnComment,
              dataType: modelForm.value.dataType,
            };
            const result = await semanticModelService.create(formData);
            if (result) {
              ElMessage.success('创建成功');
            } else {
              ElMessage.error('创建失败');
              return;
            }
          }

          dialogVisible.value = false;
          await loadSemanticModels();
        } catch (error) {
          ElMessage.error(`${isEdit.value ? '更新' : '创建'}失败`);
          console.error('Failed to save model:', error);
        }
      };

      onMounted(() => {
        loadSemanticModels();
      });

      // 批量导入相关状态
      const batchImportDialogVisible: Ref<boolean> = ref(false);
      const jsonTemplate = [
        {
          tableName: 'work_order',
          columnName: 'order_type',
          businessName: '工单类型',
          synonyms: '类型,工单种类',
          businessDesc: '用于区分工单种类。枚举值：1=资产工单, 2=账号工单',
          dataType: 'int',
        },
        {
          tableName: 'work_order',
          columnName: 'status',
          businessName: '工单状态',
          synonyms: '状态,处理状态',
          businessDesc: '工单当前处理状态。枚举值：0=待处理 1=处理中 2=已完成 3=已关闭',
          dataType: 'int',
        },
      ];

      // 打开批量导入对话框
      const openBatchImportDialog = () => {
        batchImportDialogVisible.value = true;
      };

      // 验证JSON格式
      const validateJson = (jsonText: string) => {
        try {
          const data = JSON.parse(jsonText);
          if (!Array.isArray(data)) {
            ElMessage.error('JSON格式错误：数据必须是数组');
            return false;
          }
          if (data.length === 0) {
            ElMessage.error('导入数据不能为空');
            return false;
          }
          // 验证必填字段
          for (let i = 0; i < data.length; i++) {
            const item = data[i];
            if (!item.tableName || !item.columnName || !item.businessName || !item.dataType) {
              ElMessage.error(
                `第${i + 1}条记录缺少必填字段（tableName, columnName, businessName, dataType）`,
              );
              return false;
            }
          }
          ElMessage.success('JSON格式验证通过');
          return true;
        } catch (error) {
          ElMessage.error('JSON格式错误：' + (error as Error).message);
          return false;
        }
      };

      // 执行批量导入
      const executeBatchImport = async (items: SemanticModelImportItem[]) => {
        try {
          const result = await semanticModelService.batchImport({
            agentId: props.agentId,
            items,
          });
          return result;
        } catch (error) {
          ElMessage.error('批量导入失败：' + (error as Error).message);
          console.error('Failed to batch import:', error);
          throw error;
        }
      };
      const validateExcelFile = (file: File | null) => {
        if (!file) {
          ElMessage.error('请先选择Excel文件');
          return false;
        }
        return true;
      };

      // 下载Excel模板
      const downloadExcelTemplate = async () => {
        try {
          await semanticModelService.downloadTemplate();
          ElMessage.success('模板下载成功');
        } catch (error) {
          ElMessage.error('模板下载失败：' + (error as Error).message);
          console.error('Failed to download template:', error);
        }
      };

      // 执行Excel导入
      const executeExcelImport = async (file: File) => {
        try {
          const result = await semanticModelService.importExcel(file, props.agentId);
          return result;
        } catch (error) {
          ElMessage.error('Excel导入失败：' + (error as Error).message);
          console.error('Failed to import excel:', error);
          throw error;
        }
      };
      const handleBatchImported = async () => {
        await loadSemanticModels();
      };

      // 格式化日期时间
      const formatDateTime = (dateTime: string | undefined) => {
        if (!dateTime) return '-';
        try {
          const date = new Date(dateTime);
          return date.toLocaleString('zh-CN', {
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
      };

      return {
        Plus,
        Search,
        Delete,
        semanticModelList,
        dialogVisible,
        isEdit,
        searchKeyword,
        selectedModels,
        modelForm,
        openCreateDialog,
        handleSelectionChange,
        batchDeleteModels,
        editModel,
        deleteModel,
        toggleStatus,
        saveModel,
        handleSearch,
        // 批量导入相关
        batchImportDialogVisible,
        jsonTemplate,
        openBatchImportDialog,
        validateJson,
        validateExcelFile,
        executeBatchImport,
        downloadExcelTemplate,
        executeExcelImport,
        handleBatchImported,
        // 工具函数
        formatDateTime,
      };
    },
  });
</script>

<style scoped></style>
