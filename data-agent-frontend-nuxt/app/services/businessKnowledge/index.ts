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

/**
 * @description 业务术语知识管理服务，处理业务词汇的增删改查、召回设置及向量同步
 */

import axios from 'axios';
import type { ApiResponse } from '~/services/common/index';

/**
 * @description 业务术语视图对象
 */
export interface BusinessKnowledgeVO {
  /** 术语 ID */
  id?: number;
  /** 业务术语名称 */
  businessTerm: string;
  /** 术语描述 */
  description: string;
  /** 同义词 (逗号分隔) */
  synonyms: string;
  /** 是否召回 */
  isRecall: boolean;
  /** 关联的智能体 ID */
  agentId: number;
  /** 创建时间 */
  createdTime?: string;
  /** 更新时间 */
  updatedTime?: string;
  /** 向量化状态 */
  embeddingStatus?: string;
  /** 错误信息 */
  errorMsg?: string;
}

/**
 * @description 创建业务术语的 DTO
 */
export interface CreateBusinessKnowledgeDTO {
  /** 业务术语名称 */
  businessTerm: string;
  /** 术语描述 */
  description: string;
  /** 同义词 */
  synonyms: string;
  /** 是否召回 */
  isRecall: boolean;
  /** 智能体 ID */
  agentId: number;
}

/**
 * @description 更新业务术语的 DTO
 */
export interface UpdateBusinessKnowledgeDTO {
  /** 业务术语名称 */
  businessTerm: string;
  /** 术语描述 */
  description: string;
  /** 同义词 */
  synonyms: string;
  /** 智能体 ID */
  agentId: number;
}

const API_BASE_URL = '/api/business-knowledge';

/**
 * @description 业务术语知识业务逻辑处理类
 */
class BusinessKnowledgeService {
  /**
   * @description 获取业务术语列表
   * @param {number} agentId - 智能体 ID
   * @param {string} [keyword] - 搜索关键词
   * @returns {Promise<BusinessKnowledgeVO[]>} 术语列表
   */
  async list(agentId: number, keyword?: string): Promise<BusinessKnowledgeVO[]> {
    try {
      const params: Record<string, string> = { agentId: agentId.toString() };
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
   * @description 根据 ID 获取业务术语详情
   * @param {number} id - 术语 ID
   * @returns {Promise<BusinessKnowledgeVO | null>} 术语详情
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
   * @description 创建新业务术语
   * @param {CreateBusinessKnowledgeDTO} knowledge - 术语信息
   * @returns {Promise<BusinessKnowledgeVO>} 创建成功的术语详情
   */
  async create(knowledge: CreateBusinessKnowledgeDTO): Promise<BusinessKnowledgeVO> {
    const response = await axios.post<ApiResponse<BusinessKnowledgeVO>>(API_BASE_URL, knowledge);
    return response.data.data!;
  }

  /**
   * @description 更新业务术语信息
   * @param {number} id - 术语 ID
   * @param {UpdateBusinessKnowledgeDTO} knowledge - 更新字段
   * @returns {Promise<BusinessKnowledgeVO | null>} 更新后的术语详情
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
   * @description 删除指定业务术语
   * @param {number} id - 术语 ID
   * @returns {Promise<boolean>} 是否删除成功
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
   * @description 设置业务术语的召回状态
   * @param {number} id - 术语 ID
   * @param {boolean} isRecall - 是否召回
   * @returns {Promise<boolean>} 是否操作成功
   */
  async recallKnowledge(id: number, isRecall: boolean): Promise<boolean> {
    const response = await axios.post<ApiResponse<boolean>>(`${API_BASE_URL}/recall/${id}`, null, {
      params: { isRecall },
    });
    return response.data.success;
  }

  /**
   * @description 重新触发业务术语的向量化处理
   * @param {number} id - 术语 ID
   * @returns {Promise<boolean>} 是否成功触发
   */
  async retryEmbedding(id: number): Promise<boolean> {
    const response = await axios.post<ApiResponse<boolean>>(
      `${API_BASE_URL}/retry-embedding/${id}`,
    );
    return response.data.success;
  }

  /**
   * @description 刷新智能体下所有的业务术语到向量存储
   * @param {string} agentId - 智能体 ID
   * @returns {Promise<boolean>} 是否成功触发刷新
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
