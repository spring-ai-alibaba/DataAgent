/*
 * Copyright 2024-2025 the original author or authors.
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

import axios from 'axios';
import type { ApiResponse } from './common';

export interface ModelConfig {
  id?: number;
  provider: string; // e.g. "openai", "deepseek"
  apiKey: string;
  baseUrl: string;
  modelName: string;
  modelType: string; // "CHAT" or "EMBEDDING"
  temperature?: number;
  maxTokens?: number;
  isActive?: boolean;
  completionsPath?: string; // 对话模型路径
  embeddingsPath?: string; // 嵌入模型路径
  proxyEnabled?: boolean; //代理开关，默认为关闭（直连）
  proxyHost?: string;
  proxyPort?: number;
  proxyUsername?: string;
  proxyPassword?: string;
}

export interface ModelCheckReady {
  chatModelReady: boolean;
  embeddingModelReady: boolean;
  ready: boolean;
}

const API_BASE_URL = '/api/model-config';

class ModelConfigService {
  /**
   * 获取模型配置列表
   */
  async list(): Promise<ModelConfig[]> {
    const response = await axios.get<ApiResponse<ModelConfig[]>>(`${API_BASE_URL}/list`);
    return response.data.data || [];
  }

  /**
   * 新增模型配置
   * @param config 模型配置对象
   */
  async add(config: Omit<ModelConfig, 'id'>): Promise<ApiResponse<string>> {
    console.log('config: ' + config);
    const response = await axios.post<ApiResponse<string>>(`${API_BASE_URL}/add`, config);
    return response.data;
  }

  /**
   * 更新模型配置
   * @param config 模型配置对象
   */
  async update(config: ModelConfig): Promise<ApiResponse<string>> {
    const response = await axios.put<ApiResponse<string>>(`${API_BASE_URL}/update`, config);
    return response.data;
  }

  /**
   * 删除模型配置
   * @param id 配置ID
   */
  async delete(id: number): Promise<ApiResponse<string>> {
    const response = await axios.delete<ApiResponse<string>>(`${API_BASE_URL}/${id}`);
    return response.data;
  }

  /**
   * 启用/切换模型配置
   * @param id 配置ID
   */
  async activate(id: number): Promise<ApiResponse<string>> {
    const response = await axios.post<ApiResponse<string>>(`${API_BASE_URL}/activate/${id}`);
    return response.data;
  }

  /**
   * 测试模型配置连接
   * @param config 模型配置对象
   */
  async testConnection(config: Omit<ModelConfig, 'id'>): Promise<ApiResponse<string>> {
    const response = await axios.post<ApiResponse<string>>(`${API_BASE_URL}/test`, config);
    return response.data;
  }

  /**
   * 检查模型配置是否就绪（聊天模型和嵌入模型都需要配置）
   */
  async checkReady(): Promise<ModelCheckReady> {
    const response = await axios.get<ApiResponse<ModelCheckReady>>(`${API_BASE_URL}/check-ready`);
    return (
      response.data.data || { chatModelReady: false, embeddingModelReady: false, ready: false }
    );
  }
}

export default new ModelConfigService();
