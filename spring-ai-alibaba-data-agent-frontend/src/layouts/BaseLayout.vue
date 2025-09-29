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
  <div class="base-layout">
    <!-- 现代化头部导航 -->
    <header class="page-header">
      <div class="header-content">
        <div class="brand-section">
          <div class="brand-logo">
            <i class="bi bi-robot"></i>
            <span class="brand-text">Spring AI Alibaba Data Agent</span>
          </div>
          <nav class="header-nav">
            <div 
              class="nav-item" 
              :class="{ active: $route.name === 'AgentList' }"
              @click="goToAgentList"
            >
              <i class="bi bi-grid-3x3-gap"></i>
              <span>智能体列表</span>
            </div>
          </nav>
        </div>
        <div class="header-actions">
          <button class="btn btn-outline btn-sm" @click="showHelp">
            <i class="bi bi-question-circle"></i>
            帮助
          </button>
          <button class="btn btn-primary" @click="goToCreateAgent">
            <i class="bi bi-plus-lg"></i>
            创建智能体
          </button>
        </div>
      </div>
    </header>

    <!-- 页面内容区域 -->
    <main class="page-content">
      <slot></slot>
    </main>
  </div>
</template>

<script>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

export default {
  name: 'BaseLayout',
  setup() {
    const router = useRouter()

    // 判断是否为分析相关页面
    const isAnalysisPage = computed(() => {
      const analysisRoutes = ['Home', 'NL2SQL', 'BusinessKnowledge', 'SemanticModel']
      return analysisRoutes.includes(router.currentRoute.value.name)
    })

    // 导航方法
    const goToAgentList = () => {
      router.push('/agents')
    }

    const goToCreateAgent = () => {
      router.push('/agent/create')
    }

    const showHelp = () => {
      console.log('帮助')
      ElMessage.primary('正在开发中...')
    }

    return {
      isAnalysisPage,
      goToAgentList,
      goToCreateAgent,
      showHelp
    }
  }
}
</script>

<style scoped>
.base-layout {
  min-height: 100vh;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
}

.page-header {
  background: white;
  border-bottom: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 1.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 4rem;
}

.brand-section {
  display: flex;
  align-items: center;
  gap: 2rem;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 1.25rem;
  font-weight: 600;
  color: #1e293b;
}

.brand-logo i {
  font-size: 1.5rem;
  color: #3b82f6;
}

.header-nav {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  color: #64748b;
  font-weight: 500;
}

.nav-item:hover {
  background: #f1f5f9;
  color: #334155;
}

.nav-item.active {
  background: #e0f2fe;
  color: #0369a1;
}

.nav-item i {
  font-size: 1rem;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.page-content {
  flex: 1;
  padding: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header-content {
    padding: 0 1rem;
    height: 3.5rem;
  }

  .brand-section {
    gap: 1rem;
  }

  .brand-logo {
    font-size: 1.1rem;
  }

  .brand-logo i {
    font-size: 1.25rem;
  }

  .header-nav {
    gap: 0.25rem;
  }

  .nav-item {
    padding: 0.375rem 0.75rem;
    font-size: 0.9rem;
  }

  .nav-item span {
    display: none;
  }

  .header-actions {
    gap: 0.5rem;
  }

  .header-actions .btn {
    padding: 0.5rem;
  }

  .header-actions .btn span {
    display: none;
  }
}
</style>
