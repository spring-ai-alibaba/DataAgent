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
import { ApiResponse } from './common';

export interface Agent {
  id?: number;
  name?: string;
  description?: string;
  avatar?: string;
  status?: string;
  apiKey?: string | null;
  apiKeyEnabled?: number | boolean;
  prompt?: string;
  category?: string;
  adminId?: number;
  tags?: string;
  createTime?: Date;
  updateTime?: Date;
  humanReviewEnabled?: number | boolean; // 0 or 1, default is 0
}

const API_BASE_URL = '/api/agent';

export interface AgentApiKeyResponse {
  apiKey: string | null;
  apiKeyEnabled: number | boolean;
}

export type AgentApiKeyApiResult = ApiResponse<AgentApiKeyResponse>;

class AgentService {
  /**
   * 获取 Agent 列表
   * @param status 状态筛选
   * @param keyword 关键词搜索
   */
  async list(status?: string, keyword?: string): Promise<Agent[]> {
    const params: Record<string, string> = {};
    if (status) params.status = status;
    if (keyword) params.keyword = keyword;

    const response = await axios.get<Agent[]>(`${API_BASE_URL}/list`, { params });
    return response.data;
  }

  /**
   * 根据 ID 获取 Agent 详情
   * @param id Agent ID
   */
  async get(id: number): Promise<Agent | null> {
    try {
      const response = await axios.get<Agent>(`${API_BASE_URL}/${id}`);
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * 创建 Agent
   * @param agent Agent 对象
   */
  async create(agent: Omit<Agent, 'id'>): Promise<Agent> {
    // 设置默认状态为 draft
    const agentData = {
      ...agent,
      status: agent.status || 'draft',
    };

    const response = await axios.post<Agent>(API_BASE_URL, agentData);
    return response.data;
  }

  /**
   * 更新 Agent
   * @param id Agent ID
   * @param agent Agent 对象
   */
  async update(id: number, agent: Partial<Agent>): Promise<Agent | null> {
    try {
      // 只传递可以修改的字段
      const agentData = {
        id: agent.id,
        name: agent.name,
        description: agent.description,
        avatar: agent.avatar,
        status: agent.status,
        prompt: agent.prompt,
        category: agent.category,
        tags: agent.tags,
        humanReviewEnabled: agent.humanReviewEnabled ? 1 : 0,
      };
      const response = await axios.put<Agent>(`${API_BASE_URL}/${id}`, agentData);
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * 删除 Agent
   * @param id Agent ID
   */
  async delete(id: number): Promise<boolean> {
    try {
      await axios.delete(`${API_BASE_URL}/${id}`);
      return true;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return false;
      }
      throw error;
    }
  }

  /**
   * 发布 Agent
   * @param id Agent ID
   */
  async publish(id: number): Promise<Agent | null> {
    try {
      const response = await axios.post<Agent>(`${API_BASE_URL}/${id}/publish`);
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * 下线 Agent
   * @param id Agent ID
   */
  async offline(id: number): Promise<Agent | null> {
    try {
      const response = await axios.post<Agent>(`${API_BASE_URL}/${id}/offline`);
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * 获取 API Key（遮罩态）
   */
  async getApiKey(id: number): Promise<AgentApiKeyResponse | null> {
    try {
      const response = await axios.get<AgentApiKeyApiResult>(`${API_BASE_URL}/${id}/api-key`);
      if (response.data.success) {
        return response.data.data ?? null;
      }
      throw new Error(response.data.message);
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * 生成/重置 API Key
   */
  async generateApiKey(id: number): Promise<AgentApiKeyResponse> {
    const response = await axios.post<AgentApiKeyApiResult>(
      `${API_BASE_URL}/${id}/api-key/generate`,
    );
    if (response.data.success && response.data.data) {
      return response.data.data;
    }
    throw new Error(response.data.message || '生成 API Key 失败');
  }

  async resetApiKey(id: number): Promise<AgentApiKeyResponse> {
    const response = await axios.post<AgentApiKeyApiResult>(`${API_BASE_URL}/${id}/api-key/reset`);
    if (response.data.success && response.data.data) {
      return response.data.data;
    }
    throw new Error(response.data.message || '重置 API Key 失败');
  }

  /**
   * 删除 API Key
   */
  async deleteApiKey(id: number): Promise<AgentApiKeyResponse> {
    const response = await axios.delete<AgentApiKeyApiResult>(`${API_BASE_URL}/${id}/api-key`);
    if (response.data.success && response.data.data) {
      return response.data.data;
    }
    throw new Error(response.data.message || '删除 API Key 失败');
  }

  /**
   * 启用/禁用 API Key
   */
  async toggleApiKey(id: number, enabled: boolean): Promise<AgentApiKeyResponse> {
    const response = await axios.post<AgentApiKeyApiResult>(
      `${API_BASE_URL}/${id}/api-key/enable`,
      null,
      {
        params: { enabled },
      },
    );
    if (response.data.success && response.data.data) {
      return response.data.data;
    }
    throw new Error(response.data.message || '更新 API Key 状态失败');
  }
}

export default new AgentService();
