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
  <div class="search-box" :class="sizeClass">
    <div class="search-icon">
      <i class="bi bi-search"></i>
    </div>
    <input
      ref="inputRef"
      type="text"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :class="inputClass"
      @input="handleInput"
      @focus="handleFocus"
      @blur="handleBlur"
      @keyup.enter="handleSearch"
    />
    <button
      v-if="clearable && modelValue"
      class="clear-btn"
      @click="handleClear"
      type="button"
    >
      <i class="bi bi-x"></i>
    </button>
    <button
      v-if="searchable"
      class="search-btn"
      @click="handleSearch"
      type="button"
      :disabled="disabled"
    >
      <i class="bi bi-search"></i>
    </button>
  </div>
</template>

<script>
import { ref, computed } from 'vue'

export default {
  name: 'SearchBox',
  props: {
    modelValue: {
      type: String,
      default: ''
    },
    placeholder: {
      type: String,
      default: '请输入搜索关键词...'
    },
    disabled: {
      type: Boolean,
      default: false
    },
    size: {
      type: String,
      default: 'medium',
      validator: (value) => ['small', 'medium', 'large'].includes(value)
    },
    clearable: {
      type: Boolean,
      default: true
    },
    searchable: {
      type: Boolean,
      default: false
    },
    status: {
      type: String,
      default: '',
      validator: (value) => ['', 'error', 'success'].includes(value)
    }
  },
  emits: ['update:modelValue', 'search', 'focus', 'blur', 'clear'],
  setup(props, { emit }) {
    const inputRef = ref(null)

    const sizeClass = computed(() => `search-box-${props.size}`)
    const inputClass = computed(() => [
      'search-input',
      props.status ? `is-${props.status}` : ''
    ])

    const handleInput = (event) => {
      emit('update:modelValue', event.target.value)
    }

    const handleFocus = (event) => {
      emit('focus', event)
    }

    const handleBlur = (event) => {
      emit('blur', event)
    }

    const handleSearch = () => {
      if (!props.disabled) {
        emit('search', props.modelValue)
      }
    }

    const handleClear = () => {
      emit('update:modelValue', '')
      emit('clear')
      inputRef.value?.focus()
    }

    const focus = () => {
      inputRef.value?.focus()
    }

    const blur = () => {
      inputRef.value?.blur()
    }

    return {
      inputRef,
      sizeClass,
      inputClass,
      handleInput,
      handleFocus,
      handleBlur,
      handleSearch,
      handleClear,
      focus,
      blur
    }
  }
}
</script>

<style scoped>
.search-box {
  position: relative;
  display: flex;
  align-items: center;
  background: white;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  transition: all 0.2s ease;
  overflow: hidden;
}

.search-box:hover {
  border-color: #40a9ff;
}

.search-box:focus-within {
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

.search-icon {
  position: absolute;
  left: 0.75rem;
  top: 50%;
  transform: translateY(-50%);
  color: #8c8c8c;
  z-index: 1;
  pointer-events: none;
}

.search-icon i {
  font-size: 0.875rem;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  color: #262626;
  font-size: 0.875rem;
  padding-left: 2.25rem;
  padding-right: 0.75rem;
}

.search-input::placeholder {
  color: #bfbfbf;
}

.search-input:disabled {
  color: #bfbfbf;
  cursor: not-allowed;
}

.search-input.is-error {
  color: #ff4d4f;
}

.search-input.is-success {
  color: #52c41a;
}

.clear-btn,
.search-btn {
  position: absolute;
  right: 0.5rem;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: #8c8c8c;
  cursor: pointer;
  padding: 0.25rem;
  border-radius: 4px;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.clear-btn:hover,
.search-btn:hover {
  color: #262626;
  background: #f5f5f5;
}

.search-btn:disabled {
  color: #bfbfbf;
  cursor: not-allowed;
}

.search-btn:disabled:hover {
  background: none;
}

/* 尺寸变体 */
.search-box-small {
  height: 2rem;
}

.search-box-small .search-input {
  font-size: 0.75rem;
  padding-left: 1.75rem;
  padding-right: 0.5rem;
}

.search-box-small .search-icon {
  left: 0.5rem;
}

.search-box-small .search-icon i {
  font-size: 0.75rem;
}

.search-box-small .clear-btn,
.search-box-small .search-btn {
  right: 0.25rem;
  padding: 0.125rem;
}

.search-box-medium {
  height: 2.5rem;
}

.search-box-large {
  height: 3rem;
}

.search-box-large .search-input {
  font-size: 1rem;
  padding-left: 2.5rem;
  padding-right: 1rem;
}

.search-box-large .search-icon {
  left: 1rem;
}

.search-box-large .search-icon i {
  font-size: 1rem;
}

.search-box-large .clear-btn,
.search-box-large .search-btn {
  right: 0.75rem;
  padding: 0.375rem;
}

/* 状态样式 */
.search-box.is-error {
  border-color: #ff4d4f;
}

.search-box.is-error:focus-within {
  border-color: #ff4d4f;
  box-shadow: 0 0 0 2px rgba(255, 77, 79, 0.2);
}

.search-box.is-success {
  border-color: #52c41a;
}

.search-box.is-success:focus-within {
  border-color: #52c41a;
  box-shadow: 0 0 0 2px rgba(82, 196, 26, 0.2);
}

.search-box:disabled {
  background: #f5f5f5;
  border-color: #d9d9d9;
  cursor: not-allowed;
}

.search-box:disabled:hover {
  border-color: #d9d9d9;
}

/* 响应式 */
@media (max-width: 768px) {
  .search-box-large {
    height: 2.5rem;
  }

  .search-box-large .search-input {
    font-size: 0.875rem;
    padding-left: 2.25rem;
    padding-right: 0.75rem;
  }

  .search-box-large .search-icon {
    left: 0.75rem;
  }

  .search-box-large .search-icon i {
    font-size: 0.875rem;
  }

  .search-box-large .clear-btn,
  .search-box-large .search-btn {
    right: 0.5rem;
    padding: 0.25rem;
  }
}
</style>
