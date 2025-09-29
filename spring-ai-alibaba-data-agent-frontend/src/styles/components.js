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
 * 组件样式工具
 */

import { cssVariables } from './theme.js'

// 按钮样式生成器
export function generateButtonStyles() {
  return `
    .btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--spacing-sm);
      padding: var(--spacing-sm) var(--spacing-md);
      border: 1px solid transparent;
      border-radius: var(--border-radius-base);
      font-size: var(--font-size-sm);
      font-weight: var(--font-weight-medium);
      line-height: var(--line-height-normal);
      text-decoration: none;
      cursor: pointer;
      transition: all var(--transition-base);
      white-space: nowrap;
      user-select: none;
    }

    .btn:focus {
      outline: none;
      box-shadow: 0 0 0 2px var(--color-primary-light);
    }

    .btn:disabled {
      opacity: 0.6;
      cursor: not-allowed;
      pointer-events: none;
    }

    /* 按钮尺寸 */
    .btn-xs {
      padding: var(--spacing-xs) var(--spacing-sm);
      font-size: var(--font-size-xs);
    }

    .btn-sm {
      padding: var(--spacing-xs) var(--spacing-base);
      font-size: var(--font-size-sm);
    }

    .btn-lg {
      padding: var(--spacing-md) var(--spacing-lg);
      font-size: var(--font-size-base);
    }

    .btn-xl {
      padding: var(--spacing-lg) var(--spacing-xl);
      font-size: var(--font-size-lg);
    }

    /* 按钮变体 */
    .btn-primary {
      background: var(--color-primary);
      color: var(--color-white);
      border-color: var(--color-primary);
    }

    .btn-primary:hover:not(:disabled) {
      background: var(--color-primary-hover);
      border-color: var(--color-primary-hover);
      transform: translateY(-1px);
      box-shadow: var(--shadow-md);
    }

    .btn-secondary {
      background: var(--color-secondary);
      color: var(--color-white);
      border-color: var(--color-secondary);
    }

    .btn-secondary:hover:not(:disabled) {
      background: var(--color-secondary-hover);
      border-color: var(--color-secondary-hover);
    }

    .btn-success {
      background: var(--color-success);
      color: var(--color-white);
      border-color: var(--color-success);
    }

    .btn-success:hover:not(:disabled) {
      background: var(--color-success-hover);
      border-color: var(--color-success-hover);
    }

    .btn-warning {
      background: var(--color-warning);
      color: var(--color-white);
      border-color: var(--color-warning);
    }

    .btn-warning:hover:not(:disabled) {
      background: var(--color-warning-hover);
      border-color: var(--color-warning-hover);
    }

    .btn-error {
      background: var(--color-error);
      color: var(--color-white);
      border-color: var(--color-error);
    }

    .btn-error:hover:not(:disabled) {
      background: var(--color-error-hover);
      border-color: var(--color-error-hover);
    }

    .btn-outline {
      background: transparent;
      color: var(--color-primary);
      border-color: var(--color-primary);
    }

    .btn-outline:hover:not(:disabled) {
      background: var(--color-primary);
      color: var(--color-white);
    }

    .btn-text {
      background: transparent;
      color: var(--color-primary);
      border-color: transparent;
      padding: var(--spacing-xs) var(--spacing-sm);
    }

    .btn-text:hover:not(:disabled) {
      background: var(--color-primary-light);
      color: var(--color-primary-hover);
    }
  `
}

// 表单样式生成器
export function generateFormStyles() {
  return `
    .form-group {
      margin-bottom: var(--spacing-md);
    }

    .form-label {
      display: block;
      margin-bottom: var(--spacing-xs);
      font-size: var(--font-size-sm);
      font-weight: var(--font-weight-medium);
      color: var(--color-gray-700);
    }

    .form-label.required::after {
      content: ' *';
      color: var(--color-error);
    }

    .form-control {
      display: block;
      width: 100%;
      padding: var(--spacing-sm) var(--spacing-base);
      font-size: var(--font-size-sm);
      line-height: var(--line-height-normal);
      color: var(--color-gray-900);
      background: var(--color-white);
      border: 1px solid var(--color-gray-300);
      border-radius: var(--border-radius-base);
      transition: all var(--transition-base);
    }

    .form-control:focus {
      outline: none;
      border-color: var(--color-primary);
      box-shadow: 0 0 0 2px var(--color-primary-light);
    }

    .form-control::placeholder {
      color: var(--color-gray-400);
    }

    .form-control:disabled {
      background: var(--color-gray-100);
      color: var(--color-gray-500);
      cursor: not-allowed;
    }

    .form-control.is-valid {
      border-color: var(--color-success);
    }

    .form-control.is-valid:focus {
      box-shadow: 0 0 0 2px var(--color-success-light);
    }

    .form-control.is-invalid {
      border-color: var(--color-error);
    }

    .form-control.is-invalid:focus {
      box-shadow: 0 0 0 2px var(--color-error-light);
    }

    .valid-feedback {
      display: block;
      margin-top: var(--spacing-xs);
      font-size: var(--font-size-xs);
      color: var(--color-success);
    }

    .invalid-feedback {
      display: block;
      margin-top: var(--spacing-xs);
      font-size: var(--font-size-xs);
      color: var(--color-error);
    }
  `
}

