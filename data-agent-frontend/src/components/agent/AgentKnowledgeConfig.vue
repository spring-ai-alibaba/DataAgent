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
      <h2>智能体知识库</h2>
      <p style="color: #909399; font-size: 14px; margin-top: 5px">
        管理用于增强智能体能力的知识源。
      </p>
    </div>
    <el-divider />

    <div style="margin-bottom: 30px">
      <el-row style="display: flex; justify-content: space-between; align-items: center">
        <el-col :span="12">
          <h3>知识列表</h3>
        </el-col>
        <el-col :span="12" style="text-align: right">
          <el-input
            v-model="queryParams.title"
            placeholder="请输入知识标题搜索"
            style="width: 400px; margin-right: 10px"
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
            @click="toggleFilter"
            size="large"
            :type="filterVisible ? 'primary' : ''"
            round
            :icon="FilterIcon"
          >
            筛选
          </el-button>
          <el-button @click="openCreateDialog" size="large" type="primary" round :icon="Plus">
            添加知识
          </el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 筛选面板 -->
    <el-collapse-transition>
      <div v-show="filterVisible" style="margin-bottom: 20px">
        <el-card shadow="never">
          <el-form :inline="true" :model="queryParams">
            <el-form-item label="知识类型">
              <el-select
                v-model="queryParams.type"
                placeholder="全部类型"
                clearable
                @change="handleSearch"
                style="width: 150px"
              >
                <el-option label="文档" value="DOCUMENT" />
                <el-option label="问答对" value="QA" />
                <el-option label="常见问题" value="FAQ" />
              </el-select>
            </el-form-item>
            <el-form-item label="处理状态">
              <el-select
                v-model="queryParams.embeddingStatus"
                placeholder="全部状态"
                clearable
                @change="handleSearch"
                style="width: 150px"
              >
                <el-option label="COMPLETED" value="COMPLETED" />
                <el-option label="PROCESSING" value="PROCESSING" />
                <el-option label="FAILED" value="FAILED" />
                <el-option label="PENDING" value="PENDING" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button @click="clearFilters" :icon="RefreshLeft">清空筛选</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </el-collapse-transition>

    <!-- 表格区域 -->
    <el-table :data="knowledgeList" style="width: 100%" border v-loading="loading">
      <el-table-column prop="title" label="标题" min-width="150px" />
      <el-table-column prop="type" label="类型" min-width="100px">
        <template #default="scope">
          <span v-if="scope.row.type === 'DOCUMENT'">文档</span>
          <span v-else-if="scope.row.type === 'QA'">问答对</span>
          <span v-else-if="scope.row.type === 'FAQ'">常见问题</span>
          <span v-else>{{ scope.row.type }}</span>
        </template>
      </el-table-column>
      <el-table-column label="分块策略" min-width="100px">
        <template #default="scope">
          <el-tag v-if="scope.row.splitterType === 'token'" type="primary" size="small" round>
            Token
          </el-tag>
          <el-tag
            v-else-if="scope.row.splitterType === 'recursive'"
            type="success"
            size="small"
            round
          >
            递归
          </el-tag>
          <el-tag
            v-else-if="scope.row.splitterType === 'sentence'"
            type="warning"
            size="small"
            round
          >
            句子
          </el-tag>
          <el-tag
            v-else-if="scope.row.splitterType === 'paragraph'"
            type="success"
            size="small"
            round
          >
            段落
          </el-tag>
          <el-tag v-else-if="scope.row.splitterType === 'semantic'" type="info" size="small" round>
            语义
          </el-tag>
          <span v-else style="color: #909399; font-size: 12px">-</span>
        </template>
      </el-table-column>
      <el-table-column label="处理状态" min-width="120px">
        <template #default="scope">
          <el-tag v-if="scope.row.embeddingStatus === 'COMPLETED'" type="success" round>
            {{ scope.row.embeddingStatus }}
          </el-tag>
          <el-tag v-else-if="scope.row.embeddingStatus === 'PROCESSING'" type="primary" round>
            {{ scope.row.embeddingStatus }}
          </el-tag>
          <el-tag v-else-if="scope.row.embeddingStatus === 'FAILED'" type="danger" round>
            <el-tooltip v-if="scope.row.errorMsg" :content="scope.row.errorMsg" placement="top">
              <span style="display: flex; align-items: center">
                <el-icon style="margin-right: 4px"><Warning /></el-icon>
                {{ scope.row.embeddingStatus }}
              </span>
            </el-tooltip>
            <span v-else>{{ scope.row.embeddingStatus }}</span>
          </el-tag>
          <el-tag v-else type="info" round>
            {{ scope.row.embeddingStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="召回状态" min-width="100px">
        <template #default="scope">
          <el-tag :type="scope.row.isRecall ? 'success' : 'info'" round>
            {{ scope.row.isRecall ? '已召回' : '未召回' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="280px">
        <template #default="scope">
          <el-button @click="editKnowledge(scope.row)" size="small" type="primary" round plain>
            管理
          </el-button>
          <el-button
            v-if="scope.row.embeddingStatus === 'FAILED'"
            @click="handleRetry(scope.row)"
            size="small"
            type="info"
            round
            plain
          >
            重试
          </el-button>
          <el-button
            v-if="scope.row.isRecall"
            @click="toggleStatus(scope.row)"
            size="small"
            type="warning"
            round
            plain
          >
            取消召回
          </el-button>
          <el-button
            v-else
            @click="toggleStatus(scope.row)"
            size="small"
            type="success"
            round
            plain
          >
            召回
          </el-button>
          <el-button @click="deleteKnowledge(scope.row)" size="small" type="danger" round plain>
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页组件 -->
    <div style="margin-top: 20px; display: flex; justify-content: flex-end">
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>

  <!-- 添加/编辑知识弹窗 -->
  <el-dialog
    v-model="dialogVisible"
    :title="isEdit ? '编辑知识' : '添加新知识'"
    width="800"
    :close-on-click-modal="false"
  >
    <el-form :model="knowledgeForm" label-width="100px" ref="knowledgeFormRef">
      <!-- 知识类型 -->
      <el-form-item label="知识类型" prop="type" required>
        <el-select
          v-model="knowledgeForm.type"
          placeholder="请选择知识类型"
          @change="handleTypeChange"
          :disabled="isEdit"
          style="width: 100%"
        >
          <el-option label="文档 (文件上传)" value="DOCUMENT" />
          <el-option label="问答对 (Q&A)" value="QA" />
          <el-option label="常见问题 (FAQ)" value="FAQ" />
        </el-select>
      </el-form-item>

      <!-- 知识类型说明 -->
      <el-form-item v-if="knowledgeForm.type === 'QA'">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px">
          <template #title>
            <div style="line-height: 1.6">
              请录入具体的'分析需求'作为问题,并在答案中写出详细的'思考步骤'与'数据查找逻辑'(而非直接给结果),以此教会
              AI 如何拆解任务。
            </div>
          </template>
        </el-alert>
      </el-form-item>

      <el-form-item v-if="knowledgeForm.type === 'FAQ'">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px">
          <template #title>
            <div style="line-height: 1.6">
              请针对特定的'业务术语'、'指标口径'或'常见歧义'进行提问和定义(例如:'什么是有效日活'),以此统一
              AI 的判断标准。
            </div>
          </template>
        </el-alert>
      </el-form-item>

      <el-form-item v-if="knowledgeForm.type === 'DOCUMENT'">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px">
          <template #title>
            <div style="line-height: 1.6">
              请上传完整的'数据库表结构'、'码表映射字典'或'业务背景说明',供 AI
              在分析时检索字段含义和数据关系。
            </div>
          </template>
        </el-alert>
      </el-form-item>

      <!-- 知识标题 -->
      <el-form-item label="知识标题" prop="title" required>
        <el-input v-model="knowledgeForm.title" placeholder="为这份知识起一个易于识别的名称" />
      </el-form-item>

      <!-- 分块策略选择 (仅文档类型) -->
      <el-form-item
        v-if="knowledgeForm.type === 'DOCUMENT' && !isEdit"
        label="分块策略"
        prop="splitterType"
      >
        <el-select
          v-model="knowledgeForm.splitterType"
          placeholder="请选择分块策略"
          style="width: 100%"
        >
          <el-option label="Token 分块" value="token" />
          <el-option label="递归分块" value="recursive" />
          <el-option label="句子分块" value="sentence" />
          <el-option label="段落分块" value="paragraph" />
          <el-option label="语义分块" value="semantic" />
        </el-select>
        <div style="margin-top: 8px; font-size: 12px; color: #909399">
          <div v-if="knowledgeForm.splitterType === 'token'">
            ⚡ 速度最快，按固定 token 数切分，适合代码和日志
          </div>
          <div v-else-if="knowledgeForm.splitterType === 'recursive'">
            📚 平衡之选，保留文档结构（段落、章节），适合技术文档
          </div>
          <div v-else-if="knowledgeForm.splitterType === 'sentence'">
            ✨ 保证句子完整性，语义不被截断，适合新闻和文章
          </div>
          <div v-else-if="knowledgeForm.splitterType === 'paragraph'">
            📝 按自然段落分块，保留段落完整性，适合博客、书籍等
          </div>
          <div v-else-if="knowledgeForm.splitterType === 'semantic'">
            🧠 基于语义相似度智能分块，自动识别主题边界，适合论文和长文（会产生 embedding API
            调用成本）
          </div>
        </div>
      </el-form-item>

      <!-- 文件上传区域 -->
      <el-form-item v-if="knowledgeForm.type === 'DOCUMENT'" label="上传文件" required>
        <div v-if="!isEdit" style="width: 100%">
          <el-upload
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="() => (fileList = [])"
            :file-list="fileList"
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到此处或
              <em>点击选择文件</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持 PDF, DOCX, TXT, MD
                等格式(注意：PDT,TXT等纯文本文件如果不是UTF-8编码可能导致读取失败)
              </div>
              <div v-if="fileList.length > 0" class="el-upload__tip" style="color: #409eff">
                文件大小: {{ formatFileSize(fileList[0].size) }}
              </div>
            </template>
          </el-upload>
        </div>
        <div v-else>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="文档类型知识不支持修改文件内容，如需修改请删除后重新创建"
          />
        </div>
      </el-form-item>

      <!-- Q&A / FAQ 输入区域 -->
      <template v-if="knowledgeForm.type === 'QA' || knowledgeForm.type === 'FAQ'">
        <el-form-item label="问题" prop="question" required>
          <el-input
            v-model="knowledgeForm.question"
            type="textarea"
            :rows="2"
            placeholder="输入用户可能会问的问题..."
          />
        </el-form-item>
        <el-form-item label="答案" prop="answer" required>
          <el-input
            v-model="knowledgeForm.answer"
            type="textarea"
            :rows="5"
            placeholder="输入标准答案..."
          />
        </el-form-item>
      </template>
    </el-form>

    <template #footer>
      <div style="text-align: right">
        <el-button @click="closeDialog">取消</el-button>
        <el-button type="primary" @click="saveKnowledge" :loading="saveLoading">
          {{ isEdit ? '更新' : '添加并处理' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script lang="ts">
  import { defineComponent, ref, onMounted, Ref, reactive } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import {
    Plus,
    Search,
    Filter as FilterIcon,
    RefreshLeft,
    UploadFilled,
    Warning,
  } from '@element-plus/icons-vue';
  import axios from 'axios';
  import agentKnowledgeService, {
    AgentKnowledge,
    AgentKnowledgeQueryDTO,
  } from '@/services/agentKnowledge';

  export default defineComponent({
    name: 'AgentKnowledgeConfig',
    components: {
      Search,
      Warning,
      UploadFilled,
    },
    props: {
      agentId: {
        type: Number,
        required: true,
      },
    },
    setup(props) {
      const knowledgeList: Ref<AgentKnowledge[]> = ref([]);
      const total: Ref<number> = ref(0);
      const loading: Ref<boolean> = ref(false);
      const dialogVisible: Ref<boolean> = ref(false);
      const isEdit: Ref<boolean> = ref(false);
      const saveLoading: Ref<boolean> = ref(false);
      const currentEditId: Ref<number | null> = ref(null);
      const fileList: Ref<{ name: string; size: number; raw: File }[]> = ref([]);
      const filterVisible: Ref<boolean> = ref(false);

      // 查询参数
      const queryParams = reactive<AgentKnowledgeQueryDTO>({
        agentId: props.agentId,
        title: '',
        type: '',
        embeddingStatus: '',
        pageNum: 1,
        pageSize: 10,
      });

      // 表单数据
      const knowledgeForm: Ref<
        AgentKnowledge & { question?: string; answer?: string; file?: File; splitterType?: string }
      > = ref({
        agentId: props.agentId,
        title: '',
        content: '',
        type: 'DOCUMENT',
        isRecall: true,
        question: '',
        answer: '',
        splitterType: 'recursive', // 默认使用递归分块
      } as AgentKnowledge & { question?: string; answer?: string; splitterType?: string });

      // 切换筛选面板
      const toggleFilter = () => {
        filterVisible.value = !filterVisible.value;
      };

      // 清空筛选条件
      const clearFilters = () => {
        queryParams.type = '';
        queryParams.embeddingStatus = '';
        handleSearch();
      };

      // 加载知识列表
      const loadKnowledgeList = async () => {
        loading.value = true;
        try {
          const queryDTO = {
            ...queryParams,
            type: queryParams.type ? queryParams.type : '',
            embeddingStatus: queryParams.embeddingStatus ? queryParams.embeddingStatus : '',
          };
          const result = await agentKnowledgeService.queryByPage(queryDTO);
          if (result.success) {
            knowledgeList.value = result.data;
            total.value = result.total;
          } else {
            ElMessage.error(result.message || '加载知识列表失败');
          }
        } catch (error) {
          ElMessage.error('加载知识列表失败');
          console.error('Failed to load knowledge list:', error);
        } finally {
          loading.value = false;
        }
      };

      // 搜索
      const handleSearch = () => {
        queryParams.pageNum = 1;
        loadKnowledgeList();
      };

      // 分页处理
      const handleSizeChange = (val: number) => {
        queryParams.pageSize = val;
        loadKnowledgeList();
      };

      const handleCurrentChange = (val: number) => {
        queryParams.pageNum = val;
        loadKnowledgeList();
      };

      // 打开创建对话框
      const openCreateDialog = () => {
        isEdit.value = false;
        dialogVisible.value = true;
        resetForm();
      };

      // 关闭对话框
      const closeDialog = () => {
        dialogVisible.value = false;
        resetForm();
      };

      // 编辑知识
      const editKnowledge = (knowledge: AgentKnowledge) => {
        isEdit.value = true;
        currentEditId.value = knowledge.id || null;
        knowledgeForm.value = {
          ...knowledge,
          type: knowledge.type,
        };

        if (knowledge.type === 'QA' || knowledge.type === 'FAQ') {
          knowledgeForm.value.answer = knowledge.content;
        }

        dialogVisible.value = true;
      };

      // 切换状态（召回/取消召回）
      const toggleStatus = (knowledge: AgentKnowledge) => {
        if (!knowledge.id) return;
        const newStatus = !knowledge.isRecall;
        const actionName = newStatus ? '召回' : '取消召回';

        ElMessageBox.confirm(`确定要${actionName}知识 "${knowledge.title}" 吗？`, '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        })
          .then(async () => {
            try {
              const result = await agentKnowledgeService.updateRecallStatus(
                knowledge.id!,
                newStatus,
              );
              if (result) {
                knowledge.isRecall = newStatus;
                ElMessage.success(`${actionName}成功`);
              } else {
                ElMessage.error(`${actionName}失败`);
              }
            } catch (error) {
              ElMessage.error(`${actionName}失败`);
              console.error(`Failed to ${actionName} knowledge:`, error);
            }
          })
          .catch(() => {});
      };

      // 重试向量化
      const handleRetry = async (knowledge: AgentKnowledge) => {
        if (!knowledge.id) return;
        try {
          const success = await agentKnowledgeService.retryEmbedding(knowledge.id);
          if (success) {
            ElMessage.success('重试请求已发送');
            loadKnowledgeList();
          } else {
            ElMessage.error('重试失败');
          }
        } catch (error) {
          ElMessage.error('重试失败');
        }
      };

      // 删除知识
      const deleteKnowledge = (knowledge: AgentKnowledge) => {
        if (!knowledge.id) return;

        ElMessageBox.confirm(`确定要删除知识 "${knowledge.title}" 吗？`, '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        })
          .then(async () => {
            try {
              const result = await agentKnowledgeService.delete(knowledge.id!);
              if (result) {
                ElMessage.success('删除成功');
                await loadKnowledgeList();
              } else {
                ElMessage.error('删除失败');
              }
            } catch (error) {
              ElMessage.error('删除失败');
              console.error('Failed to delete knowledge:', error);
            }
          })
          .catch(() => {});
      };

      // 处理类型变化
      const handleTypeChange = () => {
        knowledgeForm.value.content = '';
        knowledgeForm.value.question = '';
        knowledgeForm.value.answer = '';
        fileList.value = [];
      };

      // 处理文件变化
      const handleFileChange = (file: { name: string; size: number; raw: File }) => {
        fileList.value = [file];
        knowledgeForm.value.file = file.raw;
      };

      // 格式化文件大小
      const formatFileSize = (bytes: number): string => {
        if (!bytes) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
      };

      // 保存知识
      const saveKnowledge = async () => {
        // 表单验证
        if (!knowledgeForm.value.title || !knowledgeForm.value.title.trim()) {
          ElMessage.warning('请输入知识标题');
          return;
        }

        if (knowledgeForm.value.type === 'DOCUMENT') {
          if (!isEdit.value && !knowledgeForm.value.file && fileList.value.length === 0) {
            ElMessage.warning('请上传文件');
            return;
          }
        } else if (knowledgeForm.value.type === 'QA' || knowledgeForm.value.type === 'FAQ') {
          if (!knowledgeForm.value.question || !knowledgeForm.value.question.trim()) {
            ElMessage.warning('请输入问题');
            return;
          }
          if (!knowledgeForm.value.answer || !knowledgeForm.value.answer.trim()) {
            ElMessage.warning('请输入答案');
            return;
          }
          knowledgeForm.value.content = knowledgeForm.value.answer;
        }

        saveLoading.value = true;
        try {
          if (isEdit.value && currentEditId.value) {
            const updateData = {
              ...knowledgeForm.value,
              type: knowledgeForm.value.type?.toUpperCase(),
            };
            const result = await agentKnowledgeService.update(currentEditId.value, updateData);
            if (result) {
              ElMessage.success('更新成功');
            } else {
              ElMessage.error('更新失败');
              return;
            }
          } else {
            const formData = new FormData();
            formData.append('agentId', String(knowledgeForm.value.agentId));
            formData.append('title', knowledgeForm.value.title);
            formData.append('type', knowledgeForm.value.type || 'DOCUMENT');
            formData.append('isRecall', knowledgeForm.value.isRecall ? '1' : '0');

            if (knowledgeForm.value.type === 'DOCUMENT' && knowledgeForm.value.file) {
              formData.append('file', knowledgeForm.value.file);
              // 添加分块策略参数
              if (knowledgeForm.value.splitterType) {
                formData.append('splitterType', knowledgeForm.value.splitterType);
              }
            } else {
              if (knowledgeForm.value.content) {
                formData.append('content', knowledgeForm.value.content);
              }
              if (knowledgeForm.value.question) {
                formData.append('question', knowledgeForm.value.question);
              }
            }

            const response = await axios.post('/api/agent-knowledge/create', formData, {
              headers: {
                'Content-Type': 'multipart/form-data',
              },
            });

            if (response.data.success) {
              ElMessage.success('创建成功');
            } else {
              ElMessage.error(response.data.message || '创建失败');
              return;
            }
          }

          dialogVisible.value = false;
          await loadKnowledgeList();
        } catch (error) {
          ElMessage.error(`${isEdit.value ? '更新' : '创建'}失败`);
          console.error('Failed to save knowledge:', error);
        } finally {
          saveLoading.value = false;
        }
      };

      // 重置表单
      const resetForm = () => {
        knowledgeForm.value = {
          agentId: props.agentId,
          title: '',
          content: '',
          type: 'DOCUMENT',
          isRecall: true,
          question: '',
          answer: '',
          splitterType: 'recursive', // 默认使用递归分块
        } as AgentKnowledge & { question?: string; answer?: string; splitterType?: string };
        currentEditId.value = null;
        fileList.value = [];
      };

      onMounted(() => {
        loadKnowledgeList();
      });

      return {
        Plus,
        Search,
        FilterIcon,
        RefreshLeft,
        UploadFilled,
        Warning,
        knowledgeList,
        total,
        loading,
        dialogVisible,
        isEdit,
        saveLoading,
        queryParams,
        knowledgeForm,
        fileList,
        filterVisible,
        toggleFilter,
        clearFilters,
        loadKnowledgeList,
        handleSearch,
        handleSizeChange,
        handleCurrentChange,
        openCreateDialog,
        closeDialog,
        editKnowledge,
        deleteKnowledge,
        saveKnowledge,
        resetForm,
        handleTypeChange,
        handleFileChange,
        toggleStatus,
        handleRetry,
        formatFileSize,
      };
    },
  });
</script>

<style scoped>
  /* 无需额外样式，使用 ElementPlus 默认样式 */
</style>
