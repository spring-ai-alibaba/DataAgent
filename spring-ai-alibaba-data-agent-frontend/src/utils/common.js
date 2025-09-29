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
 * 通用工具函数
 */

/**
 * 防抖函数
 * @param {Function} func 要防抖的函数
 * @param {number} delay 延迟时间（毫秒）
 * @param {boolean} immediate 是否立即执行
 * @returns {Function} 防抖后的函数
 */
export function debounce(func, delay = 300, immediate = false) {
  let timeoutId = null
  let lastCallTime = 0

  return function debounced(...args) {
    const now = Date.now()
    const timeSinceLastCall = now - lastCallTime
    lastCallTime = now

    const executeFunction = () => {
      lastCallTime = Date.now()
      func.apply(this, args)
    }

    if (immediate && timeSinceLastCall > delay) {
      executeFunction()
    } else {
      clearTimeout(timeoutId)
      timeoutId = setTimeout(executeFunction, delay)
    }
  }
}

/**
 * 节流函数
 * @param {Function} func 要节流的函数
 * @param {number} delay 延迟时间（毫秒）
 * @returns {Function} 节流后的函数
 */
export function throttle(func, delay = 300) {
  let timeoutId = null
  let lastExecTime = 0

  return function throttled(...args) {
    const now = Date.now()
    const timeSinceLastExec = now - lastExecTime

    if (timeSinceLastExec >= delay) {
      lastExecTime = now
      func.apply(this, args)
    } else {
      clearTimeout(timeoutId)
      timeoutId = setTimeout(() => {
        lastExecTime = Date.now()
        func.apply(this, args)
      }, delay - timeSinceLastExec)
    }
  }
}

/**
 * 深拷贝对象
 * @param {any} obj 要拷贝的对象
 * @returns {any} 拷贝后的对象
 */
export function deepClone(obj) {
  if (obj === null || typeof obj !== 'object') {
    return obj
  }

  if (obj instanceof Date) {
    return new Date(obj.getTime())
  }

  if (obj instanceof Array) {
    return obj.map(item => deepClone(item))
  }

  if (typeof obj === 'object') {
    const clonedObj = {}
    for (const key in obj) {
      if (obj.hasOwnProperty(key)) {
        clonedObj[key] = deepClone(obj[key])
      }
    }
    return clonedObj
  }

  return obj
}

/**
 * 格式化文件大小
 * @param {number} bytes 字节数
 * @returns {string} 格式化后的文件大小
 */
export function formatFileSize(bytes) {
  if (bytes === 0) return '0 B'

  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))

  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

/**
 * 格式化时间
 * @param {Date|string|number} date 时间
 * @param {string} format 格式
 * @returns {string} 格式化后的时间
 */
export function formatDate(date, format = 'YYYY-MM-DD HH:mm:ss') {
  if (!date) return ''

  const d = new Date(date)
  if (isNaN(d.getTime())) return ''

  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * 生成随机字符串
 * @param {number} length 长度
 * @returns {string} 随机字符串
 */
export function generateRandomString(length = 8) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  let result = ''
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return result
}

/**
 * 生成UUID
 * @returns {string} UUID字符串
 */
export function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = Math.random() * 16 | 0
    const v = c === 'x' ? r : (r & 0x3 | 0x8)
    return v.toString(16)
  })
}

/**
 * 验证邮箱格式
 * @param {string} email 邮箱地址
 * @returns {boolean} 是否为有效邮箱
 */
export function isValidEmail(email) {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}

/**
 * 验证手机号格式
 * @param {string} phone 手机号
 * @returns {boolean} 是否为有效手机号
 */
export function isValidPhone(phone) {
  const phoneRegex = /^1[3-9]\d{9}$/
  return phoneRegex.test(phone)
}

/**
 * 验证URL格式
 * @param {string} url URL地址
 * @returns {boolean} 是否为有效URL
 */
export function isValidURL(url) {
  try {
    new URL(url)
    return true
  } catch {
    return false
  }
}

/**
 * 获取URL参数
 * @param {string} name 参数名
 * @returns {string|null} 参数值
 */
export function getUrlParam(name) {
  const urlParams = new URLSearchParams(window.location.search)
  return urlParams.get(name)
}

/**
 * 设置URL参数
 * @param {string} name 参数名
 * @param {string} value 参数值
 */
export function setUrlParam(name, value) {
  const url = new URL(window.location)
  url.searchParams.set(name, value)
  window.history.replaceState({}, '', url)
}

/**
 * 移除URL参数
 * @param {string} name 参数名
 */
export function removeUrlParam(name) {
  const url = new URL(window.location)
  url.searchParams.delete(name)
  window.history.replaceState({}, '', url)
}

/**
 * 复制文本到剪贴板
 * @param {string} text 要复制的文本
 * @returns {Promise<boolean>} 是否复制成功
 */
