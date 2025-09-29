/*
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
 */

/**
 * 消息提示工具
 */

// 消息容器
let toastContainer = null

// 创建消息容器
const createToastContainer = () => {
  if (toastContainer) return toastContainer
  
  toastContainer = document.createElement('div')
  toastContainer.className = 'toast-container'
  toastContainer.style.cssText = `
    position: fixed;
    top: 20px;
    right: 20px;
    z-index: 9999;
    pointer-events: none;
  `
  document.body.appendChild(toastContainer)
  
  return toastContainer
}

// 显示消息提示
const showToast = (message, type = 'info', options = {}) => {
  const {
    title = '',
    duration = 3000,
    closable = true,
    onClose = null
  } = options

  const container = createToastContainer()
  
  // 创建消息元素
  const toast = document.createElement('div')
  toast.className = `message-toast toast-${type}`
  toast.style.cssText = `
    background: white;
    border-radius: 8px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    margin-bottom: 12px;
    padding: 16px;
    max-width: 400px;
    pointer-events: auto;
    animation: slideInRight 0.3s ease-out;
    border-left: 4px solid ${getTypeColor(type)};
  `

  // 图标映射
  const icons = {
    success: 'bi bi-check-circle-fill',
    warning: 'bi bi-exclamation-triangle-fill',
    error: 'bi bi-x-circle-fill',
    info: 'bi bi-info-circle-fill'
  }

  // 构建消息内容
  toast.innerHTML = `
    <div style="display: flex; align-items: flex-start; gap: 12px;">
      <div style="flex-shrink: 0; margin-top: 2px;">
        <i class="${icons[type] || icons.info}" style="font-size: 20px; color: ${getTypeColor(type)};"></i>
      </div>
      <div style="flex: 1; min-width: 0;">
        ${title ? `<div style="font-weight: 600; font-size: 14px; margin-bottom: 4px; color: #262626;">${title}</div>` : ''}
        <div style="font-size: 14px; line-height: 1.5; color: #595959; word-wrap: break-word;">${message}</div>
      </div>
      ${closable ? `<button class="toast-close" style="flex-shrink: 0; background: none; border: none; padding: 4px; cursor: pointer; color: #8c8c8c; border-radius: 4px; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f5f5f5'" onmouseout="this.style.backgroundColor='transparent'">
        <i class="bi bi-x" style="font-size: 16px;"></i>
      </button>` : ''}
    </div>
  `

  // 添加关闭事件
  if (closable) {
    const closeBtn = toast.querySelector('.toast-close')
    closeBtn.addEventListener('click', () => {
      removeToast(toast)
      if (onClose) onClose()
    })
  }

  // 添加到容器
  container.appendChild(toast)

  // 自动移除
  if (duration > 0) {
    setTimeout(() => {
      removeToast(toast)
      if (onClose) onClose()
    }, duration)
  }

  return toast
}

// 移除消息
const removeToast = (toast) => {
  if (toast && toast.parentNode) {
    toast.style.animation = 'slideOutRight 0.3s ease-in'
    setTimeout(() => {
      if (toast.parentNode) {
        toast.parentNode.removeChild(toast)
      }
    }, 300)
  }
}

// 获取类型颜色
const getTypeColor = (type) => {
  const colors = {
    success: '#52c41a',
    warning: '#faad14',
    error: '#ff4d4f',
    info: '#1890ff'
  }
  return colors[type] || colors.info
}

// 添加CSS动画
const addToastStyles = () => {
  if (document.getElementById('toast-styles')) return
  
  const style = document.createElement('style')
  style.id = 'toast-styles'
  style.textContent = `
    @keyframes slideInRight {
      from {
        transform: translateX(100%);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }
    
    @keyframes slideOutRight {
      from {
        transform: translateX(0);
        opacity: 1;
      }
      to {
        transform: translateX(100%);
        opacity: 0;
      }
    }
  `
  document.head.appendChild(style)
}

// 初始化样式
addToastStyles()

// 导出方法
export const toast = {
  success: (message, options = {}) => showToast(message, 'success', options),
  warning: (message, options = {}) => showToast(message, 'warning', options),
  error: (message, options = {}) => showToast(message, 'error', options),
  info: (message, options = {}) => showToast(message, 'info', options),
  show: showToast
}

export default toast
