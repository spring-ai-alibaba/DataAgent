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
    <div class="agent-list-page">
      <!-- 主内容区域 -->
      <main class="main-content">
        <div class="content-header">
          <div class="header-info">
            <h1 class="content-title">智能体管理中心</h1>
            <p class="content-subtitle">创建和管理您的AI智能体，让数据分析更智能</p>
          </div>
          <div class="header-stats">
            <div class="stat-item">
              <div class="stat-number">{{ agents.length }}</div>
              <div class="stat-label">总数量</div>
            </div>
            <div class="stat-item">
              <div class="stat-number">{{ filteredAgents.filter(a => a.status === 'published').length }}</div>
              <div class="stat-label">已发布</div>
            </div>
            <div class="stat-item">
              <div class="stat-number">{{ filteredAgents.filter(a => a.status === 'draft').length }}</div>
              <div class="stat-label">草稿</div>
            </div>
          </div>
        </div>

        <!-- 过滤和搜索区域 -->
        <div class="filter-section">
          <div class="filter-tabs-row">
            <div class="filter-tabs">
              <button class="filter-tab" :class="{ active: activeFilter === 'all' }" @click="setFilter('all')">
                <i class="bi bi-grid-3x3-gap"></i>
                <span>全部智能体</span>
                <span class="tab-count">{{ agents.length }}</span>
              </button>
              <button class="filter-tab" :class="{ active: activeFilter === 'published' }" @click="setFilter('published')">
                <i class="bi bi-check-circle"></i>
                <span>已发布</span>
                <span class="tab-count">{{ agents.filter(a => a.status === 'published').length }}</span>
              </button>
              <button class="filter-tab" :class="{ active: activeFilter === 'draft' }" @click="setFilter('draft')">
                <i class="bi bi-pencil-square"></i>
                <span>草稿</span>
                <span class="tab-count">{{ agents.filter(a => a.status === 'draft').length }}</span>
              </button>
              <button class="filter-tab" :class="{ active: activeFilter === 'offline' }" @click="setFilter('offline')">
                <i class="bi bi-pause-circle"></i>
                <span>已下线</span>
                <span class="tab-count">{{ agents.filter(a => a.status === 'offline').length }}</span>
              </button>
            </div>

            <div class="search-and-actions">
              <div class="search-box">
                <i class="search-icon bi bi-search"></i>
                <input 
                  type="text" 
                  v-model="searchKeyword" 
                  class="form-control"
                  placeholder="搜索智能体名称、ID或描述..." 
                  @input="searchAgents"
                  @keyup.enter="refreshAgentList"
                >
              </div>
              <div class="action-buttons">
                <button class="btn btn-outline" @click="refreshAgentList">
                  <i class="bi bi-search"></i>
                  搜索
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 智能体网格 -->
        <div class="agents-grid" v-if="!loading">
          <div 
            v-for="agent in filteredAgents" 
            :key="agent.id" 
            class="agent-card"
            @click="enterAgent(agent.id)"
          >
            <div class="agent-avatar">
              <div v-if="agent.avatar && agent.avatar.trim()" class="avatar-image">
                <img :src="agent.avatar" :alt="agent.name" @error="handleAvatarError(agent)">
              </div>
              <div v-else class="avatar-icon" :style="{ backgroundColor: getRandomColor(agent.id) }">
                <i :class="getRandomIcon(agent.id)"></i>
              </div>
            </div>
            <div class="agent-info">
              <h3 class="agent-name">{{ agent.name }}</h3>
              <p class="agent-description">{{ agent.description }}</p>
              <div class="agent-meta">
                <span class="agent-id">ID: {{ agent.id }}</span>
                <span class="agent-time">{{ formatTime(agent.updateTime) }}</span>
              </div>
            </div>
            <div class="agent-status">
              <span class="status-badge" :class="agent.status">{{ getStatusText(agent.status) }}</span>
            </div>
            <div class="agent-actions" @click.stop>
              <button class="action-btn" @click="editAgent(agent)">
                <i class="bi bi-pencil"></i>
              </button>
              <button class="action-btn" @click="deleteAgent(agent.id)">
                <i class="bi bi-trash"></i>
              </button>
            </div>
          </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="loading" class="loading-state">
          <div class="spinner"></div>
          <p>加载中...</p>
        </div>

        <!-- 空状态 -->
        <div v-if="!loading && filteredAgents.length === 0" class="empty-state">
          <i class="bi bi-robot"></i>
          <h3>暂无智能体</h3>
          <p>点击"创建智能体"开始创建您的第一个智能体</p>
          <button class="create-first-btn" @click="createNewAgent">创建智能体</button>
        </div>
      </main>

      <!-- 创建智能体模态框 -->
      <div v-if="showCreateModal" class="modal-overlay" @click="closeCreateModal">
        <div class="modal-content" @click.stop>
          <div class="modal-header">
            <h3>创建智能体</h3>
            <button class="close-btn" @click="closeCreateModal">
              <i class="bi bi-x"></i>
            </button>
          </div>
          <div class="modal-body">
            <form @submit.prevent="createAgent">
              <div class="form-group">
                <label for="agentName">智能体名称 *</label>
                <input 
                  type="text" 
                  id="agentName"
                  v-model="newAgent.name" 
                  placeholder="请输入智能体名称"
                  required
                >
              </div>
              <div class="form-group">
                <label for="agentDescription">智能体描述</label>
                <textarea 
                  id="agentDescription"
                  v-model="newAgent.description" 
                  placeholder="请输入智能体描述"
                  rows="3"
                ></textarea>
              </div>
            </form>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeCreateModal">取消</button>
            <button type="button" class="btn btn-primary" @click="createAgent">创建</button>
          </div>
        </div>
      </div>
    </div>
  </BaseLayout>
