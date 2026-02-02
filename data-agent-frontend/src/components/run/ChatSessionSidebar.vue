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
  <el-aside
    :width="collapsed ? '48px' : '320px'"
    class="chat-session-sidebar"
    :class="{ collapsed }"
  >
    <!-- 收起时只显示展开按钮 -->
    <div v-if="collapsed" class="sidebar-collapsed">
      <el-tooltip content="展开会话列表" placement="right">
        <el-button type="primary" circle class="expand-btn" @click="collapsed = false">
          <el-icon><DArrowRight /></el-icon>
        </el-button>
      </el-tooltip>
      <el-tooltip content="返回" placement="right"></el-tooltip>
    </div>

    <!-- 展开时显示完整内容 -->
    <template v-else>
      <!-- 顶部操作栏 -->
      <div class="sidebar-header">
        <div class="header-controls">
          <!-- <el-button type="primary" @click="goBack" circle>
            <el-icon><ArrowLeft /></el-icon>
          </el-button> -->
          <!-- 头像居中 -->
          <el-avatar :src="agent.avatar" size="large" style="margin: 0 auto">
            {{ agent.name }}
          </el-avatar>

          <div class="header-right">
            <el-tooltip content="收起会话列表" placement="bottom">
              <el-button type="default" circle size="large" @click="collapsed = true">
                <el-icon><DArrowLeft /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
        </div>
        <!-- 在一行并且左80%右20%排列 gap8px -->
        <div class="new-session-section d-flex justify-content-between gap-8px">
          <div style="width: 80%">
            <el-button type="primary" @click="createNewSession" style="width: 100%">
              <el-icon><Plus /></el-icon>
              新建会话
            </el-button>
          </div>
          <div>
            <el-button type="danger" @click="clearAllSessions">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
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
            <span
              class="session-title"
              @dblclick="startEditSessionTitle(session)"
              v-if="!session.editing"
            >
              {{ session.title || '新会话' }}
            </span>
            <el-input
              v-else
              v-model="session.editingTitle"
              size="small"
              @blur="saveSessionTitle(session)"
              @keyup.enter="saveSessionTitle(session)"
              @keyup.esc="cancelEditSessionTitle(session)"
              ref="sessionTitleInputRef"
            />
            <div class="session-actions">
              <el-button type="text" size="small" @click.stop="startEditSessionTitle(session)">
                <el-icon><Edit /></el-icon>
              </el-button>
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
          <div class="session-time">
            {{ formatTime(session.updateTime || session.createTime) }}
          </div>
        </div>
      </div>
    </template>
  </el-aside>
</template>

