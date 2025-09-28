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
  <div class="agent-debug-panel">
    <!-- 调试头部 -->
    <div class="debug-header">
      <h2>智能体调试</h2>
      <p class="debug-subtitle">测试智能体的响应能力和配置正确性</p>
    </div>

    <!-- 调试结果区域 -->
    <div class="debug-result-section">
      <div class="result-header">
        <div class="result-title">
          <i class="bi bi-terminal"></i>
          调试结果
        </div>
        <div class="result-status" v-if="debugStatus">
          <span class="badge" :class="getStatusClass()">{{ debugStatus }}</span>
        </div>
      </div>
      <div class="result-content" ref="resultContainer">
        <!-- 空状态 -->
        <div v-if="!hasResults" class="empty-state">
          <div class="empty-icon">
            <i class="bi bi-chat-square-text"></i>
          </div>
          <div class="empty-text">
            输入测试问题，查看智能体的响应结果
          </div>
        </div>

        <!-- 结果展示 -->
        <div v-else class="debug-results-container">
          <div v-for="section in streamingSections" :key="section.id"
               class="agent-response-block"
               :data-type="section.type"
               :class="{ 'loading': section.isLoading }">
            <div class="agent-response-title">
              <div class="title-left">
                <i :class="section.icon"></i>
                <span class="title-text">{{ section.title }}</span>
                <span v-if="section.isLoading" class="loading-indicator">
                  <i class="bi bi-three-dots loading-dots"></i>
                </span>
              </div>
              <div class="title-actions">
                <button
                  v-if="section.type === 'sql'"
                  class="copy-button"
                  @click="copyToClipboard(section.rawContent)"
                  title="复制SQL"
                >
                  <i class="bi bi-clipboard"></i>
                </button>
                <span v-if="section.timestamp" class="timestamp">
                  {{ formatTime(section.timestamp) }}
                </span>
              </div>
            </div>
            <div class="agent-response-content" v-html="section.content"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 调试输入区域 -->
    <div class="debug-input-section">
      <div class="input-container">
        <input
          type="text"
          v-model="debugQuery"
          class="debug-input"
          placeholder="请输入问题..."
          :disabled="isDebugging || isInitializing"
          @keyup.enter="startDebug"
          ref="debugInput"
        >
        <button
          class="debug-button"
          :disabled="isDebugging"
          @click="handleDebugClick"
        >
          <i class="bi bi-play-circle" v-if="!isDebugging"></i>
          <div class="spinner" v-else></div>
          {{ isDebugging ? '调试中...' : '开始调试' }}
        </button>
        <button
          class="init-button"
          :disabled="isInitializing || isDebugging"
          @click="initializeDataSource"
        >
          <i class="bi bi-database-gear" v-if="!isInitializing"></i>
          <div class="spinner" v-if="isInitializing"></div>
          {{ isInitializing ? '检查中...' : '检查初始化状态' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'

export default {
  name: 'AgentDebugPanel',
  props: {
    agentId: {
      type: [String, Number],
      required: true
    }
  },
  setup(props) {
    // 调试模式下的 Agent ID 偏移量
    const DEBUG_AGENT_ID_OFFSET = 999999
    const debugAgentId = props.agentId + DEBUG_AGENT_ID_OFFSET

    // 响应式数据
    const debugQuery = ref('')
    const isDebugging = ref(false)
    const isInitializing = ref(false)
    const isInitialized = ref(false)
    const debugStatus = ref('')
    const hasResults = ref(false)
    const debugInput = ref(null)
    const resultContainer = ref(null)
    const streamingSections = ref([])

    // EventSource实例引用
    let currentEventSource = null

    // 获取状态样式类
    const getStatusClass = () => {
      if (debugStatus.value.includes('完成')) return 'badge-success'
      if (debugStatus.value.includes('错误') || debugStatus.value.includes('失败')) return 'badge-error'
      if (debugStatus.value.includes('处理中') || debugStatus.value.includes('调试中')) return 'badge-warning'
      return 'badge-info'
    }

    // 处理调试按钮点击
    const handleDebugClick = () => {
      if (isDebugging.value) return
      if (!debugQuery.value || !debugQuery.value.trim()) {
        debugQuery.value = ''
      }
      startDebug()
    }

    // 开始调试
    const startDebug = () => {
      if (!debugQuery.value.trim() || isDebugging.value) {
        return
      }

      // 清理之前的EventSource连接
      if (currentEventSource) {
        currentEventSource.close()
        currentEventSource = null
      }

      isDebugging.value = true
      debugStatus.value = '正在连接...'
      hasResults.value = true
      streamingSections.value = []

      try {
        const eventSource = new EventSource(`/nl2sql/stream/search?query=${encodeURIComponent(debugQuery.value)}&agentId=${debugAgentId}`)
        currentEventSource = eventSource

        // 流式数据处理逻辑
        const streamState = {
          contentByType: {},
          typeOrder: [],
        }

        const typeMapping = {
          'status': { title: '当前状态', icon: 'bi bi-activity' },
          'rewrite': { title: '需求理解', icon: 'bi bi-pencil-square' },
          'keyword_extract': { title: '关键词提取', icon: 'bi bi-key' },
          'plan_generation': { title: '计划生成', icon: 'bi bi-diagram-3' },
          'schema_recall': { title: 'Schema初步召回', icon: 'bi bi-database-gear' },
          'schema_deep_recall': { title: 'Schema深度召回', icon: 'bi bi-database-fill-gear' },
          'sql': { title: '生成的SQL', icon: 'bi bi-code-square' },
          'execute_sql': { title: '执行SQL', icon: 'bi bi-play-circle' },
          'python_execute': { title: 'Python执行', icon: 'bi bi-play-circle-fill' },
          'python_generate': { title: 'Python代码生成', icon: 'bi bi-code-square-fill' },
          'python_analysis': { title: 'Python分析执行', icon: 'bi bi-code-slash' },
          'validation': { title: '校验', icon: 'bi bi-check-circle' },
          'output_report': { title: '输出报告', icon: 'bi bi-file-earmark-text' },
          'explanation': { title: '解释说明', icon: 'bi bi-info-circle' },
          'result': { title: '查询结果', icon: 'bi bi-table' },
          'error': { title: '解析错误', icon: 'bi bi-exclamation-triangle' }
        }

        const updateDisplay = () => {
          streamingSections.value = []

          for (const type of streamState.typeOrder) {
            const typeInfo = typeMapping[type] || { title: type, icon: 'bi bi-file-text' }
            const content = streamState.contentByType[type] || ''

            streamingSections.value.push({
              id: `${type}-${Date.now()}`,
              type,
              title: typeInfo.title,
              icon: typeInfo.icon,
              content: content,
              rawContent: content,
              timestamp: Date.now(),
              isLoading: !content || content.trim() === ''
            })
          }

          nextTick(() => {
            if (resultContainer.value) {
              resultContainer.value.scrollTop = resultContainer.value.scrollHeight
            }
          })
        }

        eventSource.onmessage = (event) => {
          try {
            let parsedData = JSON.parse(event.data)
            if (typeof parsedData === 'string') {
              parsedData = JSON.parse(parsedData)
            }

            const actualType = parsedData['type']
            const actualData = parsedData['data']

            if (actualType && actualData !== undefined && actualData !== null) {
              let processedData = actualData

              if (actualType === 'sql' && typeof actualData === 'string') {
                processedData = actualData.replace(/^```\s*sql?\s*/i, '').replace(/```\s*$/, '').trim()
              }

              if (!streamState.contentByType.hasOwnProperty(actualType)) {
                streamState.typeOrder.push(actualType)
                streamState.contentByType[actualType] = ''
              }

              if (processedData) {
                streamState.contentByType[actualType] += processedData
              }

              updateDisplay()
            }
          } catch (e) {
            console.error('JSON解析失败:', e, event.data)
          }
        }

        eventSource.addEventListener('complete', () => {
          isDebugging.value = false
          debugStatus.value = '调试完成'
          eventSource.close()
        })

        eventSource.onerror = (error) => {
          console.error('流式连接错误:', error)
          isDebugging.value = false
          debugStatus.value = '连接出错'
          eventSource.close()
        }

      } catch (error) {
        console.error('发送消息失败:', error)
        isDebugging.value = false
        debugStatus.value = '发送失败'
      }
    }

    // 初始化数据源
    const initializeDataSource = async () => {
      if (isInitializing.value || isDebugging.value) return

      try {
        isInitializing.value = true
        debugStatus.value = '正在检查初始化状态...'

        const response = await fetch(`/api/agent/${debugAgentId}/schema/statistics`)
        
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`)
        }
        
        const result = await response.json()
        
        if (result.success) {
          const hasData = result.data && result.data.documentCount > 0
          isInitialized.value = hasData
          
          if (hasData) {
            debugStatus.value = `✅ 数据源已初始化，共有 ${result.data.documentCount} 个向量文档，可以开始调试`
          } else {
            debugStatus.value = '⚠️ 数据源未初始化，请点击"初始化信息源"进行配置'
          }
        } else {
          isInitialized.value = false
          debugStatus.value = `❌ 检查失败: ${result.message || '未知错误'}`
        }
        
      } catch (error) {
        console.error('检查初始化状态错误:', error)
        isInitialized.value = false
        
        if (error.message.includes('Failed to fetch') || error.message.includes('NetworkError')) {
          debugStatus.value = '❌ 网络连接失败，请检查网络状态或后端服务是否正常'
        } else if (error.message.includes('HTTP error')) {
          debugStatus.value = `❌ 服务异常 (${error.message})，请联系管理员`
        } else {
          debugStatus.value = '❌ 检查失败，请确保智能体配置正确'
        }
      } finally {
        isInitializing.value = false
        
        const clearDelay = isInitialized.value ? 5000 : 8000
        setTimeout(() => {
          debugStatus.value = ''
        }, clearDelay)
      }
    }

    // 格式化时间
    const formatTime = (timestamp) => {
      if (!timestamp) return ''
      const date = new Date(timestamp)
      return date.toLocaleTimeString('zh-CN', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      })
    }

    // 复制到剪贴板
    const copyToClipboard = (text) => {
      navigator.clipboard.writeText(text).then(() => {
        console.log('复制成功')
      }).catch(err => {
        console.error('复制失败:', err)
      })
    }

    // 组件销毁时清理EventSource
    onUnmounted(() => {
      if (currentEventSource) {
        currentEventSource.close()
        currentEventSource = null
      }
    })

    // 初始检查状态
    onMounted(() => {
      initializeDataSource()
    })

    return {
      debugQuery,
      isDebugging,
      isInitializing,
      isInitialized,
      debugStatus,
      hasResults,
      debugInput,
      resultContainer,
      streamingSections,
      getStatusClass,
      handleDebugClick,
      startDebug,
      initializeDataSource,
      copyToClipboard,
      formatTime
    }
  }
}
</script>

<style scoped>
.agent-debug-panel {
  padding: 1rem;
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  gap: 1rem;
}

.debug-header {
  background: white;
  padding: 1.5rem;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border: 1px solid #e2e8f0;
}

.debug-header h2 {
  font-size: 1.5rem;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 0.5rem;
}

.debug-subtitle {
  color: #64748b;
  font-size: 0.95rem;
  margin: 0;
}

.debug-result-section {
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid #e2e8f0;
}

.result-header {
  padding: 1rem 1.5rem;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.result-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.result-status .badge {
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 500;
}

.badge-success {
  background-color: #f6ffed;
  color: #52c41a;
}

.badge-error {
  background-color: #fff2f0;
  color: #ff4d4f;
}

.badge-warning {
  background-color: #fffbe6;
  color: #faad14;
}

.badge-info {
  background-color: #e6f7ff;
  color: #1890ff;
}

.result-content {
  flex: 1;
  overflow-y: auto;
  background: #fafbfc;
}

.empty-state {
  text-align: center;
  padding: 4rem 2rem;
  color: #64748b;
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: 1.5rem;
  color: #cbd5e1;
}

.empty-text {
  font-size: 1.2rem;
  margin-bottom: 2rem;
  color: #475569;
  font-weight: 500;
}

.debug-results-container {
  padding: 1.5rem;
}

.agent-response-block {
  margin-bottom: 1.5rem;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  border: 1px solid #e2e8f0;
  overflow: hidden;
  background: white;
}

.agent-response-title {
  padding: 0.75rem 1rem;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.9rem;
  font-weight: 600;
  color: #334155;
}

.title-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.title-text {
  font-weight: 600;
  color: #1e293b;
}

.title-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.timestamp {
  font-size: 0.75rem;
  color: #64748b;
  background: #f1f5f9;
  padding: 0.25rem 0.5rem;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}

.loading-indicator {
  display: flex;
  align-items: center;
}

.loading-dots {
  color: #3b82f6;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.copy-button {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 0.375rem 0.75rem;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 0.8rem;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.copy-button:hover {
  background: #f8fafc;
  border-color: #3b82f6;
  color: #3b82f6;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(59, 130, 246, 0.1);
}

.agent-response-title i {
  color: #3b82f6;
  font-size: 1.1rem;
}

.agent-response-content {
  padding: 1rem;
  font-size: 0.9rem;
  line-height: 1.6;
  color: #475569;
  background: white;
}

.debug-input-section {
  background: white;
  padding: 1.5rem;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border: 1px solid #e2e8f0;
}

.input-container {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.debug-input {
  flex: 1;
  padding: 1rem 1.25rem;
  font-size: 1rem;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  transition: all 0.3s ease;
  outline: none;
  background: #fafbfc;
  color: #1e293b;
}

.debug-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
  background: #ffffff;
}

.debug-input:disabled {
  background-color: #f1f5f9;
  color: #94a3b8;
  cursor: not-allowed;
  border-color: #e2e8f0;
}

.debug-button {
  padding: 1rem 1.5rem;
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 130px;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

.debug-button:hover:not(:disabled) {
  background: linear-gradient(135deg, #1d4ed8 0%, #1e40af 100%);
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.4);
}

.debug-button:disabled {
  background: #e2e8f0;
  color: #94a3b8;
  cursor: not-allowed;
  opacity: 0.6;
  transform: none;
  box-shadow: none;
}

.init-button {
  padding: 1rem 1.5rem;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 160px;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.3);
}

.init-button:hover:not(:disabled) {
  background: linear-gradient(135deg, #059669 0%, #047857 100%);
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(16, 185, 129, 0.4);
}

.init-button:disabled {
  background: #e2e8f0;
  color: #94a3b8;
  cursor: not-allowed;
  opacity: 0.6;
  transform: none;
  box-shadow: none;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .input-container {
    flex-direction: column;
    gap: 0.75rem;
  }

  .debug-input {
    font-size: 0.9rem;
    padding: 0.875rem 1rem;
  }

  .debug-button,
  .init-button {
    font-size: 0.9rem;
    padding: 0.875rem 1.25rem;
    min-width: auto;
    width: 100%;
  }
}
</style>
