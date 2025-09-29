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
 * HTTP请求工具类
 * 提供统一的API请求方法
 */

// 基础配置
const BASE_URL = ''
const DEFAULT_TIMEOUT = 30000

// 请求拦截器
const requestInterceptors = []
const responseInterceptors = []

// 添加请求拦截器
export function addRequestInterceptor(interceptor) {
  requestInterceptors.push(interceptor)
}

// 添加响应拦截器
export function addResponseInterceptor(interceptor) {
  responseInterceptors.push(interceptor)
}

// 基础请求方法
async function request(url, options = {}) {
  const {
    method = 'GET',
    headers = {},
    body,
    timeout = DEFAULT_TIMEOUT,
    ...restOptions
  } = options

  // 构建完整URL
  const fullUrl = url.startsWith('http') ? url : `${BASE_URL}${url}`

  // 默认请求头
  const defaultHeaders = {
    'Content-Type': 'application/json',
    ...headers
  }

  // 应用请求拦截器
  let requestConfig = {
    method,
    headers: defaultHeaders,
    body: body ? JSON.stringify(body) : undefined,
    timeout,
    ...restOptions
  }

  for (const interceptor of requestInterceptors) {
    requestConfig = await interceptor(requestConfig)
  }

  // 创建AbortController用于超时控制
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeout)

  try {
    const response = await fetch(fullUrl, {
      ...requestConfig,
      signal: controller.signal
    })

    clearTimeout(timeoutId)

    // 应用响应拦截器
    let processedResponse = response
    for (const interceptor of responseInterceptors) {
      processedResponse = await interceptor(processedResponse)
    }

    // 检查响应状态
    if (!processedResponse.ok) {
      throw new Error(`HTTP error! status: ${processedResponse.status}`)
    }

    // 解析响应数据
    const contentType = processedResponse.headers.get('content-type')
    if (contentType && contentType.includes('application/json')) {
      return await processedResponse.json()
    } else {
      return await processedResponse.text()
    }

  } catch (error) {
    clearTimeout(timeoutId)
    
    if (error.name === 'AbortError') {
      throw new Error('请求超时')
    }
    
    throw error
  }
}

// GET请求
export function get(url, params = {}) {
  const queryString = new URLSearchParams(params).toString()
  const fullUrl = queryString ? `${url}?${queryString}` : url
  
  return request(fullUrl, { method: 'GET' })
}

// POST请求
export function post(url, data = {}) {
  return request(url, {
    method: 'POST',
    body: data
  })
}

// PUT请求
export function put(url, data = {}) {
  return request(url, {
    method: 'PUT',
    body: data
  })
}

// DELETE请求
export function del(url) {
  return request(url, { method: 'DELETE' })
}

// 文件上传
export function upload(url, file, onProgress) {
  const formData = new FormData()
  formData.append('file', file)

  return request(url, {
    method: 'POST',
    body: formData,
    headers: {
      // 不设置Content-Type，让浏览器自动设置
    },
    onUploadProgress: onProgress
  })
}

// 流式请求
export function stream(url, options = {}) {
  return new EventSource(url, options)
}
