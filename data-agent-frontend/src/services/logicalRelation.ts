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
import { ApiResponse } from '@/services/common';

// 逻辑外键接口定义
export interface LogicalRelation {
  id?: number;
  datasourceId?: number;
  sourceTableName: string;
  sourceColumnName: string;
  targetTableName: string;
  targetColumnName: string;
  relationType?: string; // 1:1, 1:N, N:1
  description?: string;
  isDeleted?: number;
  createdTime?: string;
  updatedTime?: string;
}

const API_BASE_URL = '/api/datasource';

class LogicalRelationService {
  // 获取指定数据源的逻辑外键列表
  async getLogicalRelations(datasourceId: number): Promise<LogicalRelation[]> {
    try {
      const response = await axios.get<ApiResponse<LogicalRelation[]>>(
        `${API_BASE_URL}/${datasourceId}/logical-relations`,
      );
      return response.data.data || [];
    } catch (error) {
      console.error('Failed to get logical relations:', error);
      return [];
    }
  }

  // 添加逻辑外键
  async addLogicalRelation(
    datasourceId: number,
    logicalRelation: Omit<
      LogicalRelation,
      'id' | 'datasourceId' | 'isDeleted' | 'createdTime' | 'updatedTime'
    >,
  ): Promise<LogicalRelation | null> {
    try {
      const response = await axios.post<ApiResponse<LogicalRelation>>(
        `${API_BASE_URL}/${datasourceId}/logical-relations`,
        logicalRelation,
      );
      return response.data.data || null;
    } catch (error) {
      console.error('Failed to add logical relation:', error);
      throw error;
    }
  }

  // 更新逻辑外键
  async updateLogicalRelation(
    datasourceId: number,
    relationId: number,
    logicalRelation: Omit<
      LogicalRelation,
      'id' | 'datasourceId' | 'isDeleted' | 'createdTime' | 'updatedTime'
    >,
  ): Promise<ApiResponse<LogicalRelation>> {
    const response = await axios.put<ApiResponse<LogicalRelation>>(
      `${API_BASE_URL}/${datasourceId}/logical-relations/${relationId}`,
      logicalRelation,
    );
    return response.data;
  }

  // 删除逻辑外键
  async deleteLogicalRelation(
    datasourceId: number,
    relationId: number,
  ): Promise<ApiResponse<void>> {
    const response = await axios.delete<ApiResponse<void>>(
      `${API_BASE_URL}/${datasourceId}/logical-relations/${relationId}`,
    );
    return response.data;
  }

  // 批量保存逻辑外键（替换现有的所有外键）
  async saveLogicalRelations(
    datasourceId: number,
    logicalRelations: LogicalRelation[],
  ): Promise<ApiResponse<LogicalRelation[]>> {
    const response = await axios.put<ApiResponse<LogicalRelation[]>>(
      `${API_BASE_URL}/${datasourceId}/logical-relations`,
      logicalRelations,
    );
    return response.data;
  }

  // 获取数据源表的字段列表
  async getTableColumns(datasourceId: number, tableName: string): Promise<string[]> {
    try {
      const response = await axios.get<ApiResponse<string[]>>(
        `${API_BASE_URL}/${datasourceId}/tables/${tableName}/columns`,
      );
      if (response.data.success) {
        return response.data.data || [];
      }
      throw new Error(response.data.message);
    } catch (error) {
      console.error('Failed to get table columns:', error);
      return [];
    }
  }
}

export default new LogicalRelationService();