// 卡片样式生成器
export function generateCardStyles() {
  return `
    .card {
      background: var(--color-white);
      border: 1px solid var(--color-gray-200);
      border-radius: var(--border-radius-lg);
      box-shadow: var(--shadow-sm);
      overflow: hidden;
      transition: all var(--transition-base);
    }

    .card:hover {
      box-shadow: var(--shadow-md);
      transform: translateY(-2px);
    }

    .card-header {
      padding: var(--spacing-lg);
      border-bottom: 1px solid var(--color-gray-200);
      background: var(--color-gray-50);
    }

    .card-title {
      margin: 0;
      font-size: var(--font-size-lg);
      font-weight: var(--font-weight-semibold);
      color: var(--color-gray-900);
    }

    .card-subtitle {
      margin: var(--spacing-xs) 0 0 0;
      font-size: var(--font-size-sm);
      color: var(--color-gray-600);
    }

    .card-body {
      padding: var(--spacing-lg);
    }

    .card-text {
      margin-bottom: var(--spacing-md);
      color: var(--color-gray-700);
      line-height: var(--line-height-relaxed);
    }

    .card-footer {
      padding: var(--spacing-lg);
      border-top: 1px solid var(--color-gray-200);
      background: var(--color-gray-50);
    }
  `
}

// 徽章样式生成器
export function generateBadgeStyles() {
  return `
    .badge {
      display: inline-flex;
      align-items: center;
      padding: var(--spacing-xs) var(--spacing-sm);
      font-size: var(--font-size-xs);
      font-weight: var(--font-weight-medium);
      line-height: 1;
      border-radius: var(--border-radius-full);
      text-transform: uppercase;
      letter-spacing: 0.025em;
    }

    .badge-primary {
      background: var(--color-primary-light);
      color: var(--color-primary);
    }

    .badge-secondary {
      background: var(--color-secondary-light);
      color: var(--color-secondary);
    }

    .badge-success {
      background: var(--color-success-light);
      color: var(--color-success);
    }

    .badge-warning {
      background: var(--color-warning-light);
      color: var(--color-warning);
    }

    .badge-error {
      background: var(--color-error-light);
      color: var(--color-error);
    }

    .badge-info {
      background: var(--color-info-light);
      color: var(--color-info);
    }
  `
}

// 表格样式生成器
export function generateTableStyles() {
  return `
    .table {
      width: 100%;
      border-collapse: collapse;
      font-size: var(--font-size-sm);
      background: var(--color-white);
      border-radius: var(--border-radius-lg);
      overflow: hidden;
      box-shadow: var(--shadow-sm);
    }

    .table th {
      padding: var(--spacing-md) var(--spacing-lg);
      text-align: left;
      font-weight: var(--font-weight-semibold);
      color: var(--color-gray-700);
      background: var(--color-gray-50);
      border-bottom: 1px solid var(--color-gray-200);
    }

    .table td {
      padding: var(--spacing-md) var(--spacing-lg);
      border-bottom: 1px solid var(--color-gray-200);
      color: var(--color-gray-900);
    }

    .table tbody tr:hover {
      background: var(--color-gray-50);
    }

    .table tbody tr:last-child td {
      border-bottom: none;
    }
  `
}

