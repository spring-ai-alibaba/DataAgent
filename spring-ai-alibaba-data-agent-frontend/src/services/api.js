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
 * 业务API服务
 * 封装所有业务相关的API调用
 */
// todo: 拆分此脚本，且用ts规范输入输出
import { get, post, put, del, upload } from '@/utils/http'

// 预设问题API
export const presetQuestionApi = {
  // 获取预设问题列表
  getList(params = {}) {
    return get('/api/preset-questions', params)
  },

  // 根据智能体ID获取预设问题
  getByAgentId(agentId) {
    return get(`/api/agent/${agentId}/preset-questions`)
  },

  // 创建预设问题
  create(data) {
    return post('/api/preset-questions', data)
  },

  // 更新预设问题
  update(id, data) {
    return put(`/api/preset-questions/${id}`, data)
  },

  // 删除预设问题
  delete(id) {
    return del(`/api/preset-questions/${id}`)
  },

  // 批量创建预设问题
  createList(dataList) {
    return post('/api/preset-questions/batch', dataList)
  }
}

// 文件上传API
export const fileUploadApi = {
  // 上传头像
  uploadAvatar(file) {
    return upload('/api/upload/avatar', file)
  },

  // 上传文件
  uploadFile(file, onProgress) {
    return upload('/api/upload/file', file, onProgress)
  }
}
