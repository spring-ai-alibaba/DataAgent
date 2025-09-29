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

import { get, post, put, del, upload, stream } from '@/utils/http'

// 智能体管理API
export const agentApi = {
  // 获取智能体列表
  getList(params = {}) {
    return get('/api/agent/list', params)
  },

  // 创建智能体
  create(data) {
    return post('/api/agent', data)
  },

  // 获取智能体详情
  getDetail(id) {
    return get(`/api/agent/${id}`)
  },

  // 更新智能体
  update(id, data) {
    return put(`/api/agent/${id}`, data)
  },

  // 删除智能体
  delete(id) {
    return del(`/api/agent/${id}`)
  },

  // 发布智能体
  publish(id) {
    return post(`/api/agent/${id}/publish`)
  },

  // 下线智能体
  offline(id) {
    return post(`/api/agent/${id}/offline`)
  }
}

// 业务知识管理API
export const businessKnowledgeApi = {
  // 获取知识列表
  getList(params = {}) {
    return get('/api/business-knowledge', params)
  },

  // 根据数据集ID获取知识
  getByDatasetId(datasetId) {
    return get(`/api/business-knowledge/dataset/${datasetId}`)
  },

  // 根据智能体ID获取知识
  getByAgentId(agentId) {
    return get(`/api/business-knowledge/agent/${agentId}`)
  },

  // 搜索知识
  search(keyword) {
    return get('/api/business-knowledge/search', { keyword })
  },

  // 在智能体中搜索知识
  searchInAgent(agentId, keyword) {
    return get(`/api/business-knowledge/agent/${agentId}/search`, { keyword })
  },

  // 创建知识
  create(data) {
    return post('/api/business-knowledge', data)
  },

  // 批量创建知识
  createList(dataList) {
    return post('/api/business-knowledge/batch', dataList)
  },

  // 为智能体创建知识
  createForAgent(agentId, data) {
    return post(`/api/business-knowledge/agent/${agentId}`, data)
  },

  // 为智能体批量创建知识
  createListForAgent(agentId, dataList) {
    return post(`/api/business-knowledge/agent/${agentId}/batch`, dataList)
  },

  // 获取知识详情
  getDetail(id) {
    return get(`/api/business-knowledge/${id}`)
  },

  // 更新知识
  update(id, data) {
    return put(`/api/business-knowledge/${id}`, data)
  },

  // 删除知识
  delete(id) {
    return del(`/api/business-knowledge/${id}`)
  },

  // 根据智能体ID删除知识
  deleteByAgentId(agentId) {
    return del(`/api/business-knowledge/agent/${agentId}`)
  },

  // 获取数据集ID列表
  getDatasetIds() {
    return get('/api/business-knowledge/datasets')
  }
}

// 语义模型配置API
export const semanticModelApi = {
  // 获取语义模型列表
  getList(params = {}) {
    return get('/api/semantic-model', params)
  },

  // 搜索语义模型
  search(keyword) {
    return get('/api/semantic-model/search', { keyword })
  },

  // 根据数据集ID获取语义模型
  getByDatasetId(datasetId) {
    return get(`/api/semantic-model/dataset/${datasetId}`)
  },

  // 根据智能体ID获取语义模型
  getByAgentId(agentId) {
    return get(`/api/semantic-model/agent/${agentId}`)
  },

  // 创建语义模型
  create(data) {
    return post('/api/semantic-model', data)
  },

  // 获取语义模型详情
  getDetail(id) {
    return get(`/api/semantic-model/${id}`)
  },

  // 更新语义模型
  update(id, data) {
    return put(`/api/semantic-model/${id}`, data)
  },

  // 删除语义模型
  delete(id) {
    return del(`/api/semantic-model/${id}`)
  },

  // 批量启用/禁用
  batchEnable(datasetId, enabled) {
    return post(`/api/semantic-model/dataset/${datasetId}/batch-enable`, { enabled })
  },

  // 获取数据集列表
  getDatasets() {
    return get('/api/semantic-model/datasets')
  },

  // 创建语义模型
  createSemanticModel(data) {
    return post('/api/semantic-model', data)
  },

  // 更新语义模型
  updateSemanticModel(id, data) {
    return put(`/api/semantic-model/${id}`, data)
  },

  // 删除语义模型
  deleteSemanticModel(id) {
    return del(`/api/semantic-model/${id}`)
  }
}

