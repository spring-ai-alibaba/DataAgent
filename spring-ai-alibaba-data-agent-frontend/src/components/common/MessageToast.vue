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
  <div class="message-toast" :class="toastClass" v-if="visible">
    <div class="toast-icon">
      <i :class="iconClass"></i>
    </div>
    <div class="toast-content">
      <div class="toast-title" v-if="title">{{ title }}</div>
      <div class="toast-message">{{ message }}</div>
    </div>
    <button v-if="closable" class="toast-close" @click="close">
      <i class="bi bi-x"></i>
    </button>
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted } from 'vue'

export default {
  name: 'MessageToast',
  props: {
    type: {
      type: String,
      default: 'info',
      validator: (value) => ['success', 'warning', 'error', 'info'].includes(value)
    },
    title: {
      type: String,
      default: ''
    },
    message: {
      type: String,
      required: true
    },
    duration: {
      type: Number,
      default: 3000
    },
    closable: {
      type: Boolean,
      default: true
    },
    position: {
      type: String,
      default: 'top-right',
      validator: (value) => ['top-left', 'top-right', 'bottom-left', 'bottom-right'].includes(value)
    }
  },
  emits: ['close'],
  setup(props, { emit }) {
    const visible = ref(true)
    let timer = null

    const toastClass = computed(() => [
      `toast-${props.type}`,
      `toast-${props.position}`
    ])

    const iconClass = computed(() => {
      const icons = {
        success: 'bi bi-check-circle-fill',
        warning: 'bi bi-exclamation-triangle-fill',
        error: 'bi bi-x-circle-fill',
        info: 'bi bi-info-circle-fill'
      }
      return icons[props.type] || icons.info
    })

    const close = () => {
      visible.value = false
      emit('close')
    }

    const startTimer = () => {
      if (props.duration > 0) {
        timer = setTimeout(() => {
          close()
        }, props.duration)
      }
    }

    onMounted(() => {
      startTimer()
    })

    onUnmounted(() => {
      if (timer) {
        clearTimeout(timer)
      }
    })

    return {
      visible,
      toastClass,
      iconClass,
      close
    }
  }
}
</script>

<style scoped>
.message-toast {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1rem;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  margin-bottom: 0.75rem;
  max-width: 400px;
  animation: slideIn 0.3s ease-out;
  position: relative;
  overflow: hidden;
}

.message-toast::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: currentColor;
}

.toast-icon {
  flex-shrink: 0;
  margin-top: 0.125rem;
}

.toast-icon i {
  font-size: 1.25rem;
}

.toast-content {
  flex: 1;
  min-width: 0;
}

.toast-title {
  font-weight: 600;
  font-size: 0.875rem;
  margin-bottom: 0.25rem;
  line-height: 1.4;
}

.toast-message {
  font-size: 0.875rem;
  line-height: 1.5;
  word-wrap: break-word;
}

.toast-close {
  flex-shrink: 0;
  background: none;
  border: none;
  padding: 0.25rem;
  cursor: pointer;
  border-radius: 4px;
  transition: background-color 0.2s;
  margin-top: -0.25rem;
  margin-right: -0.25rem;
}

.toast-close:hover {
  background: rgba(0, 0, 0, 0.1);
}

.toast-close i {
  font-size: 1rem;
}

/* 类型样式 */
.toast-success {
  background: #f6ffed;
  color: #52c41a;
  border: 1px solid #b7eb8f;
}

.toast-warning {
  background: #fffbe6;
  color: #faad14;
  border: 1px solid #ffe58f;
}

.toast-error {
  background: #fff2f0;
  color: #ff4d4f;
  border: 1px solid #ffccc7;
}

.toast-info {
  background: #e6f7ff;
  color: #1890ff;
  border: 1px solid #91d5ff;
}

/* 位置样式 */
.toast-top-left {
  position: fixed;
  top: 1rem;
  left: 1rem;
  z-index: 1000;
}

.toast-top-right {
  position: fixed;
  top: 1rem;
  right: 1rem;
  z-index: 1000;
}

.toast-bottom-left {
  position: fixed;
  bottom: 1rem;
  left: 1rem;
  z-index: 1000;
}

.toast-bottom-right {
  position: fixed;
  bottom: 1rem;
  right: 1rem;
  z-index: 1000;
}

/* 动画 */
@keyframes slideIn {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

/* 响应式 */
@media (max-width: 768px) {
  .message-toast {
    max-width: calc(100vw - 2rem);
    margin: 0 1rem 0.75rem 1rem;
  }

  .toast-top-left,
  .toast-top-right,
  .toast-bottom-left,
  .toast-bottom-right {
    position: fixed;
    top: auto;
    bottom: 1rem;
    left: 1rem;
    right: 1rem;
  }
}
</style>
