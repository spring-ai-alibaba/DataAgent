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
 * 设计系统 - CSS变量和主题配置
 */

// CSS变量定义
export const cssVariables = {
  // 颜色系统
  colors: {
    // 主色调
    primary: '#3b82f6',
    primaryHover: '#1d4ed8',
    primaryActive: '#1e40af',
    primaryLight: '#dbeafe',
    
    // 辅助色
    secondary: '#64748b',
    secondaryHover: '#475569',
    secondaryActive: '#334155',
    secondaryLight: '#f1f5f9',
    
    // 功能色
    success: '#10b981',
    successHover: '#059669',
    successLight: '#d1fae5',
    
    warning: '#f59e0b',
    warningHover: '#d97706',
    warningLight: '#fef3c7',
    
    error: '#ef4444',
    errorHover: '#dc2626',
    errorLight: '#fee2e2',
    
    info: '#06b6d4',
    infoHover: '#0891b2',
    infoLight: '#cffafe',
    
    // 中性色
    white: '#ffffff',
    black: '#000000',
    gray50: '#f8fafc',
    gray100: '#f1f5f9',
    gray200: '#e2e8f0',
    gray300: '#cbd5e1',
    gray400: '#94a3b8',
    gray500: '#64748b',
    gray600: '#475569',
    gray700: '#334155',
    gray800: '#1e293b',
    gray900: '#0f172a'
  },
  
  // 字体系统
  typography: {
    fontFamily: {
      sans: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
      mono: '"Monaco", "Menlo", "Ubuntu Mono", monospace'
    },
    fontSize: {
      xs: '0.75rem',    // 12px
      sm: '0.875rem',   // 14px
      base: '1rem',     // 16px
      lg: '1.125rem',   // 18px
      xl: '1.25rem',    // 20px
      '2xl': '1.5rem',  // 24px
      '3xl': '1.875rem', // 30px
      '4xl': '2.25rem'  // 36px
    },
    fontWeight: {
      light: '300',
      normal: '400',
      medium: '500',
      semibold: '600',
      bold: '700'
    },
    lineHeight: {
      tight: '1.25',
      normal: '1.5',
      relaxed: '1.75'
    }
  },
  
  // 间距系统
  spacing: {
    xs: '0.25rem',   // 4px
    sm: '0.5rem',    // 8px
    base: '0.75rem',  // 12px
    md: '1rem',      // 16px
    lg: '1.5rem',    // 24px
    xl: '2rem',      // 32px
    '2xl': '3rem',   // 48px
    '3xl': '4rem'    // 64px
  },
  
  // 圆角系统
  borderRadius: {
    none: '0',
    sm: '0.25rem',   // 4px
    base: '0.375rem', // 6px
    md: '0.5rem',    // 8px
    lg: '0.75rem',   // 12px
    xl: '1rem',      // 16px
    full: '9999px'
  },
  
  // 阴影系统
  boxShadow: {
    sm: '0 1px 2px rgba(0, 0, 0, 0.05)',
    base: '0 1px 3px rgba(0, 0, 0, 0.1), 0 1px 2px rgba(0, 0, 0, 0.06)',
    md: '0 4px 6px rgba(0, 0, 0, 0.07), 0 2px 4px rgba(0, 0, 0, 0.06)',
    lg: '0 10px 15px rgba(0, 0, 0, 0.1), 0 4px 6px rgba(0, 0, 0, 0.05)',
    xl: '0 20px 25px rgba(0, 0, 0, 0.1), 0 10px 10px rgba(0, 0, 0, 0.04)',
    inner: 'inset 0 2px 4px rgba(0, 0, 0, 0.06)'
  },
  
  // 过渡动画
  transition: {
    fast: '0.15s ease',
    base: '0.2s ease',
    slow: '0.3s ease'
  },
  
  // Z-index层级
  zIndex: {
    dropdown: '1000',
    sticky: '1020',
    fixed: '1030',
    modal: '1040',
    popover: '1050',
    tooltip: '1060'
  }
}

