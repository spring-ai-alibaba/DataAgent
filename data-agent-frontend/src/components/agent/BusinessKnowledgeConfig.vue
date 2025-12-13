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
      <h2>业务知识管理</h2>
    </div>
    <el-divider />

    <div style="margin-bottom: 30px">
      <el-row style="display: flex; justify-content: space-between; align-items: center">
        <el-col :span="12">
          <h3>业务知识列表</h3>
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
          <el-button
            @click="refreshVectorStore"
            v-if="!refreshLoading"
            size="large"
            type="success"
            round
            :icon="Document"
          >
            同步到向量库
          </el-button>
          <el-button v-else size="large" type="success" round loading>同步中...</el-button>
          <el-button @click="openCreateDialog" size="large" type="primary" round :icon="Plus">
            添加知识
          </el-button>
        </el-col>
      </el-row>
    </div>

    <el-table :data="businessKnowledgeList" style="width: 100%" border>
      <el-table-column prop="id" label="ID" min-width="60px" />
      <el-table-column prop="businessTerm" label="业务名词" min-width="120px" />
      <el-table-column prop="description" label="描述" min-width="150px" />
      <el-table-column prop="synonyms" label="同义词" min-width="120px" />
      <el-table-column label="向量化状态" min-width="120px">
        <template #default="scope">
          <el-tag :type="getVectorStatusType(scope.row.embeddingStatus)" round>
            {{ scope.row.embeddingStatus || '未知' }}
            <el-tooltip
              v-if="scope.row.embeddingStatus === 'FAILED' && scope.row.errorMsg"
              :content="scope.row.errorMsg"
              placement="top"
            >
              <el-icon style="margin-left: 4px">
                <Warning />
              </el-icon>
            </el-tooltip>
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="是否召回" min-width="80px">
        <template #default="scope">
          <el-tag :type="scope.row.isRecall ? 'success' : 'info'" round>
            {{ scope.row.isRecall ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdTime" label="创建时间" min-width="120px" />
      <el-table-column label="操作" min-width="180px">
        <template #default="scope">
          <el-button @click="editKnowledge(scope.row)" size="small" type="primary" round plain>
            编辑
          </el-button>
          <el-button
            v-if="scope.row.embeddingStatus === 'FAILED'"
            @click="retryEmbedding(scope.row)"
            size="small"
            type="info"
            round
            plain
            :loading="retryLoadingMap[scope.row.id]"
          >
            重试
          </el-button>
          <el-button
            v-if="scope.row.isRecall"
            @click="toggleRecall(scope.row, false)"
            size="small"
            type="warning"
            round
            plain
          >
            取消召回
          </el-button>
          <el-button
            v-else
            @click="toggleRecall(scope.row, true)"
            size="small"
            type="success"
            round
            plain
          >
            设为召回
          </el-button>
          <el-button @click="deleteKnowledge(scope.row)" size="small" type="danger" round plain>
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <!-- 添加/编辑业务知识Dialog -->
  <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑业务知识' : '添加业务知识'" width="800">
    <el-form :model="knowledgeForm" label-width="100px" ref="knowledgeFormRef">
      <el-form-item label="业务名词" prop="businessTerm" required>
        <el-input v-model="knowledgeForm.businessTerm" placeholder="请输入业务名词" />
      </el-form-item>

      <el-form-item label="描述" prop="description" required>
        <el-input
          v-model="knowledgeForm.description"
          type="textarea"
          :rows="3"
          placeholder="请输入业务知识描述"
        />
      </el-form-item>

      <el-form-item label="同义词" prop="synonyms">
        <el-input
          v-model="knowledgeForm.synonyms"
          type="textarea"
          :rows="2"
          placeholder="请输入同义词，多个同义词用逗号分隔"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div style="text-align: right">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="saveKnowledge"
          :loading="saveLoading"
          :disabled="saveLoading"
        >
          {{ isEdit ? '更新' : '创建' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script lang="ts">
  import { defineComponent, ref, onMounted, Ref } from 'vue';
  import { Plus, Search, Document, Warning } from '@element-plus/icons-vue';
  import businessKnowledgeService, {
    BusinessKnowledgeVO,
    CreateBusinessKnowledgeDTO,
    UpdateBusinessKnowledgeDTO,
  } from '@/services/businessKnowledge';
  import { ElMessage, ElMessageBox } from 'element-plus';

  export default defineComponent({
    name: 'AgentKnowledgeConfig',
    components: {
      Search,
      Warning,
    },
    props: {
      agentId: {
        type: Number,
        required: true,
      },
    },
    setup(props) {
      const businessKnowledgeList: Ref<BusinessKnowledgeVO[]> = ref([]);
      const dialogVisible: Ref<boolean> = ref(false);
      const isEdit: Ref<boolean> = ref(false);
      const searchKeyword: Ref<string> = ref('');
      const knowledgeForm: Ref<BusinessKnowledgeVO> = ref({
        businessTerm: '',
        description: '',
        synonyms: '',
        isRecall: false,
      } as BusinessKnowledgeVO);

      const currentEditId: Ref<number | null> = ref(null);
      const refreshLoading: Ref<boolean> = ref(false);
      const saveLoading: Ref<boolean> = ref(false);
      const retryLoadingMap: Ref<Record<number, boolean>> = ref({});

      const openCreateDialog = () => {
        isEdit.value = false;
        dialogVisible.value = true;
      };

      // 处理搜索
      const handleSearch = () => {
        loadBusinessKnowledge();
      };

      // 加载业务知识列表
      const loadBusinessKnowledge = async () => {
        try {
          businessKnowledgeList.value = await businessKnowledgeService.list(
            props.agentId,
            searchKeyword.value || undefined,
          );
        } catch (error) {
          ElMessage.error('加载业务知识列表失败');
          console.error('Failed to load business knowledge:', error);
        }
      };

      // 编辑业务知识
      const editKnowledge = (knowledge: BusinessKnowledgeVO) => {
        isEdit.value = true;
        currentEditId.value = knowledge.id || null;
        knowledgeForm.value = { ...knowledge };
        dialogVisible.value = true;
      };

      // 删除业务知识
      const deleteKnowledge = async (knowledge: BusinessKnowledgeVO) => {
        if (!knowledge.id) return;

        try {
          await ElMessageBox.confirm(
            `确定要删除业务知识 "${knowledge.businessTerm}" 吗？`,
            '确认删除',
            {
              confirmButtonText: '确定',
              cancelButtonText: '取消',
              type: 'warning',
            },
          );

          const result = await businessKnowledgeService.delete(knowledge.id);
          if (result) {
            ElMessage.success('删除成功');
            await loadBusinessKnowledge();
          } else {
            ElMessage.error('删除失败');
          }
        } catch {
          // 用户取消操作时不显示错误消息
        }
      };

      // 切换召回状态
      const toggleRecall = async (knowledge: BusinessKnowledgeVO, isRecall: boolean) => {
        if (!knowledge.id) return;

        try {
          const result = await businessKnowledgeService.recallKnowledge(knowledge.id, isRecall);
          if (result) {
            ElMessage.success(`${isRecall ? '设为召回' : '取消召回'}成功`);
            knowledge.isRecall = isRecall;
          } else {
            ElMessage.error(`${isRecall ? '设为召回' : '取消召回'}失败`);
          }
        } catch (error) {
          ElMessage.error(`${isRecall ? '设为召回' : '取消召回'}失败`);
          console.error('Failed to toggle recall:', error);
        }
      };

      // 保存业务知识
      const saveKnowledge = async () => {
        saveLoading.value = true;
        try {
          if (isEdit.value && currentEditId.value) {
            // 更新操作使用 UpdateBusinessKnowledgeDTO
            const updateData: UpdateBusinessKnowledgeDTO = {
              businessTerm: knowledgeForm.value.businessTerm,
              description: knowledgeForm.value.description,
              synonyms: knowledgeForm.value.synonyms,
              agentId: props.agentId,
            };

            const result = await businessKnowledgeService.update(currentEditId.value, updateData);
            if (result) {
              ElMessage.success('更新成功');
            } else {
              ElMessage.error('更新失败');
              return;
            }
          } else {
            // 创建操作使用 CreateBusinessKnowledgeDTO
            const createData: CreateBusinessKnowledgeDTO = {
              businessTerm: knowledgeForm.value.businessTerm,
              description: knowledgeForm.value.description,
              synonyms: knowledgeForm.value.synonyms,
              isRecall: knowledgeForm.value.isRecall,
              agentId: props.agentId,
            };

            await businessKnowledgeService.create(createData);
            ElMessage.success('创建成功');
          }

          dialogVisible.value = false;
          await loadBusinessKnowledge();
        } catch (error) {
          ElMessage.error(`${isEdit.value ? '更新' : '创建'}失败`);
          console.error('Failed to save knowledge:', error);
        } finally {
          saveLoading.value = false;
        }
      };

      // 刷新向量存储
      const refreshVectorStore = async () => {
        try {
          await ElMessageBox.confirm(
            '如果所有向量状态正常，即无需同步。确定要清除现有数据并开始重新同步吗？',
            '确认同步',
            {
              confirmButtonText: '确定',
              cancelButtonText: '取消',
              type: 'warning',
            },
          );

          refreshLoading.value = true;
          const result = await businessKnowledgeService.refreshAllKnowledgeToVectorStore(
            props.agentId.toString(),
          );
          if (result) {
            ElMessage.success('同步到向量库成功');
          } else {
            ElMessage.error('同步到向量库失败');
          }
        } catch (error) {
          if (error !== 'cancel') {
            ElMessage.error('同步到向量库失败');
            console.error('Failed to refresh vector store:', error);
          }
        } finally {
          refreshLoading.value = false;
        }
      };

      // 重试向量化
      const retryEmbedding = async (knowledge: BusinessKnowledgeVO) => {
        if (!knowledge.id) return;

        try {
          // 设置加载状态为true
          retryLoadingMap.value[knowledge.id] = true;

          const result = await businessKnowledgeService.retryEmbedding(knowledge.id);
          if (result) {
            ElMessage.success('重试向量化成功');
            // 刷新列表以更新状态
            await loadBusinessKnowledge();
          } else {
            ElMessage.error('重试向量化失败');
          }
        } catch (error) {
          ElMessage.error('重试向量化失败');
          console.error('Failed to retry vectorization:', error);
        } finally {
          // 无论成功还是失败，都将加载状态设置为false
          retryLoadingMap.value[knowledge.id] = false;
        }
      };

      // 获取向量化状态对应的标签类型
      const getVectorStatusType = (status?: string): string => {
        switch (status) {
          case 'COMPLETED':
            return 'success';
          case 'FAILED':
            return 'danger';
          case 'PENDING':
            return 'warning';
          case 'PROCESSING':
            return 'primary';
          default:
            return 'info';
        }
      };

      onMounted(() => {
        loadBusinessKnowledge();
      });

      return {
        Plus,
        Search,
        Document,
        businessKnowledgeList,
        dialogVisible,
        isEdit,
        searchKeyword,
        knowledgeForm,
        refreshLoading,
        saveLoading,
        retryLoadingMap,
        openCreateDialog,
        editKnowledge,
        deleteKnowledge,
        toggleRecall,
        saveKnowledge,
        handleSearch,
        refreshVectorStore,
        retryEmbedding,
        getVectorStatusType,
      };
    },
  });
</script>

<style scoped></style>
