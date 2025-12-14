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
  <BaseLayout>
    <div class="model-config-page">
      <!-- 主内容区域 -->
      <main class="main-content">
        <!-- 内容头部 -->
        <div class="content-header">
          <div class="header-info">
            <h1 class="content-title">模型配置管理</h1>
            <p class="content-subtitle">配置和管理AI模型参数，支持多种模型提供商</p>
          </div>
        </div>

        <!-- 操作区域 -->
        <div class="action-section">
          <el-card>
            <div class="action-content">
              <div class="action-buttons">
                <el-button type="primary" :icon="Plus" @click="showAddDialog" size="large">
                  新增配置
                </el-button>
                <el-button :icon="Refresh" @click="loadConfigs" size="large">刷新</el-button>
              </div>
              <div class="filter-options">
                <el-select
                  v-model="activeFilter"
                  placeholder="筛选模型类型"
                  size="large"
                  clearable
                  style="width: 300px"
                >
                  <el-option label="全部" value="" />
                  <el-option label="对话模型 (CHAT)" value="CHAT" />
                  <el-option label="嵌入模型 (EMBEDDING)" value="EMBEDDING" />
                </el-select>
              </div>
            </div>
          </el-card>
        </div>

        <!-- 配置表格 -->
        <div class="config-table" v-if="!loading">
          <el-card>
            <el-table :data="filteredConfigs" style="width: 100%" stripe>
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column prop="provider" label="提供商" width="120">
                <template #default="scope">
                  <el-tag :type="getProviderTagType(scope.row.provider)" size="small">
                    {{ scope.row.provider }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="modelName" label="模型名称" width="180" />
              <el-table-column prop="modelType" label="模型类型" width="120">
                <template #default="scope">
                  <el-tag
                    :type="scope.row.modelType === 'CHAT' ? 'primary' : 'success'"
                    size="small"
                  >
                    {{ scope.row.modelType === 'CHAT' ? '对话模型' : '嵌入模型' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                prop="baseUrl"
                label="API地址"
                min-width="200"
                show-overflow-tooltip
              />
              <el-table-column label="路径配置" min-width="180" show-overflow-tooltip>
                <template #default="scope">
                  <div v-if="scope.row.modelType === 'CHAT' && scope.row.completionsPath">
                    <el-tag type="primary" size="small">
                      对话: {{ scope.row.completionsPath }}
                    </el-tag>
                  </div>
                  <div v-else-if="scope.row.modelType === 'EMBEDDING' && scope.row.embeddingsPath">
                    <el-tag type="success" size="small">
                      嵌入: {{ scope.row.embeddingsPath }}
                    </el-tag>
                  </div>
                  <div v-else>
                    <span class="text-muted">使用默认路径</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="temperature" label="温度" width="100">
                <template #default="scope">
                  {{ scope.row.temperature || 0.0 }}
                </template>
              </el-table-column>
              <el-table-column prop="maxTokens" label="最大Token" width="120">
                <template #default="scope">
                  {{ scope.row.maxTokens || 2000 }}
                </template>
              </el-table-column>
              <el-table-column prop="isActive" label="状态" width="100">
                <template #default="scope">
                  <el-tag
                    :type="scope.row.isActive ? 'success' : 'info'"
                    size="small"
                    effect="light"
                  >
                    {{ scope.row.isActive ? '已启用' : '未启用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="320" fixed="right">
                <template #default="scope">
                  <div class="action-buttons-cell">
                    <el-button
                      type="info"
                      size="small"
                      @click="handleTestConnection(scope.row)"
                      :loading="testingId === scope.row.id"
                    >
                      连接测试
                    </el-button>
                    <el-button
                      v-if="!scope.row.isActive"
                      type="success"
                      size="small"
                      @click="handleActivate(scope.row.id)"
                      :loading="activatingId === scope.row.id"
                    >
                      启用
                    </el-button>
                    <el-button type="primary" size="small" @click="handleEdit(scope.row)">
                      编辑
                    </el-button>
                    <el-button type="danger" size="small" @click="handleDelete(scope.row)">
                      删除
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>

        <!-- 加载状态 -->
        <div v-if="loading" class="loading-state">
          <el-skeleton :rows="6" animated />
        </div>

        <!-- 空状态 -->
        <div v-if="!loading && filteredConfigs.length === 0" class="empty-state">
          <el-empty description="暂无模型配置">
            <template #image>
              <el-icon size="60"><Cpu /></el-icon>
            </template>
            <el-button type="primary" :icon="Plus" @click="showAddDialog">新增配置</el-button>
          </el-empty>
        </div>
      </main>

      <!-- 新增/编辑对话框 -->
      <el-dialog
        v-model="dialogVisible"
        :title="dialogTitle"
        width="600px"
        :close-on-click-modal="false"
      >
        <el-form
          ref="formRef"
          :model="formData"
          :rules="formRules"
          label-width="120px"
          label-position="left"
        >
          <el-form-item label="提供商" prop="provider">
            <el-select
              v-model="formData.provider"
              placeholder="请选择提供商"
              style="width: 100%"
              @change="updateBaseUrlByProvider"
            >
              <el-option label="DeepSeek" value="deepseek" />
              <el-option label="Qwen" value="qwen" />
              <el-option label="OpenAI" value="openai" />
              <el-option label="Siliconflow" value="siliconflow" />
              <el-option label="Custom" value="custom" />
            </el-select>
          </el-form-item>

          <el-form-item label="模型类型" prop="modelType">
            <el-radio-group v-model="formData.modelType">
              <el-radio label="CHAT">对话模型</el-radio>
              <el-radio label="EMBEDDING">嵌入模型</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="模型名称" prop="modelName">
            <el-input
              v-model="formData.modelName"
              placeholder="例如: gpt-4, deepseek-chat,qwen-plus,text-embedding-v4"
            />
          </el-form-item>

          <el-form-item label="API密钥" prop="apiKey">
            <el-input
              v-model="formData.apiKey"
              type="password"
              show-password
              placeholder="请输入API密钥"
            />
          </el-form-item>

          <el-form-item label="Base URL" prop="baseUrl">
            <el-input
              v-model="formData.baseUrl"
              placeholder="请填写兼容 OpenAI 协议的 Base URL，通常不包含 /v1 后缀"
            />
          </el-form-item>

          <el-form-item
            v-if="formData.modelType === 'CHAT'"
            label="Completions路径"
            prop="completionsPath"
          >
            <el-input
              v-model="formData.completionsPath"
              placeholder="附加到base-url的路径。留空则使用默认值/v1/chat/completions"
            />
          </el-form-item>

          <el-form-item
            v-if="formData.modelType === 'EMBEDDING'"
            label="Embeddings路径"
            prop="embeddingsPath"
          >
            <el-input
              v-model="formData.embeddingsPath"
              placeholder="附加到附加到base-url的路径。留空则使用默认值/v1/embeddings"
            />
          </el-form-item>

          <el-form-item label="温度" prop="temperature">
            <el-slider
              v-model="formData.temperature"
              :min="0"
              :max="2"
              :step="0.1"
              show-input
              show-input-controls
            />
            <div class="form-tip">建议默认0。控制生成文本的随机性，值越高越随机</div>
          </el-form-item>

          <el-form-item label="最大Token" prop="maxTokens">
            <el-input-number
              v-model="formData.maxTokens"
              :min="100"
              :max="10000"
              :step="100"
              style="width: 100%"
            />
            <div class="form-tip">控制生成文本的最大长度</div>
          </el-form-item>
        </el-form>

        <template #footer>
          <span class="dialog-footer">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleSubmit" :loading="submitting">
              {{ isEditMode ? '更新' : '创建' }}
            </el-button>
          </span>
        </template>
      </el-dialog>
    </div>
  </BaseLayout>
</template>

<script lang="ts">
  import { defineComponent, ref, computed, onMounted } from 'vue';
  import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
  import { Plus, Refresh, Cpu } from '@element-plus/icons-vue';
  import BaseLayout from '@/layouts/BaseLayout.vue';
  import modelConfigService, { type ModelConfig } from '@/services/modelConfig';

  export default defineComponent({
    name: 'ModelConfig',
    components: {
      BaseLayout,
      Cpu,
    },
    setup() {
      const loading = ref(true);
      const dialogVisible = ref(false);
      const isEditMode = ref(false);
      const submitting = ref(false);
      const activatingId = ref<number | null>(null);
      const testingId = ref<number | null>(null);
      const activeFilter = ref('');
      const configs = ref<ModelConfig[]>([]);
      const formRef = ref<FormInstance>();

      // 表单数据
      const formData = ref<ModelConfig>({
        provider: '',
        apiKey: '',
        baseUrl: '',
        modelName: '',
        modelType: 'CHAT',
        temperature: 0.0,
        maxTokens: 2000,
        completionsPath: '',
        embeddingsPath: '',
        isActive: false,
      });

      // 提供商与API地址的映射
      const providerBaseUrlMap: Record<string, string> = {
        deepseek: 'https://api.deepseek.com',
        qwen: 'https://dashscope.aliyuncs.com/compatible-mode',
        openai: 'https://api.openai.com',
        siliconflow: 'https://api.siliconflow.cn',
        custom: '', // 自定义提供商不设置默认API地址
      };

      // 监听提供商变化，自动更新API地址
      const updateBaseUrlByProvider = (provider: string) => {
        if (provider && provider !== 'custom') {
          formData.value.baseUrl = providerBaseUrlMap[provider] || '';
        }
      };

      // 表单验证规则
      const formRules: FormRules = {
        provider: [{ required: true, message: '请选择提供商', trigger: 'change' }],
        modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }],
        modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
        apiKey: [{ required: true, message: '请输入API密钥', trigger: 'blur' }],
        baseUrl: [{ required: true, message: '请输入API地址', trigger: 'blur' }],
        temperature: [
          { type: 'number', min: 0, max: 2, message: '温度值必须在0-2之间', trigger: 'blur' },
        ],
        maxTokens: [
          {
            type: 'number',
            min: 100,
            max: 10000,
            message: '最大Token必须在100-10000之间',
            trigger: 'blur',
          },
        ],
      };

      // 计算属性
      const dialogTitle = computed(() => {
        return isEditMode.value ? '编辑模型配置' : '新增模型配置';
      });

      const filteredConfigs = computed(() => {
        let filtered = configs.value;

        // 按模型类型过滤
        if (activeFilter.value) {
          filtered = filtered.filter(config => config.modelType === activeFilter.value);
        }

        return filtered;
      });

      // 方法
      const loadConfigs = async () => {
        loading.value = true;
        try {
          const response = await modelConfigService.list();
          configs.value = response || [];
        } catch (error) {
          ElMessage.error('获取模型配置列表失败，请检查网络！');
          configs.value = [];
        } finally {
          loading.value = false;
        }
      };

      const showAddDialog = () => {
        isEditMode.value = false;
        formData.value = {
          provider: '',
          apiKey: '',
          baseUrl: '',
          modelName: '',
          modelType: 'CHAT',
          temperature: 0.0,
          maxTokens: 2000,
          completionsPath: '',
          embeddingsPath: '',
          isActive: false,
        };
        dialogVisible.value = true;
      };

      const handleEdit = (config: ModelConfig) => {
        isEditMode.value = true;
        formData.value = { ...config };
        dialogVisible.value = true;
      };

      const handleSubmit = async () => {
        if (!formRef.value) return;

        try {
          await formRef.value.validate();
          submitting.value = true;

          if (isEditMode.value) {
            // 更新配置
            const result = await modelConfigService.update(formData.value);
            if (result.success) {
              ElMessage.success('配置更新成功');
              dialogVisible.value = false;
              loadConfigs();
            } else {
              ElMessage.error(result.message || '配置更新失败');
            }
          } else {
            // 新增配置
            const result = await modelConfigService.add(formData.value);
            if (result.success) {
              ElMessage.success('配置添加成功');
              dialogVisible.value = false;
              loadConfigs();
            } else {
              ElMessage.error(result.message || '配置添加失败');
            }
          }
        } catch (error) {
          console.error('表单验证失败:', error);
        } finally {
          submitting.value = false;
        }
      };

      const handleDelete = async (config: ModelConfig) => {
        try {
          await ElMessageBox.confirm(
            `确定要删除配置 "${config.provider} - ${config.modelName}" 吗？此操作不可恢复。`,
            '删除确认',
            {
              confirmButtonText: '确定删除',
              cancelButtonText: '取消',
              type: 'warning',
            },
          );

          if (config.id) {
            const result = await modelConfigService.delete(config.id);
            if (result.success) {
              ElMessage.success('配置删除成功');
              loadConfigs();
            } else {
              ElMessage.error(result.message || '配置删除失败');
            }
          }
        } catch (error) {
          // 用户取消了删除操作
          console.log('删除操作已取消');
        }
      };

      const handleActivate = async (id?: number) => {
        if (!id) return;

        try {
          activatingId.value = id;
          const result = await modelConfigService.activate(id);
          if (result.success) {
            ElMessage.success('模型启用成功');
            loadConfigs();
          } else {
            ElMessage.error(result.message || '模型启用失败');
          }
        } catch (error) {
          ElMessage.error('启用过程中发生错误');
        } finally {
          activatingId.value = null;
        }
      };

      const handleTestConnection = async (config: ModelConfig) => {
        if (!config.id) return;

        try {
          testingId.value = config.id;
          const result = await modelConfigService.testConnection(config);
          if (result.success) {
            ElMessage.success(result.message || '连接测试成功！');
          } else {
            ElMessage.error(result.message || '连接测试失败');
          }
        } catch (error) {
          ElMessage.error('连接测试过程中发生错误');
        } finally {
          testingId.value = null;
        }
      };

      const getProviderTagType = (provider: string) => {
        const typeMap: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
          deepseek: 'success',
          qwen: 'warning',
          openai: 'primary',
          siliconflow: 'danger',
          custom: 'info',
        };
        return typeMap[provider] || 'info';
      };

      // 生命周期
      onMounted(() => {
        loadConfigs();
      });

      return {
        loading,
        dialogVisible,
        isEditMode,
        submitting,
        activatingId,
        testingId,
        activeFilter,
        configs,
        formData,
        formRef,
        formRules,
        filteredConfigs,
        dialogTitle,
        loadConfigs,
        showAddDialog,
        handleEdit,
        handleSubmit,
        handleDelete,
        handleActivate,
        handleTestConnection,
        getProviderTagType,
        updateBaseUrlByProvider,
        Plus,
        Refresh,
      };
    },
  });
</script>

<style scoped>
  .model-config-page {
    min-height: 100vh;
    background: #f8fafc;
    font-family:
      -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  }

  /* 主内容区域 */
  .main-content {
    width: 100%;
    margin: 0 auto;
    padding: 2rem;
  }

  /* 内容头部 */
  .content-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 2rem;
  }

  .header-info h1 {
    font-size: 2rem;
    font-weight: 600;
    color: #1f2937;
    margin: 0 0 0.5rem 0;
  }

  .header-info p {
    color: #6b7280;
    margin: 0;
    font-size: 1.1rem;
  }

  /* 操作区域 */
  .action-section {
    margin-bottom: 2rem;
  }

  .action-content {
    padding: 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .action-buttons {
    display: flex;
    gap: 1rem;
  }

  .filter-options {
    display: flex;
    gap: 1rem;
  }

  /* 配置表格 */
  .config-table {
    margin-bottom: 2rem;
  }

  .action-buttons-cell {
    display: flex;
    gap: 0.5rem;
  }

  /* 加载状态 */
  .loading-state {
    padding: 4rem 2rem;
  }

  /* 空状态 */
  .empty-state {
    padding: 4rem 2rem;
  }

  /* 表单提示 */
  .form-tip {
    font-size: 0.75rem;
    color: #6b7280;
    margin-top: 0.25rem;
  }

  /* 文本样式 */
  .text-muted {
    color: #9ca3af;
    font-style: italic;
  }

  /* 响应式设计 */
  @media (max-width: 768px) {
    .main-content {
      padding: 1rem;
    }

    .content-header {
      flex-direction: column;
      align-items: flex-start;
      gap: 1rem;
    }

    .header-stats {
      gap: 1rem;
    }

    .action-content {
      flex-direction: column;
      align-items: stretch;
      gap: 1rem;
    }

    .action-buttons {
      width: 100%;
    }

    .action-buttons .el-button {
      flex: 1;
    }

    .filter-options {
      width: 100%;
    }

    .filter-options .el-select {
      width: 100%;
    }
  }
</style>
