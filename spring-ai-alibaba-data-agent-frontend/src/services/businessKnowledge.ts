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

interface BusinessKnowledge {
  id?: number;
  businessTerm: string;
  description: string;
  synonyms: string;
  isRecall: boolean;
  agentId: number;
  createTime?: string;
  updateTime?: string;
}

interface BusinessKnowledgeDTO {
  businessTerm: string;
  description: string;
  synonyms: string;
  isRecall: boolean;
  agentId: number;
}

const API_BASE_URL = '/api/business-knowledge';

class BusinessKnowledgeService {
  /**
   * 获取业务知识列表
   * @param agentId 关联的 Agent ID
   * @param keyword 搜索关键词
   */
  async list(agentId?: number, keyword?: string): Promise<BusinessKnowledge[]> {
    const params: { agentId?: string; keyword?: string } = {};
    if (agentId !== undefined) params.agentId = agentId.toString();
    if (keyword) params.keyword = keyword;

    const response = await axios.get<BusinessKnowledge[]>(API_BASE_URL, { params });
    return response.data;
  }

  /**
   * 根据 ID 获取业务知识详情
   * @param id 业务知识 ID
   */
  async get(id: number): Promise<BusinessKnowledge | null> {
    try {
      const response = await axios.get<BusinessKnowledge>(`${API_BASE_URL}/${id}`);
      return response.data;
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
  async create(knowledge: BusinessKnowledgeDTO): Promise<BusinessKnowledge> {
    const response = await axios.post<BusinessKnowledge>(API_BASE_URL, knowledge);
    return response.data;
  }

  /**
   * 更新业务知识
   * @param id 业务知识 ID
   * @param knowledge 业务知识 DTO 对象
   */
  async update(id: number, knowledge: BusinessKnowledgeDTO): Promise<BusinessKnowledge | null> {
    try {
      const response = await axios.put<BusinessKnowledge>(`${API_BASE_URL}/${id}`, knowledge);
      return response.data;
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
   * 设置业务知识召回状态
   * @param id 业务知识 ID
   * @param isRecall 是否召回
   */
  async recallKnowledge(id: number, isRecall: boolean): Promise<boolean> {
    const response = await axios.post<boolean>(`${API_BASE_URL}/recall/${id}`, null, {
      params: { isRecall },
    });
    return response.data;
  }
}

export default new BusinessKnowledgeService();
export type { BusinessKnowledge, BusinessKnowledgeDTO };
