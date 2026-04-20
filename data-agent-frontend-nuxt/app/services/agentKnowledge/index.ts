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
 * @description 智能体知识库管理服务，处理知识的分页查询、增删改查及向量化重试
 */

import axios from 'axios';

/**
 * @description 知识库实体接口
 */
export interface AgentKnowledge {
  /** 知识 ID */
  id?: number;
  /** 关联的智能体 ID */
  agentId?: number;
  /** 标题 */
  title?: string;
  /** 内容 */
  content?: string;
  /** 类型 (如 text, file) */
  type?: string;
  /** 问题 (针对 QA 类型) */
  question?: string;
  /** 是否召回 (true=召回, false=非召回) */
  isRecall?: boolean;
  /** 向量化状态 */
  embeddingStatus?: string;
  /** 错误信息 */
  errorMsg?: string;
  /** 创建时间 */
  createdTime?: string;
  /** 更新时间 */
  updatedTime?: string;
}

/**
 * @description 知识库分页查询 DTO
 */
export interface AgentKnowledgeQueryDTO {
  /** 智能体 ID */
  agentId: number;
  /** 标题关键词 */
  title?: string;
  /** 知识类型 */
  type?: string;
  /** 向量化状态 */
  embeddingStatus?: string;
  /** 当前页码 */
  pageNum?: number;
  /** 每页条数 */
  pageSize?: number;
}

/**
 * @description 通用分页响应结构
 */
export interface PageResult<T> {
  /** 是否成功 */
  success: boolean;
  /** 数据列表 */
  data: T[];
  /** 总条数 */
  total: number;
  /** 当前页码 */
  pageNum: number;
  /** 每页条数 */
  pageSize: number;
  /** 总页数 */
  totalPages: number;
  /** 提示消息 */
  message?: string;
}

const API_BASE_URL = '/api/agent-knowledge';

/**
 * @description 智能体知识库业务逻辑处理类
 */
class AgentKnowledgeService {
  /**
   * @description 分页查询知识列表
   * @param {AgentKnowledgeQueryDTO} queryDTO - 查询条件
   * @returns {Promise<PageResult<AgentKnowledge>>} 分页结果
   */
  async queryByPage(queryDTO: AgentKnowledgeQueryDTO): Promise<PageResult<AgentKnowledge>> {
    const response = await axios.post<PageResult<AgentKnowledge>>(
      `${API_BASE_URL}/query/page`,
      queryDTO,
    );
    return response.data;
  }

  /**
   * @description 根据智能体 ID 获取所有知识列表
   * @param {number} agentId - 智能体 ID
   * @param {string} [type] - 类型筛选
   * @param {string} [status] - 状态筛选
   * @param {string} [keyword] - 关键词搜索
   * @returns {Promise<AgentKnowledge[]>} 知识列表
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
   * @description 根据 ID 获取知识详情
   * @param {number} id - 知识 ID
   * @returns {Promise<AgentKnowledge | null>} 知识详情
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
   * @description 创建新知识
   * @param {AgentKnowledge} knowledge - 知识信息
   * @returns {Promise<AgentKnowledge>} 创建成功的知识
   */
  async create(knowledge: AgentKnowledge): Promise<AgentKnowledge> {
    const response = await axios.post<{ success: boolean; data: AgentKnowledge }>(
      `${API_BASE_URL}/create`,
      knowledge,
    );
    return response.data.data;
  }

  /**
   * @description 通过 FormData 创建知识（支持文件上传）
   * @param {FormData} formData - 包含文件与知识元数据的 FormData
   * @returns {Promise<{success: boolean; message?: string}>} 操作结果
   */
  async createWithFile(formData: FormData): Promise<{ success: boolean; message?: string }> {
    const response = await axios.post<{ success: boolean; message?: string }>(
      `${API_BASE_URL}/create`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return response.data;
  }

  /**
   * @description 更新知识信息
   * @param {number} id - 知识 ID
   * @param {Partial<AgentKnowledge>} knowledge - 更新字段
   * @returns {Promise<AgentKnowledge | null>} 更新后的知识详情
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
   * @description 更新知识的召回状态
   * @param {number} id - 知识 ID
   * @param {boolean} recalled - 是否召回
   * @returns {Promise<AgentKnowledge | null>} 更新后的知识详情
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
   * @description 删除指定知识
   * @param {number} id - 知识 ID
   * @returns {Promise<boolean>} 是否删除成功
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
   * @description 重新触发知识的向量化处理
   * @param {number} id - 知识 ID
   * @returns {Promise<boolean>} 是否成功触发
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
   * @description 获取智能体知识库的统计信息
   * @param {number} agentId - 智能体 ID
   * @returns {Promise<{totalCount: number, typeStatistics: Array<[string, number]>}>} 统计数据
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
