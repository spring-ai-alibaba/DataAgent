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
 * 头像生成工具
 */

/**
 * 生成专业头像
 * @param {string} seed 种子字符串
 * @returns {string} 头像URL
 */
export function generateProfessionalAvatar(seed = null) {
  const seedStr = seed || Math.random().toString(36).substring(7)
  const hash = hashString(seedStr)
  
  // 使用hash生成一致的样式
  const bgColors = [
    '#3b82f6', '#8b5cf6', '#06b6d4', '#10b981', 
    '#f59e0b', '#ef4444', '#84cc16', '#f97316'
  ]
  const bgColor = bgColors[hash % bgColors.length]
  
  // 生成SVG头像
  const svg = `
    <svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
      <rect width="100" height="100" fill="${bgColor}" rx="50"/>
      <text x="50" y="60" font-family="Arial, sans-serif" font-size="40" 
            font-weight="bold" text-anchor="middle" fill="white">
        ${seedStr.charAt(0).toUpperCase()}
      </text>
    </svg>
  `
  
  return `data:image/svg+xml;base64,${btoa(svg)}`
}

/**
 * 获取智能体头像
 * @param {string|number} agentId 智能体ID
 * @returns {string} 头像URL
 */
export function getAgentAvatar(agentId) {
  return generateProfessionalAvatar(`agent_${agentId}`)
}

/**
 * 生成随机头像
 * @returns {string} 头像URL
 */
export function generateRandomAvatar() {
  return generateProfessionalAvatar()
}

/**
 * 字符串哈希函数
 * @param {string} str 字符串
 * @returns {number} 哈希值
 */
function hashString(str) {
  let hash = 0
  if (str.length === 0) return hash
  
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash // 转换为32位整数
  }
  
  return Math.abs(hash)
}

/**
 * 验证头像URL是否有效
 * @param {string} url 头像URL
 * @returns {boolean} 是否有效
 */
export function isValidAvatarUrl(url) {
  if (!url) return false
  
  // 检查是否为有效的URL格式
  try {
    new URL(url)
    return true
  } catch {
    // 检查是否为data URL
    return url.startsWith('data:image/')
  }
}

/**
 * 获取显示头像
 * @param {string} customAvatar 自定义头像
 * @param {string|number} agentId 智能体ID
 * @returns {string} 显示头像URL
 */
export function getDisplayAvatar(customAvatar, agentId) {
  if (customAvatar && isValidAvatarUrl(customAvatar)) {
    return customAvatar
  }
  return getAgentAvatar(agentId)
}

export default {
  generateProfessionalAvatar,
  getAgentAvatar,
  generateRandomAvatar,
  isValidAvatarUrl,
  getDisplayAvatar
}