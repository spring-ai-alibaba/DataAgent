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

/**
 * 知识库实体
 */
export interface AgentKnowledge {
  id?: number;
  agentId?: number;
  title?: string;
  content?: string;
  type?: string;
  question?: string;
  isRecall?: boolean; // true=召回, false=非召回
  embeddingStatus?: string;
  errorMsg?: string;
  createdTime?: string;
  updatedTime?: string;
}

/**
 * 分页查询请求参数
 */
export interface AgentKnowledgeQueryDTO {
  agentId: number;
  title?: string;
  type?: string;
  embeddingStatus?: string;
  pageNum?: number;
  pageSize?: number;
}

/**
 * 分页查询响应
 */
export interface PageResult<T> {
  success: boolean;
  data: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  totalPages: number;
  message?: string;
}

const API_BASE_URL = '/api/agent-knowledge';

class AgentKnowledgeService {
  /**
   * 分页查询知识列表（支持多条件过滤）
   */
  async queryByPage(queryDTO: AgentKnowledgeQueryDTO): Promise<PageResult<AgentKnowledge>> {
    const response = await axios.post<PageResult<AgentKnowledge>>(
      `${API_BASE_URL}/query/page`,
      queryDTO,
    );
    return response.data;
  }

  /**
   * 根据智能体ID获取知识列表
   */
  async listByAgentId(
    agentId: number,
    type?: string,
    status?: string,
    keyword?: string,
  ): Promise<AgentKnowledge[]> {
    const params: Record<string, string | number> = {};
    if (type) params.type = type;
    if (status) params.status = status;
    if (keyword) params.keyword = keyword;

    const response = await axios.get<{ success: boolean; data: AgentKnowledge[] }>(
      `${API_BASE_URL}/agent/${agentId}`,
      { params },
    );
    return response.data.data;
  }

  /**
   * 根据ID获取知识详情
   */
  async getById(id: number): Promise<AgentKnowledge | null> {
    try {
      const response = await axios.get<{ success: boolean; data: AgentKnowledge }>(
        `${API_BASE_URL}/${id}`,
      );
      return response.data.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * 创建知识
   */
  async create(knowledge: AgentKnowledge): Promise<AgentKnowledge> {
    const response = await axios.post<{ success: boolean; data: AgentKnowledge }>(
      `${API_BASE_URL}/create`,
      knowledge,
    );
    return response.data.data;
  }

  /**
   * 更新知识
   */
  async update(id: number, knowledge: Partial<AgentKnowledge>): Promise<AgentKnowledge | null> {
    try {
      const response = await axios.put<{ success: boolean; data: AgentKnowledge }>(
        `${API_BASE_URL}/${id}`,
        knowledge,
      );
      return response.data.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * 更新召回状态
   */
  async updateRecallStatus(id: number, recalled: boolean): Promise<AgentKnowledge | null> {
    try {
      const response = await axios.put<{ success: boolean; data: AgentKnowledge }>(
        `${API_BASE_URL}/recall/${id}`,
        null,
        {
          params: {
            isRecall: recalled,
          },
        },
      );
      return response.data.data;
    } catch (error) {
      console.error('Failed to update recall status:', error);
      return null;
    }
  }

  /**
   * 删除知识
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
   * 重试向量化
   */
  async retryEmbedding(id: number): Promise<boolean> {
    try {
      const response = await axios.post<{ success: boolean }>(
        `${API_BASE_URL}/retry-embedding/${id}`,
      );
      return response.data.success;
    } catch (error) {
      console.error('Failed to retry embedding:', error);
      return false;
    }
  }

  /**
   * 获取统计信息
   */
  async getStatistics(agentId: number): Promise<{
    totalCount: number;
    typeStatistics: Array<[string, number]>;
  }> {
    const response = await axios.get<{
      success: boolean;
      data: {
        totalCount: number;
        typeStatistics: Array<[string, number]>;
      };
    }>(`${API_BASE_URL}/statistics/${agentId}`);
    return response.data.data;
  }
}

export default new AgentKnowledgeService();
