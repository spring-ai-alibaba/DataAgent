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
    <div class="agent-create-page">
      <div class="create-form-wrapper">
        <!-- 表单区域 -->
        <div class="form-section">
          <div class="section-card">
            <div class="section-header">
              <h3>创建智能体</h3>
              <p>配置您的专属数据分析智能体，让AI帮助您更好地理解和分析数据</p>
            </div>

            <!-- 头像上传组件 -->
            <div class="form-group">
              <label>头像设置</label>
              <div class="avatar-upload">
                <div class="avatar-preview">
                  <img
                    :src="agentForm.avatar"
                    alt="智能体头像"
                    @load="handleImageLoad"
                    @error="handleImageError"
                  />
                </div>
                <div class="avatar-controls">
                  <div class="avatar-buttons">
                    <el-button @click="regenerateAvatar" size="large">
                      <el-icon><Refresh /></el-icon>
                      重新生成
                    </el-button>
                    <el-button @click="triggerFileUpload" :disabled="uploading" size="large">
                      <el-icon v-if="!uploading"><Upload /></el-icon>
                      <el-icon v-if="uploading"><Loading /></el-icon>
                      {{ uploading ? '上传中...' : '上传图片' }}
                    </el-button>
                    <input
                      ref="fileInput"
                      type="file"
                      accept="image/*"
                      style="display: none"
                      @change="handleFileUpload"
                    />
                  </div>
                </div>
              </div>
            </div>

            <!-- 复制自 BaseSetting 的代码 -->
            <el-row :gutter="20">
              <el-col :span="12">
                <div class="form-item">
                  <label>智能体名称 *</label>
                  <el-input v-model="agentForm.name" placeholder="请输入智能体名称" size="large" />
                </div>
              </el-col>
              <el-col :span="12">
                <div class="form-item">
                  <label>分类 *</label>
                  <el-input
                    v-model="agentForm.category"
                    placeholder="请输入智能体分类"
                    size="large"
                  />
                </div>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="24">
                <div class="form-item">
                  <label>描述</label>
                  <el-input
                    v-model="agentForm.description"
                    :rows="4"
                    type="textarea"
                    placeholder="请输入智能体描述"
                    size="large"
                  />
                </div>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="24">
                <div class="form-item">
                  <label>智能体Prompt</label>
                  <el-input
                    v-model="agentForm.prompt"
                    :rows="4"
                    type="textarea"
                    placeholder="请输入智能体Prompt"
                    size="large"
                  />
                </div>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <div class="form-item">
                  <label>标签 *</label>
                  <el-input
                    v-model="agentForm.tags"
                    placeholder="多个标签用逗号分隔"
                    size="large"
                  />
                </div>
              </el-col>
              <el-col :span="12">
                <div class="form-item">
                  <label>状态</label>
                  <el-select
                    v-model="agentForm.status"
                    placeholder="请选择状态"
                    style="width: 100%"
                    size="large"
                  >
                    <el-option key="draft" label="待发布" value="draft" />
                    <el-option key="published" label="已发布" value="published" />
                    <el-option key="offline" label="已下线" value="offline" />
                  </el-select>
                </div>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="24">
                <div class="form-item form-switch">
                  <el-switch v-model="agentForm.humanReviewEnabled" size="large" />
                  <span>启用计划人工复核</span>
                  <el-text class="mx-1">（开启后，Planner 计划会在执行前等待人工复核）</el-text>
                </div>
              </el-col>
            </el-row>
          </div>
        </div>

        <!-- 页面底部操作按钮 -->
        <div class="bottom-actions">
          <div class="action-card">
            <div class="form-actions">
              <el-button @click="goBack" size="large">取消</el-button>
              <el-button
                type="primary"
                :icon="Plus"
                round
                @click="createAgent"
                :loading="loading"
                size="large"
              >
                {{ loading ? '创建中...' : '创建智能体' }}
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </BaseLayout>
</template>

