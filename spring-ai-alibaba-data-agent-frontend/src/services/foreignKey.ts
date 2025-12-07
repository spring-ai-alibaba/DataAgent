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
export interface ForeignKey {
  id?: number;
  datasourceId?: number;
  sourceTable: string;
  sourceColumn: string;
  targetTable: string;
  targetColumn: string;
  description?: string;
  createTime?: string;
  updateTime?: string;
}

const API_BASE_URL = '/api/datasource';

class ForeignKeyService {
  // 获取指定数据源的逻辑外键列表
  async getForeignKeys(datasourceId: number): Promise<ForeignKey[]> {
    try {
      const response = await axios.get<ForeignKey[]>(
        `${API_BASE_URL}/${datasourceId}/foreign-keys`,
      );
      return response.data;
    } catch (error) {
      console.error('Failed to get foreign keys:', error);
      return [];
    }
  }

  // 添加逻辑外键
  async addForeignKey(datasourceId: number, foreignKey: ForeignKey): Promise<ForeignKey> {
    const response = await axios.post<ForeignKey>(
      `${API_BASE_URL}/${datasourceId}/foreign-keys`,
      foreignKey,
    );
    return response.data;
  }

  // 删除逻辑外键
  async deleteForeignKey(datasourceId: number, foreignKeyId: number): Promise<ApiResponse<void>> {
    const response = await axios.delete<ApiResponse<void>>(
      `${API_BASE_URL}/${datasourceId}/foreign-keys/${foreignKeyId}`,
    );
    return response.data;
  }

  // 批量保存逻辑外键（替换现有的所有外键）
  async saveForeignKeys(
    datasourceId: number,
    foreignKeys: ForeignKey[],
  ): Promise<ApiResponse<ForeignKey[]>> {
    const response = await axios.put<ApiResponse<ForeignKey[]>>(
      `${API_BASE_URL}/${datasourceId}/foreign-keys`,
      foreignKeys,
    );
    return response.data;
  }

  // 获取数据源表的字段列表
  async getTableColumns(datasourceId: number, tableName: string): Promise<string[]> {
    try {
      const response = await axios.get<string[]>(
        `${API_BASE_URL}/${datasourceId}/tables/${tableName}/columns`,
      );
      return response.data;
    } catch (error) {
      console.error('Failed to get table columns:', error);
      return [];
    }
  }
}

export default new ForeignKeyService();
