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
 * 样式系统初始化
 */

import { generateCSSVariables, applyTheme } from './theme.js'
import { generateAllComponentStyles } from './components.js'

// 初始化样式系统
export function initStyleSystem() {
  // 生成并注入CSS变量
  const cssVariables = generateCSSVariables()
  const componentStyles = generateAllComponentStyles()
  
  // 创建样式元素
  const styleElement = document.createElement('style')
  styleElement.id = 'design-system-styles'
  styleElement.textContent = cssVariables + '\n' + componentStyles
  
  // 添加到文档头部
  document.head.appendChild(styleElement)
  
  // 应用默认主题
  applyTheme('light')
  
  console.log('设计系统样式已初始化')
}

// 动态更新样式
export function updateStyles() {
  const styleElement = document.getElementById('design-system-styles')
  if (styleElement) {
    const cssVariables = generateCSSVariables()
    const componentStyles = generateAllComponentStyles()
    styleElement.textContent = cssVariables + '\n' + componentStyles
  }
}

// 响应式工具
export function addResponsiveStyles() {
  const responsiveStyles = `
    /* 响应式断点 */
    @media (max-width: 768px) {
      .container {
        padding: 0 var(--spacing-md);
      }
      
      .card {
        margin-bottom: var(--spacing-md);
      }
      
      .card-header,
      .card-body,
      .card-footer {
        padding: var(--spacing-md);
      }
      
      .table th,
      .table td {
        padding: var(--spacing-sm) var(--spacing-md);
        font-size: var(--font-size-xs);
      }
      
      .btn-lg {
        padding: var(--spacing-sm) var(--spacing-md);
        font-size: var(--font-size-sm);
      }
    }

    @media (max-width: 480px) {
      .btn {
        padding: var(--spacing-xs) var(--spacing-sm);
        font-size: var(--font-size-xs);
      }
      
      .d-flex {
        flex-direction: column;
      }
      
      .gap-md {
        gap: var(--spacing-sm);
      }
    }

    /* 高对比度模式 */
    @media (prefers-contrast: high) {
      :root {
        --color-gray-200: #000000;
        --color-gray-300: #000000;
        --color-gray-400: #000000;
      }
      
      .card,
      .btn,
      .form-control {
        border-width: 2px;
      }
      
      .btn {
        font-weight: var(--font-weight-bold);
      }
    }

    /* 减少动画模式 */
    @media (prefers-reduced-motion: reduce) {
      *,
      *::before,
      *::after {
        animation-duration: 0.01ms !important;
        animation-iteration-count: 1 !important;
        transition-duration: 0.01ms !important;
      }
      
      .btn::before {
        transition: none;
      }
    }

    /* 深色模式 */
    @media (prefers-color-scheme: dark) {
      :root {
        --color-white: #1a1a1a;
        --color-gray-50: #2d2d2d;
        --color-gray-100: #404040;
        --color-gray-200: #525252;
        --color-gray-300: #737373;
        --color-gray-400: #a3a3a3;
        --color-gray-500: #d4d4d4;
        --color-gray-600: #e5e5e5;
        --color-gray-700: #f5f5f5;
        --color-gray-800: #fafafa;
        --color-gray-900: #ffffff;
      }
    }
  `
  
  const styleElement = document.createElement('style')
  styleElement.id = 'responsive-styles'
  styleElement.textContent = responsiveStyles
  document.head.appendChild(styleElement)
}

// 打印样式
export function addPrintStyles() {
  const printStyles = `
    @media print {
      * {
        background: transparent !important;
        color: black !important;
        box-shadow: none !important;
        text-shadow: none !important;
      }
      
      a,
      a:visited {
        text-decoration: underline;
      }
      
      a[href]:after {
        content: " (" attr(href) ")";
      }
      
      abbr[title]:after {
        content: " (" attr(title) ")";
      }
      
      .btn,
      .badge {
        border: 1px solid black;
      }
      
      .table {
        border-collapse: collapse !important;
      }
      
      .table th,
      .table td {
        border: 1px solid black;
      }
      
      .card {
        border: 1px solid black;
        page-break-inside: avoid;
      }
      
      .d-print-none {
        display: none !important;
      }
      
      .d-print-block {
        display: block !important;
      }
      
      .d-print-inline {
        display: inline !important;
      }
      
      .d-print-inline-block {
        display: inline-block !important;
      }
    }
  `
  
  const styleElement = document.createElement('style')
  styleElement.id = 'print-styles'
  styleElement.textContent = printStyles
  document.head.appendChild(styleElement)
}

// 完整初始化
export function initCompleteStyleSystem() {
  initStyleSystem()
  addResponsiveStyles()
  addPrintStyles()
  
  console.log('完整样式系统已初始化')
}

export default {
  initStyleSystem,
  updateStyles,
  addResponsiveStyles,
  addPrintStyles,
  initCompleteStyleSystem
}