<script lang="ts">
  import { defineComponent, reactive, ref, onMounted } from 'vue';
  import { useRouter } from 'vue-router';
  import { ElMessage } from 'element-plus';
  import { Plus, Refresh, Upload, Loading } from '@element-plus/icons-vue';
  import BaseLayout from '../layouts/BaseLayout.vue';
  import agentService from '@/services/agent';
  import { fileUploadApi } from '@/services/fileUpload';

  export default defineComponent({
    name: 'AgentCreate',
    components: {
      BaseLayout,
    },
    setup() {
      const router = useRouter();
      const loading = ref(false);
      const fileInput = ref<HTMLInputElement | null>(null);
      const uploading = ref(false);

      const agentForm = reactive({
        name: '',
        description: '',
        avatar: '',
        category: '',
        tags: '',
        prompt: '',
        status: 'draft',
        humanReviewEnabled: false,
      });

      // 组件挂载时生成随机头像
      onMounted(() => {
        agentForm.avatar = generateFallbackAvatar();
      });

      // 备用头像生成函数
      const generateFallbackAvatar = (): string => {
        const colors = [
          '3B82F6',
          '8B5CF6',
          '10B981',
          'F59E0B',
          'EF4444',
          '6366F1',
          'EC4899',
          '14B8A6',
        ];
        const randomColor = colors[Math.floor(Math.random() * colors.length)];
        const letters = ['AI', '数据', '智能', 'DA', 'BI', 'ML', 'DL', 'NL'];
        const randomLetter = letters[Math.floor(Math.random() * letters.length)];

        const svg = `<svg width="200" height="200" xmlns="http://www.w3.org/2000/svg">
        <rect width="200" height="200" fill="#${randomColor}"/>
        <text x="100" y="120" font-family="Arial, sans-serif" font-size="48" font-weight="bold" text-anchor="middle" fill="white">${randomLetter}</text>
      </svg>`;

        return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
      };

      const goBack = () => {
        router.push('/agents');
      };

      const regenerateAvatar = () => {
        agentForm.avatar = generateFallbackAvatar();
      };

      // 触发文件选择
      const triggerFileUpload = () => {
        if (fileInput.value) {
          fileInput.value.click();
        }
      };

      // 处理文件上传
      const handleFileUpload = async (event: Event) => {
        const target = event.target as HTMLInputElement;
        const file = target.files?.[0];
        if (!file) return;

        // 验证文件类型
        if (!file.type.startsWith('image/')) {
          ElMessage.error('请选择图片文件');
          return;
        }

        // 验证文件大小 (5MB)
        if (file.size > 5 * 1024 * 1024) {
          ElMessage.error('图片大小不能超过5MB');
          return;
        }

        try {
          uploading.value = true;

          // 显示上传中的预览（使用base64）
          const reader = new FileReader();
          reader.onload = e => {
            if (e.target?.result) {
              agentForm.avatar = e.target.result as string;
            }
          };
          reader.readAsDataURL(file);

          // 上传文件
          const response = await fileUploadApi.uploadAvatar(file);

          if (response.success) {
            // 上传成功，使用服务器返回的URL
            agentForm.avatar = response.url;
            ElMessage.success('头像上传成功');
          } else {
            throw new Error(response.message || '上传失败');
          }
        } catch (error) {
          console.error('头像上传失败:', error);
          ElMessage.error('头像上传失败: ' + (error instanceof Error ? error.message : '未知错误'));
          // 恢复之前的头像
          agentForm.avatar = generateFallbackAvatar();
        } finally {
          uploading.value = false;
          // 清空文件输入
          if (fileInput.value) {
            fileInput.value.value = '';
          }
        }
      };

      // 图片加载成功处理
      const handleImageLoad = () => {
        console.log('头像图片加载成功');
      };

      // 图片加载失败处理
      const handleImageError = () => {
        console.error('头像图片加载失败');
        // 设置默认头像
        agentForm.avatar = generateFallbackAvatar();
      };

      const createAgent = async () => {
        if (!agentForm.name.trim() || !agentForm.category.trim() || !agentForm.tags.trim()) {
          ElMessage.error('请填写必要的字段！');
          return;
        }

        try {
          loading.value = true;

          const agentData = {
            name: agentForm.name.trim(),
            description: agentForm.description.trim(),
            avatar: agentForm.avatar.trim(),
            category: agentForm.category.trim(),
            tags: agentForm.tags.trim(),
            prompt: agentForm.prompt.trim(),
            status: agentForm.status,
            humanReviewEnabled: agentForm.humanReviewEnabled ? 1 : 0,
          };

          const result = await agentService.create(agentData);

          ElMessage.success(
            `智能体创建成功！状态：${agentData.status === 'published' ? '已发布' : '草稿'}`,
          );
          await router.push(`/agent/${result.id}`);
        } catch (error) {
          console.error('创建智能体失败:', error);
          ElMessage.error('创建失败，请重试');
        } finally {
          loading.value = false;
        }
      };

      return {
        Plus,
        Refresh,
        Upload,
        Loading,
        agentForm,
        loading,
        fileInput,
        uploading,
        goBack,
        regenerateAvatar,
        triggerFileUpload,
        handleFileUpload,
        handleImageLoad,
        handleImageError,
        createAgent,
      };
    },
  });
