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
    <div class="home-page">
      <!-- 页面标题 -->
      <div class="page-header">
        <h1 class="page-title">
          <i class="bi bi-graph-up-arrow"></i>
          NL2SQL 智能分析
        </h1>
        <p class="page-description">
          使用自然语言查询数据库，智能生成SQL语句
        </p>
      </div>

    <div class="container">
      <div class="search-container">
        <input 
          type="text" 
          v-model="query"
          class="search-input" 
          placeholder="例如：查询销售额前10的产品..." 
          :disabled="isInitializing"
          @keyup.enter="performSearch"
          autofocus
        >
        <button 
          class="search-button"
          :disabled="isInitializing"
          @click="performSearch"
        >
          <i class="bi bi-search"></i> 查询
        </button>
        <button 
          class="init-button"
          :class="{ loading: isInitializing }"
          :disabled="isInitializing"
          @click="initializeDataSource"
        >
          <i v-if="!isInitializing" :class="isInitialized ? 'bi bi-check-circle' : 'bi bi-database-add'"></i>
          <span v-if="isInitializing" style="opacity: 0;">初始化数据源</span>
          <span v-else>{{ isInitialized ? '重新初始化' : '初始化数据源' }}</span>
        </button>
      </div>

      <div class="result-container">
        <div class="result-header">
          <div class="result-title">
            <i class="bi bi-file-earmark-text"></i> 查询结果
          </div>
          <div v-if="status" v-html="status"></div>
        </div>
        <div class="result-content" ref="resultsDiv" @scroll="handleScroll">
          <!-- 滚动到底部按钮 -->
          <div v-if="showScrollToBottomBtn" class="scroll-to-bottom-btn-bottom" @click="scrollToBottomManually">
            <i class="bi bi-arrow-down"></i>
            <span>回到底部</span>
          </div>
          <!-- 空状态 -->
          <div v-if="showEmptyState" class="empty-state">
            <div class="empty-icon">
              <i class="bi bi-chat-square-text"></i>
            </div>
            <div class="empty-text">
              输入自然语言问题，查看系统如何将其转换为SQL查询语句
            </div>
            <div class="example-queries">
              <div 
                v-for="example in exampleQueries" 
                :key="example"
                class="example-query"
                @click="useExampleQuery(example)"
              >
                {{ example }}
              </div>
            </div>
          </div>

          <!-- 初始化提示 -->
          <div v-if="initTip.show" :class="initTip.type">
            <div class="init-tip-header">
              <i :class="initTip.icon"></i>
              <span class="init-tip-title">{{ initTip.title }}</span>
              <span class="init-tip-badge" :class="{ error: initTip.type === 'init-error-tip' }">
                {{ initTip.type === 'init-error-tip' ? 'ERROR' : 'SUCCESS' }}
              </span>
            </div>
            <div class="init-tip-content" v-html="initTip.content"></div>
          </div>

          <!-- 查询结果 -->
          <div v-if="!showEmptyState && results.length > 0" class="results">
            <div 
              v-for="(result, index) in results" 
              :key="index" 
              class="result-item"
              :class="{ 'user-message': result.type === 'user', 'assistant-message': result.type === 'assistant' }"
            >
              <div class="message-header">
                <div class="message-avatar">
                  <i :class="result.type === 'user' ? 'bi bi-person-fill' : 'bi bi-robot'"></i>
                </div>
                <div class="message-info">
                  <span class="message-author">{{ result.type === 'user' ? '用户' : 'AI助手' }}</span>
                  <span class="message-time">{{ formatTime(result.timestamp) }}</span>
                </div>
              </div>
              <div class="message-content">
                <div v-if="result.type === 'user'" class="user-query">
                  {{ result.content }}
                </div>
                <div v-else class="assistant-response">
                  <div v-if="result.sql" class="sql-section">
                    <div class="sql-header">
                      <i class="bi bi-code-slash"></i>
                      <span>生成的SQL语句</span>
                      <button class="copy-btn" @click="copyToClipboard(result.sql)">
                        <i class="bi bi-clipboard"></i>
                      </button>
                    </div>
                    <pre class="sql-code"><code>{{ result.sql }}</code></pre>
                  </div>
                  <div v-if="result.explanation" class="explanation-section">
                    <div class="explanation-header">
                      <i class="bi bi-lightbulb"></i>
                      <span>解释说明</span>
                    </div>
                    <div class="explanation-content" v-html="result.explanation"></div>
                  </div>
                  <div v-if="result.data && result.data.length > 0" class="data-section">
                    <div class="data-header">
                      <i class="bi bi-table"></i>
                      <span>查询结果 ({{ result.data.length }} 条记录)</span>
                    </div>
                    <div class="data-table-container">
                      <table class="data-table">
                        <thead>
                          <tr>
                            <th v-for="column in Object.keys(result.data[0])" :key="column">
                              {{ column }}
                            </th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="(row, rowIndex) in result.data" :key="rowIndex">
                            <td v-for="(value, column) in row" :key="column">
                              {{ value }}
                            </td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 加载状态 -->
          <div v-if="isLoading" class="loading-state">
            <div class="loading-spinner"></div>
            <span>AI正在思考中...</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</BaseLayout>
