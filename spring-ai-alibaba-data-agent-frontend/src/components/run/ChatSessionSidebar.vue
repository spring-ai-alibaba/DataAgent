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
  <el-aside width="320px" style="background-color: white; border-right: 1px solid #e8e8e8">
    <!-- 顶部操作栏 -->
    <div class="sidebar-header">
      <div class="header-controls">
        <el-button type="primary" @click="goBack" circle>
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <el-avatar :src="agent.avatar" size="large">{{ agent.name }}</el-avatar>
        <el-button type="danger" @click="clearAllSessions" circle>
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
      <div class="new-session-section">
        <el-button type="primary" @click="createNewSession" style="width: 100%">
          <el-icon><Plus /></el-icon>
          新建会话
        </el-button>
      </div>
    </div>

    <el-divider style="margin: 0" />

    <!-- 会话列表 -->
    <div class="session-list" style="margin-top: 20px">
      <div
        v-for="session in sessions"
        :key="session.id"
        :class="[
          'session-item',
          { active: handleGetCurrentSession()?.id === session.id, pinned: session.isPinned },
        ]"
        @click="handleSelectSession(session)"
      >
        <div class="session-header">
          <span class="session-title">{{ session.title || '新会话' }}</span>
          <div class="session-actions">
            <el-button type="text" size="small" @click.stop="togglePinSession(session)">
              <el-icon>
                <StarFilled v-if="session.isPinned" />
                <Star v-else />
              </el-icon>
            </el-button>
            <el-button type="text" size="small" @click.stop="deleteSession(session)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
        <div class="session-preview">
          {{ getSessionPreview(session) }}
        </div>
        <div class="session-time">
          {{ formatTime(session.updateTime || session.createTime) }}
        </div>
      </div>
    </div>
  </el-aside>
</template>

<script lang="ts">
  import { defineComponent, PropType } from 'vue';
  import { ref, onMounted, computed } from 'vue';
  import { useRouter, useRoute } from 'vue-router';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import ChatService from '../../services/chat';
  import { ArrowLeft, Plus, Delete, Star, StarFilled } from '@element-plus/icons-vue';
  import { type Agent } from '../../services/agent';
  import { type ChatSession } from '../../services/chat';

  export default defineComponent({
    name: 'ChatSessionSidebar',
    components: {
      ArrowLeft,
      Plus,
      Delete,
      Star,
      StarFilled,
    },
    props: {
      agent: {
        type: Object as PropType<Agent>,
        required: true,
      },
      handleSetCurrentSession: {
        type: Function as PropType<(session: ChatSession | null) => Promise<void>>,
        required: true,
      },
      handleGetCurrentSession: {
        type: Function as PropType<() => ChatSession | null>,
        required: true,
      },
      handleSelectSession: {
        type: Function as PropType<(session: ChatSession) => Promise<void>>,
        required: true,
      },
    },
    setup(props) {
      const sessions = ref<ChatSession[]>([]);

      const router = useRouter();
      const route = useRoute();

      const getSessionPreview = (session: ChatSession) => {
        // todo: 这里应该从消息中获取预览，暂时返回标题
        return session.title;
      };

      const formatTime = (time: Date | string | undefined) => {
        if (!time) return '';
        const date = new Date(time);
        return date.toLocaleString('zh-CN');
      };

      // 计算属性
      const agentId = computed(() => route.params.id as string);

      // 方法
      const goBack = () => {
        router.push(`/agent/${agentId.value}`);
      };

      const loadSessions = async () => {
        try {
          sessions.value = await ChatService.getAgentSessions(parseInt(agentId.value));
          // 默认选择第一个会话或创建新会话
          if (sessions.value.length > 0) {
            await props.handleSelectSession(sessions.value[0]);
          } else {
            await createNewSession();
          }
        } catch (error) {
          ElMessage.error('加载会话列表失败');
          console.error('加载会话列表失败:', error);
        }
      };

      const createNewSession = async () => {
        try {
          const newSession = await ChatService.createSession(parseInt(agentId.value), '新会话');
          sessions.value.unshift(newSession);
          await props.handleSelectSession(newSession);
          ElMessage.success('新会话创建成功');
        } catch (error) {
          ElMessage.error('创建会话失败');
          console.error('创建会话失败:', error);
        }
      };

      const togglePinSession = async (session: ChatSession) => {
        try {
          await ChatService.pinSession(session.id, !session.isPinned);
          session.isPinned = !session.isPinned;
          ElMessage.success(session.isPinned ? '会话已置顶' : '会话已取消置顶');
        } catch (error) {
          ElMessage.error('操作失败');
          console.error('置顶会话失败:', error);
        }
      };

      const deleteSession = async (session: ChatSession) => {
        try {
          await ElMessageBox.confirm('确定要删除这个会话吗？', '确认删除', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning',
          });
          await ChatService.deleteSession(session.id);
          sessions.value = sessions.value.filter((s: ChatSession) => s.id !== session.id);
          if (props.handleGetCurrentSession() == session) {
            await props.handleSetCurrentSession(null);
          }
          ElMessage.success('会话删除成功');
        } catch (error) {
          if (error !== 'cancel') {
            ElMessage.error('删除会话失败');
            console.error('删除会话失败:', error);
          }
        }
      };

      const clearAllSessions = async () => {
        try {
          await ElMessageBox.confirm('确定要清空所有会话吗？此操作不可恢复。', '确认清空', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning',
          });
          await ChatService.clearAgentSessions(parseInt(agentId.value));
          sessions.value = [];
          await props.handleSetCurrentSession(null);
          ElMessage.success('所有会话已清空');
        } catch (error) {
          if (error !== 'cancel') {
            ElMessage.error('清空会话失败');
            console.error('清空会话失败:', error);
          }
        }
      };

      // 生命周期
      onMounted(async () => {
        await loadSessions();
      });

      return {
        sessions,
        getSessionPreview,
        formatTime,
        goBack,
        createNewSession,
        togglePinSession,
        deleteSession,
        clearAllSessions,
      };
    },
  });
</script>

<style scoped>
  /* 左侧边栏样式 */
  .sidebar-header {
    padding: 20px;
  }

  .header-controls {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
  }

  /* 会话列表样式 */
  .session-list {
    max-height: calc(100vh - 200px);
    overflow-y: auto;
    padding: 0 20px 20px;
  }

  .session-item {
    padding: 16px;
    border: 1px solid #e8e8e8;
    border-radius: 8px;
    margin-bottom: 12px;
    cursor: pointer;
    transition: all 0.3s ease;
    background: white;
  }

  .session-item:hover {
    border-color: #409eff;
    background-color: #f8fbff;
  }

  .session-item.active {
    border-color: #409eff;
    background-color: #ecf5ff;
  }

  .session-item.pinned {
    border-left: 4px solid #e6a23c;
  }

  .session-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 8px;
  }

  .session-title {
    font-weight: 600;
    font-size: 14px;
    color: #303133;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    margin-right: 8px;
  }

  .session-actions {
    display: flex;
    gap: 4px;
    flex-shrink: 0;
  }

  .session-preview {
    font-size: 13px;
    color: #606266;
    margin-bottom: 4px;
    line-height: 1.4;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .session-time {
    font-size: 12px;
    color: #909399;
  }

  /* 响应式设计 */
  @media (max-width: 768px) {
    .el-aside {
      width: 250px !important;
    }
  }
</style>