</script>

<style scoped>
  .agent-create-page {
    padding: 20px;
    background: #f8fafc;
    min-height: 100vh;
  }

  .page-header {
    margin-bottom: 20px;
  }

  .page-header h2 {
    font-size: 24px;
    font-weight: 600;
    color: #1f2937;
    margin: 0 0 8px 0;
  }

  .page-header p {
    color: #6b7280;
    margin: 0;
    font-size: 14px;
  }

  .create-form-wrapper {
    display: flex;
    flex-direction: column;
    gap: 24px;
    max-width: 800px;
    margin: 0 auto;
  }

  /* 表单区域 */
  .form-section {
    display: flex;
    flex-direction: column;
    gap: 24px;
  }

  .section-card {
    background: white;
    border-radius: 12px;
    padding: 24px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
    border: 1px solid #e5e7eb;
  }

  .section-header {
    margin-bottom: 24px;
  }

  .section-header h3 {
    font-size: 18px;
    font-weight: 600;
    color: #1f2937;
    margin: 0 0 8px 0;
  }

  .section-header p {
    color: #6b7280;
    margin: 0;
    font-size: 14px;
  }

  /* 底部操作按钮 */
  .bottom-actions {
    margin-top: 24px;
  }

  .bottom-actions .action-card {
    background: white;
    border-radius: 12px;
    padding: 24px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
    border: 1px solid #e5e7eb;
  }

  .bottom-actions .form-actions {
    display: flex;
    gap: 12px;
    justify-content: flex-end;
  }

  /* 表单样式 */
  .form-group {
    margin-bottom: 25px;
  }

  .form-group label {
    display: block;
    margin-bottom: 10px;
    font-weight: 500;
    font-size: 15px;
    color: #374151;
  }

  .form-item {
    margin-bottom: 25px;
  }

  .form-item label {
    display: block;
    margin-bottom: 10px;
    font-weight: 500;
    font-size: 15px;
    color: #374151;
  }

  .form-switch {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 15px;
    color: #374151;
  }

  .form-actions {
    display: flex;
    gap: 12px;
    justify-content: flex-end;
  }

  /* 头像上传样式 */
  .avatar-upload {
    display: flex;
    gap: 1rem;
    align-items: flex-start;
  }

  .avatar-preview {
    width: 80px;
    height: 80px;
    border-radius: 8px;
    overflow: hidden;
    border: 2px solid #e5e7eb;
    background: white;
  }

  .avatar-preview img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .avatar-controls {
    flex: 1;
  }

  .avatar-buttons {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  /* 响应式设计 */
  @media (max-width: 768px) {
    .agent-create-page {
      padding: 16px;
    }

    .section-card,
    .bottom-actions .action-card {
      padding: 20px;
    }

    .avatar-upload {
      flex-direction: column;
      align-items: center;
    }

    .avatar-buttons {
      justify-content: center;
    }

    .bottom-actions .form-actions {
      flex-direction: column;
    }
  }
</style>