</template>

<script>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import BaseLayout from '../layouts/BaseLayout.vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/default.css'

export default {
  name: 'Home',
  components: {
    BaseLayout
  },
  setup() {
    const router = useRouter()
    const query = ref('')
    const isInitializing = ref(false)
    const isInitialized = ref(false)
    const status = ref('')
    const showEmptyState = ref(true)
    const resultsDiv = ref(null)
    const showScrollToBottomBtn = ref(false)
    const isLoading = ref(false)
    const results = ref([])

    // 示例查询
    const exampleQueries = ref([
      '查询销售额前10的产品',
      '统计每个月的订单数量',
      '找出最受欢迎的商品类别',
      '分析用户购买行为趋势'
    ])

    // 初始化提示
    const initTip = reactive({
      show: false,
      type: '',
      title: '',
      content: '',
      icon: ''
    })

    // 格式化时间
    const formatTime = (timestamp) => {
      return new Date(timestamp).toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit'
      })
    }

    // 复制到剪贴板
    const copyToClipboard = async (text) => {
      try {
        await navigator.clipboard.writeText(text)
        // 可以添加一个提示
        console.log('已复制到剪贴板')
      } catch (err) {
        console.error('复制失败:', err)
      }
    }

    // 使用示例查询
    const useExampleQuery = (example) => {
      query.value = example
      performSearch()
    }

    // 执行搜索
    const performSearch = async () => {
      if (!query.value.trim() || isLoading.value) return

      isLoading.value = true
      showEmptyState.value = false

      // 添加用户消息
      results.value.push({
        type: 'user',
        content: query.value,
        timestamp: Date.now()
      })

      try {
        // 模拟API调用
        await new Promise(resolve => setTimeout(resolve, 2000))

        // 添加AI响应
        results.value.push({
          type: 'assistant',
          sql: `SELECT * FROM products ORDER BY sales DESC LIMIT 10`,
          explanation: '这个查询会返回销售额最高的前10个产品',
          data: [
            { product_name: '产品A', sales: 10000 },
            { product_name: '产品B', sales: 9500 },
            { product_name: '产品C', sales: 9000 }
          ],
          timestamp: Date.now()
        })

        // 滚动到底部
        await nextTick()
        scrollToBottom()
      } catch (error) {
        console.error('搜索失败:', error)
      } finally {
        isLoading.value = false
      }
    }

    // 初始化数据源
    const initializeDataSource = async () => {
      isInitializing.value = true
      
      try {
        // 模拟初始化过程
        await new Promise(resolve => setTimeout(resolve, 3000))
        
        isInitialized.value = true
        initTip.show = true
        initTip.type = 'init-success-tip'
        initTip.title = '数据源初始化成功'
        initTip.content = '数据源已成功连接，可以开始查询了'
        initTip.icon = 'bi bi-check-circle-fill'
        
        // 3秒后隐藏提示
        setTimeout(() => {
          initTip.show = false
        }, 3000)
      } catch (error) {
        initTip.show = true
        initTip.type = 'init-error-tip'
        initTip.title = '数据源初始化失败'
        initTip.content = '请检查数据源配置是否正确'
        initTip.icon = 'bi bi-exclamation-triangle-fill'
      } finally {
        isInitializing.value = false
      }
    }

    // 滚动处理
    const handleScroll = () => {
      if (!resultsDiv.value) return
      
      const { scrollTop, scrollHeight, clientHeight } = resultsDiv.value
      const isNearBottom = scrollTop + clientHeight >= scrollHeight - 100
      showScrollToBottomBtn.value = !isNearBottom && results.value.length > 0
    }

    // 滚动到底部
    const scrollToBottom = () => {
      if (resultsDiv.value) {
        resultsDiv.value.scrollTop = resultsDiv.value.scrollHeight
      }
    }

    // 手动滚动到底部
    const scrollToBottomManually = () => {
      scrollToBottom()
      showScrollToBottomBtn.value = false
    }

    // 生命周期
    onMounted(() => {
      // 初始化marked配置
      marked.setOptions({
        highlight: function(code, lang) {
          if (lang && hljs.getLanguage(lang)) {
            return hljs.highlight(code, { language: lang }).value
          }
          return hljs.highlightAuto(code).value
        }
      })
    })

    return {
      query,
      isInitializing,
      isInitialized,
      status,
      showEmptyState,
      resultsDiv,
      showScrollToBottomBtn,
      isLoading,
      results,
      exampleQueries,
      initTip,
      formatTime,
      copyToClipboard,
      useExampleQuery,
      performSearch,
      initializeDataSource,
      handleScroll,
      scrollToBottom,
      scrollToBottomManually
    }
  }
}
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: #f8fafc;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}