</template>

<script>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import BaseLayout from '../layouts/BaseLayout.vue'
import { agentApi } from '../services/api.js'

export default {
  name: 'AgentList',
  components: {
    BaseLayout
  },
  setup() {
    const router = useRouter()
    const loading = ref(true)
    const activeFilter = ref('all')
    const searchKeyword = ref('')
    const showCreateModal = ref(false)
    const agents = ref([])
    const newAgent = reactive({
      name: '',
      description: ''
    })

    // 模拟数据
    const useMockData = ref(true) // 开发时使用模拟数据

    const mockAgents = [
      {
        id: 1,
        name: '中国人口GDP数据智能体',
        description: '专门处理中国人口和GDP相关数据查询分析的智能体',
        status: 'published',
        avatar: '',
        updateTime: '2025/9/22',
        createTime: '2025/9/22'
      },
      {
        id: 2,
        name: '销售数据分析智能体',
        description: '专注于销售数据分析和业务指标计算的智能体',
        status: 'published',
        avatar: '',
        updateTime: '2025/9/25',
        createTime: '2025/9/25'
      },
      {
        id: 3,
        name: '财务报表智能体',
        description: '专门处理财务数据报表分析的智能体',
        status: 'draft',
        avatar: '',
        updateTime: '2025/9/22',
        createTime: '2025/9/22'
      },
      {
        id: 4,
        name: '库存管理智能体',
        description: '专注于库存数据管理和供应链分析的智能体',
        status: 'published',
        avatar: '',
        updateTime: '2025/9/22',
        createTime: '2025/9/22'
      }
    ]

    // 计算属性
    const filteredAgents = computed(() => {
      let filtered = agents.value

      // 按状态过滤
      if (activeFilter.value !== 'all') {
        filtered = filtered.filter(agent => agent.status === activeFilter.value)
      }

      // 按关键词搜索
      if (searchKeyword.value.trim()) {
        const keyword = searchKeyword.value.toLowerCase()
        filtered = filtered.filter(agent => 
          agent.name.toLowerCase().includes(keyword) ||
          agent.description.toLowerCase().includes(keyword) ||
          agent.id.toString().includes(keyword)
        )
      }

      return filtered
    })

    // 方法
    const setFilter = (filter) => {
      activeFilter.value = filter
    }

    const refreshAgentList = async () => {
      await loadAgents()
    }

    const loadAgents = async () => {
      loading.value = true
      try {
        if (useMockData.value) {
          // 使用模拟数据
          await new Promise(resolve => setTimeout(resolve, 1000)) // 模拟加载延迟
          agents.value = mockAgents
        } else {
          // 使用真实 API
          const response = await agentApi.getList()
          agents.value = response.data || []
        }
      } catch (error) {
        console.error('加载智能体列表失败:', error)
        agents.value = []
      } finally {
        loading.value = false
      }
    }

    const searchAgents = () => {
      // 搜索逻辑已通过计算属性实现
    }

    const enterAgent = (agentId) => {
      router.push(`/agent/${agentId}`)
    }

    const createNewAgent = () => {
      showCreateModal.value = true
    }

    const editAgent = (agent) => {
      // 编辑智能体逻辑
      console.log('编辑智能体:', agent)
    }

    const deleteAgent = async (agentId) => {
      if (!confirm('确定要删除这个智能体吗？')) {
        return
      }

      try {
        if (useMockData.value) {
          // 使用模拟数据
          const index = agents.value.findIndex(agent => agent.id === agentId)
          if (index !== -1) {
            agents.value.splice(index, 1)
          }
        } else {
          // 使用真实 API
          await agentApi.delete(agentId)
          await loadAgents()
        }
        alert('删除成功')
      } catch (error) {
        console.error('删除失败:', error)
        alert('删除失败，请重试')
      }
    }

    const createAgent = async () => {
      if (!newAgent.name.trim()) {
        alert('请填写智能体名称')
        return
      }

      try {
        if (useMockData.value) {
          // 使用模拟数据
          const newId = Math.max(...agents.value.map(a => a.id)) + 1
          agents.value.push({
            id: newId,
            name: newAgent.name,
            description: newAgent.description || '暂无描述',
            status: 'draft',
            avatar: '',
            updateTime: new Date().toLocaleDateString('zh-CN'),
            createTime: new Date().toLocaleDateString('zh-CN')
          })
        } else {
          // 使用真实 API
          await agentApi.create(newAgent)
          await loadAgents()
        }
        
        // 重置表单
        newAgent.name = ''
        newAgent.description = ''
        showCreateModal.value = false
        alert('创建成功')
      } catch (error) {
        console.error('创建失败:', error)
        alert('创建失败，请重试')
      }
    }

    const closeCreateModal = () => {
      showCreateModal.value = false
      newAgent.name = ''
      newAgent.description = ''
    }

    const getStatusText = (status) => {
      const statusMap = {
        published: '已发布',
        draft: '草稿',
        offline: '已下线'
      }
      return statusMap[status] || status
    }

    const formatTime = (time) => {
      if (!time) return ''
      return time.replace(/\//g, '/')
    }

    // 随机颜色生成
    const colors = [
      '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
      '#DDA0DD', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E9'
    ]

    const getRandomColor = (id) => {
      return colors[id % colors.length]
    }

    // 随机图标生成
    const icons = [
      'bi-robot', 'bi-cpu', 'bi-gear', 'bi-lightning', 'bi-star',
      'bi-heart', 'bi-shield', 'bi-gem', 'bi-fire', 'bi-magic'
    ]

    const getRandomIcon = (id) => {
      return icons[id % icons.length]
    }

    // 头像加载失败处理
    const handleAvatarError = (agent) => {
      console.error('智能体头像加载失败:', agent.name, agent.avatar)
      // 清空avatar字段，这样会显示默认图标
      agent.avatar = ''
    }

    // 生命周期
    onMounted(() => {
      loadAgents()
    })

    return {
      loading,
      activeFilter,
      searchKeyword,
      showCreateModal,
      agents,
      newAgent,
      filteredAgents,
      setFilter,
      searchAgents,
      enterAgent,
      createNewAgent,
      editAgent,
      deleteAgent,
      createAgent,
      closeCreateModal,
      getStatusText,
      formatTime,
      getRandomColor,
      getRandomIcon,
      handleAvatarError,
      refreshAgentList
    }
  }
}
</script>

<style scoped>
.agent-list-page {
  min-height: 100vh;
  background: #f8fafc;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}

/* 主内容区域 */
.main-content {
  max-width: 1400px;
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

.header-stats {
  display: flex;
  gap: 2rem;
}

.stat-item {
  text-align: center;
}

.stat-number {
  font-size: 2rem;
  font-weight: 700;
  color: #3b82f6;
  line-height: 1;
}

.stat-label {
  font-size: 0.875rem;
  color: #6b7280;
  margin-top: 0.25rem;
}

/* 过滤和搜索区域 */
.filter-section {
  margin-bottom: 2rem;
}

.filter-tabs-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.filter-tabs {
  display: flex;
  gap: 1rem;
}

.filter-tab {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: white;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 0.875rem;
  font-weight: 500;
}

.filter-tab:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.filter-tab.active {
  background: #3b82f6;
  color: white;
  border-color: #3b82f6;
}

.tab-count {
  background: #f3f4f6;
  color: #6b7280;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
}

.filter-tab.active .tab-count {
  background: rgba(255, 255, 255, 0.2);
  color: white;
}

.search-and-actions {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.search-box {
  position: relative;
  display: flex;
  align-items: center;
}

.search-box input {
  width: 300px;
  padding: 0.75rem 1rem 0.75rem 2.5rem;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 0.875rem;
  background: white;
}

.search-box input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.search-icon {
  position: absolute;
  left: 0.75rem;
  color: #9ca3af;
  font-size: 1rem;
}

.action-buttons {
  display: flex;
  gap: 0.5rem;
}

.btn {
  padding: 0.75rem 1.5rem;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  border: none;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.btn-outline {
  background: white;
  color: #6b7280;
  border: 1px solid #e5e7eb;
}

.btn-outline:hover {
  background: #f9fafb;
  border-color: #d1d5db;
}

.btn-primary {
  background: #3b82f6;
  color: white;
}

.btn-primary:hover {
  background: #2563eb;
}

/* 智能体网格 */
.agents-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.agent-card {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid #e5e7eb;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.agent-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}

.agent-avatar {
  display: flex;
  justify-content: center;
  margin-bottom: 1rem;
}

.avatar-image img {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  object-fit: cover;
}

.avatar-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 1.5rem;
}

.agent-info {
  text-align: center;
  margin-bottom: 1rem;
}

.agent-name {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 0.5rem 0;
}

.agent-description {
  color: #6b7280;
  font-size: 0.875rem;
  line-height: 1.5;
  margin: 0 0 0.75rem 0;
}

.agent-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.75rem;
  color: #9ca3af;
}

.agent-status {
  position: absolute;
  top: 1rem;
  right: 1rem;
}

.status-badge {
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
}

.status-badge.published {
  background: #d1fae5;
  color: #065f46;
}

.status-badge.draft {
  background: #fef3c7;
  color: #92400e;
}

.status-badge.offline {
  background: #fee2e2;
  color: #991b1b;
}

.agent-actions {
  position: absolute;
  top: 1rem;
  left: 1rem;
  display: flex;
  gap: 0.5rem;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.agent-card:hover .agent-actions {
  opacity: 1;
}

.action-btn {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: none;
  background: rgba(255, 255, 255, 0.9);
  color: #6b7280;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.action-btn:hover {
  background: white;
  color: #374151;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

/* 加载状态 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  color: #6b7280;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #e5e7eb;
  border-top: 4px solid #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 1rem;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  text-align: center;
  color: #6b7280;
}

.empty-state i {
  font-size: 4rem;
  color: #d1d5db;
  margin-bottom: 1rem;
}

.empty-state h3 {
  font-size: 1.5rem;
  font-weight: 600;
  color: #374151;
  margin: 0 0 0.5rem 0;
}

.empty-state p {
  margin: 0 0 2rem 0;
  font-size: 1rem;
}

.create-first-btn {
  background: #3b82f6;
  color: white;
  border: none;
  padding: 0.75rem 1.5rem;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.create-first-btn:hover {
  background: #2563eb;
}

/* 模态框样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 12px;
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.5rem;
  border-bottom: 1px solid #e5e7eb;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: #1f2937;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  color: #6b7280;
  cursor: pointer;
  padding: 0.25rem;
}

.close-btn:hover {
  color: #374151;
}

.modal-body {
  padding: 1.5rem;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #374151;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.875rem;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1.5rem;
  border-top: 1px solid #e5e7eb;
}

.btn-secondary {
  background: #f9fafb;
  color: #374151;
  border: 1px solid #d1d5db;
}

.btn-secondary:hover {
  background: #f3f4f6;
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
  
  .filter-tabs-row {
    flex-direction: column;
    align-items: stretch;
    gap: 1rem;
  }
  
  .filter-tabs {
    justify-content: center;
  }
  
  .search-box input {
    width: 100%;
  }
  
  .agents-grid {
    grid-template-columns: 1fr;
  }
}
</style>