// 工具类样式生成器
export function generateUtilityStyles() {
  return `
    /* 显示工具 */
    .d-none { display: none !important; }
    .d-block { display: block !important; }
    .d-inline { display: inline !important; }
    .d-inline-block { display: inline-block !important; }
    .d-flex { display: flex !important; }
    .d-inline-flex { display: inline-flex !important; }
    .d-grid { display: grid !important; }

    /* Flexbox工具 */
    .flex-row { flex-direction: row !important; }
    .flex-column { flex-direction: column !important; }
    .flex-wrap { flex-wrap: wrap !important; }
    .flex-nowrap { flex-wrap: nowrap !important; }
    .justify-start { justify-content: flex-start !important; }
    .justify-end { justify-content: flex-end !important; }
    .justify-center { justify-content: center !important; }
    .justify-between { justify-content: space-between !important; }
    .justify-around { justify-content: space-around !important; }
    .align-start { align-items: flex-start !important; }
    .align-end { align-items: flex-end !important; }
    .align-center { align-items: center !important; }
    .align-baseline { align-items: baseline !important; }
    .align-stretch { align-items: stretch !important; }

    /* 间距工具 */
    .m-0 { margin: 0 !important; }
    .m-1 { margin: var(--spacing-xs) !important; }
    .m-2 { margin: var(--spacing-sm) !important; }
    .m-3 { margin: var(--spacing-base) !important; }
    .m-4 { margin: var(--spacing-md) !important; }
    .m-5 { margin: var(--spacing-lg) !important; }

    .p-0 { padding: 0 !important; }
    .p-1 { padding: var(--spacing-xs) !important; }
    .p-2 { padding: var(--spacing-sm) !important; }
    .p-3 { padding: var(--spacing-base) !important; }
    .p-4 { padding: var(--spacing-md) !important; }
    .p-5 { padding: var(--spacing-lg) !important; }

    /* 文本工具 */
    .text-left { text-align: left !important; }
    .text-center { text-align: center !important; }
    .text-right { text-align: right !important; }
    .text-justify { text-align: justify !important; }

    .text-primary { color: var(--color-primary) !important; }
    .text-secondary { color: var(--color-secondary) !important; }
    .text-success { color: var(--color-success) !important; }
    .text-warning { color: var(--color-warning) !important; }
    .text-error { color: var(--color-error) !important; }
    .text-info { color: var(--color-info) !important; }

    .fw-light { font-weight: var(--font-weight-light) !important; }
    .fw-normal { font-weight: var(--font-weight-normal) !important; }
    .fw-medium { font-weight: var(--font-weight-medium) !important; }
    .fw-semibold { font-weight: var(--font-weight-semibold) !important; }
    .fw-bold { font-weight: var(--font-weight-bold) !important; }

    /* 位置工具 */
    .position-static { position: static !important; }
    .position-relative { position: relative !important; }
    .position-absolute { position: absolute !important; }
    .position-fixed { position: fixed !important; }
    .position-sticky { position: sticky !important; }

    /* 边框工具 */
    .border { border: 1px solid var(--color-gray-200) !important; }
    .border-0 { border: 0 !important; }
    .border-top { border-top: 1px solid var(--color-gray-200) !important; }
    .border-bottom { border-bottom: 1px solid var(--color-gray-200) !important; }
    .border-left { border-left: 1px solid var(--color-gray-200) !important; }
    .border-right { border-right: 1px solid var(--color-gray-200) !important; }

    .rounded { border-radius: var(--border-radius-base) !important; }
    .rounded-sm { border-radius: var(--border-radius-sm) !important; }
    .rounded-lg { border-radius: var(--border-radius-lg) !important; }
    .rounded-xl { border-radius: var(--border-radius-xl) !important; }
    .rounded-full { border-radius: var(--border-radius-full) !important; }

    /* 阴影工具 */
    .shadow-none { box-shadow: none !important; }
    .shadow-sm { box-shadow: var(--shadow-sm) !important; }
    .shadow { box-shadow: var(--shadow-base) !important; }
    .shadow-md { box-shadow: var(--shadow-md) !important; }
    .shadow-lg { box-shadow: var(--shadow-lg) !important; }
    .shadow-xl { box-shadow: var(--shadow-xl) !important; }
  `
}

// 动画样式生成器
export function generateAnimationStyles() {
  return `
    /* 动画关键帧 */
    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    @keyframes fadeOut {
      from { opacity: 1; }
      to { opacity: 0; }
    }

    @keyframes slideInUp {
      from {
        transform: translateY(100%);
        opacity: 0;
      }
      to {
        transform: translateY(0);
        opacity: 1;
      }
    }

    @keyframes slideInDown {
      from {
        transform: translateY(-100%);
        opacity: 0;
      }
      to {
        transform: translateY(0);
        opacity: 1;
      }
    }

    @keyframes slideInLeft {
      from {
        transform: translateX(-100%);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }

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

    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.5; }
    }

    @keyframes bounce {
      0%, 20%, 53%, 80%, 100% {
        transform: translate3d(0, 0, 0);
      }
      40%, 43% {
        transform: translate3d(0, -30px, 0);
      }
      70% {
        transform: translate3d(0, -15px, 0);
      }
      90% {
        transform: translate3d(0, -4px, 0);
      }
    }

    /* 动画类 */
    .animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
    .animate-fade-out { animation: fadeOut 0.3s ease-in-out; }
    .animate-slide-in-up { animation: slideInUp 0.3s ease-out; }
    .animate-slide-in-down { animation: slideInDown 0.3s ease-out; }
    .animate-slide-in-left { animation: slideInLeft 0.3s ease-out; }
    .animate-slide-in-right { animation: slideInRight 0.3s ease-out; }
    .animate-spin { animation: spin 1s linear infinite; }
    .animate-pulse { animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite; }
    .animate-bounce { animation: bounce 1s infinite; }

    /* 过渡类 */
    .transition-all { transition: all var(--transition-base); }
    .transition-colors { transition: color var(--transition-base), background-color var(--transition-base), border-color var(--transition-base); }
    .transition-opacity { transition: opacity var(--transition-base); }
    .transition-transform { transition: transform var(--transition-base); }
  `
}

// 生成所有组件样式
export function generateAllComponentStyles() {
  return [
    generateButtonStyles(),
    generateFormStyles(),
    generateCardStyles(),
    generateBadgeStyles(),
    generateTableStyles(),
    generateUtilityStyles(),
    generateAnimationStyles()
  ].join('\n')
}

export default {
  generateButtonStyles,
  generateFormStyles,
  generateCardStyles,
  generateBadgeStyles,
  generateTableStyles,
  generateUtilityStyles,
  generateAnimationStyles,
  generateAllComponentStyles
}