/* 页面头部 */
.page-header {
  text-align: center;
  margin-bottom: 2rem;
  padding: 2rem 0;
}

.page-title {
  font-size: 2rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 0.5rem 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
}

.page-title i {
  color: #3b82f6;
  font-size: 1.75rem;
}

.page-description {
  color: #6b7280;
  font-size: 1.1rem;
  margin: 0;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 2rem;
}

.search-container {
  display: flex;
  gap: 1rem;
  margin-bottom: 2rem;
  align-items: center;
}

.search-input {
  flex: 1;
  padding: 0.75rem 1rem;
  font-size: 1rem;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  transition: all 0.2s ease;
  outline: none;
  background: white;
}

.search-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.search-input:disabled {
  background-color: #f9fafb;
  color: #9ca3af;
  cursor: not-allowed;
  border-color: #d1d5db;
}

.search-button {
  padding: 0.75rem 1.5rem;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 500;
}

.search-button:hover:not(:disabled) {
  background: #2563eb;
  transform: translateY(-1px);
}

.search-button:disabled {
  background: #9ca3af;
  cursor: not-allowed;
  transform: none;
}

.init-button {
  padding: 0.75rem 1.5rem;
  background: #10b981;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 500;
}

.init-button:hover:not(:disabled) {
  background: #059669;
  transform: translateY(-1px);
}

.init-button:disabled {
  background: #9ca3af;
  cursor: not-allowed;
  transform: none;
}

.init-button.loading {
  background: #3b82f6;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
  100% {
    opacity: 1;
  }
}

/* 结果容器 */
.result-container {
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid #e5e7eb;
  overflow: hidden;
}

.result-header {
  padding: 1.5rem;
  border-bottom: 1px solid #e5e7eb;
  background: #f9fafb;
}

.result-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.result-title i {
  color: #3b82f6;
}

.result-content {
  max-height: 600px;
  overflow-y: auto;
  padding: 1.5rem;
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 3rem 2rem;
  color: #6b7280;
}

.empty-icon {
  font-size: 4rem;
  color: #d1d5db;
  margin-bottom: 1rem;
}

.empty-text {
  font-size: 1.1rem;
  margin-bottom: 2rem;
  line-height: 1.6;
}

.example-queries {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1rem;
  max-width: 800px;
  margin: 0 auto;
}

