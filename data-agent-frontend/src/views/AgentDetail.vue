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
    <el-container class="detail-page">
      <!-- 顶部 Header -->
      <el-header class="detail-header">
        <button class="back-btn" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回列表
        </button>
        <div class="agent-title-section">
          <div
            class="avatar-wrapper"
            @mouseenter="showHeaderAvatarButton = true"
            @mouseleave="showHeaderAvatarButton = false"
          >
            <el-avatar :src="agent.avatar" size="large" class="header-avatar">
              {{ agent.name }}
            </el-avatar>
            <div v-if="showHeaderAvatarButton" class="avatar-overlay-header">
              <el-button
                type="primary"
                size="small"
                @click="triggerHeaderFileUpload"
                :loading="headerUploading"
              >
                {{ headerUploading ? '上传中...' : '替换头像' }}
              </el-button>
            </div>
          </div>
          <input
            ref="headerFileInput"
            type="file"
            accept="image/*"
            style="display: none"
            @change="handleHeaderFileUpload"
          />
          <div class="agent-title-info">
            <h2 class="agent-name-text">{{ agent.name }}</h2>
            <p class="agent-desc-text">{{ agent.description || '暂无描述' }}</p>
          </div>
        </div>
        <el-divider style="margin: 1rem 0 0 0" />
      </el-header>

      <!-- 主体内容 -->
      <el-container class="detail-body">
        <!-- 左侧菜单 -->
        <el-aside width="200px" class="detail-aside">
          <el-menu
            :default-active="activeMenuIndex"
            class="el-menu-vertical-demo"
            @select="handleMenuSelect"
          >
            <el-menu-item-group title="基本信息">
              <el-menu-item index="basic">
                <el-icon><InfoFilled /></el-icon>
                基本信息
              </el-menu-item>
            </el-menu-item-group>
            <el-menu-item-group title="数据源配置">
              <el-menu-item index="datasource">
                <el-icon><Coin /></el-icon>
                数据源配置
              </el-menu-item>
            </el-menu-item-group>
            <el-menu-item-group title="PROMPT配置">
              <el-menu-item index="prompt">
                <el-icon><ChatLineSquare /></el-icon>
                自定义 PROMPT 配置
              </el-menu-item>
            </el-menu-item-group>
            <el-menu-item-group title="知识配置">
              <el-menu-item index="agent-knowledge">
                <el-icon><Document /></el-icon>
                智能体知识配置
              </el-menu-item>
              <el-menu-item index="business-knowledge">
                <el-icon><User /></el-icon>
                业务知识配置
              </el-menu-item>
              <el-menu-item index="semantic-model">
                <el-icon><Suitcase /></el-icon>
                语义模型配置
              </el-menu-item>
            </el-menu-item-group>
            <el-menu-item-group title="预设问题管理">
              <el-menu-item index="preset-questions">
                <el-icon><Setting /></el-icon>
                预设问题管理
              </el-menu-item>
            </el-menu-item-group>
            <el-menu-item-group title="运行与发布">
              <el-menu-item index="go-run">
                <el-icon><VideoPlay /></el-icon>
                前往运行页面
              </el-menu-item>
              <el-menu-item index="access-api">
                <el-icon><Connection /></el-icon>
                访问 API
              </el-menu-item>
            </el-menu-item-group>
          </el-menu>
        </el-aside>

        <!-- 右侧内容 -->
        <el-main class="detail-main">
          <AgentBaseSetting v-if="activeMenuIndex === 'basic'" :agent="agent" />
          <AgentDataSourceConfig
            v-else-if="activeMenuIndex === 'datasource'"
            :agent-id="agent.id"
          />
          <AgentPromptConfig
            v-else-if="activeMenuIndex === 'prompt'"
            :agent-id="agent.id"
            :agent-prompt="agent.prompt"
          />
          <BusinessKnowledgeConfig
            v-else-if="activeMenuIndex === 'business-knowledge'"
            :agent-id="agent.id"
          />
          <AgentSemanticsConfig
            v-else-if="activeMenuIndex === 'semantic-model'"
            :agent-id="agent.id"
          />
          <AgentPresetsConfig
            v-else-if="activeMenuIndex === 'preset-questions'"
            :agent-id="agent.id"
          />
          <AgentAccessApi v-else-if="activeMenuIndex === 'access-api'" :agent-id="agent.id" />
          <AgentKnowledgeConfig
            v-else-if="activeMenuIndex === 'agent-knowledge'"
            :agent-id="agent.id"
          />
          <NotFound v-else />
        </el-main>
      </el-container>
    </el-container>
  </BaseLayout>
