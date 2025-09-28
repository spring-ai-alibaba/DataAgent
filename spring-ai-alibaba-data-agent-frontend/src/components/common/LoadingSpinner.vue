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
  <div class="loading-spinner" :class="sizeClass">
    <div class="spinner" :class="typeClass">
      <div v-if="type === 'dots'" class="dots">
        <div class="dot"></div>
        <div class="dot"></div>
        <div class="dot"></div>
      </div>
      <div v-else-if="type === 'pulse'" class="pulse"></div>
      <div v-else class="circle"></div>
    </div>
    <div v-if="text" class="loading-text">{{ text }}</div>
  </div>
</template>

<script>
import { computed } from 'vue'

export default {
  name: 'LoadingSpinner',
  props: {
    type: {
      type: String,
      default: 'circle',
      validator: (value) => ['circle', 'dots', 'pulse'].includes(value)
    },
    size: {
      type: String,
      default: 'medium',
      validator: (value) => ['small', 'medium', 'large'].includes(value)
    },
    text: {
      type: String,
      default: ''
    },
    color: {
      type: String,
      default: 'primary'
    }
  },
  setup(props) {
    const sizeClass = computed(() => `spinner-${props.size}`)
    const typeClass = computed(() => `spinner-${props.type}`)

    return {
      sizeClass,
      typeClass
    }
  }
}
</script>

<style scoped>
.loading-spinner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
}

.spinner {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 尺寸变体 */
.spinner-small {
  width: 1rem;
  height: 1rem;
}

.spinner-medium {
  width: 2rem;
  height: 2rem;
}

.spinner-large {
  width: 3rem;
  height: 3rem;
}

/* 圆形旋转动画 */
.spinner-circle .circle {
  width: 100%;
  height: 100%;
  border: 2px solid #e2e8f0;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

/* 点状动画 */
.spinner-dots .dots {
  display: flex;
  gap: 0.25rem;
}

.spinner-dots .dot {
  width: 0.5rem;
  height: 0.5rem;
  background: #3b82f6;
  border-radius: 50%;
  animation: bounce 1.4s ease-in-out infinite both;
}

.spinner-dots .dot:nth-child(1) {
  animation-delay: -0.32s;
}

.spinner-dots .dot:nth-child(2) {
  animation-delay: -0.16s;
}

/* 脉冲动画 */
.spinner-pulse .pulse {
  width: 100%;
  height: 100%;
  background: #3b82f6;
  border-radius: 50%;
  animation: pulse 1.5s ease-in-out infinite;
}

/* 文字样式 */
.loading-text {
  font-size: 0.875rem;
  color: #64748b;
  font-weight: 500;
}

.spinner-small + .loading-text {
  font-size: 0.75rem;
}

.spinner-large + .loading-text {
  font-size: 1rem;
}

/* 动画定义 */
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

@keyframes pulse {
  0% {
    transform: scale(0);
    opacity: 1;
  }
  100% {
    transform: scale(1);
    opacity: 0;
  }
}

/* 颜色变体 */
.spinner-circle .circle {
  border-top-color: var(--primary-color, #3b82f6);
}

.spinner-dots .dot {
  background: var(--primary-color, #3b82f6);
}

.spinner-pulse .pulse {
  background: var(--primary-color, #3b82f6);
}
</style>
