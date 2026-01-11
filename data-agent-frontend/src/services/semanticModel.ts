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

interface SemanticModel {
  id?: number;
  agentId: number;
  datasourceId?: number;
  tableName: string;
  columnName: string;
  businessName: string;
  synonyms: string;
  businessDescription: string;
  columnComment: string;
  dataType: string;
  status: number;
  createdTime?: string;
  updateTime?: string;
}

interface SemanticModelAddDto {
  agentId: number;
  tableName: string;
  columnName: string;
  businessName: string;
  synonyms: string;
  businessDescription: string;
  columnComment: string;
  dataType: string;
}

interface SemanticModelImportItem {
  tableName: string;
  columnName: string;
  businessName: string;
  synonyms?: string;
  businessDescription?: string;
  columnComment?: string;
  dataType: string;
}

interface SemanticModelBatchImportDTO {
  agentId: number;
  items: SemanticModelImportItem[];
}

interface BatchImportResult {
  total: number;
  successCount: number;
  failCount: number;
  errors: string[];
}

const API_BASE_URL = '/api/semantic-model';

class SemanticModelService {
  /**
   * 获取语义模型列表
   * @param agentId 关联的 Agent ID
   * @param keyword 搜索关键词
   */
  async list(agentId?: number, keyword?: string): Promise<SemanticModel[]> {
    const params: { agentId?: string; keyword?: string } = {};
    if (agentId !== undefined) params.agentId = agentId.toString();
    if (keyword) params.keyword = keyword;

    const response = await axios.get<ApiResponse<SemanticModel[]>>(API_BASE_URL, { params });
    return response.data.data || [];
  }

  /**
   * 根据 ID 获取语义模型详情
   * @param id 语义模型 ID
   */
  async get(id: number): Promise<SemanticModel | null> {
    try {
      const response = await axios.get<ApiResponse<SemanticModel>>(`${API_BASE_URL}/${id}`);
      return response.data.data || null;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * 创建语义模型
   * @param model 语义模型 DTO 对象
   */
  async create(model: SemanticModelAddDto): Promise<boolean> {
    const response = await axios.post<ApiResponse>(API_BASE_URL, model);
    return response.data.success;
  }

  /**
   * 更新语义模型
   * @param id 语义模型 ID
   * @param model 语义模型对象
   */
  async update(id: number, model: SemanticModel): Promise<boolean> {
    try {
      const response = await axios.put<ApiResponse>(`${API_BASE_URL}/${id}`, model);
      return response.data.success;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return false;
      }
      throw error;
    }
  }

  /**
   * 删除语义模型
   * @param id 语义模型 ID
   */
  async delete(id: number): Promise<boolean> {
    try {
      const response = await axios.delete<ApiResponse>(`${API_BASE_URL}/${id}`);
      return response.data.success;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return false;
      }
      throw error;
    }
  }

  /**
   * 批量删除语义模型
   * @param ids 语义模型 ID 列表
   */
  async batchDelete(ids: number[]): Promise<boolean> {
    const response = await axios.delete<ApiResponse>(`${API_BASE_URL}/batch`, {
      data: ids,
    });
    return response.data.success;
  }

  /**
   * 启用语义模型
   * @param ids 语义模型 ID 列表
   */
  async enable(ids: number[]): Promise<boolean> {
    const response = await axios.put<ApiResponse>(`${API_BASE_URL}/enable`, ids);
    return response.data.success;
  }

  /**
   * 禁用语义模型
   * @param ids 语义模型 ID 列表
   */
  async disable(ids: number[]): Promise<boolean> {
    const response = await axios.put<ApiResponse>(`${API_BASE_URL}/disable`, ids);
    return response.data.success;
  }

  /**
   * 批量导入语义模型
   * @param dto 批量导入DTO
   */
  async batchImport(dto: SemanticModelBatchImportDTO): Promise<BatchImportResult> {
    const response = await axios.post<ApiResponse<BatchImportResult>>(
      `${API_BASE_URL}/batch-import`,
      dto,
    );
    return response.data.data || { total: 0, successCount: 0, failCount: 0, errors: [] };
  }

  /**
   * Excel文件导入
   * @param file Excel文件
   * @param agentId 智能体ID
   */
  async importExcel(file: File, agentId: number): Promise<BatchImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('agentId', agentId.toString());

    const response = await axios.post<ApiResponse<BatchImportResult>>(
      `${API_BASE_URL}/import/excel`,
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      },
    );
    return response.data.data || { total: 0, successCount: 0, failCount: 0, errors: [] };
  }

  /**
   * 下载Excel模板
   */
  async downloadTemplate(): Promise<void> {
    const response = await axios.get(`${API_BASE_URL}/template/download`, {
      responseType: 'blob',
    });

    // 创建下载链接
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'semantic_model_template.xlsx');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  }
}

export default new SemanticModelService();
export type {
  SemanticModel,
  SemanticModelAddDto,
  SemanticModelImportItem,
  SemanticModelBatchImportDTO,
  BatchImportResult,
};
