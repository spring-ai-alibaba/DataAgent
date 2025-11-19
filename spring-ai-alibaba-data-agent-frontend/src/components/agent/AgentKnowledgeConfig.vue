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
  <div class="p-4 md:p-8 font-sans text-gray-800">
    <!-- 移除外层容器的 shadow 和 rounded，使其融入父容器 -->
    <div class="w-full bg-white">
      <!-- 头部 -->
      <div class="pb-5 border-b border-gray-200 mb-5">
        <h1 class="text-xl font-bold text-gray-800">智能体知识库</h1>
        <p class="text-sm text-gray-500 mt-1">管理用于增强智能体能力的知识源。</p>
      </div>

      <!-- 核心操作区 -->
      <div class="border-b border-gray-100">
        <!-- 第一行：搜索与主操作 (始终显示) -->
        <div class="pb-5 flex gap-3">
          <!-- 搜索框 (自适应宽度) -->
          <div class="relative flex-grow">
            <span class="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">
              <i class="fas fa-search"></i>
            </span>
            <input
              v-model="queryParams.title"
              type="text"
              placeholder="搜索知识标题..."
              class="pl-10 w-full py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-[#5f70e1] focus:border-[#5f70e1] outline-none transition text-gray-800"
              @keyup.enter="handleSearch"
            />
          </div>

          <!-- 筛选开关按钮 (点击切换下方面板) -->
          <button
            @click="toggleFilter"
            :class="[
              'flex-shrink-0 border px-4 py-2 rounded-md transition flex items-center',
              filterVisible
                ? 'bg-[#e8ebff] text-[#5f70e1] border-[#5f70e1]'
                : 'border-gray-300 text-gray-600 hover:bg-gray-50 hover:border-[#5f70e1] hover:text-[#5f70e1]',
            ]"
          >
            <i class="fas fa-filter mr-2"></i>
            筛选
          </button>

          <!-- 添加按钮 -->
          <button
            @click="openCreateDialog"
            class="flex-shrink-0 bg-[#5f70e1] text-white font-medium py-2 px-4 rounded-md hover:bg-[#4c63d2] transition flex items-center"
          >
            <i class="fas fa-plus mr-0 md:mr-2"></i>
            <span class="hidden md:inline">添加知识</span>
          </button>
        </div>

        <!-- 第二行：折叠筛选面板 (默认隐藏) -->
        <div v-show="filterVisible" class="bg-gray-50 px-5 py-4 border-t border-gray-100 mb-5">
          <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
            <!-- 筛选项 1 -->
            <div>
              <label class="block text-xs font-medium text-gray-500 mb-1">知识类型</label>
              <select
                v-model="queryParams.type"
                class="custom-select w-full border border-gray-300 rounded-md py-1.5 px-3 text-sm focus:ring-1 focus:ring-[#5f70e1] outline-none bg-white text-gray-800"
                @change="handleSearch"
              >
                <option value="">全部类型</option>
                <option value="DOCUMENT">文档</option>
                <option value="QA">问答对</option>
                <option value="FAQ">常见问题</option>
              </select>
            </div>

            <!-- 筛选项 3 -->
            <div>
              <label class="block text-xs font-medium text-gray-500 mb-1">处理状态</label>
              <select
                v-model="queryParams.embeddingStatus"
                class="custom-select w-full border border-gray-300 rounded-md py-1.5 px-3 text-sm focus:ring-1 focus:ring-[#5f70e1] outline-none bg-white text-gray-800"
                @change="handleSearch"
              >
                <option value="">全部状态</option>
                <option value="COMPLETED">✅ COMPLETED</option>
                <option value="PROCESSING">⏳ PROCESSING</option>
                <option value="FAILED">❌ FAILED</option>
                <option value="PENDING">PENDING</option>
              </select>
            </div>

            <!-- 操作按钮 -->
            <div class="flex items-end">
              <button
                @click="clearFilters"
                class="text-sm text-gray-500 hover:text-[#5f70e1] py-2 transition"
              >
                <i class="fas fa-undo mr-1"></i>
                清空筛选条件
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 表格区域 -->
      <div class="overflow-x-auto">
        <table class="w-full text-sm text-left text-gray-600">
          <thead class="text-xs text-gray-700 uppercase bg-gray-50 border-b">
            <tr>
              <th class="px-5 py-3">标题</th>
              <th class="px-5 py-3 whitespace-nowrap">类型</th>
              <th class="px-5 py-3 whitespace-nowrap">状态</th>
              <th class="px-5 py-3 whitespace-nowrap">召回状态</th>
              <th class="px-5 py-3 whitespace-nowrap">操作</th>
            </tr>
          </thead>
          <tbody v-if="loading">
            <tr>
              <td colspan="5" class="px-5 py-8 text-center text-gray-400">
                <i class="fas fa-spinner fa-spin mr-2"></i>
                加载中...
              </td>
            </tr>
          </tbody>
          <tbody v-else-if="knowledgeList.length === 0">
            <tr>
              <td colspan="5" class="px-5 py-8 text-center text-gray-400">暂无数据</td>
            </tr>
          </tbody>
          <tbody v-else>
            <tr
              v-for="item in knowledgeList"
              :key="item.id"
              class="bg-white border-b hover:bg-gray-50"
            >
              <td class="px-5 py-4 font-medium text-gray-900 truncate max-w-xs">
                {{ item.title }}
              </td>
              <td class="px-5 py-4">
                <span v-if="item.type === 'DOCUMENT'">文档</span>
                <span v-else-if="item.type === 'QA'">问答对</span>
                <span v-else-if="item.type === 'FAQ'">常见问题</span>
                <span v-else>{{ item.type }}</span>
              </td>
              <td class="px-5 py-4">
                <span
                  v-if="item.embeddingStatus === 'COMPLETED'"
                  class="bg-green-100 text-green-800 text-xs font-medium px-2 py-0.5 rounded-full"
                >
                  {{ item.embeddingStatus }}
                </span>
                <span
                  v-else-if="item.embeddingStatus === 'PROCESSING'"
                  class="bg-blue-100 text-blue-800 text-xs font-medium px-2 py-0.5 rounded-full"
                >
                  {{ item.embeddingStatus }}
                </span>
                <span
                  v-else-if="item.embeddingStatus === 'FAILED'"
                  class="bg-red-100 text-red-800 text-xs font-medium px-2 py-0.5 rounded-full flex items-center gap-1"
                  :title="item.errorMsg"
                >
                  <i class="fas fa-exclamation-circle"></i>
                  {{ item.embeddingStatus }}
                  <button
                    @click="handleRetry(item)"
                    class="ml-1 text-red-600 hover:text-red-800 underline"
                  >
                    [重试]
                  </button>
                </span>
                <span
                  v-else
                  class="bg-gray-100 text-gray-800 text-xs font-medium px-2 py-0.5 rounded-full"
                >
                  {{ item.embeddingStatus }}
                </span>
              </td>
              <td class="px-5 py-4">
                <span
                  v-if="item.isRecall === 1"
                  class="text-green-600 text-xs font-medium flex items-center"
                >
                  <i class="fas fa-check-circle mr-1"></i>
                  已召回
                </span>
                <span v-else class="text-gray-400 text-xs font-medium flex items-center">
                  <i class="fas fa-times-circle mr-1"></i>
                  未召回
                </span>
              </td>
              <td class="px-5 py-4 text-[#5f70e1] cursor-pointer hover:underline space-x-2">
                <button @click="editKnowledge(item)">管理</button>
                <button
                  v-if="item.isRecall === 1"
                  @click="toggleStatus(item)"
                  class="text-red-500 hover:text-red-700"
                >
                  取消召回
                </button>
                <button
                  v-else
                  @click="toggleStatus(item)"
                  class="text-green-600 hover:text-green-800"
                >
                  召回
                </button>
                <button @click="deleteKnowledge(item)" class="text-red-500 hover:text-red-700">
                  删除
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页组件 -->
      <div class="mt-4 flex justify-end">
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
  </div>

  <!-- 添加/编辑知识弹窗 -->
  <div
    v-if="dialogVisible"
    class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
    @click.self="closeDialog"
  >
    <!-- 弹窗容器 -->
    <div class="bg-white rounded-lg shadow-2xl w-full max-w-2xl">
      <div class="p-6 border-b border-gray-200">
        <h2 class="text-xl font-semibold text-gray-800">
          {{ isEdit ? '编辑知识' : '添加新知识' }}
        </h2>
      </div>

      <form class="p-8 space-y-6" @submit.prevent="saveKnowledge">
        <!-- 知识类型 -->
        <div>
          <label for="knowledge-type" class="block text-sm font-medium text-gray-700">
            知识类型
            <span class="text-red-500">*</span>
          </label>
          <select
            id="knowledge-type"
            v-model="knowledgeForm.type"
            class="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-[#5f70e1] focus:border-[#5f70e1] sm:text-sm rounded-md border text-gray-800"
            @change="handleTypeChange"
            :disabled="isEdit"
          >
            :disabled="isEdit" >
            <option value="DOCUMENT">文档 (文件上传)</option>
            <option value="QA">问答对 (Q&A)</option>
            <option value="FAQ">常见问题 (FAQ)</option>
          </select>
        </div>

        <!-- 知识标题 -->
        <div>
          <label for="title" class="block text-sm font-medium text-gray-700">
            知识标题
            <span class="text-red-500">*</span>
          </label>
          <input
            type="text"
            id="title"
            v-model="knowledgeForm.title"
            class="w-full p-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-[#5f70e1] text-gray-800"
            placeholder="为这份知识起一个易于识别的名称"
          />
        </div>

        <!-- 文件上传区域 (默认显示) -->
        <div v-if="knowledgeForm.type === 'DOCUMENT'" id="section-document">
          <!-- 编辑模式下不显示文件上传 -->
          <div v-if="!isEdit">
            <label class="block text-sm font-medium text-gray-700 mb-1">上传文件</label>
            <div
              class="mt-1 flex justify-center px-6 pt-5 pb-6 border-2 border-gray-300 border-dashed rounded-md"
            >
              <div class="space-y-1 text-center">
                <i class="fas fa-cloud-upload-alt text-4xl text-gray-400"></i>
                <div class="flex flex-col items-center text-sm text-gray-600">
                  <label
                    for="file-upload"
                    class="relative cursor-pointer bg-white rounded-md font-medium text-[#5f70e1] hover:text-[#4c63d2] focus-within:outline-none px-2 py-1 border border-[#5f70e1] mb-2"
                  >
                    <span class="text-xs">选择文件</span>
                    <input
                      id="file-upload"
                      name="file-upload"
                      type="file"
                      class="sr-only"
                      @change="handleFileChange"
                    />
                  </label>
                  <p class="pl-1">或拖拽到此处</p>
                </div>
                <p class="text-xs text-gray-500">支持 PDF, DOCX, TXT, MD 等</p>
                <p
                  v-if="fileList.length > 0"
                  class="text-base font-bold text-[#5f70e1] mt-2 bg-blue-50 p-2 rounded"
                >
                  已选择: {{ fileList[0].name }} ({{ formatFileSize(fileList[0].size) }})
                </p>
              </div>
            </div>
          </div>
          <div v-else class="text-sm text-gray-500 italic">
            文档类型知识不支持修改文件内容，如需修改请删除后重新创建。
          </div>
        </div>

        <!-- Q&A / FAQ 输入区域 (默认隐藏) -->
        <div
          v-if="knowledgeForm.type === 'QA' || knowledgeForm.type === 'FAQ'"
          id="section-qa"
          class="space-y-6"
        >
          <div>
            <label for="qa-question" class="block text-sm font-medium text-gray-700 mb-1">
              问题
              <span class="text-red-500">*</span>
            </label>
            <textarea
              id="qa-question"
              v-model="knowledgeForm.question"
              rows="2"
              class="w-full p-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-[#5f70e1] text-gray-800"
              placeholder="输入用户可能会问的问题..."
            ></textarea>
          </div>
          <div>
            <label for="qa-answer" class="block text-sm font-medium text-gray-700 mb-1">
              答案
              <span class="text-red-500">*</span>
            </label>
            <textarea
              id="qa-answer"
              v-model="knowledgeForm.answer"
              rows="5"
              class="w-full p-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-[#5f70e1] text-gray-800"
              placeholder="输入标准答案..."
            ></textarea>
          </div>
        </div>
      </form>

      <div class="p-6 bg-gray-50 rounded-b-lg flex justify-end space-x-4">
        <button
          @click="closeDialog"
          class="bg-white py-2 px-4 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          取消
        </button>
        <button
          @click="saveKnowledge"
          :disabled="saveLoading"
          class="bg-[#5f70e1] text-white font-bold py-2 px-4 rounded-md hover:bg-[#4c63d2] disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <i v-if="saveLoading" class="fas fa-spinner fa-spin mr-2"></i>
          {{ isEdit ? '更新' : '添加并处理' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
  import { defineComponent, ref, onMounted, Ref, reactive } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import axios from 'axios';
  import agentKnowledgeService, {
    AgentKnowledge,
    AgentKnowledgeQueryDTO,
  } from '@/services/agentKnowledge';

  export default defineComponent({
    name: 'AgentKnowledgeConfig',
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
      const fileList: Ref<File[]> = ref([]);
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
        AgentKnowledge & { question?: string; answer?: string; file?: File }
      > = ref({
        agentId: props.agentId,
        title: '',
        content: '',
        type: 'DOCUMENT',
        isRecall: 1,
        question: '',
        answer: '',
      } as AgentKnowledge & { question?: string; answer?: string });

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
          // 将查询参数传递给后端
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
        // 复制对象
        knowledgeForm.value = {
          ...knowledge,
          type: knowledge.type,
        };

        // 如果是 QA/FAQ，需要把 content 拆分回 question 和 answer (如果 content 是组合的)
        // 这里假设后端返回的 VO 已经有了 question 和 content (作为 answer)
        if (knowledge.type === 'QA' || knowledge.type === 'FAQ') {
          knowledgeForm.value.answer = knowledge.content;
          // question 已经在 knowledge 对象中了
        }

        dialogVisible.value = true;
      };

      // 切换状态（召回/取消召回）
      const toggleStatus = (knowledge: AgentKnowledge) => {
        if (!knowledge.id) return;
        // 当前是1则改为0，当前是0则改为1
        const newStatus = knowledge.isRecall === 1 ? 0 : 1;
        const actionName = newStatus === 1 ? '召回' : '取消召回';

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
                // 更新本地列表中的状态
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
          .catch(() => {
            // 取消操作
          });
      };

      // 重试向量化
      const handleRetry = async (knowledge: AgentKnowledge) => {
        if (!knowledge.id) return;
        try {
          const success = await agentKnowledgeService.retryEmbedding(knowledge.id);
          if (success) {
            ElMessage.success('重试请求已发送');
            // 刷新列表
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
          .catch(() => {
            // 取消操作
          });
      };

      // 处理类型变化
      const handleTypeChange = () => {
        knowledgeForm.value.content = '';
        knowledgeForm.value.question = '';
        knowledgeForm.value.answer = '';
        fileList.value = [];
      };

      // 处理文件变化
      const handleFileChange = (event: Event) => {
        const target = event.target as HTMLInputElement;
        if (target.files && target.files.length > 0) {
          fileList.value = [target.files[0]];
          knowledgeForm.value.file = target.files[0];
        }
      };

      const formatFileSize = (bytes: number) => {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
      };

      // 保存知识
      const saveKnowledge = async () => {
        // 表单验证
        if (!knowledgeForm.value.title || !knowledgeForm.value.title.trim()) {
          ElMessage.warning('请输入知识标题');
          return;
        }

        // 根据类型验证不同字段
        if (knowledgeForm.value.type === 'DOCUMENT') {
          // 编辑模式下不允许修改文件，所以不需要验证文件
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
          // 将答案赋值给 content
          knowledgeForm.value.content = knowledgeForm.value.answer;
        }

        saveLoading.value = true;
        try {
          if (isEdit.value && currentEditId.value) {
            // 更新时需要将 type 转换为大写
            const updateData = {
              ...knowledgeForm.value,
              type: knowledgeForm.value.type?.toUpperCase(),
            };
            const result = await agentKnowledgeService.update(currentEditId.value, updateData);
            // update 返回的是对象或null，只要不是null就是成功
            if (result) {
              ElMessage.success('更新成功');
            } else {
              ElMessage.error('更新失败');
              return;
            }
          } else {
            // 统一使用 FormData 提交
            const formData = new FormData();
            formData.append('agentId', String(knowledgeForm.value.agentId));
            formData.append('title', knowledgeForm.value.title);
            formData.append('type', knowledgeForm.value.type || 'DOCUMENT');
            formData.append('isRecall', '1');

            if (knowledgeForm.value.type === 'DOCUMENT' && knowledgeForm.value.file) {
              formData.append('file', knowledgeForm.value.file);
            } else {
              // QA/FAQ
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
          isRecall: 1,
          question: '',
          answer: '',
        } as AgentKnowledge & { question?: string; answer?: string };
        currentEditId.value = null;
        fileList.value = [];
      };

      onMounted(() => {
        loadKnowledgeList();
      });

      return {
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
  /* 下拉框美化 */
  .custom-select {
    appearance: none;
    background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%236b7280' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
    background-position: right 0.5rem center;
    background-repeat: no-repeat;
    background-size: 1.5em 1.5em;
    padding-right: 2.5rem;
  }

  /* 隐藏文件输入 */
  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border-width: 0;
  }
</style>