</template>

<script lang="ts">
  import { ref, defineComponent, Ref, onMounted } from 'vue';
  import { useRouter } from 'vue-router';
  import { ElMessage } from 'element-plus';
  import AgentService from '@/services/agent';
  import {
    ArrowLeft,
    InfoFilled,
    Coin,
    ChatLineSquare,
    User,
    Suitcase,
    Setting,
    VideoPlay,
    Connection,
    Document,
  } from '@element-plus/icons-vue';
  import BaseLayout from '@/layouts/BaseLayout.vue';
  import AgentBaseSetting from '@/components/agent/BaseSetting.vue';
  import AgentPromptConfig from '@/components/agent/PromptConfig.vue';
  import BusinessKnowledgeConfig from '@/components/agent/BusinessKnowledgeConfig.vue';
  import AgentSemanticsConfig from '@/components/agent/SemanticsConfig.vue';
  import AgentPresetsConfig from '@/components/agent/PresetsConfig.vue';
  import AgentAccessApi from '@/components/agent/AccessApi.vue';
  import AgentDataSourceConfig from '@/components/agent/DataSourceConfig.vue';
  import AgentKnowledgeConfig from '@/components/agent/AgentKnowledgeConfig.vue';
  import NotFound from '@/views/NotFound.vue';
  import { Agent } from '@/services/agent';
  import { fileUploadApi } from '@/services/fileUpload';

  export default defineComponent({
    name: 'AgentDetail',
    components: {
      BaseLayout,
      AgentBaseSetting,
      AgentPromptConfig,
      BusinessKnowledgeConfig,
      AgentSemanticsConfig,
      AgentPresetsConfig,
      AgentAccessApi,
      AgentDataSourceConfig,
      AgentKnowledgeConfig,
      NotFound,
      InfoFilled,
      Coin,
      ChatLineSquare,
      User,
      Suitcase,
      Setting,
      VideoPlay,
      Connection,
      Document,
    },
    setup() {
      const router = useRouter();

      // 响应式数据
      const activeMenuIndex: Ref<string> = ref('basic');
      const agent: Ref<Agent> = ref({
        id: '',
        name: 'loading...',
        description: '',
        status: 'draft',
        createdAt: '',
        updatedAt: '',
        avatar: '',
        prompt: '',
        category: '',
        adminId: '',
        tags: '',
        humanReviewEnabled: false,
      } as Agent);

      const headerFileInput = ref<HTMLInputElement | null>(null);
      const headerUploading = ref(false);
      const showHeaderAvatarButton = ref(false);
      const originalHeaderAvatar = ref<string>('');

      const triggerHeaderFileUpload = () => {
        if (headerFileInput.value) {
          headerFileInput.value.click();
        }
      };

      const handleHeaderFileUpload = async (event: Event) => {
        const target = event.target as HTMLInputElement;
        const file = target.files?.[0];
        if (!file) return;

        // 验证文件类型
        if (!file.type.startsWith('image/')) {
          ElMessage.error('请选择图片文件');
          return;
        }

        if (file.size > 5 * 1024 * 1024) {
          ElMessage.error('图片大小不能超过5MB');
          return;
        }

        try {
          headerUploading.value = true;

          originalHeaderAvatar.value = agent.value.avatar;

          const reader = new FileReader();
          reader.onload = e => {
            agent.value.avatar = e.target?.result as string;
          };
          reader.readAsDataURL(file);

          const response = await fileUploadApi.uploadAvatar(file);

          if (response.success) {
            agent.value.avatar = response.url;
            ElMessage.success('头像上传成功');
          } else {
            throw new Error(response.message || '上传失败');
          }
        } catch (error) {
          ElMessage.error('头像上传失败: ' + (error instanceof Error ? error.message : '未知错误'));
          agent.value.avatar = originalHeaderAvatar.value;
        } finally {
          headerUploading.value = false;
          if (headerFileInput.value) {
            headerFileInput.value.value = '';
          }
        }
      };

      const handleMenuSelect = (index: string) => {
        const id = router.currentRoute.value.params.id;
        activeMenuIndex.value = index;
        if (index === 'go-run') {
          router.push(`/agent/${id}/run`);
        }
      };

      const goBack = () => {
        router.push('/agents');
      };

      const loadAgent = async () => {
        try {
          const id = router.currentRoute.value.params.id;
          const loadAgent = await AgentService.get(id);
          if (loadAgent) {
            agent.value = loadAgent;
          } else {
            throw new Error('Agent 不存在');
          }
        } catch (error) {
          ElMessage.error('加载失败');
          console.error('加载失败:', error);
        }
      };

      onMounted(async () => {
        await loadAgent();
      });

      return {
        ArrowLeft,
        agent,
        activeMenuIndex,
        handleMenuSelect,
        goBack,
        headerFileInput,
        headerUploading,
        showHeaderAvatarButton,
        triggerHeaderFileUpload,
        handleHeaderFileUpload,
      };
    },
  });