<script lang="ts">
  import { defineComponent, PropType } from 'vue';
  import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue';
  import { useRouter, useRoute } from 'vue-router';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import ChatService from '../../services/chat';
  import {
    Plus,
    Delete,
    Star,
    StarFilled,
    Edit,
    DArrowLeft,
    DArrowRight,
  } from '@element-plus/icons-vue';
  import { type Agent } from '../../services/agent';
  import { type ChatSession } from '../../services/chat';

  // 扩展ChatSession接口以包含编辑相关属性
  interface ExtendedChatSession extends ChatSession {
    editing?: boolean;
    editingTitle?: string;
  }

  interface SessionUpdateEvent {
    type: string;
    sessionId: string;
    title: string;
  }

  export default defineComponent({
    name: 'ChatSessionSidebar',
    components: {
      Plus,
      Delete,
      Star,
      StarFilled,
      Edit,
      DArrowLeft,
      DArrowRight,
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
      handleDeleteSessionState: {
        type: Function as PropType<(sessionId: string) => void>,
        required: true,
      },
    },
    setup(props) {
      const sessions = ref<ExtendedChatSession[]>([]);
      const collapsed = ref(false);
      const sessionEventSource = ref<EventSource | null>(null);
      let reconnectTimer: number | null = null;
      let isComponentActive = true;

      const router = useRouter();
      const route = useRoute();

      const formatTime = (time: Date | string | undefined) => {
        if (!time) return '';
        const date = new Date(time);
        return date.toLocaleString('zh-CN');
      };

      const clearReconnectTimer = () => {
        if (reconnectTimer) {
          window.clearTimeout(reconnectTimer);
          reconnectTimer = null;
        }
      };

      const handleTitleUpdate = (eventData: SessionUpdateEvent) => {
        if (!eventData?.sessionId) {
          return;
        }
        const target = sessions.value.find(session => session.id === eventData.sessionId);
        if (target) {
          target.title = eventData.title;
          target.editingTitle = eventData.title;
        }
        const current = props.handleGetCurrentSession();
        if (current && current.id === eventData.sessionId) {
          current.title = eventData.title;
        }
      };

      const connectSessionStream = () => {
        clearReconnectTimer();
        const currentAgentId = agentId.value;
        if (!currentAgentId) {
          return;
        }
        if (sessionEventSource.value) {
          sessionEventSource.value.close();
        }
        const source = new EventSource(`/api/agent/${currentAgentId}/sessions/stream`);
        source.addEventListener('title-updated', event => {
          try {
            const data = JSON.parse((event as MessageEvent<string>).data) as SessionUpdateEvent;
            handleTitleUpdate(data);
          } catch (error) {
            console.error('解析会话标题更新失败', error);
          }
        });
        source.onerror = error => {
          console.error('会话推送连接异常:', error);
          source.close();
          sessionEventSource.value = null;
          if (isComponentActive) {
            reconnectTimer = window.setTimeout(() => connectSessionStream(), 3000);
          }
        };
        sessionEventSource.value = source;
      };

      // 开始编辑会话标题
      const startEditSessionTitle = (session: ExtendedChatSession) => {
        session.editing = true;
        session.editingTitle = session.title || '新会话';
        nextTick(() => {
          const input = document.querySelector('.el-input__inner') as HTMLInputElement;
          if (input) {
            input.focus();
            input.select();
          }
        });
      };

      // 保存会话标题
      const saveSessionTitle = async (session: ExtendedChatSession) => {
        if (!session.editingTitle || session.editingTitle.trim() === '') {
          ElMessage.warning('会话标题不能为空');
          return;
        }

        const newTitle = session.editingTitle.trim();
        if (newTitle === session.title) {
          session.editing = false;
          return;
        }

        try {
          await ChatService.renameSession(session.id, newTitle);
          session.title = newTitle;
          session.editing = false;
          ElMessage.success('会话标题已更新');
        } catch (error) {
          ElMessage.error('更新会话标题失败');
          console.error('更新会话标题失败:', error);
        }
      };

      // 取消编辑会话标题
      const cancelEditSessionTitle = (session: ExtendedChatSession) => {
        session.editing = false;
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
          props.handleDeleteSessionState(session.id);
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
          sessions.value.forEach((session: ChatSession) => {
            props.handleDeleteSessionState(session.id);
          });
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
        connectSessionStream();
        await loadSessions();
      });

      onUnmounted(() => {
        isComponentActive = false;
        clearReconnectTimer();
        if (sessionEventSource.value) {
          sessionEventSource.value.close();
          sessionEventSource.value = null;
        }
      });

      return {
        sessions,
        collapsed,
        formatTime,
        goBack,
        createNewSession,
        togglePinSession,
        deleteSession,
        clearAllSessions,
        startEditSessionTitle,
        saveSessionTitle,
        cancelEditSessionTitle,
      };
    },
  });
</script>

<style scoped>
  .chat-session-sidebar {
    background-color: white;
    border-right: 1px solid #e8e8e8;
    transition: width 0.3s ease;
    overflow: hidden;
  }

  .chat-session-sidebar.collapsed {
    overflow: visible;
  }

  /* 收起状态 */
  .sidebar-collapsed {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 16px 0;
    gap: 12px;
  }

  .sidebar-collapsed .expand-btn {
    flex-shrink: 0;
  }

  .sidebar-collapsed .back-btn {
    flex-shrink: 0;
  }

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

  .header-right {
    display: flex;
    align-items: center;
    gap: 8px;
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
