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
  <div class="error-boundary">
    <div v-if="hasError" class="error-fallback">
      <slot name="fallback" :error="error" :retry="retry">
        <div class="error-content">
          <div class="error-icon">
            <i class="bi bi-exclamation-triangle"></i>
          </div>
          <div class="error-info">
            <h3 class="error-title">{{ title }}</h3>
            <p class="error-message">{{ message }}</p>
            <div v-if="showDetails && error" class="error-details">
              <details>
                <summary>错误详情</summary>
                <pre class="error-stack">{{ error.stack || error.message }}</pre>
              </details>
            </div>
            <div class="error-actions">
              <button class="btn btn-primary" @click="retry">
                <i class="bi bi-arrow-clockwise"></i>
                重试
              </button>
              <button v-if="showReload" class="btn btn-outline" @click="reload">
                <i class="bi bi-arrow-repeat"></i>
                刷新页面
              </button>
            </div>
          </div>
        </div>
      </slot>
    </div>
    <div v-else>
      <slot></slot>
    </div>
  </div>
</template>

<script>
import { ref, onErrorCaptured, nextTick } from 'vue'

export default {
  name: 'ErrorBoundary',
  props: {
    title: {
      type: String,
      default: '出现了一些问题'
    },
    message: {
      type: String,
      default: '抱歉，页面遇到了错误。请尝试刷新页面或联系技术支持。'
    },
    showDetails: {
      type: Boolean,
      default: false
    },
    showReload: {
      type: Boolean,
      default: true
    },
    onError: {
      type: Function,
      default: null
    }
  },
  emits: ['error', 'retry'],
  setup(props, { emit }) {
    const hasError = ref(false)
    const error = ref(null)

    const retry = async () => {
      hasError.value = false
      error.value = null
      emit('retry')
      
      // 等待下一个tick，让组件重新渲染
      await nextTick()
    }

    const reload = () => {
      window.location.reload()
    }

    onErrorCaptured((err, instance, info) => {
      console.error('ErrorBoundary caught an error:', err)
      console.error('Component instance:', instance)
      console.error('Error info:', info)

      hasError.value = true
      error.value = err

      // 调用自定义错误处理函数
      if (props.onError) {
        props.onError(err, instance, info)
      }

      // 发出错误事件
      emit('error', {
        error: err,
        instance,
        info
      })

      // 阻止错误继续向上传播
      return false
    })

    return {
      hasError,
      error,
      retry,
      reload
    }
  }
}
</script>

<style scoped>
.error-boundary {
  width: 100%;
  height: 100%;
}

.error-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  padding: 2rem;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.error-content {
  text-align: center;
  max-width: 500px;
}

.error-icon {
  margin-bottom: 1.5rem;
}

.error-icon i {
  font-size: 4rem;
  color: #ff4d4f;
}

.error-info {
  margin-bottom: 2rem;
}

.error-title {
  margin: 0 0 1rem 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: #262626;
}

.error-message {
  margin: 0 0 1.5rem 0;
  color: #8c8c8c;
  line-height: 1.6;
}

.error-details {
  margin: 1.5rem 0;
  text-align: left;
}

.error-details summary {
  cursor: pointer;
  color: #8c8c8c;
  font-size: 0.875rem;
  margin-bottom: 0.5rem;
}

.error-details summary:hover {
  color: #595959;
}

.error-stack {
  background: #f5f5f5;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  padding: 1rem;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.75rem;
  color: #595959;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

.error-actions {
  display: flex;
  gap: 1rem;
  justify-content: center;
  flex-wrap: wrap;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  border: 1px solid transparent;
  border-radius: 6px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  text-decoration: none;
  white-space: nowrap;
}

.btn-primary {
  background: #1890ff;
  color: white;
  border-color: #1890ff;
}

.btn-primary:hover {
  background: #40a9ff;
  border-color: #40a9ff;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.3);
}

.btn-outline {
  background: transparent;
  color: #8c8c8c;
  border-color: #d9d9d9;
}

.btn-outline:hover {
  background: #f5f5f5;
  color: #595959;
  border-color: #8c8c8c;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .error-fallback {
    padding: 1.5rem;
    min-height: 300px;
  }
  
  .error-icon i {
    font-size: 3rem;
  }
  
  .error-title {
    font-size: 1.25rem;
  }
  
  .error-actions {
    flex-direction: column;
    align-items: center;
  }
  
  .btn {
    width: 100%;
    max-width: 200px;
    justify-content: center;
  }
}
</style>