// 生成CSS变量字符串
export function generateCSSVariables() {
  const variables = []
  
  // 颜色变量
  Object.entries(cssVariables.colors).forEach(([key, value]) => {
    if (typeof value === 'object') {
      Object.entries(value).forEach(([subKey, subValue]) => {
        variables.push(`--color-${key}-${subKey}: ${subValue};`)
      })
    } else {
      variables.push(`--color-${key}: ${value};`)
    }
  })
  
  // 字体变量
  Object.entries(cssVariables.typography.fontSize).forEach(([key, value]) => {
    variables.push(`--font-size-${key}: ${value};`)
  })
  
  Object.entries(cssVariables.typography.fontWeight).forEach(([key, value]) => {
    variables.push(`--font-weight-${key}: ${value};`)
  })
  
  Object.entries(cssVariables.typography.lineHeight).forEach(([key, value]) => {
    variables.push(`--line-height-${key}: ${value};`)
  })
  
  // 间距变量
  Object.entries(cssVariables.spacing).forEach(([key, value]) => {
    variables.push(`--spacing-${key}: ${value};`)
  })
  
  // 圆角变量
  Object.entries(cssVariables.borderRadius).forEach(([key, value]) => {
    variables.push(`--border-radius-${key}: ${value};`)
  })
  
  // 阴影变量
  Object.entries(cssVariables.boxShadow).forEach(([key, value]) => {
    variables.push(`--shadow-${key}: ${value};`)
  })
  
  // 过渡变量
  Object.entries(cssVariables.transition).forEach(([key, value]) => {
    variables.push(`--transition-${key}: ${value};`)
  })
  
  // Z-index变量
  Object.entries(cssVariables.zIndex).forEach(([key, value]) => {
    variables.push(`--z-index-${key}: ${value};`)
  })
  
  return `:root {\n  ${variables.join('\n  ')}\n}`
}

// 主题配置
export const themes = {
  light: {
    name: 'light',
    colors: {
      background: cssVariables.colors.white,
      surface: cssVariables.colors.gray50,
      text: cssVariables.colors.gray900,
      textSecondary: cssVariables.colors.gray600,
      border: cssVariables.colors.gray200,
      shadow: cssVariables.boxShadow.base
    }
  },
  
  dark: {
    name: 'dark',
    colors: {
      background: cssVariables.colors.gray900,
      surface: cssVariables.colors.gray800,
      text: cssVariables.colors.gray100,
      textSecondary: cssVariables.colors.gray400,
      border: cssVariables.colors.gray700,
      shadow: cssVariables.boxShadow.lg
    }
  }
}

// 应用主题
export function applyTheme(themeName = 'light') {
  const theme = themes[themeName]
  if (!theme) return
  
  const root = document.documentElement
  
  Object.entries(theme.colors).forEach(([key, value]) => {
    root.style.setProperty(`--theme-${key}`, value)
  })
  
  root.setAttribute('data-theme', themeName)
}

// 获取当前主题
export function getCurrentTheme() {
  return document.documentElement.getAttribute('data-theme') || 'light'
}

// 切换主题
export function toggleTheme() {
  const currentTheme = getCurrentTheme()
  const newTheme = currentTheme === 'light' ? 'dark' : 'light'
  applyTheme(newTheme)
  return newTheme
}

// 响应式断点
export const breakpoints = {
  xs: '480px',
  sm: '640px',
  md: '768px',
  lg: '1024px',
  xl: '1280px',
  '2xl': '1536px'
}

// 媒体查询工具
export const mediaQueries = {
  xs: `@media (min-width: ${breakpoints.xs})`,
  sm: `@media (min-width: ${breakpoints.sm})`,
  md: `@media (min-width: ${breakpoints.md})`,
  lg: `@media (min-width: ${breakpoints.lg})`,
  xl: `@media (min-width: ${breakpoints.xl})`,
  '2xl': `@media (min-width: ${breakpoints['2xl']})`
}

export default {
  cssVariables,
  generateCSSVariables,
  themes,
  applyTheme,
  getCurrentTheme,
  toggleTheme,
  breakpoints,
  mediaQueries
}