// 数据源管理API
export const datasourceApi = {
  // 获取数据源列表
  getByAgentId(agentId, params = {}) {
    return get(`/api/datasource/agent/${agentId}`, params)
  },

  // 获取数据源详情
  getDetail(id) {
    return get(`/api/datasource/${id}`)
  },

  // 创建数据源
  create(data) {
    return post('/api/datasource', data)
  },

  // 更新数据源
  update(id, data) {
    return put(`/api/datasource/${id}`, data)
  },

  // 删除数据源
  delete(id) {
    return del(`/api/datasource/${id}`)
  },

  // 批量更新状态
  batchUpdateStatus(data) {
    return post('/api/datasource/batch-status', data)
  },

  // 获取统计信息
  getStatistics(agentId) {
    return get(`/api/datasource/agent/${agentId}/statistics`)
  },

  // 搜索数据源
  search(agentId, keyword) {
    return get(`/api/datasource/agent/${agentId}/search`, { keyword })
  },

  // 根据类型获取数据源
  getByType(agentId, type) {
    return get(`/api/datasource/agent/${agentId}/type/${type}`)
  },

  // 根据状态获取数据源
  getByStatus(agentId, status) {
    return get(`/api/datasource/agent/${agentId}/status/${status}`)
  },

  // 获取所有数据源列表（用于选择已有数据源）
  getList(params = {}) {
    return get('/api/datasource', params)
  },

  // 获取智能体的数据源
  getAgentDatasources(agentId) {
    return get(`/api/datasource/agent/${agentId}`)
  },

  // 添加数据源到智能体
  addToAgent(agentId, datasourceId) {
    return post(`/api/datasource/agent/${agentId}`, { datasourceId })
  },

  // 从智能体移除数据源
  removeFromAgent(agentId, datasourceId) {
    return del(`/api/datasource/agent/${agentId}/${datasourceId}`)
  },

  // 切换数据源状态
  toggleDatasource(agentId, datasourceId, isActive) {
    return put(`/api/datasource/agent/${agentId}/${datasourceId}/toggle`, { isActive })
  },

  // 测试数据源连接
  testConnection(id) {
    return post(`/api/datasource/${id}/test`)
  }
}

// 系统管理API
export const systemAPI = {
  // 获取系统配置列表
  getList(params = {}) {
    return get('/api/system/configs', params)
  },

  // 获取系统配置详情
  getDetail(id) {
    return get(`/api/system/configs/${id}`)
  },

  // 创建系统配置
  create(data) {
    return post('/api/system/configs', data)
  },

  // 更新系统配置
  update(id, data) {
    return put(`/api/system/configs/${id}`, data)
  },

  // 删除系统配置
  delete(id) {
    return del(`/api/system/configs/${id}`)
  },

  // 测试连接
  testConnection(id) {
    return post(`/api/system/configs/${id}/test`)
  },

  // 获取统计信息
  getStatistics() {
    return get('/api/system/statistics')
  }
}

// 调试API
export const debugAPI = {
  // 创建调试流
  createDebugStream(agentId, query) {
    return stream(`/nl2sql/stream/search?query=${encodeURIComponent(query)}&agentId=${agentId}`)
  },

  // 获取调试历史
  getDebugHistory(agentId, params = {}) {
    return get(`/api/agent/${agentId}/debug/history`, params)
  },

  // 清空调试历史
  clearDebugHistory(agentId) {
    return del(`/api/agent/${agentId}/debug/history`)
  },

  // 获取调试统计
  getDebugStatistics(agentId) {
    return get(`/api/agent/${agentId}/debug/statistics`)
  },

  // 保存调试会话
  saveDebugSession(agentId, sessionData) {
    return post(`/api/agent/${agentId}/debug/session`, sessionData)
  }
}

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

// 文件上传API
export const uploadAPI = {
  // 上传头像
  uploadAvatar(file) {
    return upload('/api/upload/avatar', file)
  },

  // 上传文件
  uploadFile(file, onProgress) {
    return upload('/api/upload/file', file, onProgress)
  }
}

// Schema管理API
export const schemaAPI = {
  // 获取Schema统计信息
  getStatistics(agentId) {
    return get(`/api/agent/${agentId}/schema/statistics`)
  },

  // 初始化Schema
  init(agentId, data) {
    return post(`/api/agent/${agentId}/schema/init`, data)
  },

  // 清空Schema数据
  clear(agentId) {
    return del(`/api/agent/${agentId}/schema/clear`)
  },

  // 获取数据源列表
  getDatasources(agentId) {
    return get(`/api/agent/${agentId}/schema/datasources`)
  },

  // 获取数据源表列表
  getTables(agentId, datasourceId) {
    return get(`/api/agent/${agentId}/schema/datasources/${datasourceId}/tables`)
  }
}
