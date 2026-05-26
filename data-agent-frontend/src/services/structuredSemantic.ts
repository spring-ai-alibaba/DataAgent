/*
 * Copyright 2024-2026 the original author or authors.
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
import { ApiResponse, PageResponse } from '@/services/common';

export interface SemanticTableItem {
  id?: number;
  agentId: number;
  datasourceId: number;
  tableName: string;
  businessName?: string;
  synonyms?: string;
  businessDescription?: string;
  tableComment?: string;
  isVisible?: number;
  status?: number;
  createdTime?: string;
  updateTime?: string;
}

export interface SemanticColumnItem {
  id?: number;
  agentId: number;
  datasourceId: number;
  tableName: string;
  columnName: string;
  businessName?: string;
  synonyms?: string;
  businessDescription?: string;
  columnComment?: string;
  dataType?: string;
  status?: number;
  createdTime?: string;
  updateTime?: string;
}

export interface SemanticRelationItem {
  id?: number;
  agentId: number;
  datasourceId: number;
  sourceTableName: string;
  sourceColumnNames: string;
  targetTableName: string;
  targetColumnNames: string;
  relationType?: string;
  description?: string;
  status?: number;
  createdTime?: string;
  updateTime?: string;
}

export interface SemanticTableQuery {
  agentId: number;
  datasourceId: number;
  keyword?: string;
  pageNum?: number;
  pageSize?: number;
}

export interface SemanticColumnQuery extends SemanticTableQuery {
  tableName: string;
}

export interface SemanticRelationQuery extends SemanticTableQuery {
  tableName?: string;
}

export interface SemanticTableUpsertDTO {
  agentId: number;
  datasourceId: number;
  tableName: string;
  businessName?: string;
  synonyms?: string;
  businessDescription?: string;
  tableComment?: string;
  isVisible?: number;
  status?: number;
}

export interface SemanticColumnUpsertDTO {
  agentId: number;
  datasourceId: number;
  tableName: string;
  columnName: string;
  businessName?: string;
  synonyms?: string;
  businessDescription?: string;
  columnComment?: string;
  dataType?: string;
  isVisible?: number;
  status?: number;
}

export interface SemanticRelationUpsertDTO {
  agentId: number;
  datasourceId: number;
  sourceTableName: string;
  sourceColumnNames: string;
  targetTableName: string;
  targetColumnNames: string;
  relationType?: string;
  description?: string;
  status?: number;
}

const API_BASE_URL = '/api/semantic';

const extractApiErrorMessage = (error: unknown, fallback: string): string => {
  if (axios.isAxiosError(error)) {
    const responseMessage = error.response?.data?.message;
    if (typeof responseMessage === 'string' && responseMessage.trim()) {
      return responseMessage;
    }
    if (typeof error.message === 'string' && error.message.trim()) {
      return error.message;
    }
  }
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }
  return fallback;
};

class StructuredSemanticService {
  async listTables(query: SemanticTableQuery): Promise<PageResponse<SemanticTableItem[]>> {
    try {
      const response = await axios.get<PageResponse<SemanticTableItem[]>>(`${API_BASE_URL}/tables`, {
        params: query,
      });
      return response.data;
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '加载表语义失败'));
    }
  }

  async createTable(dto: SemanticTableUpsertDTO): Promise<SemanticTableItem> {
    try {
      const response = await axios.post<ApiResponse<SemanticTableItem>>(`${API_BASE_URL}/tables`, dto);
      if (response.data.success && response.data.data) {
        return response.data.data;
      }
      throw new Error(response.data.message || '创建表语义失败');
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '创建表语义失败'));
    }
  }

  async updateTable(id: number, dto: SemanticTableUpsertDTO): Promise<SemanticTableItem> {
    try {
      const response = await axios.put<ApiResponse<SemanticTableItem>>(
        `${API_BASE_URL}/tables/${id}`,
        dto,
      );
      if (response.data.success && response.data.data) {
        return response.data.data;
      }
      throw new Error(response.data.message || '更新表语义失败');
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '更新表语义失败'));
    }
  }

  async deleteTable(id: number): Promise<void> {
    try {
      const response = await axios.delete<ApiResponse<boolean>>(`${API_BASE_URL}/tables/${id}`);
      if (!response.data.success) {
        throw new Error(response.data.message || '删除表语义失败');
      }
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '删除表语义失败'));
    }
  }

  async listColumns(query: SemanticColumnQuery): Promise<PageResponse<SemanticColumnItem[]>> {
    try {
      const response = await axios.get<PageResponse<SemanticColumnItem[]>>(
        `${API_BASE_URL}/columns`,
        {
          params: query,
        },
      );
      return response.data;
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '加载列语义失败'));
    }
  }

  async createColumn(dto: SemanticColumnUpsertDTO): Promise<SemanticColumnItem> {
    try {
      const response = await axios.post<ApiResponse<SemanticColumnItem>>(
        `${API_BASE_URL}/columns`,
        dto,
      );
      if (response.data.success && response.data.data) {
        return response.data.data;
      }
      throw new Error(response.data.message || '创建列语义失败');
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '创建列语义失败'));
    }
  }

  async updateColumn(id: number, dto: SemanticColumnUpsertDTO): Promise<SemanticColumnItem> {
    try {
      const response = await axios.put<ApiResponse<SemanticColumnItem>>(
        `${API_BASE_URL}/columns/${id}`,
        dto,
      );
      if (response.data.success && response.data.data) {
        return response.data.data;
      }
      throw new Error(response.data.message || '更新列语义失败');
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '更新列语义失败'));
    }
  }

  async deleteColumn(id: number): Promise<void> {
    try {
      const response = await axios.delete<ApiResponse<boolean>>(`${API_BASE_URL}/columns/${id}`);
      if (!response.data.success) {
        throw new Error(response.data.message || '删除列语义失败');
      }
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '删除列语义失败'));
    }
  }

  async listRelations(query: SemanticRelationQuery): Promise<PageResponse<SemanticRelationItem[]>> {
    try {
      const response = await axios.get<PageResponse<SemanticRelationItem[]>>(
        `${API_BASE_URL}/relations`,
        {
          params: query,
        },
      );
      return response.data;
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '加载关系语义失败'));
    }
  }

  async createRelation(dto: SemanticRelationUpsertDTO): Promise<SemanticRelationItem> {
    try {
      const response = await axios.post<ApiResponse<SemanticRelationItem>>(
        `${API_BASE_URL}/relations`,
        dto,
      );
      if (response.data.success && response.data.data) {
        return response.data.data;
      }
      throw new Error(response.data.message || '创建关系语义失败');
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '创建关系语义失败'));
    }
  }

  async updateRelation(id: number, dto: SemanticRelationUpsertDTO): Promise<SemanticRelationItem> {
    try {
      const response = await axios.put<ApiResponse<SemanticRelationItem>>(
        `${API_BASE_URL}/relations/${id}`,
        dto,
      );
      if (response.data.success && response.data.data) {
        return response.data.data;
      }
      throw new Error(response.data.message || '更新关系语义失败');
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '更新关系语义失败'));
    }
  }

  async deleteRelation(id: number): Promise<void> {
    try {
      const response = await axios.delete<ApiResponse<boolean>>(`${API_BASE_URL}/relations/${id}`);
      if (!response.data.success) {
        throw new Error(response.data.message || '删除关系语义失败');
      }
    } catch (error) {
      throw new Error(extractApiErrorMessage(error, '删除关系语义失败'));
    }
  }
}

export default new StructuredSemanticService();