.example-query {
  padding: 1rem;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 0.9rem;
  line-height: 1.5;
}

.example-query:hover {
  background: #f3f4f6;
  border-color: #3b82f6;
  transform: translateY(-1px);
}

/* 初始化提示 */
.init-success-tip {
  background: #d1fae5;
  border: 1px solid #a7f3d0;
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1rem;
}

.init-error-tip {
  background: #fee2e2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1rem;
}

.init-tip-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.init-tip-title {
  font-weight: 600;
  color: #1f2937;
}

.init-tip-badge {
  background: #10b981;
  color: white;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
}

.init-tip-badge.error {
  background: #ef4444;
}

/* 消息样式 */
.result-item {
  margin-bottom: 2rem;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.875rem;
}

.user-message .message-avatar {
  background: #3b82f6;
  color: white;
}

.assistant-message .message-avatar {
  background: #10b981;
  color: white;
}

.message-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.message-author {
  font-weight: 600;
  color: #1f2937;
}

.message-time {
  font-size: 0.75rem;
  color: #9ca3af;
}

.message-content {
  margin-left: 2.5rem;
}

.user-query {
  background: #f3f4f6;
  padding: 1rem;
  border-radius: 8px;
  border-left: 4px solid #3b82f6;
  font-size: 1rem;
  line-height: 1.6;
}

.assistant-response {
  background: #f9fafb;
  padding: 1.5rem;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}

/* SQL部分 */
.sql-section {
  margin-bottom: 1.5rem;
}

.sql-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
  font-weight: 600;
  color: #1f2937;
}

.sql-header i {
  color: #3b82f6;
}

.copy-btn {
  margin-left: auto;
  background: #6b7280;
  color: white;
  border: none;
  border-radius: 4px;
  padding: 0.25rem 0.5rem;
  cursor: pointer;
  font-size: 0.75rem;
  transition: all 0.2s ease;
}

.copy-btn:hover {
  background: #4b5563;
}

.sql-code {
  background: #1f2937;
  color: #f9fafb;
  padding: 1rem;
  border-radius: 6px;
  overflow-x: auto;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.875rem;
  line-height: 1.5;
}

/* 解释部分 */
.explanation-section {
  margin-bottom: 1.5rem;
}

.explanation-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
  font-weight: 600;
  color: #1f2937;
}

.explanation-header i {
  color: #f59e0b;
}

.explanation-content {
  color: #374151;
  line-height: 1.6;
}

/* 数据表格 */
.data-section {
  margin-bottom: 1.5rem;
}

.data-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
  font-weight: 600;
  color: #1f2937;
}

.data-header i {
  color: #10b981;
}

.data-table-container {
  overflow-x: auto;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.data-table th {
  background: #f9fafb;
  padding: 0.75rem;
  text-align: left;
  font-weight: 600;
  color: #374151;
  border-bottom: 1px solid #e5e7eb;
}

.data-table td {
  padding: 0.75rem;
  border-bottom: 1px solid #f3f4f6;
  color: #6b7280;
}

.data-table tr:last-child td {
  border-bottom: none;
}

/* 加载状态 */
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 2rem;
  color: #6b7280;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid #e5e7eb;
  border-top: 2px solid #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 滚动到底部按钮 */
.scroll-to-bottom-btn-bottom {
  position: sticky;
  bottom: 1rem;
  left: 50%;
  transform: translateX(-50%);
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 20px;
  padding: 0.5rem 1rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
  transition: all 0.2s ease;
}

.scroll-to-bottom-btn-bottom:hover {
  background: #2563eb;
  transform: translateX(-50%) translateY(-1px);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .container {
    padding: 0 1rem;
  }
  
  .search-container {
    flex-direction: column;
    align-items: stretch;
  }
  
  .search-button,
  .init-button {
    width: 100%;
    justify-content: center;
  }
  
  .example-queries {
    grid-template-columns: 1fr;
  }
  
  .message-content {
    margin-left: 0;
  }
  
  .data-table-container {
    font-size: 0.75rem;
  }
  
  .data-table th,
  .data-table td {
    padding: 0.5rem;
  }
}
</style>