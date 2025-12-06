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

interface BusinessKnowledgeVO {
  id?: number;
  businessTerm: string;
  description: string;
  synonyms: string;
  isRecall: boolean; // 0 or 1
  agentId: number;
  createdTime?: string;
  updatedTime?: string;
  embeddingStatus?: string; // 嵌入状态
  errorMsg?: string; // 错误信息
}

interface CreateBusinessKnowledgeDTO {
  businessTerm: string;
  description: string;
  synonyms: string;
  isRecall: boolean; // 0 or 1
  agentId: number;
}

interface UpdateBusinessKnowledgeDTO {
  businessTerm: string;
  description: string;
  synonyms: string;
  agentId: number;
}

const API_BASE_URL = '/api/business-knowledge';

class BusinessKnowledgeService {
  /**
   * 获取业务知识列表
   * @param agentId 代理ID
   * @param keyword 搜索关键词（可选）
   * @returns 业务知识列表
   */
  async list(agentId: number, keyword?: string): Promise<BusinessKnowledgeVO[]> {
    try {
      const params = { agentId: agentId.toString() };
      if (keyword) {
        params.keyword = keyword;
      }
      const response = await axios.get<ApiResponse<BusinessKnowledgeVO[]>>(API_BASE_URL, {
        params,
      });
      return response.data.data || [];
    } catch (error) {
      console.error('Failed to fetch business knowledge list:', error);
      throw error;
    }
  }

  /**
   * 根据 ID 获取业务知识详情
   * @param id 业务知识 ID
   */
  async get(id: number): Promise<BusinessKnowledgeVO | null> {
    try {
      const response = await axios.get<ApiResponse<BusinessKnowledgeVO>>(`${API_BASE_URL}/${id}`);
      return response.data.data || null;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * 创建业务知识
   * @param knowledge 业务知识 DTO 对象
   */
  async create(knowledge: CreateBusinessKnowledgeDTO): Promise<BusinessKnowledgeVO> {
    const response = await axios.post<ApiResponse<BusinessKnowledgeVO>>(API_BASE_URL, knowledge);
    return response.data.data!;
  }

  /**
   * 更新业务知识
   * @param id 业务知识 ID
   * @param knowledge 业务知识 DTO 对象
   */
  async update(
    id: number,
    knowledge: UpdateBusinessKnowledgeDTO,
  ): Promise<BusinessKnowledgeVO | null> {
    try {
      const response = await axios.put<ApiResponse<BusinessKnowledgeVO>>(
        `${API_BASE_URL}/${id}`,
        knowledge,
      );
      return response.data.data || null;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * 删除业务知识
   * @param id 业务知识 ID
   */
  async delete(id: number): Promise<boolean> {
    try {
      const response = await axios.delete<ApiResponse<boolean>>(`${API_BASE_URL}/${id}`);
      return response.data.success;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return false;
      }
      throw error;
    }
  }

  /**
   * 设置业务知识召回状态
   * @param id 业务知识 ID
   * @param isRecall 是否召回 (true or false)
   */
  async recallKnowledge(id: number, isRecall: boolean): Promise<boolean> {
    const response = await axios.post<ApiResponse<boolean>>(`${API_BASE_URL}/recall/${id}`, null, {
      params: { isRecall },
    });
    return response.data.success;
  }

  /**
   * 重试业务知识向量化
   * @param id 业务知识 ID
   */
  async retryEmbedding(id: number): Promise<boolean> {
    const response = await axios.post<ApiResponse<boolean>>(
      `${API_BASE_URL}/retry-embedding/${id}`,
    );
    return response.data.success;
  }

  /**
   * 刷新所有业务知识到向量存储
   * @param agentId Agent ID
   */
  async refreshAllKnowledgeToVectorStore(agentId: string): Promise<boolean> {
    const response = await axios.post<ApiResponse<boolean>>(
      `${API_BASE_URL}/refresh-vector-store`,
      null,
      {
        params: { agentId },
      },
    );
    return response.data.success;
  }
}

export default new BusinessKnowledgeService();
export type { BusinessKnowledgeVO, CreateBusinessKnowledgeDTO, UpdateBusinessKnowledgeDTO };
