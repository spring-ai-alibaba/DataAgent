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

export interface Datasource {
  id?: number;
  name?: string;
  type?: string;
  host?: string;
  port?: number;
  databaseName?: string;
  username?: string;
  password?: string;
  connectionUrl?: string;
  status?: string;
  testStatus?: string;
  description?: string;
  creatorId?: number;
  createTime?: string; // 使用字符串表示日期时间，格式为 "yyyy-MM-dd HH:mm:ss"
  updateTime?: string; // 使用字符串表示日期时间，格式为 "yyyy-MM-dd HH:mm:ss"
}

// 定义 AgentDatasource 接口
export interface AgentDatasource {
  id?: number;
  agentId?: number;
  datasourceId?: number;
  isActive?: number | boolean;
  createTime?: string;
  updateTime?: string;
  datasource?: Datasource;
  selectTables?: string[];
}

const API_BASE_URL = '/api/datasource';

class DatasourceService {
  // 1. 获取所有数据源列表
  async getAllDatasource(status?: string, type?: string): Promise<Datasource[]> {
    const params = new URLSearchParams();
    if (status) params.append('status', status);
    if (type) params.append('type', type);

    const response = await axios.get<Datasource[]>(
      `${API_BASE_URL}${params.toString() ? `?${params.toString()}` : ''}`,
    );
    return response.data;
  }

  // 2. 根据 ID 获取数据源详情
  async getDatasourceById(id: number): Promise<Datasource | null> {
    try {
      const response = await axios.get<Datasource>(`${API_BASE_URL}/${id}`);
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  // 3. 获取数据源的表列表
  async getDatasourceTables(id: number): Promise<string[]> {
    try {
      const response = await axios.get<string[]>(`${API_BASE_URL}/${id}/tables`);
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 400) {
        return [];
      }
      throw error;
    }
  }

  // 4. 创建数据源
  async createDatasource(datasource: Datasource): Promise<Datasource> {
    const response = await axios.post<Datasource>(API_BASE_URL, datasource);
    return response.data;
  }

  // 5. 更新数据源
  async updateDatasource(id: number, datasource: Datasource): Promise<Datasource> {
    const response = await axios.put<Datasource>(`${API_BASE_URL}/${id}`, datasource);
    return response.data;
  }

  // 6. 删除数据源
  async deleteDatasource(id: number): Promise<ApiResponse<void>> {
    const response = await axios.delete<ApiResponse<void>>(`${API_BASE_URL}/${id}`);
    return response.data;
  }

  // 7. 测试数据源连接
  async testConnection(id: number): Promise<ApiResponse<boolean>> {
    const response = await axios.post<ApiResponse<boolean>>(`${API_BASE_URL}/${id}/test`);
    return response.data;
  }
}

export default new DatasourceService();