</script>

<style scoped>
  /* 页面容器 */
  .detail-page {
    min-height: 100vh;
    background: var(--bg-layout);
    padding: 1.5rem;
    flex-direction: column;
    gap: 0;
    font-family:
      -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  }

  /* 顶部 Header */
  .detail-header {
    background: var(--bg-primary);
    border: 1px solid var(--border-secondary);
    border-radius: var(--radius-lg);
    padding: 1.25rem 1.5rem 0;
    height: auto !important;
    margin-bottom: 1rem;
    box-shadow: var(--shadow-sm);
  }

  /* 返回按钮 */
  .back-btn {
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    color: var(--text-secondary);
    font-size: 0.875rem;
    cursor: pointer;
    padding: 0.4rem 0;
    background: none;
    border: none;
    outline: none;
    transition: color var(--transition-base);
    margin-bottom: 0.75rem;
  }

  .back-btn:hover {
    color: var(--primary-color);
  }

  /* 智能体标题区 */
  .agent-title-section {
    display: flex;
    align-items: center;
    gap: 1rem;
  }

  .agent-title-info {
    flex: 1;
  }

  .agent-name-text {
    font-size: 1.5rem;
    font-weight: 600;
    color: var(--text-primary);
    margin: 0 0 0.25rem 0;
    line-height: 1.3;
  }

  .agent-desc-text {
    font-size: 0.875rem;
    color: var(--text-secondary);
    margin: 0;
    line-height: 1.5;
  }

  /* 主体布局 */
  .detail-body {
    flex: 1;
    gap: 0;
    border: 1px solid var(--border-secondary);
    border-radius: var(--radius-lg);
    overflow: hidden;
    box-shadow: var(--shadow-sm);
  }

  /* 左侧菜单 */
  .detail-aside {
    background: var(--bg-primary);
    border-right: 1px solid var(--border-primary);
    padding-top: 0.5rem;
  }

  /* 右侧内容区 */
  .detail-main {
    background: var(--bg-secondary);
    padding: 1.5rem;
  }

  /* 头像 hover 区域 */
  .avatar-wrapper {
    position: relative;
    width: 60px;
    height: 60px;
    cursor: pointer;
    border-radius: 50%;
    overflow: hidden;
    flex-shrink: 0;
    transition: all 0.3s ease;
  }

  .header-avatar {
    width: 100% !important;
    height: 100% !important;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: opacity 0.3s ease;
  }

  .avatar-wrapper:hover .header-avatar {
    opacity: 0.3;
  }

  .avatar-overlay-header {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: rgba(0, 0, 0, 0.4);
    animation: fadeIn 0.3s ease;
  }

  @keyframes fadeIn {
    from {
      opacity: 0;
    }
    to {
      opacity: 1;
    }
  }

  /* 响应式 */
  @media (max-width: 768px) {
    .detail-page {
      padding: 1rem;
    }

    .agent-name-text {
      font-size: 1.25rem;
    }
  }
</style>