export async function copyToClipboard(text) {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch (err) {
    console.error('复制失败:', err)
    return false
  }
}

/**
 * 下载文件
 * @param {string} url 文件URL
 * @param {string} filename 文件名
 */
export function downloadFile(url, filename) {
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

/**
 * 获取文件扩展名
 * @param {string} filename 文件名
 * @returns {string} 扩展名
 */
export function getFileExtension(filename) {
  return filename.slice((filename.lastIndexOf('.') - 1 >>> 0) + 2)
}

/**
 * 检查是否为图片文件
 * @param {string} filename 文件名
 * @returns {boolean} 是否为图片
 */
export function isImageFile(filename) {
  const imageExtensions = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg']
  const extension = getFileExtension(filename).toLowerCase()
  return imageExtensions.includes(extension)
}

/**
 * 检查是否为视频文件
 * @param {string} filename 文件名
 * @returns {boolean} 是否为视频
 */
export function isVideoFile(filename) {
  const videoExtensions = ['mp4', 'avi', 'mov', 'wmv', 'flv', 'webm', 'mkv']
  const extension = getFileExtension(filename).toLowerCase()
  return videoExtensions.includes(extension)
}

/**
 * 检查是否为音频文件
 * @param {string} filename 文件名
 * @returns {boolean} 是否为音频
 */
export function isAudioFile(filename) {
  const audioExtensions = ['mp3', 'wav', 'flac', 'aac', 'ogg', 'wma']
  const extension = getFileExtension(filename).toLowerCase()
  return audioExtensions.includes(extension)
}

/**
 * 数组去重
 * @param {Array} arr 数组
 * @param {string} key 去重的键名（可选）
 * @returns {Array} 去重后的数组
 */
export function uniqueArray(arr, key) {
  if (!key) {
    return [...new Set(arr)]
  }
  
  const seen = new Set()
  return arr.filter(item => {
    const value = item[key]
    if (seen.has(value)) {
      return false
    }
    seen.add(value)
    return true
  })
}

/**
 * 数组分组
 * @param {Array} arr 数组
 * @param {string|Function} key 分组的键名或函数
 * @returns {Object} 分组后的对象
 */
export function groupBy(arr, key) {
  return arr.reduce((groups, item) => {
    const groupKey = typeof key === 'function' ? key(item) : item[key]
    if (!groups[groupKey]) {
      groups[groupKey] = []
    }
    groups[groupKey].push(item)
    return groups
  }, {})
}

/**
 * 数组排序
 * @param {Array} arr 数组
 * @param {string} key 排序的键名
 * @param {string} order 排序顺序 'asc' | 'desc'
 * @returns {Array} 排序后的数组
 */
export function sortArray(arr, key, order = 'asc') {
  return [...arr].sort((a, b) => {
    const aVal = a[key]
    const bVal = b[key]
    
    if (aVal < bVal) return order === 'asc' ? -1 : 1
    if (aVal > bVal) return order === 'asc' ? 1 : -1
    return 0
  })
}

/**
 * 对象转查询字符串
 * @param {Object} obj 对象
 * @returns {string} 查询字符串
 */
export function objectToQueryString(obj) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(obj)) {
    if (value !== null && value !== undefined) {
      params.append(key, value)
    }
  }
  return params.toString()
}

/**
 * 查询字符串转对象
 * @param {string} queryString 查询字符串
 * @returns {Object} 对象
 */
export function queryStringToObject(queryString) {
  const params = new URLSearchParams(queryString)
  const obj = {}
  for (const [key, value] of params) {
    obj[key] = value
  }
  return obj
}

/**
 * 等待指定时间
 * @param {number} ms 等待时间（毫秒）
 * @returns {Promise} Promise对象
 */
export function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

/**
 * 重试函数
 * @param {Function} fn 要重试的函数
 * @param {number} maxRetries 最大重试次数
 * @param {number} delay 重试间隔（毫秒）
 * @returns {Promise} Promise对象
 */
export async function retry(fn, maxRetries = 3, delay = 1000) {
  let lastError
  
  for (let i = 0; i <= maxRetries; i++) {
    try {
      return await fn()
    } catch (error) {
      lastError = error
      if (i < maxRetries) {
        await sleep(delay)
      }
    }
  }
  
  throw lastError
}

export default {
  debounce,
  throttle,
  deepClone,
  formatFileSize,
  formatDate,
  generateRandomString,
  generateUUID,
  isValidEmail,
  isValidPhone,
  isValidURL,
  getUrlParam,
  setUrlParam,
  removeUrlParam,
  copyToClipboard,
  downloadFile,
  getFileExtension,
  isImageFile,
  isVideoFile,
  isAudioFile,
  uniqueArray,
  groupBy,
  sortArray,
  objectToQueryString,
  queryStringToObject,
  sleep,
  retry
}
